#!/usr/bin/env python3
"""Fail-closed maintainability/testability guard for Verto.

The guard deliberately checks evidence and the protected seams introduced in
v328.  It does not measure coverage as a proxy for behavior protection.
"""
from __future__ import annotations

import argparse
import json
import re
import tempfile
from pathlib import Path
from typing import Any


FORMAT = "verto-testability-guard-v1"
PROTECTED = {
    "invoice": "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt",
    "sync": "data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt",
}
REQUIRED_FLOWS = {
    "invoice-write",
    "inventory-effect",
    "payment-effect",
    "logistics-persistence-outbox",
    "sync-orchestration",
}


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def scan_text(scope: str, text: str) -> list[dict[str, str]]:
    """Use the production classifier for verification and self-test mutations."""
    patterns: list[tuple[str, str, str]] = []
    if scope == "invoice":
        patterns += [
            ("T1", r"\bSystem\.currentTimeMillis\s*\(", "direct wall-clock access"),
            ("T2", r"\bUUID\.randomUUID\s*\(", "direct random UUID access"),
        ]
    if scope == "sync":
        patterns += [
            ("T3", r"\bandroid\.content\.Context\b|\bContext\b", "platform Context coupling"),
            ("T4", r"\bSyncWorker\s*\.", "direct SyncWorker orchestration call"),
            ("T5", r"\bAppDatabase\b", "direct database orchestration coupling"),
            ("T9", r"\bSystem\.currentTimeMillis\s*\(", "direct wall-clock access"),
        ]
    if scope == "test":
        patterns.append(("T6", r"\bThread\.sleep\s*\(", "sleep-based deterministic test"))
    if scope == "production":
        patterns += [
            ("T7", r"\bServiceLocator\b|\bGlobalCapabilityHub\b|\bAnyFeatureService\b", "generic service locator/hub"),
            ("T8", r"(?m)^\s*object\s+\w*(Hook|Hooks|TestHook|TestHooks)\b", "global mutable test hook"),
        ]
    findings = []
    for rule, pattern, detail in patterns:
        if re.search(pattern, text):
            findings.append({"rule": rule, "detail": detail})
    return findings


def evidence_path(root: Path, value: Any) -> bool:
    if not isinstance(value, str) or not value:
        return False
    return (root / value).is_file()


def verify(root: Path) -> dict[str, Any]:
    root = root.resolve()
    base = root / "docs/quality/maintainability"
    registry_path = base / "CRITICAL_FLOW_REGISTRY_v329.json"
    matrix_path = base / "MUTATION_MATRIX_v329.json"
    contract_path = base / "MAINTAINABILITY_TESTABILITY_CONTRACT_v329.json"
    baseline_path = base / "MAINTAINABILITY_TESTABILITY_BASELINE_v329.json"
    failures: list[dict[str, Any]] = []
    if not registry_path.is_file():
        failures.append({"code": "MISSING_CRITICAL_FLOW_REGISTRY", "path": str(registry_path.relative_to(root))})
        registry = {}
    else:
        registry = load_json(registry_path)
    if not matrix_path.is_file():
        failures.append({"code": "MISSING_MUTATION_MATRIX", "path": str(matrix_path.relative_to(root))})
        matrix = {}
    else:
        matrix = load_json(matrix_path)
    for path in (contract_path, baseline_path):
        if not path.is_file():
            failures.append({"code": "MISSING_TESTABILITY_ARTIFACT", "path": str(path.relative_to(root))})

    flows = registry.get("flows", []) if isinstance(registry, dict) else []
    flow_ids = {f.get("id") for f in flows if isinstance(f, dict)}
    missing_flows = sorted(REQUIRED_FLOWS - flow_ids)
    if missing_flows:
        failures.append({"code": "MISSING_CRITICAL_FLOWS", "flows": missing_flows})
    for flow in flows:
        if not isinstance(flow, dict):
            failures.append({"code": "INVALID_CRITICAL_FLOW_ENTRY"})
            continue
        if flow.get("id") in REQUIRED_FLOWS and flow.get("status") != "PASS":
            failures.append({"code": "CRITICAL_FLOW_NOT_PASS", "flow": flow.get("id"), "status": flow.get("status")})
        for evidence_kind in ("unit_test", "contract_test", "negative_test", "integration_test", "mutation_test"):
            value = flow.get(evidence_kind)
            values = value if isinstance(value, list) else [value]
            if not any(evidence_path(root, x) for x in values):
                failures.append({"code": "MISSING_FLOW_EVIDENCE", "flow": flow.get("id"), "kind": evidence_kind})

    mutations = matrix.get("mutations", []) if isinstance(matrix, dict) else []
    detected = [m for m in mutations if isinstance(m, dict) and m.get("detected") is True]
    if len(mutations) != 8:
        failures.append({"code": "REQUIRED_MUTATION_COUNT", "expected": 8, "actual": len(mutations)})
    if len(detected) != 8:
        failures.append({"code": "UNDETECTED_MUTATION", "expected": 8, "actual": len(detected)})

    for name, relative in PROTECTED.items():
        path = root / relative
        if not path.is_file():
            failures.append({"code": "MISSING_PROTECTED_TARGET", "target": relative})
            continue
        for finding in scan_text(name, path.read_text(encoding="utf-8", errors="replace")):
            failures.append({"code": "PROTECTED_SEAM_VIOLATION", "target": relative, **finding})

    test_files = []
    for source_set in ("src/test", "src/androidTest"):
        for path in root.rglob(source_set + "/**/*.kt"):
            test_files.append(path)
            for finding in scan_text("test", path.read_text(encoding="utf-8", errors="replace")):
                failures.append({"code": "TEST_DETERMINISM_VIOLATION", "path": str(path.relative_to(root)), **finding})
    for path in root.rglob("src/main/**/*.kt"):
        for finding in scan_text("production", path.read_text(encoding="utf-8", errors="replace")):
            failures.append({"code": "PRODUCTION_TESTABILITY_VIOLATION", "path": str(path.relative_to(root)), **finding})

    result = {
        "format": FORMAT,
        "status": "PASS" if not failures else "FAIL",
        "protected_targets": PROTECTED,
        "metrics": {
            "critical_flows": len(flow_ids & REQUIRED_FLOWS),
            "required_critical_flows": len(REQUIRED_FLOWS),
            "required_mutations": len(mutations),
            "detected_mutations": len(detected),
            "test_files_scanned": len(test_files),
        },
        "failures": failures,
    }
    return result


def self_test() -> int:
    cases = [
        ("T1", "invoice", "System.currentTimeMillis()"),
        ("T2", "invoice", "UUID.randomUUID()"),
        ("T3", "sync", "android.content.Context"),
        ("T4", "sync", "SyncWorker.enqueue()"),
        ("T5", "sync", "private val database: AppDatabase"),
        ("T6", "test", "Thread.sleep(10)"),
        ("T7", "production", "class X(private val locator: ServiceLocator)"),
        ("T8", "production", "object GlobalTestHooks"),
    ]
    failures = []
    for rule, scope, source in cases:
        if not any(x["rule"] == rule for x in scan_text(scope, source)):
            failures.append(rule)
    if failures:
        print(json.dumps({"TESTABILITY_GUARD_SELF_TEST": "FAIL", "undetected": failures}, sort_keys=True))
        return 2
    print(json.dumps({"TESTABILITY_GUARD_SELF_TEST": "PASS", "mutations_detected": 8}, sort_keys=True))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("verify", "self-test", "report"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.command == "self-test":
        return self_test()
    result = verify(args.root)
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
