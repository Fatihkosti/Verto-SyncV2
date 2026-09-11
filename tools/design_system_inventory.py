#!/usr/bin/env python3
"""Deterministic design-system component and public API inventory (Session 302)."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path

CORE_PACKAGE = "com.verto.app.ui.components"
CORE_DIR = Path("core/designsystem/src/main/kotlin/com/verto/app/ui/components")
LEGACY_COMPONENTS = [
    "Button", "OutlinedButton", "TextButton", "OutlinedTextField", "TextField",
    "Checkbox", "RadioButton", "Switch", "IconButton", "AlertDialog",
    "ModalBottomSheet", "LinearProgressIndicator", "CircularProgressIndicator",
    "VertoCard", "VertoTextField", "VertoPrimaryButton", "VertoSecondaryButton",
    "VertoStatusBanner", "VertoLoadingState", "VertoEmptyState",
]
SUPPORTING_HELPERS = {"dialogFieldColors"}


def production_kotlin(root: Path) -> list[Path]:
    return [
        p for p in sorted(root.rglob("*.kt"))
        if "/src/main/kotlin/" in p.relative_to(root).as_posix()
        and "/build/" not in p.relative_to(root).as_posix()
    ]


def strip_comments_and_strings(text: str) -> str:
    pattern = re.compile(
        r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
        re.S,
    )
    def blank(match: re.Match[str]) -> str:
        value = match.group(0)
        return "".join("\n" if c == "\n" else " " for c in value)
    return pattern.sub(blank, text)


def package_name(text: str) -> str:
    m = re.search(r"^package\s+([A-Za-z0-9_.]+)\s*$", text, re.M)
    return m.group(1) if m else ""


def module_name(rel: str) -> str:
    return rel.split("/src/main/kotlin/", 1)[0]


def imports_for(text: str) -> tuple[set[str], bool]:
    explicit: set[str] = set()
    wildcard = False
    for m in re.finditer(r"^import\s+([^\s]+)", text, re.M):
        target = m.group(1)
        if target == CORE_PACKAGE + ".*": wildcard = True
        elif target.startswith(CORE_PACKAGE + "."):
            explicit.add(target.rsplit(".", 1)[-1])
    return explicit, wildcard


def public_declarations(root: Path) -> list[dict]:
    base = root / CORE_DIR
    records: list[dict] = []
    for path in sorted(base.glob("*.kt")):
        rel = path.relative_to(root).as_posix()
        text = path.read_text(errors="replace")
        lines = text.splitlines()
        for idx, line in enumerate(lines, 1):
            # Public/default-public top-level functions only. Top-level declarations are unindented.
            m = re.match(r"^fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", line)
            if m:
                name = m.group(1)
                start = idx
                sig_lines = []
                depth = 0; seen = False
                for l in lines[idx-1:]:
                    sig_lines.append(l.strip())
                    for c in l:
                        if c == "(": depth += 1; seen = True
                        elif c == ")": depth -= 1
                    if seen and depth == 0: break
                signature = " ".join(sig_lines)
                records.append({"symbol": name, "kind": "fun", "file": rel, "line": start, "signature": signature})
                continue
            m = re.match(r"^(?:enum\s+class|sealed\s+class|class|interface)\s+([A-Z][A-Za-z0-9_]*)\b", line)
            if m:
                records.append({"symbol": m.group(1), "kind": "type", "file": rel, "line": idx, "signature": line.strip()})
                continue
            m = re.match(r"^(?:val|var)\s+([A-Z][A-Za-z0-9_]*)\b", line)
            if m:
                records.append({"symbol": m.group(1), "kind": "prop", "file": rel, "line": idx, "signature": line.strip()})
    return records


def simple_local_declared(code: str, name: str) -> bool:
    pats = [
        rf"^\s*(?:private\s+|internal\s+)?fun\s+{re.escape(name)}\s*\(",
        rf"^\s*(?:private\s+|internal\s+)?(?:val|var)\s+{re.escape(name)}\b",
        rf"^\s*(?:private\s+|internal\s+)?(?:class|object|interface|enum\s+class)\s+{re.escape(name)}\b",
    ]
    return any(re.search(p, code, re.M) for p in pats)


def consumer_stats(root: Path, decl: dict, infos: list[dict]) -> dict:
    name = decl["symbol"]
    kind = decl["kind"]
    declaration_file = decl["file"]
    total = 0; matched_files: list[str] = []; modules: set[str] = set(); syntaxes: set[str] = set()
    core_internal = 0
    fq = CORE_PACKAGE + "." + name
    for info in infos:
        rel = info["rel"]
        pkg = info["package"]
        explicit = info["explicit"]
        wildcard = info["wildcard"]
        executable = info["executable"]
        simple_allowed = pkg == CORE_PACKAGE or name in explicit or wildcard
        # In non-core files, a local same-name declaration shadows name-only attribution.
        if pkg != CORE_PACKAGE and simple_local_declared(executable, name):
            simple_allowed = False

        count = 0
        if kind == "fun":
            if simple_allowed:
                paren = len(re.findall(rf"(?<![A-Za-z0-9_.]){re.escape(name)}\s*\(", executable))
                trailing = len(re.findall(rf"(?<![A-Za-z0-9_.]){re.escape(name)}\s*\{{", executable))
                count += paren + trailing
                if paren: syntaxes.add("Name(...)")
                if trailing: syntaxes.add("Name { ... }")
            fq_paren = len(re.findall(rf"(?<![A-Za-z0-9_]){re.escape(fq)}\s*\(", executable))
            fq_trailing = len(re.findall(rf"(?<![A-Za-z0-9_]){re.escape(fq)}\s*\{{", executable))
            count += fq_paren + fq_trailing
            if fq_paren or fq_trailing: syntaxes.add("fully-qualified")
            # Exclude the top-level declaration itself in its owning file.
            if rel == declaration_file:
                count -= len(re.findall(rf"^fun\s+{re.escape(name)}\s*\(", executable, re.M))
        else:
            if simple_allowed:
                count += len(re.findall(rf"(?<![A-Za-z0-9_.]){re.escape(name)}\b", executable))
            count += len(re.findall(rf"(?<![A-Za-z0-9_]){re.escape(fq)}\b", executable))
            if rel == declaration_file:
                if kind == "type": count -= len(re.findall(rf"^(?:enum\s+class|sealed\s+class|class|interface)\s+{re.escape(name)}\b", executable, re.M))
                else: count -= len(re.findall(rf"^(?:val|var)\s+{re.escape(name)}\b", executable, re.M))
        if count > 0:
            total += count; matched_files.append(rel); modules.add(module_name(rel))
            if pkg == CORE_PACKAGE: core_internal += count
    return {
        "externalCallCount": total,
        "externalFileCount": len(matched_files),
        "externalModuleCount": len(modules),
        "files": matched_files,
        "modules": sorted(modules),
        "callSyntaxEvidence": sorted(syntaxes),
        "coreInternalCallCount": core_internal,
    }


def inventory(root: Path) -> dict:
    files = production_kotlin(root)
    infos = []
    for path in files:
        rel = path.relative_to(root).as_posix()
        raw = path.read_text(errors="replace")
        code = strip_comments_and_strings(raw)
        explicit, wildcard = imports_for(raw)
        infos.append({
            "path": path,
            "rel": rel,
            "package": package_name(raw),
            "explicit": explicit,
            "wildcard": wildcard,
            "executable": re.sub(r"^(?:package|import)\s+.*$", "", code, flags=re.M),
        })
    declarations = public_declarations(root)
    api_rows = []
    for d in declarations:
        stats = consumer_stats(root, d, infos)
        row = dict(d)
        row.update(stats)
        row["signatureFingerprint"] = hashlib.sha256(d["signature"].encode()).hexdigest()
        api_rows.append(row)

    symbol_counts: dict[str, int] = defaultdict(int)
    for d in declarations: symbol_counts[d["symbol"]] += 1
    public_zero = sum(1 for r in api_rows if r["externalCallCount"] == 0)
    duplicate_symbols = sum(1 for n in symbol_counts.values() if n > 1)
    function_count = sum(1 for d in declarations if d["kind"] == "fun")
    type_count = sum(1 for d in declarations if d["kind"] == "type")
    prop_count = sum(1 for d in declarations if d["kind"] == "prop")
    visual_count = function_count - sum(1 for d in declarations if d["symbol"] in SUPPORTING_HELPERS)

    usage: dict[str, dict[str, object]] = {}
    for name in LEGACY_COMPONENTS:
        pattern = re.compile(rf"(?<![A-Za-z0-9_]){re.escape(name)}\s*(?:\(|\{{)")
        matches_files: list[str] = []; count = 0
        for info in infos:
            rel = info["rel"]
            n = len(pattern.findall(info["executable"]))
            if n: matches_files.append(rel); count += n
        usage[name] = {"count": count, "files": matches_files}

    local_components: dict[str, list[str]] = defaultdict(list)
    declarations_re = re.compile(r"(?:fun|class|object|interface)\s+([A-Z][A-Za-z0-9_]*)")
    for info in infos:
        rel = info["rel"]
        for match in declarations_re.finditer(info["executable"]):
            name = match.group(1)
            if name.endswith(("Screen", "Content", "Components", "Card", "Row", "Dialog", "Sheet")):
                local_components[name].append(rel)

    return {
        "schemaVersion": 302,
        "publicApiSurface": {
            "declarationCount": len(declarations),
            "uniqueSymbolCount": len(symbol_counts),
            "publicFunctionCount": function_count,
            "supportingTypeCount": type_count,
            "supportingPropertyCount": prop_count,
            "officialVisualComponentCount": visual_count,
            "publicZeroConsumerCount": public_zero,
            "duplicatePublicSymbolCount": duplicate_symbols,
            "declarations": api_rows,
        },
        "components": usage,
        "localComponentDeclarations": {
            k: sorted(v) for k, v in sorted(local_components.items()) if len(v) > 1
        },
    }


def write_outputs(payload: dict, json_path: Path | None, md_path: Path | None) -> None:
    if json_path:
        json_path.parent.mkdir(parents=True, exist_ok=True)
        json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=False) + "\n")
    if md_path:
        md_path.parent.mkdir(parents=True, exist_ok=True)
        api = payload["publicApiSurface"]
        lines = [
            "# Component Inventory", "",
            "Generated deterministically from production Kotlin sources.", "",
            "## Public API Surface", "",
            f"- Schema: `{payload['schemaVersion']}`",
            f"- Declarations: `{api['declarationCount']}`",
            f"- Unique symbols: `{api['uniqueSymbolCount']}`",
            f"- Public functions: `{api['publicFunctionCount']}`",
            f"- Supporting types: `{api['supportingTypeCount']}`",
            f"- Supporting properties: `{api['supportingPropertyCount']}`",
            f"- Official visual/composable components: `{api['officialVisualComponentCount']}`",
            f"- Zero-consumer public declarations: `{api['publicZeroConsumerCount']}`",
            f"- Duplicate public symbols: `{api['duplicatePublicSymbolCount']}`", "",
            "| Symbol | Kind | Calls | Files | Modules | Source |", "|---|---|---:|---:|---:|---|",
        ]
        for row in api["declarations"]:
            lines.append(f"| `{row['symbol']}` | `{row['kind']}` | {row['externalCallCount']} | {row['externalFileCount']} | {row['externalModuleCount']} | `{row['file']}:{row['line']}` |")
        lines += ["", "## Legacy Component Usage", "", "| Component | Uses | Files |", "|---|---:|---:|"]
        for name, value in payload["components"].items():
            lines.append(f"| `{name}` | {value['count']} | {len(value['files'])} |")
        lines += ["", "## Duplicate declaration candidates", ""]
        candidates = payload["localComponentDeclarations"]
        if candidates:
            for name, paths in candidates.items(): lines.append(f"- `{name}`: {', '.join(f'`{f}`' for f in paths)}")
        else:
            lines.append("- None detected by the name-based preflight; manual contract review remains required.")
        md_path.write_text("\n".join(lines) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--json", dest="json_path")
    parser.add_argument("--md", dest="md_path")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    write_outputs(inventory(root), Path(args.json_path) if args.json_path else None, Path(args.md_path) if args.md_path else None)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
