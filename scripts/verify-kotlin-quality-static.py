#!/usr/bin/env python3
"""Offline Kotlin quality guard for Verto.

Standard-library only. No Gradle, network, Git, server, or Android SDK is required.
The scanner deliberately measures production Kotlin under */src/main/**/*.kt.

Usage:
  python3 scripts/verify-kotlin-quality-static.py scan
  python3 scripts/verify-kotlin-quality-static.py verify
  python3 scripts/verify-kotlin-quality-static.py verify --json-out /tmp/result.json

The committed v188 baseline is monotonic: every debt metric may decrease but must
never increase. Zero-baseline metrics therefore remain zero.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

FORMAT_SCAN = "verto-kotlin-quality-scan-v1"
FORMAT_BASELINE = "verto-kotlin-quality-baseline-v1"
DEFAULT_BASELINE = "config/kotlin-quality/baseline-v188.json"
EXCLUDED_DIRS = {".git", ".gradle", ".idea", ".kotlin", "build", "out", "generated"}

LONG_FUNCTION_LINES = 60
FUNCTION_PARAMETER_LIMIT = 6
CONSTRUCTOR_PARAMETER_LIMIT = 7

BROAD_CATCH_RE = re.compile(r"\bcatch\s*\(\s*[^:)]*:\s*(Exception|Throwable)\b")
MANUAL_SCOPE_RE = re.compile(r"(?<![A-Za-z0-9_.])CoroutineScope\s*\(")
GLOBAL_SCOPE_RE = re.compile(r"\bGlobalScope\b")
LATEINIT_RE = re.compile(r"\blateinit\s+var\b")
MUTABLE_TYPE_RE = re.compile(
    r"\b(?:MutableStateFlow|MutableSharedFlow|MutableFlow|MutableList|MutableSet|MutableMap|"
    r"MutableCollection|ArrayList|HashSet|HashMap)\b"
)
MUTABLE_FACTORY_RE = re.compile(
    r"\b(?:MutableStateFlow|MutableSharedFlow|mutableListOf|mutableSetOf|mutableMapOf|"
    r"arrayListOf|hashSetOf|hashMapOf)\s*(?:<[^\n>{}]*>)?\s*\("
)
PROPERTY_RE = re.compile(
    r"^(?P<indent>\s*)(?P<prefix>(?:(?:public|internal|protected|private|lateinit|override|"
    r"open|final|abstract|const|@\w+(?:\([^\n]*\))?)\s+)*)"
    r"(?P<kind>val|var)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\b(?P<rest>.*)$"
)
FUN_TOKEN_RE = re.compile(r"\bfun\b")
CLASS_TOKEN_RE = re.compile(r"\b(?:class|data\s+class|sealed\s+class|value\s+class)\b")


@dataclass
class FunctionDecl:
    path: str
    name: str
    start_line: int
    end_line: int
    lines: int
    parameters: int


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def rel(path: Path, root: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def is_excluded(path: Path, root: Path) -> bool:
    try:
        parts = path.resolve().relative_to(root.resolve()).parts
    except ValueError:
        return True
    return any(part in EXCLUDED_DIRS for part in parts)


def production_kotlin(root: Path) -> list[Path]:
    result = []
    for path in root.rglob("*.kt"):
        rp = rel(path, root)
        if "/src/main/" in f"/{rp}" and not is_excluded(path, root):
            result.append(path)
    return sorted(result)


def sanitize_kotlin(text: str) -> str:
    """Mask comments and string/char contents while preserving length/newlines."""
    out = list(text)
    i = 0
    n = len(text)
    state = "code"
    block_depth = 0
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        tri = text[i:i + 3]
        if state == "code":
            if tri == '"""':
                for j in range(i, min(i + 3, n)):
                    if out[j] != "\n": out[j] = " "
                i += 3; state = "triple"; continue
            if ch == '"':
                out[i] = " "; i += 1; state = "string"; continue
            if ch == "'":
                out[i] = " "; i += 1; state = "char"; continue
            if ch == "/" and nxt == "/":
                out[i] = out[i + 1] = " "; i += 2; state = "line_comment"; continue
            if ch == "/" and nxt == "*":
                out[i] = out[i + 1] = " "; i += 2; state = "block_comment"; block_depth = 1; continue
            i += 1; continue
        if state == "line_comment":
            if ch == "\n": state = "code"
            else: out[i] = " "
            i += 1; continue
        if state == "block_comment":
            if ch == "/" and nxt == "*":
                out[i] = out[i + 1] = " "; block_depth += 1; i += 2; continue
            if ch == "*" and nxt == "/":
                out[i] = out[i + 1] = " "; block_depth -= 1; i += 2
                if block_depth == 0: state = "code"
                continue
            if ch != "\n": out[i] = " "
            i += 1; continue
        if state == "string":
            if ch == "\\" and i + 1 < n:
                out[i] = " ";
                if out[i + 1] != "\n": out[i + 1] = " "
                i += 2; continue
            if ch == '"': out[i] = " "; i += 1; state = "code"; continue
            if ch != "\n": out[i] = " "
            i += 1; continue
        if state == "char":
            if ch == "\\" and i + 1 < n:
                out[i] = " ";
                if out[i + 1] != "\n": out[i + 1] = " "
                i += 2; continue
            if ch == "'": out[i] = " "; i += 1; state = "code"; continue
            if ch != "\n": out[i] = " "
            i += 1; continue
        if state == "triple":
            if tri == '"""':
                for j in range(i, min(i + 3, n)):
                    if out[j] != "\n": out[j] = " "
                i += 3; state = "code"; continue
            if ch != "\n": out[i] = " "
            i += 1; continue
    return "".join(out)


