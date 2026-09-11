#!/usr/bin/env python3
"""Verto documentation drift gate (v318).

Uses only Python standard library. The repository root defaults to the script's
location and may be overridden only by --root for isolated mutation tests.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path, PurePosixPath
from typing import Iterable
from urllib.parse import unquote

CHECK_IDS = (
    "D1_CANONICAL_REGISTRY",
    "D2_METADATA",
    "D3_INTERNAL_LINKS",
    "D4_DEPRECATED_REFERENCES",
    "D5_RPC_DRIFT",
    "D6_MODULE_MAP_DRIFT",
    "D7_FEATURE_COVERAGE",
    "D8_CANONICAL_NAMING",
    "D9_INDEX_INTEGRITY",
)
REQUIRED_METADATA = ("status", "scope", "owner", "last_verified_against")
CURRENT_LIFECYCLE = {"canonical", "draft", "deprecated", "archived"}
EVIDENCE_LIFECYCLE = {"supporting"}
FORBIDDEN_CANONICAL_NAME = re.compile(
    r"(?:^|[-_.])(FINAL2?|LATEST|NEW|FIXED|REVISED|UPDATED_FINAL)(?:[-_.]|$)", re.I
)
MD_LINK_RE = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
RPC_CALL_RE = re.compile(r"\.rpc\s*\(", re.M)
RPC_LITERAL_RE = re.compile(
    r"\.rpc\s*\(\s*(?:(?:function|fn|name)\s*=\s*)?\"([^\"]+)\"",
    re.S,
)
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$", re.M)


@dataclass
class CheckResult:
    id: str
    status: str
    metrics: dict
    errors: list[str]


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except Exception as exc:
        raise RuntimeError(f"unreadable governed file: {path}: {exc}") from exc


def posix_rel(root: Path, path: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def parse_frontmatter(path: Path) -> dict[str, str] | None:
    text = read_text(path)
    if not text.startswith("---\n"):
        return None
    end = text.find("\n---\n", 4)
    if end < 0:
        raise ValueError(f"malformed frontmatter: {path}")
    values: dict[str, str] = {}
    for line_no, line in enumerate(text[4:end].splitlines(), 2):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if ":" not in line:
            raise ValueError(f"malformed frontmatter line {line_no}: {path}")
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if not key:
            raise ValueError(f"empty frontmatter key line {line_no}: {path}")
        if key in values:
            raise ValueError(f"duplicate frontmatter key {key}: {path}")
        values[key] = value
    return values


def parse_markdown_table(text: str, expected_first: str) -> tuple[list[str], list[list[str]]]:
    lines = text.splitlines()
    header_idx = None
    for idx, line in enumerate(lines):
        if line.startswith("|"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if cells and cells[0] == expected_first:
                header_idx = idx
                header = cells
                break
    if header_idx is None:
        raise ValueError(f"table header not found: {expected_first}")
    if header_idx + 1 >= len(lines) or not lines[header_idx + 1].lstrip().startswith("|---"):
        raise ValueError(f"table separator missing: {expected_first}")
    rows: list[list[str]] = []
    for line in lines[header_idx + 2 :]:
        if not line.startswith("|"):
            if rows:
                break
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != len(header):
            raise ValueError(f"ambiguous table row has {len(cells)} cells, expected {len(header)}: {line[:120]}")
        rows.append(cells)
    if not rows:
        raise ValueError(f"empty table: {expected_first}")
    return header, rows


def canonical_rows(root: Path) -> list[dict[str, str]]:
    path = root / "docs/CANONICAL_DOCUMENT_MAP.md"
    header, rows = parse_markdown_table(read_text(path), "Responsibility")
    parsed = []
    for row in rows:
        item = dict(zip(header, row))
        doc = item.get("Canonical document", "").strip().strip("`")
        responsibility = item.get("Responsibility", "").strip()
        if not responsibility or not doc:
            raise ValueError(f"empty canonical responsibility/path in {path}")
        item["Canonical document"] = doc
        item["Responsibility"] = responsibility
        parsed.append(item)
    return parsed


def all_markdown(root: Path) -> list[Path]:
    ignored_parts = {".git", "build", ".gradle"}
    return sorted(
        p for p in root.rglob("*.md")
        if not any(part in ignored_parts for part in p.relative_to(root).parts)
    )


def governed_documents(root: Path) -> list[Path]:
    docs = []
    for p in all_markdown(root):
        fm = parse_frontmatter(p)
        if fm is not None:
            docs.append(p)
    return docs


def canonical_documents_from_metadata(root: Path) -> set[str]:
    out = set()
    for p in all_markdown(root):
        fm = parse_frontmatter(p)
        if fm and fm.get("status") == "canonical":
            out.add(posix_rel(root, p))
    return out


def check_result(cid: str, errors: Iterable[str], **metrics) -> CheckResult:
    errs = sorted(set(str(e) for e in errors))
    return CheckResult(cid, "PASS" if not errs else "FAIL", metrics, errs)


def d1(root: Path) -> CheckResult:
    errors: list[str] = []
    rows = canonical_rows(root)
    paths = [r["Canonical document"] for r in rows]
    responsibilities = [r["Responsibility"] for r in rows]
    for p in paths:
        target = root / p
        if not target.is_file():
            errors.append(f"canonical target missing: {p}")
            continue
        try:
            fm = parse_frontmatter(target)
        except Exception as exc:
            errors.append(str(exc))
            continue
        if not fm or fm.get("status") != "canonical":
            errors.append(f"canonical map target is not status=canonical: {p}")
        if p.startswith("docs/archive/"):
            errors.append(f"archive path owns canonical responsibility: {p}")
    for value in sorted(set(paths)):
        count = paths.count(value)
        if count != 1:
            errors.append(f"canonical document represented {count} times: {value}")
    for value in sorted(set(responsibilities)):
        count = responsibilities.count(value)
        if count != 1:
            errors.append(f"duplicate canonical responsibility ({count}): {value}")
    metadata_canonical = canonical_documents_from_metadata(root)
    mapped = set(paths)
    for p in sorted(metadata_canonical - mapped):
        errors.append(f"orphan status=canonical document: {p}")
    for p in sorted(mapped - metadata_canonical):
        errors.append(f"mapped canonical not discoverable by metadata: {p}")
    return check_result(
        CHECK_IDS[0], errors,
        canonical_documents=len(metadata_canonical),
        registry_rows=len(rows),
        duplicate_responsibilities=sum(responsibilities.count(x) - 1 for x in set(responsibilities)),
        orphan_canonical=len(metadata_canonical - mapped),
    )


def d2(root: Path) -> CheckResult:
    errors: list[str] = []
    rows = canonical_rows(root)
    mapped = {r["Canonical document"] for r in rows}
    statuses: dict[str, int] = {}
    for path in governed_documents(root):
        rel = posix_rel(root, path)
        fm = parse_frontmatter(path) or {}
        for key in REQUIRED_METADATA:
            if key not in fm:
                errors.append(f"missing metadata {key}: {rel}")
            elif not fm[key].strip():
                errors.append(f"empty metadata {key}: {rel}")
        status = fm.get("status", "")
        statuses[status] = statuses.get(status, 0) + 1
        if status not in CURRENT_LIFECYCLE | EVIDENCE_LIFECYCLE:
            errors.append(f"unsupported lifecycle status={status!r}: {rel}")
        if rel in mapped and status != "canonical":
            errors.append(f"canonical map path must be status=canonical: {rel} ({status})")
        if status == "supporting" and rel in mapped:
            errors.append(f"supporting evidence cannot substitute canonical owner: {rel}")
    return check_result(CHECK_IDS[1], errors, governed_documents=sum(statuses.values()), statuses=statuses)


def split_link_target(raw: str) -> tuple[str, str]:
    raw = raw.strip()
    if raw.startswith("<") and raw.endswith(">"):
        raw = raw[1:-1]
    # Optional markdown title follows whitespace after the target.
    if " " in raw and not raw.startswith(("http://", "https://")):
        raw = raw.split(None, 1)[0]
    if "#" in raw:
        path, frag = raw.split("#", 1)
    else:
        path, frag = raw, ""
    return unquote(path), unquote(frag)


def is_external(target: str) -> bool:
    low = target.lower()
    return low.startswith(("http://", "https://", "mailto:", "tel:"))


def resolve_link(root: Path, source: Path, target: str) -> Path | None:
    if target == "":
        return source
    if target.startswith("/"):
        candidate = root / target.lstrip("/")
    else:
        candidate = source.parent / target
    candidate = candidate.resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError:
        return None
    if candidate.is_dir():
        for name in ("README.md", "INDEX.md", "index.md"):
            idx = candidate / name
            if idx.is_file():
                return idx
    return candidate


def github_anchor(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"[`*_~]", "", text)
    text = text.strip().lower()
    text = re.sub(r"[^\w\- ]", "", text, flags=re.UNICODE)
    text = re.sub(r"\s+", "-", text)
    return text


def document_anchors(path: Path) -> set[str]:
    text = read_text(path)
    seen: dict[str, int] = {}
    anchors: set[str] = set()
    for m in HEADING_RE.finditer(text):
        base = github_anchor(m.group(2))
        if not base:
            continue
        n = seen.get(base, 0)
        seen[base] = n + 1
        anchors.add(base if n == 0 else f"{base}-{n}")
    return anchors


def links_from(path: Path) -> list[tuple[str, str]]:
    text = read_text(path)
    out = []
    for line_no, line in enumerate(text.splitlines(), 1):
        # Remove inline code spans so code examples are never parsed as links.
        scrubbed = re.sub(r"`[^`]*`", "", line)
        for m in MD_LINK_RE.finditer(scrubbed):
            out.append((m.group(1).strip(), str(line_no)))
    return out


def validate_link(root: Path, source: Path, raw: str, line_no: str, check_fragments: bool = True) -> str | None:
    path_part, fragment = split_link_target(raw)
    if is_external(path_part):
        return None
    target = resolve_link(root, source, path_part)
    rel_source = posix_rel(root, source)
    if target is None:
        return f"unsupported/out-of-repository internal link: {rel_source}:{line_no} -> {raw}"
    if not target.exists():
        return f"broken internal link: {rel_source}:{line_no} -> {raw}"
    if target.is_dir():
        return f"unsupported directory link without index: {rel_source}:{line_no} -> {raw}"
    if fragment and check_fragments and target.suffix.lower() == ".md":
        if fragment not in document_anchors(target):
            return f"broken markdown anchor: {rel_source}:{line_no} -> {raw}"
    return None


def d3(root: Path) -> CheckResult:
    errors: list[str] = []
    checked = 0
    for source in governed_documents(root):
        for raw, line_no in links_from(source):
            path_part, _ = split_link_target(raw)
            if is_external(path_part):
                continue
            checked += 1
            err = validate_link(root, source, raw, line_no)
            if err:
                errors.append(err)
    return check_result(CHECK_IDS[2], errors, internal_links_checked=checked, broken_internal_links=len(errors))


def active_index_lines(index_text: str) -> list[tuple[int, str]]:
    active = True
    lines = []
    for n, line in enumerate(index_text.splitlines(), 1):
        if re.match(r"^##+\s+", line):
            heading = re.sub(r"^##+\s+", "", line).strip().lower()
            if "histor" in heading or "archive" in heading or "supporting" in heading:
                active = False
            elif heading:
                active = True
        if active:
            lines.append((n, line))
    return lines


def status_of(root: Path, path: Path) -> str | None:
    if not path.is_file() or path.suffix.lower() != ".md":
        return None
    fm = parse_frontmatter(path)
    return fm.get("status") if fm else None


def d4(root: Path) -> CheckResult:
    errors: list[str] = []
    rows = canonical_rows(root)
    for row in rows:
        rel = row["Canonical document"]
        p = root / rel
        if rel.startswith("docs/archive/"):
            errors.append(f"archived current authority in canonical map: {rel}")
        if p.is_file() and status_of(root, p) in {"deprecated", "archived"}:
            errors.append(f"deprecated/archived current authority in canonical map: {rel}")

    index = root / "docs/INDEX.md"
    for n, line in active_index_lines(read_text(index)):
        scrubbed = re.sub(r"`[^`]*`", "", line)
        for m in MD_LINK_RE.finditer(scrubbed):
            raw = m.group(1)
            path_part, _ = split_link_target(raw)
            if is_external(path_part):
                continue
            target = resolve_link(root, index, path_part)
            if target and target.exists():
                rel = posix_rel(root, target)
                if rel.startswith("docs/archive/") or status_of(root, target) in {"deprecated", "archived"}:
                    errors.append(f"active INDEX promotes deprecated/archive target: docs/INDEX.md:{n} -> {rel}")

    readme = root / "README.md"
    for raw, line_no in links_from(readme):
        path_part, _ = split_link_target(raw)
        if is_external(path_part):
            continue
        target = resolve_link(root, readme, path_part)
        if target and target.exists():
            rel = posix_rel(root, target)
            if rel.startswith("docs/archive/") or status_of(root, target) in {"deprecated", "archived"}:
                errors.append(f"README promotes deprecated/archive target: README.md:{line_no} -> {rel}")

    alias = root / "docs/design-system/DESIGN-SYSTEM-CONTRACT.md"
    if not alias.is_file():
        errors.append("known Design System compatibility alias missing")
    elif status_of(root, alias) != "deprecated":
        errors.append("known Design System compatibility alias is not status=deprecated")
    if any(r["Canonical document"] == "docs/design-system/DESIGN-SYSTEM-CONTRACT.md" for r in rows):
        errors.append("known deprecated Design System compatibility alias owns Canonical responsibility")
    return check_result(
        CHECK_IDS[3], errors,
        deprecated_active_references=len(errors),
        archived_current_authorities=sum("archived current authority" in e for e in errors),
    )


def production_kotlin(root: Path) -> list[Path]:
    out = []
    for p in root.rglob("*.kt"):
        rel = p.relative_to(root).as_posix()
        if "/src/main/" in f"/{rel}" and not rel.startswith(("build/", "docs/archive/")):
            out.append(p)
    return sorted(out)


def extract_rpc_calls(root: Path) -> tuple[list[tuple[str, str, int]], list[str]]:
    calls = []
    unsupported = []
    for p in production_kotlin(root):
        text = read_text(p)
        literal_by_start = {m.start(): m.group(1) for m in RPC_LITERAL_RE.finditer(text)}
        for m in RPC_CALL_RE.finditer(text):
            line = text.count("\n", 0, m.start()) + 1
            if m.start() not in literal_by_start:
                excerpt = text[m.start():m.start()+120].splitlines()[0]
                unsupported.append(f"dynamic/unsupported RPC invocation: {posix_rel(root,p)}:{line}: {excerpt}")
            else:
                calls.append((literal_by_start[m.start()], posix_rel(root, p), line))
    return calls, unsupported


def documented_rpcs(root: Path) -> tuple[list[str], list[str]]:
    path = root / "docs/api/rpc-reference.md"
    text = read_text(path)
    names = []
    malformed = []
    for line_no, line in enumerate(text.splitlines(), 1):
        if not line.startswith("## "):
            continue
        title = line[3:].strip()
        m = re.fullmatch(r"`([A-Za-z0-9_]+)`", title)
        if m:
            names.append(m.group(1))
        elif title.lower() != "evidence methodology":
            malformed.append(f"unsupported active RPC heading: {path.relative_to(root)}:{line_no}: {title}")
    if not names:
        malformed.append("no active RPC sections found")
    return names, malformed


def d5(root: Path) -> CheckResult:
    errors: list[str] = []
    calls, unsupported = extract_rpc_calls(root)
    docs, malformed = documented_rpcs(root)
    errors.extend(unsupported)
    errors.extend(malformed)
    actual = {name for name, _, _ in calls}
    documented = set(docs)
    for name in sorted(actual - documented):
        errors.append(f"production RPC missing documentation: {name}")
    for name in sorted(documented - actual):
        errors.append(f"documented active RPC absent from production: {name}")
    for name in sorted(set(docs)):
        if docs.count(name) != 1:
            errors.append(f"duplicate active RPC documentation section ({docs.count(name)}): {name}")
    coverage = "100%" if actual and actual == documented and not unsupported else f"{len(actual & documented)}/{len(actual)}"
    return check_result(
        CHECK_IDS[4], errors,
        rpc_call_sites=len(calls),
        unique_production_rpc_names=len(actual),
        documented_active_rpc_names=len(documented),
        rpc_documentation_coverage=coverage,
        unknown_rpc=len(actual - documented),
        stale_active_rpc=len(documented - actual),
        unsupported_invocations=len(unsupported),
    )


def settings_modules(root: Path) -> list[str]:
    text = read_text(root / "settings.gradle.kts")
    modules = re.findall(r'^\s*include\(\s*"(:[A-Za-z0-9:_-]+)"\s*\)\s*$', text, re.M)
    include_lines = [l for l in text.splitlines() if re.match(r"^\s*include\s*\(", l)]
    if len(modules) != len(include_lines):
        raise ValueError("unsupported/ambiguous settings.gradle.kts include declaration")
    return modules


def module_map_modules(root: Path) -> list[str]:
    text = read_text(root / "docs/architecture/module-map.md")
    modules = []
    for line in text.splitlines():
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if not cells:
            continue
        m = re.fullmatch(r"`(:[A-Za-z0-9:_-]+)`", cells[0])
        if m:
            modules.append(m.group(1))
    if not modules:
        raise ValueError("module map contains no module rows")
    return modules


def d6(root: Path) -> CheckResult:
    errors: list[str] = []
    actual = settings_modules(root)
    docs = module_map_modules(root)
    for m in sorted(set(actual) - set(docs)):
        errors.append(f"module missing from module-map: {m}")
    for m in sorted(set(docs) - set(actual)):
        errors.append(f"module documented active but absent from settings: {m}")
    for m in sorted(set(docs)):
        if docs.count(m) != 1:
            errors.append(f"duplicate module-map row ({docs.count(m)}): {m}")
    for m in sorted(set(actual)):
        if actual.count(m) != 1:
            errors.append(f"duplicate settings module declaration ({actual.count(m)}): {m}")
    return check_result(
        CHECK_IDS[5], errors,
        settings_modules=len(actual), module_map_modules=len(docs), module_map_drift=len(errors),
    )


def critical_feature_config(root: Path) -> list[str]:
    path = root / "scripts/documentation/critical-features.json"
    try:
        data = json.loads(read_text(path))
    except Exception as exc:
        raise ValueError(f"invalid critical feature config: {exc}") from exc
    docs = data.get("documents")
    if not isinstance(docs, list) or not docs or not all(isinstance(x, str) and x for x in docs):
        raise ValueError("critical-features.json documents must be a non-empty string list")
    if len(docs) != len(set(docs)):
        raise ValueError("critical-features.json contains duplicate documents")
    return docs


def d7(root: Path) -> CheckResult:
    errors: list[str] = []
    required = critical_feature_config(root)
    rows = canonical_rows(root)
    mapped = {r["Canonical document"] for r in rows}
    current_pages = sorted(posix_rel(root, p) for p in (root / "docs/features").glob("*.md"))
    for p in required:
        if not (root / p).is_file():
            errors.append(f"critical feature document missing: {p}")
        if p not in mapped:
            errors.append(f"critical feature lacks Canonical owner mapping: {p}")
        elif status_of(root, root / p) != "canonical":
            errors.append(f"critical feature owner is not canonical: {p}")
    for p in sorted(set(current_pages) - set(required)):
        errors.append(f"feature documentation page not admitted to critical-feature baseline: {p}")
    # Canonical feature-behavior responsibilities are also required to belong to the admitted set.
    for row in rows:
        if "feature behavior" in row["Responsibility"].lower() and row["Canonical document"] not in set(required):
            errors.append(f"Canonical feature-behavior owner missing from critical-feature config: {row['Canonical document']}")
    coverage = "100%" if not errors else f"{sum((root/p).is_file() and p in mapped for p in required)}/{len(required)}"
    return check_result(
        CHECK_IDS[6], errors,
        critical_features=len(required), feature_pages=len(current_pages), critical_feature_coverage=coverage,
    )


def d8(root: Path) -> CheckResult:
    errors = []
    rows = canonical_rows(root)
    for row in rows:
        rel = row["Canonical document"]
        if rel.startswith("docs/archive/"):
            continue
        name = PurePosixPath(rel).stem
        if FORBIDDEN_CANONICAL_NAME.search(name):
            errors.append(f"canonical lifecycle-noise filename: {rel}")
    return check_result(CHECK_IDS[7], errors, canonical_naming_violations=len(errors))


def resolved_md_links(root: Path, source: Path) -> list[Path]:
    out = []
    for raw, line_no in links_from(source):
        path_part, _ = split_link_target(raw)
        if is_external(path_part):
            continue
        target = resolve_link(root, source, path_part)
        if target and target.exists() and target.is_file() and target.suffix.lower() == ".md":
            out.append(target)
    return out


def d9(root: Path) -> CheckResult:
    errors: list[str] = []
    index = root / "docs/INDEX.md"
    for raw, line_no in links_from(index):
        path_part, _ = split_link_target(raw)
        if is_external(path_part):
            continue
        err = validate_link(root, index, raw, line_no)
        if err:
            errors.append(f"INDEX target invalid: {err}")
    rows = canonical_rows(root)
    canonical = {str((root / r["Canonical document"]).resolve()) for r in rows}
    # Reachability is recursive: INDEX may delegate to another current Markdown index/subtree.
    queue = [index.resolve()]
    visited: set[str] = set()
    while queue:
        current = queue.pop(0)
        key = str(current)
        if key in visited:
            continue
        visited.add(key)
        for target in resolved_md_links(root, current):
            rel = posix_rel(root, target)
            # Historical/supporting evidence does not define current navigation subtrees.
            if rel.startswith("docs/archive/") or status_of(root, target) in {"deprecated", "archived", "supporting"}:
                continue
            if str(target.resolve()) not in visited:
                queue.append(target.resolve())
    unreachable = sorted(posix_rel(root, Path(p)) for p in canonical if p not in visited)
    for rel in unreachable:
        errors.append(f"Canonical document unreachable from docs/INDEX.md: {rel}")
    return check_result(
        CHECK_IDS[8], errors,
        index_integrity="PASS" if not errors else "FAIL", unreachable_canonical_docs=len(unreachable),
    )



def inventory_coverage(root: Path) -> CheckResult:
    errors: list[str] = []
    path = root / "docs/DOCUMENTATION_INVENTORY.md"
    text = read_text(path)
    recorded = []
    in_table = False
    for line in text.splitlines():
        if line.startswith("| Current path |"):
            in_table = True
            continue
        if not in_table:
            continue
        if line.startswith("|---"):
            continue
        if not line.startswith("|"):
            if recorded:
                break
            continue
        # Historical rows predate v318 and one retained row has an extra legacy cell.
        # Coverage depends only on the stable first-path column; do not rewrite history.
        first = line.strip().strip("|").split("|", 1)[0].strip().strip("`")
        if not first:
            raise ValueError("empty Current path in documentation inventory")
        recorded.append(first)
    actual = [posix_rel(root, p) for p in all_markdown(root)]
    for rel in sorted(set(actual) - set(recorded)):
        errors.append(f"Markdown missing from documentation inventory: {rel}")
    for rel in sorted(set(recorded) - set(actual)):
        errors.append(f"inventory path does not exist: {rel}")
    for rel in sorted(set(recorded)):
        if recorded.count(rel) != 1:
            errors.append(f"duplicate documentation inventory row ({recorded.count(rel)}): {rel}")
    coverage = "100%" if not errors and set(actual) == set(recorded) else f"{len(set(actual) & set(recorded))}/{len(set(actual))}"
    return check_result(
        "INVENTORY_COVERAGE", errors,
        markdown_files=len(actual), inventory_rows=len(recorded), documentation_inventory_coverage=coverage,
    )

def run(root: Path) -> list[CheckResult]:
    checks = list(zip(CHECK_IDS, (d1, d2, d3, d4, d5, d6, d7, d8, d9)))
    checks.append(("INVENTORY_COVERAGE", inventory_coverage))
    results = []
    for cid, fn in checks:
        try:
            results.append(fn(root))
        except Exception as exc:
            results.append(CheckResult(cid, "FAIL", {}, [f"validator error: {type(exc).__name__}: {exc}"]))
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    default_root = Path(__file__).resolve().parents[2]
    parser.add_argument("--root", type=Path, default=default_root, help=argparse.SUPPRESS)
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    results = run(root)
    overall = "PASS" if all(r.status == "PASS" for r in results) else "FAIL"
    payload = {
        "schema": 1,
        "gate": "documentation",
        "root": str(root),
        "status": overall,
        "checks": [asdict(r) for r in results],
    }
    for r in results:
        metric_text = " ".join(f"{k}={json.dumps(v, sort_keys=True)}" for k, v in sorted(r.metrics.items()))
        print(f"{r.id} {r.status}" + (f" {metric_text}" if metric_text else ""))
        for err in r.errors:
            print(f"  ERROR {err}")
    print(f"DOCUMENTATION_GATE {overall}")
    if args.json_out:
        out = args.json_out if args.json_out.is_absolute() else root / args.json_out
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if overall == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
