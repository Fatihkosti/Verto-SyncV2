#!/usr/bin/env python3
"""Deterministic, standard-library Kotlin complexity ratchet for Verto.

This is deliberately a lexical fitness function, not a compiler/AST substitute. The
algorithm is versioned and source-bound so a scanner change is governance-visible.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path
from typing import Any

ALGORITHM_VERSION = "verto-kotlin-complexity-lexical-v1"
EXCLUDED_DIRS = {".git", ".gradle", ".idea", ".kotlin", "build", "out", "generated", "__pycache__"}
DECL_RE = re.compile(r"\b(?:(?:data|sealed|value|enum|annotation)\s+)?(?:class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)")
FUN_RE = re.compile(r"\bfun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*|`[^`]+`)\s*\(")
IMPORT_RE = re.compile(r"^\s*import\s+([^\s;]+)", re.MULTILINE)
BRANCH_RE = re.compile(r"\b(if|for|while|catch)\b|&&|\|\||\?:")
WHEN_RE = re.compile(r"\bwhen\b")


def rel(path: Path, root: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def production_kotlin(root: Path) -> list[Path]:
    rows = []
    for path in root.rglob("*.kt"):
        rp = rel(path, root)
        if "/src/main/" not in f"/{rp}":
            continue
        if any(part in EXCLUDED_DIRS for part in Path(rp).parts):
            continue
        rows.append(path)
    return sorted(rows)


def sanitize(text: str) -> str:
    out = list(text)
    i, n = 0, len(text)
    state, depth = "code", 0
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        tri = text[i:i+3]
        if state == "code":
            if tri == '"""':
                for j in range(i, min(i + 3, n)):
                    if out[j] != "\n": out[j] = " "
                i += 3; state = "triple"; continue
            if ch == '"': out[i] = " "; i += 1; state = "string"; continue
            if ch == "'": out[i] = " "; i += 1; state = "char"; continue
            if ch == "/" and nxt == "/": out[i] = out[i+1] = " "; i += 2; state = "line"; continue
            if ch == "/" and nxt == "*": out[i] = out[i+1] = " "; i += 2; state = "block"; depth = 1; continue
            i += 1; continue
        if state == "line":
            if ch == "\n": state = "code"
            else: out[i] = " "
            i += 1; continue
        if state == "block":
            if ch == "/" and nxt == "*": out[i] = out[i+1] = " "; depth += 1; i += 2; continue
            if ch == "*" and nxt == "/":
                out[i] = out[i+1] = " "; depth -= 1; i += 2
                if depth == 0: state = "code"
                continue
            if ch != "\n": out[i] = " "
            i += 1; continue
        if state in {"string", "char"}:
            end = '"' if state == "string" else "'"
            if ch == "\\" and i + 1 < n:
                out[i] = " "
                if out[i+1] != "\n": out[i+1] = " "
                i += 2; continue
            if ch == end: out[i] = " "; i += 1; state = "code"; continue
            if ch != "\n": out[i] = " "
            i += 1; continue
        if state == "triple":
            if tri == '"""':
                for j in range(i, min(i + 3, n)):
                    if out[j] != "\n": out[j] = " "
                i += 3; state = "code"; continue
            if ch != "\n": out[i] = " "
            i += 1
    return "".join(out)


def matching(text: str, start: int, opening: str, closing: str) -> int | None:
    depth = 0
    for i in range(start, len(text)):
        if text[i] == opening: depth += 1
        elif text[i] == closing:
            depth -= 1
            if depth == 0: return i
    return None