def line_of(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def find_matching(text: str, start: int, opening: str, closing: str) -> int | None:
    depth = 0
    for i in range(start, len(text)):
        ch = text[i]
        if ch == opening: depth += 1
        elif ch == closing:
            depth -= 1
            if depth == 0: return i
    return None


def top_level_comma_count(fragment: str) -> int:
    if not fragment.strip(): return 0
    paren = bracket = brace = angle = 0
    commas = 0
    i = 0
    while i < len(fragment):
        ch = fragment[i]
        if ch == "(": paren += 1
        elif ch == ")": paren = max(0, paren - 1)
        elif ch == "[": bracket += 1
        elif ch == "]": bracket = max(0, bracket - 1)
        elif ch == "{": brace += 1
        elif ch == "}": brace = max(0, brace - 1)
        elif ch == "<": angle += 1
        elif ch == ">": angle = max(0, angle - 1)
        elif ch == "," and paren == bracket == brace == angle == 0: commas += 1
        i += 1
    return commas + 1


def find_functions(path: Path, root: Path, source: str, clean: str) -> list[FunctionDecl]:
    decls: list[FunctionDecl] = []
    for m in FUN_TOKEN_RE.finditer(clean):
        if re.match(r"\s+interface\b", clean[m.end():]):
            continue
        # Ignore identifiers such as ::class etc; regex boundary is enough for Kotlin 'fun'.
        p_open = clean.find("(", m.end())
        if p_open < 0: continue
        # A declaration must reach '(' before a body delimiter/new declaration line.
        head = clean[m.end():p_open]
        if "\n" in head and len(head.splitlines()) > 4: continue
        p_close = find_matching(clean, p_open, "(", ")")
        if p_close is None: continue
        params = top_level_comma_count(clean[p_open + 1:p_close])
        name_head = re.sub(r"\s+", " ", head).strip()
        name_match = re.search(r"(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*|`[^`]+`)\s*$", name_head)
        name = name_match.group(1) if name_match else "<anonymous>"
        j = p_close + 1
        # Find either block body or expression body. A newline is allowed in return type / where clause.
        brace = clean.find("{", j)
        eq = clean.find("=", j)
        next_fun = clean.find("\nfun ", j)
        candidates = [x for x in (brace, eq) if x >= 0]
        if not candidates: end = p_close
        else:
            body = min(candidates)
            if next_fun >= 0 and next_fun < body: end = p_close
            elif clean[body] == "{":
                close = find_matching(clean, body, "{", "}")
                end = close if close is not None else body
            else:
                nl = clean.find("\n", body)
                end = len(clean) - 1 if nl < 0 else nl
        start_line = line_of(clean, m.start())
        end_line = line_of(clean, max(m.start(), end))
        decls.append(FunctionDecl(rel(path, root), name, start_line, end_line, end_line - start_line + 1, params))
    # Remove duplicate nested lambda false-positives are naturally absent because they lack 'fun'.
    return decls


def find_primary_constructor_params(clean: str) -> list[tuple[int, int]]:
    result: list[tuple[int, int]] = []
    # Conservative: class Name(...) only. Explicit secondary constructors are not included in this metric.
    rx = re.compile(r"\b(?:data\s+|sealed\s+|value\s+|enum\s+|annotation\s+)?class\s+[A-Za-z_][A-Za-z0-9_]*[^\n({]*\(")
    for m in rx.finditer(clean):
        p_open = clean.find("(", m.start(), m.end() + 1)
        if p_open < 0: continue
        p_close = find_matching(clean, p_open, "(", ")")
        if p_close is None: continue
        result.append((line_of(clean, m.start()), top_level_comma_count(clean[p_open + 1:p_close])))
    return result


def spans_for_functions(functions: list[FunctionDecl]) -> list[tuple[int, int]]:
    return [(f.start_line, f.end_line) for f in functions]


def line_inside_spans(line: int, spans: list[tuple[int, int]]) -> bool:
    return any(a <= line <= b for a, b in spans)


def exposed_mutable_state(path: Path, root: Path, source: str, clean: str, functions: list[FunctionDecl]) -> list[dict[str, Any]]:
    evidence: list[dict[str, Any]] = []
    spans = spans_for_functions(functions)
    src_lines = source.splitlines()
    clean_lines = clean.splitlines()
    for idx, (raw, code) in enumerate(zip(src_lines, clean_lines), 1):
        if line_inside_spans(idx, spans): continue
        pm = PROPERTY_RE.match(code)
        if not pm: continue
        prefix = pm.group("prefix") or ""
        if re.search(r"\bprivate\b", prefix): continue
        rest = pm.group("rest")
        if MUTABLE_TYPE_RE.search(rest) or MUTABLE_FACTORY_RE.search(rest):
            evidence.append({"path": rel(path, root), "line": idx, "property": pm.group("name")})
    return evidence


def architecture_scan(root: Path) -> tuple[int, dict[str, int], int]:
    """Consume the canonical v319/v320 architecture contracts, never legacy v181 inputs."""
    guard_path = root / "tools/architecture/verto_arch_guard.py"
    if not guard_path.exists():
        raise RuntimeError("architecture guard missing: tools/architecture/verto_arch_guard.py")
    spec = importlib.util.spec_from_file_location("verto_arch_guard", guard_path)
    if spec is None or spec.loader is None: raise RuntimeError("cannot load architecture guard")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    contract = json.loads((root / "docs/architecture/contracts/architecture-contracts.json").read_text(encoding="utf-8"))
    debt = json.loads((root / "docs/architecture/contracts/technical-debt-baseline.json").read_text(encoding="utf-8"))
    modules = module.parse_modules(root)
    manifests, _, _ = module.load_manifests(root, modules)
    result = module.scan_project(root, contract, debt, manifests)
    return len(result.get("violations", [])), result.get("rule_counts", {}), result["metrics"]["dependency_cycles"]


def scan(root: Path) -> dict[str, Any]:
    files = production_kotlin(root)
    evidence: dict[str, list[Any]] = {
        "not_null_assertions": [], "broad_catches": [], "manual_coroutine_scopes": [],
        "large_files_over_500": [], "long_functions": [], "excessive_parameter_lists": [],
        "exposed_mutable_state": [], "global_scope": [], "lateinit_var": [],
    }
    for path in files:
        source = path.read_text(encoding="utf-8", errors="replace")
        clean = sanitize_kotlin(source)
        rp = rel(path, root)
        lines = source.count("\n") + (0 if source.endswith("\n") or not source else 1)
        if lines > 500: evidence["large_files_over_500"].append({"path": rp, "lines": lines})
        # Count source-level !! occurrences. Kotlin string templates contain executable expressions,
        # so a raw source count preserves assertions used inside ${...} as required by the v188 contract.
        for match in re.finditer(r"!!", source):
            evidence["not_null_assertions"].append({"path": rp, "line": line_of(source, match.start())})
        for label, rx in (
            ("broad_catches", BROAD_CATCH_RE),
            ("manual_coroutine_scopes", MANUAL_SCOPE_RE),
            ("global_scope", GLOBAL_SCOPE_RE),
            ("lateinit_var", LATEINIT_RE),
        ):
            for match in rx.finditer(clean):
                evidence[label].append({"path": rp, "line": line_of(clean, match.start())})
        funcs = find_functions(path, root, source, clean)
        for f in funcs:
            if f.lines > LONG_FUNCTION_LINES:
                evidence["long_functions"].append({"path": f.path, "line": f.start_line, "name": f.name, "lines": f.lines})
            if f.parameters > FUNCTION_PARAMETER_LIMIT:
                evidence["excessive_parameter_lists"].append({
                    "path": f.path, "line": f.start_line, "kind": "function", "name": f.name,
                    "parameters": f.parameters, "limit": FUNCTION_PARAMETER_LIMIT,
                })
        for line, params in find_primary_constructor_params(clean):
            if params > CONSTRUCTOR_PARAMETER_LIMIT:
                evidence["excessive_parameter_lists"].append({
                    "path": rp, "line": line, "kind": "primary_constructor", "parameters": params,
                    "limit": CONSTRUCTOR_PARAMETER_LIMIT,
                })
        evidence["exposed_mutable_state"].extend(exposed_mutable_state(path, root, source, clean, funcs))

    arch_count, arch_rules, cycles = architecture_scan(root)
    metrics = {
        "production_kotlin_files": len(files),
        "not_null_assertions": len(evidence["not_null_assertions"]),
        "broad_catches": len(evidence["broad_catches"]),
        "manual_coroutine_scopes": len(evidence["manual_coroutine_scopes"]),
        "large_files_over_500": len(evidence["large_files_over_500"]),
        "long_functions": len(evidence["long_functions"]),
        "excessive_parameter_lists": len(evidence["excessive_parameter_lists"]),
        "exposed_mutable_state": len(evidence["exposed_mutable_state"]),
        "global_scope": len(evidence["global_scope"]),
        "lateinit_var": len(evidence["lateinit_var"]),
        "architecture_violation_count": arch_count,
        "dependency_cycles": cycles,
    }
    for key in evidence:
        evidence[key] = sorted(evidence[key], key=lambda x: (x["path"], x.get("line", 0), x.get("name", "")))
    return {
        "format": FORMAT_SCAN,
        "scanner": {
            "long_function_lines": LONG_FUNCTION_LINES,
            "function_parameter_limit": FUNCTION_PARAMETER_LIMIT,
            "constructor_parameter_limit": CONSTRUCTOR_PARAMETER_LIMIT,
            "production_scope": "*/src/main/**/*.kt",
        },
        "metrics": metrics,
        "architecture_rule_counts": arch_rules,
        "evidence": evidence,
    }


MONOTONIC_METRICS = [
    "not_null_assertions", "broad_catches", "manual_coroutine_scopes", "large_files_over_500",
    "long_functions", "excessive_parameter_lists", "exposed_mutable_state", "global_scope",
    "lateinit_var", "architecture_violation_count", "dependency_cycles",
]

# v195 closes the improvement plan by freezing the achieved floors/ceilings as hard policy.
# These are not a replacement baseline: baseline-v188.json remains immutable evidence.
V195_FINAL_MAXIMA = {
    "broad_catches": 20,
    "manual_coroutine_scopes": 1,
    "large_files_over_500": 4,
    "long_functions": 328,
    "excessive_parameter_lists": 436,
    "lateinit_var": 4,
    "architecture_violation_count": 18,
}

V195_ALLOWED_MANUAL_SCOPE_PATHS = {
    "app/src/main/kotlin/com/verto/app/notifications/VertoFirebaseMessagingService.kt",
}

# Broad Exception catches in Domain/Application are forbidden except for these documented
# owning boundaries. Their semantics are described in KOTLIN_EXCEPTION_POLICY.md.
V195_ALLOWED_APPLICATION_BROAD_CATCH_PATHS = {
    "feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/UnifiedHomeSearchUseCase.kt",
    "feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/OptimalAudioMessageUseCases.kt",
    "feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/SaveLogisticsDocumentUseCase.kt",
}

V195_REQUIRED_DETEKT_GROUPS = {
    "complexity", "exceptions", "naming", "performance", "potential-bugs", "style", "coroutines",
}

V195_REQUIRED_DETEKT_RULES = {
    "LongMethod", "LongParameterList", "GlobalCoroutineUsage", "TooGenericExceptionCaught",
    "ClassNaming", "FunctionNaming", "ForEachOnRange",
    "MapGetWithNotNullAssertionOperator", "UnsafeCallOnNullableType", "ForbiddenSuppress",
}


def detekt_section(config: str, name: str) -> str:
    lines = config.splitlines()
    start = None
    for index, line in enumerate(lines):
        if line == f"{name}:":
            start = index + 1
            break
    if start is None:
        return ""
    end = len(lines)
    for index in range(start, len(lines)):
        line = lines[index]
        if line and not line[0].isspace() and line.endswith(":"):
            end = index
            break
    return "\n".join(lines[start:end])


def v195_detekt_failures(root: Path) -> list[dict[str, Any]]:
    failures: list[dict[str, Any]] = []
    config_path = root / "config/detekt/detekt.yml"
    build_path = root / "build.gradle.kts"
    ci_path = root / "scripts/ci/run-quality-gate.sh"
    if not config_path.exists():
        return [{"rule": "DETEKT_CONFIG_MISSING", "path": "config/detekt/detekt.yml"}]
    config = config_path.read_text(encoding="utf-8")
    for group in sorted(V195_REQUIRED_DETEKT_GROUPS):
        section = detekt_section(config, group)
        if not section or not re.search(r"(?m)^  active:\s*true\s*$", section):
            failures.append({"rule": "DETEKT_GROUP_NOT_ACTIVE", "group": group})
    for rule in sorted(V195_REQUIRED_DETEKT_RULES):
        if not re.search(rf"(?m)^  {re.escape(rule)}:\s*$", config):
            failures.append({"rule": "DETEKT_RULE_NOT_CONFIGURED", "detekt_rule": rule})
    if re.search(r"(?mi)^\s*baseline\s*[:=]", config):
        failures.append({"rule": "DETEKT_BASELINE_FORBIDDEN"})
    build = build_path.read_text(encoding="utf-8") if build_path.exists() else ""
    required_build_tokens = [
        'pluginManager.apply("io.gitlab.arturbosch.detekt")',
        'config.setFrom(rootProject.files("config/detekt/detekt.yml"))',
        'ignoreFailures = false',
        'dependsOn(subprojects.map { "${it.path}:detekt" })',
    ]
    missing_build = [token for token in required_build_tokens if token not in build]
    if missing_build:
        failures.append({"rule": "DETEKT_BUILD_GATE_NOT_WIRED", "missing": missing_build})
    ci = ci_path.read_text(encoding="utf-8") if ci_path.exists() else ""
    if 'detekt)       command=("$GRADLEW" --no-daemon --stacktrace detekt)' not in ci:
        failures.append({"rule": "DETEKT_CI_GATE_NOT_WIRED"})
    return failures


def verify_historical(root: Path, baseline_path: Path) -> dict[str, Any]:
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    if baseline.get("format") != FORMAT_BASELINE:
        raise RuntimeError(f"unsupported baseline format: {baseline.get('format')}")
    current = scan(root)
    base_metrics = baseline["metrics"]
    failures: list[dict[str, Any]] = []
    # Threshold semantics are part of the baseline contract and cannot drift silently.
    if current["scanner"] != baseline.get("scanner"):
        failures.append({"rule": "SCANNER_CONTRACT_CHANGED", "expected": baseline.get("scanner"), "actual": current["scanner"]})
    for metric in MONOTONIC_METRICS:
        old = base_metrics.get(metric)
        new = current["metrics"].get(metric)
        if old is None or new is None:
            failures.append({"rule": "MISSING_METRIC", "metric": metric, "baseline": old, "current": new})
        elif new > old:
            failures.append({"rule": "BASELINE_INCREASE", "metric": metric, "expected_max": old, "actual": new})
    # These architecture invariants are hard gates, not debt budgets.
    if current["metrics"]["dependency_cycles"] != 0:
        failures.append({"rule": "DEPENDENCY_CYCLE", "expected": 0, "actual": current["metrics"]["dependency_cycles"]})
    # v195 hard floors: once zero has been reached it may never regress, even though
    # the historical v188 baseline recorded a larger amount of debt.
    for metric in ("not_null_assertions", "exposed_mutable_state", "global_scope"):
        if current["metrics"][metric] != 0:
            failures.append({
                "rule": "V195_ZERO_FLOOR",
                "metric": metric,
                "expected": 0,
                "actual": current["metrics"][metric],
            })

    # Freeze the achieved v195 debt ceilings so later cleanup can only move downward.
    for metric, maximum in V195_FINAL_MAXIMA.items():
        actual = current["metrics"][metric]
        if actual > maximum:
            failures.append({
                "rule": "V195_FINAL_CEILING",
                "metric": metric,
                "expected_max": maximum,
                "actual": actual,
            })

    # The sole direct CoroutineScope construction is the documented, lifecycle-owned FCM
    # service scope. Any other direct scope is unmanaged by policy and therefore forbidden.
    manual_scope_paths = {item["path"] for item in current["evidence"]["manual_coroutine_scopes"]}
    unmanaged_scope_paths = sorted(manual_scope_paths - V195_ALLOWED_MANUAL_SCOPE_PATHS)
    if unmanaged_scope_paths:
        failures.append({
            "rule": "UNMANAGED_COROUTINE_SCOPE",
            "allowed_paths": sorted(V195_ALLOWED_MANUAL_SCOPE_PATHS),
            "actual_paths": unmanaged_scope_paths,
        })

    # Domain/Application broad catches require an explicitly documented owning boundary.
    application_broad_catches = [
        item for item in current["evidence"]["broad_catches"]
        if "/domain/" in f"/{item['path']}" or "/application/" in f"/{item['path']}"
    ]
    undocumented_broad_catches = sorted({
        item["path"] for item in application_broad_catches
        if item["path"] not in V195_ALLOWED_APPLICATION_BROAD_CATCH_PATHS
    })
    if undocumented_broad_catches:
        failures.append({
            "rule": "BROAD_CATCH_OUTSIDE_DOCUMENTED_BOUNDARY",
            "actual_paths": undocumented_broad_catches,
        })

    failures.extend(v195_detekt_failures(root))

    return {
        "status": "PASS" if not failures else "FAIL",
        "baseline": (baseline_path.relative_to(root).as_posix() if baseline_path.is_relative_to(root) else baseline_path.as_posix()),
        "metrics": current["metrics"],
        "failures": failures,
        "scan": current,
    }


def evidence_identity(metric: str, item: dict[str, Any]) -> str:
    """Stable-enough current ratchet identity. Values are excluded where they are measured severity."""
    keys = ["path", "line", "kind", "name"]
    parts = [str(item.get(k, "")) for k in keys]
    return metric + "|" + "|".join(parts)


def canonical_current_identity(metric: str, item: dict[str, Any], admitted_rows: list[dict[str, Any]]) -> str:
    """Preserve identity across behavior-preserving DAO splits.

    The method/class name remains the stable identity; the old line/path are
    evidence coordinates, not a new debt. The source paths are explicit so this
    alias cannot hide unrelated new identities.
    """
    path = str(item.get("path", ""))
    dao_root = "data/database/src/main/kotlin/com/verto/app/data/local/dao/"
    source_path = None
    if path.startswith(dao_root + "Inventory") and path.endswith("Dao.kt") and path != dao_root + "InventoryDao.kt":
        source_path = dao_root + "InventoryDao.kt"
    elif path.startswith(dao_root + "UnifiedSync") and path.endswith("Dao.kt") and path != dao_root + "UnifiedSyncDao.kt":
        source_path = dao_root + "UnifiedSyncDao.kt"
    elif path == dao_root + "UnifiedSyncModels.kt":
        source_path = dao_root + "UnifiedSyncDao.kt"
    elif path.startswith(dao_root + "Invoice") and path.endswith("Dao.kt") and path != dao_root + "InvoiceDao.kt":
        source_path = dao_root + "InvoiceDao.kt"
    elif path.startswith(dao_root + "Logistics") and path.endswith("Dao.kt") and path != dao_root + "LogisticsDao.kt":
        source_path = dao_root + "LogisticsDao.kt"
    if source_path:
        candidates = [row for row in admitted_rows if row.get("path") == source_path and row.get("name") == item.get("name")]
        if len(candidates) == 1:
            return evidence_identity(metric, candidates[0])
    return evidence_identity(metric, item)


def verify_current(root: Path, debt_path: Path) -> dict[str, Any]:
    debt = json.loads(debt_path.read_text(encoding="utf-8"))
    if debt.get("format") != "verto-technical-debt-baseline-v1":
        raise RuntimeError(f"unsupported current-ratchet baseline format: {debt.get('format')}")
    admitted = debt.get("kotlin_quality", {})
    current = scan(root)
    failures: list[dict[str, Any]] = []
    ratchet_metrics = [m for m in MONOTONIC_METRICS if m in admitted.get("metrics", {})]
    for metric in ratchet_metrics:
        old = admitted["metrics"].get(metric); new = current["metrics"].get(metric)
        if old is None or new is None:
            failures.append({"rule": "MISSING_CURRENT_RATCHET_METRIC", "metric": metric, "baseline": old, "current": new})
        elif new > old:
            failures.append({"rule": "CURRENT_RATCHET_INCREASE", "metric": metric, "expected_max": old, "actual": new})
    admitted_evidence = admitted.get("evidence", {})
    identity_deltas: dict[str, Any] = {}
    for metric, old_rows in admitted_evidence.items():
        if metric not in current.get("evidence", {}):
            continue
        old_ids = {evidence_identity(metric, x) for x in old_rows}
        new_ids = {canonical_current_identity(metric, x, old_rows) for x in current["evidence"][metric]}
        added = sorted(new_ids - old_ids); removed = sorted(old_ids - new_ids)
        identity_deltas[metric] = {"added": added, "removed": removed}
        if added:
            failures.append({"rule": "CURRENT_RATCHET_NEW_IDENTITY", "metric": metric, "added": added})
    # Architecture identity semantics are delegated to Architecture Guard v2; retain count sanity here.
    expected_arch = admitted.get("metrics", {}).get("architecture_violation_count")
    if expected_arch is not None and current["metrics"]["architecture_violation_count"] > expected_arch:
        failures.append({"rule": "CURRENT_ARCHITECTURE_COUNT_INCREASE", "expected_max": expected_arch, "actual": current["metrics"]["architecture_violation_count"]})
    return {
        "status": "PASS" if not failures else "FAIL",
        "mode": "current-ratchet",
        "baseline": debt_path.relative_to(root).as_posix() if debt_path.is_relative_to(root) else debt_path.as_posix(),
        "metrics": current["metrics"],
        "identity_deltas": identity_deltas,
        "failures": failures,
        "scan": current,
    }


def print_metrics(metrics: dict[str, Any]) -> None:
    for key in sorted(metrics): print(f"{key}={metrics[key]}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Verto offline Kotlin quality guard")
    parser.add_argument("command", choices=["scan", "verify"], nargs="?", default="verify")
    parser.add_argument("--root", default=str(project_root()))
    parser.add_argument("--mode", choices=["current-ratchet", "historical-v188"], default="current-ratchet")
    parser.add_argument("--baseline")
    parser.add_argument("--json-out")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    if args.command == "scan":
        result = scan(root); code = 0
    else:
        if args.baseline:
            baseline = Path(args.baseline)
        else:
            baseline = Path("docs/architecture/contracts/technical-debt-baseline.json" if args.mode == "current-ratchet" else DEFAULT_BASELINE)
        if not baseline.is_absolute(): baseline = root / baseline
        result = verify_current(root, baseline) if args.mode == "current-ratchet" else verify_historical(root, baseline)
        code = 0 if result["status"] == "PASS" else 2
    if args.json_out:
        out = Path(args.json_out); out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print_metrics(result["metrics"])
    if args.command == "verify":
        print(f"KOTLIN_QUALITY_RATCHET_GATE={result['status']} mode={result.get('mode', args.mode)}")
        if result["failures"]: print(json.dumps(result["failures"], ensure_ascii=False, sort_keys=True))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