def line_no(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def param_count(fragment: str) -> int:
    if not fragment.strip(): return 0
    depths = {"(": 0, "[": 0, "{": 0, "<": 0}
    pairs = {")": "(", "]": "[", "}": "{", ">": "<"}
    count = 1
    for ch in fragment:
        if ch in depths: depths[ch] += 1
        elif ch in pairs: depths[pairs[ch]] = max(0, depths[pairs[ch]] - 1)
        elif ch == "," and all(v == 0 for v in depths.values()): count += 1
    return count


def body_span(clean: str, after: int) -> tuple[int, int]:
    brace = clean.find("{", after)
    eq = clean.find("=", after)
    candidates = [x for x in (brace, eq) if x >= 0]
    if not candidates: return after, after
    start = min(candidates)
    if clean[start] == "{":
        end = matching(clean, start, "{", "}")
        return start, end if end is not None else start
    nl = clean.find("\n", start)
    return start, (len(clean) - 1 if nl < 0 else nl)


def cognitive_complexity(fragment: str) -> int:
    """Lexical cognitive proxy: branch token + current brace nesting; boolean chains +1."""
    score = 0
    nesting = 0
    token = re.compile(r"\b(if|for|while|when|catch)\b|&&|\|\||[{}]")
    for m in token.finditer(fragment):
        t = m.group(0)
        if t == "{": nesting += 1
        elif t == "}": nesting = max(0, nesting - 1)
        elif t in {"&&", "||"}: score += 1
        else: score += 1 + nesting
    return score


def percentile(values: list[int], q: float) -> int:
    if not values: return 0
    xs = sorted(values)
    idx = max(0, min(len(xs)-1, math.ceil(q * len(xs)) - 1))
    return xs[idx]


def scan(root: Path) -> dict[str, Any]:
    root = root.resolve()
    files = production_kotlin(root)
    file_rows, class_rows, function_rows = [], [], []
    coupling_rows = []
    class_seen: dict[tuple[str, str], int] = {}
    fun_seen: dict[tuple[str, str, int], int] = {}
    for path in files:
        rp = rel(path, root)
        source = path.read_text(encoding="utf-8", errors="replace")
        clean = sanitize(source)
        lines = source.count("\n") + (0 if source.endswith("\n") or not source else 1)
        file_rows.append({"identity": rp, "lines": lines})
        imports = sorted(set(IMPORT_RE.findall(clean)))
        coupling = len([x for x in imports if not (x.startswith("kotlin.") or x.startswith("java.") or x.startswith("javax."))])
        coupling_rows.append({"identity": rp, "imports": coupling})

        for m in DECL_RE.finditer(clean):
            name = m.group(1)
            brace = clean.find("{", m.end())
            if brace < 0: end = m.end()
            else:
                close = matching(clean, brace, "{", "}")
                end = close if close is not None else brace
            p_open = clean.find("(", m.end(), brace if brace >= 0 else min(len(clean), m.end()+1000))
            deps = 0
            if p_open >= 0:
                p_close = matching(clean, p_open, "(", ")")
                if p_close is not None and (brace < 0 or p_close < brace): deps = param_count(clean[p_open+1:p_close])
            key = (rp, name); class_seen[key] = class_seen.get(key, 0) + 1
            identity = f"{rp}::class::{name}#{class_seen[key]}"
            class_rows.append({
                "identity": identity, "path": rp, "name": name,
                "lines": line_no(clean, end) - line_no(clean, m.start()) + 1,
                "constructor_dependencies": deps,
            })

        for m in FUN_RE.finditer(clean):
            name = m.group(1)
            p_open = clean.find("(", m.start(), m.end()+1)
            if p_open < 0: continue
            p_close = matching(clean, p_open, "(", ")")
            if p_close is None: continue
            params = param_count(clean[p_open+1:p_close])
            start, end = body_span(clean, p_close + 1)
            fragment = clean[start:end+1]
            cyclo = 1 + len(BRANCH_RE.findall(fragment))
            # Each when branch arrow is a decision; count only within function fragment.
            cyclo += fragment.count("->") if WHEN_RE.search(fragment) else 0
            cognitive = cognitive_complexity(fragment)
            key = (rp, name, params); fun_seen[key] = fun_seen.get(key, 0) + 1
            identity = f"{rp}::fun::{name}/{params}#{fun_seen[key]}"
            function_rows.append({
                "identity": identity, "path": rp, "name": name, "parameters": params,
                "lines": line_no(clean, end) - line_no(clean, m.start()) + 1,
                "cyclomatic": cyclo, "cognitive": cognitive,
            })

    budgets = {
        "file_lines": percentile([x["lines"] for x in file_rows], 0.95),
        "class_lines": percentile([x["lines"] for x in class_rows], 0.95),
        "constructor_dependencies": percentile([x["constructor_dependencies"] for x in class_rows], 0.95),
        "function_lines": percentile([x["lines"] for x in function_rows], 0.95),
        "function_parameters": percentile([x["parameters"] for x in function_rows], 0.95),
        "cyclomatic": percentile([x["cyclomatic"] for x in function_rows], 0.95),
        "cognitive": percentile([x["cognitive"] for x in function_rows], 0.95),
        "coupling_imports": percentile([x["imports"] for x in coupling_rows], 0.95),
    }
    return {
        "format": "verto-complexity-scan-v1",
        "algorithm_version": ALGORITHM_VERSION,
        "budget_derivation": "nearest-rank p95 of v319 observed identities; historical identities retain exact per-identity ratchet",
        "budgets": budgets,
        "counts": {"files": len(file_rows), "classes": len(class_rows), "functions": len(function_rows)},
        "files": sorted(file_rows, key=lambda x: x["identity"]),
        "classes": sorted(class_rows, key=lambda x: x["identity"]),
        "functions": sorted(function_rows, key=lambda x: x["identity"]),
        "coupling": sorted(coupling_rows, key=lambda x: x["identity"]),
    }


def make_baseline(root: Path, source_version: str, source_sha256: str) -> dict[str, Any]:
    observed = scan(root)
    payload = {
        "format": "verto-complexity-baseline-v1",
        "source_version": source_version,
        "source_sha256": source_sha256,
        "measurement_algorithm_version": ALGORITHM_VERSION,
        "budget_derivation": observed["budget_derivation"],
        "new_identity_budgets": observed["budgets"],
        "counts": observed["counts"],
        "identities": {
            "files": observed["files"], "classes": observed["classes"],
            "functions": observed["functions"], "coupling": observed["coupling"],
        },
    }
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    payload["content_sha256"] = hashlib.sha256(canonical).hexdigest()
    return payload


def verify(root: Path, baseline: dict[str, Any]) -> dict[str, Any]:
    current = scan(root)
    failures: list[dict[str, Any]] = []
    if baseline.get("measurement_algorithm_version") != ALGORITHM_VERSION:
        failures.append({"code": "FAIL_COMPLEXITY_SCANNER_DRIFT", "expected": baseline.get("measurement_algorithm_version"), "actual": ALGORITHM_VERSION})
    budgets = baseline.get("new_identity_budgets", {})
    dimensions = {
        "files": ["lines"],
        "classes": ["lines", "constructor_dependencies"],
        "functions": ["lines", "parameters", "cyclomatic", "cognitive"],
        "coupling": ["imports"],
    }
    budget_map = {
        ("files", "lines"): "file_lines", ("classes", "lines"): "class_lines",
        ("classes", "constructor_dependencies"): "constructor_dependencies",
        ("functions", "lines"): "function_lines", ("functions", "parameters"): "function_parameters",
        ("functions", "cyclomatic"): "cyclomatic", ("functions", "cognitive"): "cognitive",
        ("coupling", "imports"): "coupling_imports",
    }
    deltas: dict[str, Any] = {}
    for kind, fields in dimensions.items():
        old = {x["identity"]: x for x in baseline.get("identities", {}).get(kind, [])}
        now = {x["identity"]: x for x in current.get(kind, [])}
        added = sorted(set(now) - set(old)); removed = sorted(set(old) - set(now))
        regressions = []
        for identity in sorted(set(now) & set(old)):
            for field in fields:
                if now[identity].get(field, 0) > old[identity].get(field, 0):
                    regressions.append({"identity": identity, "metric": field, "baseline": old[identity].get(field), "current": now[identity].get(field)})
        for identity in added:
            current_row = now[identity]
            path = str(current_row.get("path", ""))
            dao_root = "data/database/src/main/kotlin/com/verto/app/data/local/dao/"
            source_path = None
            if path.startswith(dao_root + "Inventory") and path.endswith("Dao.kt") and path != dao_root + "InventoryDao.kt":
                source_path = dao_root + "InventoryDao.kt"
            elif path.startswith(dao_root + "UnifiedSync") and path.endswith("Dao.kt") and path != dao_root + "UnifiedSyncDao.kt":
                source_path = dao_root + "UnifiedSyncDao.kt"
            elif path.startswith(dao_root + "Invoice") and path.endswith("Dao.kt") and path != dao_root + "InvoiceDao.kt":
                source_path = dao_root + "InvoiceDao.kt"
            elif path.startswith(dao_root + "Logistics") and path.endswith("Dao.kt") and path != dao_root + "LogisticsDao.kt":
                source_path = dao_root + "LogisticsDao.kt"
            if source_path and kind in {"classes", "functions"}:
                if kind == "classes":
                    legacy_name = {
                        dao_root + "InventoryDao.kt": "InventoryDao",
                        dao_root + "UnifiedSyncDao.kt": "UnifiedSyncDao",
                        dao_root + "InvoiceDao.kt": "InvoiceDao",
                        dao_root + "LogisticsDao.kt": "LogisticsDao",
                    }[source_path]
                    aliases = [row for row in old.values() if row.get("path") == source_path and row.get("name") == legacy_name]
                else:
                    aliases = [row for row in old.values() if row.get("path") == source_path and row.get("name") == current_row.get("name")]
                if len(aliases) == 1:
                    legacy = aliases[0]
                    for field in fields:
                        if current_row.get(field, 0) > legacy.get(field, 0):
                            regressions.append({"identity": identity, "metric": field, "baseline_alias": legacy.get("identity"), "baseline": legacy.get(field), "current": current_row.get(field)})
                    continue
            for field in fields:
                bkey = budget_map[(kind, field)]; limit = budgets.get(bkey)
                if limit is not None and current_row.get(field, 0) > limit:
                    regressions.append({"identity": identity, "metric": field, "new_identity_budget": limit, "current": current_row.get(field)})
        if regressions:
            failures.append({"code": "FAIL_COMPLEXITY_REGRESSION", "kind": kind, "regressions": regressions})
        deltas[kind] = {"added": added, "removed": removed, "regressions": regressions}
    return {
        "status": "PASS" if not failures else "FAIL",
        "algorithm_version": ALGORITHM_VERSION,
        "current": current,
        "deltas": deltas,
        "failures": failures,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("command", choices=["scan", "baseline", "verify"])
    ap.add_argument("--root", default=".")
    ap.add_argument("--baseline", default="docs/architecture/contracts/complexity-baseline-v322.json")
    ap.add_argument("--source-version", default="v319")
    ap.add_argument("--source-sha256")
    ap.add_argument("--output")
    args = ap.parse_args()
    root = Path(args.root).resolve()
    if args.command == "scan": obj = scan(root); code = 0
    elif args.command == "baseline":
        if not args.source_sha256: ap.error("--source-sha256 is required for baseline")
        obj = make_baseline(root, args.source_version, args.source_sha256); code = 0
    else:
        baseline_path = Path(args.baseline)
        if not baseline_path.is_absolute(): baseline_path = root / baseline_path
        obj = verify(root, json.loads(baseline_path.read_text(encoding="utf-8"))); code = 0 if obj["status"] == "PASS" else 2
    if args.output:
        out = Path(args.output); out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({k: obj[k] for k in ("status", "algorithm_version", "counts", "budgets") if k in obj}, sort_keys=True))
    return code

if __name__ == "__main__":
    raise SystemExit(main())
