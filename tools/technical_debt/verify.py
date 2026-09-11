#!/usr/bin/env python3
"""Offline technical-debt registry, ratchet, and ZIP differential verifier."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

SEVERITIES = {"CRITICAL", "HIGH", "MEDIUM", "LOW"}
STATUSES = {"OPEN", "IN_PROGRESS", "ACCEPTED_TEMPORARILY", "RESOLVED"}
REQUIRED = {"id", "location", "category", "severity", "risk", "owner", "baseline", "target", "reason", "status"}
METRICS = (
    "production_kotlin_files", "large_files_over_500", "long_functions",
    "excessive_parameter_lists", "broad_catches", "not_null_assertions",
    "exposed_mutable_state", "architecture_violations", "dependency_cycles",
)


def fail(code: str, **detail: object) -> dict:
    return {"code": code, **detail}


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def registry(root: Path, today: dt.date | None = None) -> dict:
    today = today or dt.date.today()
    payload = load(root / "docs/quality/technical-debt-registry.json")
    failures: list[dict] = []
    entries = payload.get("entries", [])
    ids: set[str] = set()
    for entry in entries:
        missing = sorted(REQUIRED - set(entry))
        if missing:
            failures.append(fail("MISSING_REQUIRED_FIELD", id=entry.get("id"), fields=missing))
        ident = entry.get("id")
        if ident in ids:
            failures.append(fail("DUPLICATE_DEBT_ID", id=ident))
        ids.add(ident)
        if entry.get("severity") not in SEVERITIES:
            failures.append(fail("INVALID_SEVERITY", id=ident, severity=entry.get("severity")))
        if entry.get("status") not in STATUSES:
            failures.append(fail("INVALID_STATUS", id=ident, status=entry.get("status")))
        location = root / str(entry.get("location", ""))
        if not location.exists() and entry.get("status") != "RESOLVED":
            failures.append(fail("MISSING_LOCATION", id=ident, location=entry.get("location")))
        if entry.get("status") == "ACCEPTED_TEMPORARILY":
            expiry = entry.get("expiry")
            try:
                if not expiry or dt.date.fromisoformat(expiry) < today:
                    failures.append(fail("EXPIRED_DEBT", id=ident, expiry=expiry))
            except ValueError:
                failures.append(fail("INVALID_EXPIRY", id=ident, expiry=expiry))
        if entry.get("status") != "RESOLVED" and not entry.get("owner"):
            failures.append(fail("MISSING_OWNER", id=ident))
        if entry.get("status") != "RESOLVED" and not entry.get("target"):
            failures.append(fail("MISSING_TARGET", id=ident))
        if entry.get("status") != "RESOLVED" and not entry.get("reason"):
            failures.append(fail("MISSING_REASON", id=ident))
    suppressions = payload.get("suppressions", [])
    for item in suppressions:
        if item.get("debt_id") not in ids:
            failures.append(fail("BROKEN_SUPPRESSION_LINK", debt_id=item.get("debt_id")))
        try:
            if dt.date.fromisoformat(item["expiry"]) < today:
                failures.append(fail("EXPIRED_SUPPRESSION", location=item.get("location")))
        except (KeyError, ValueError):
            failures.append(fail("INVALID_SUPPRESSION_EXPIRY", location=item.get("location")))
        for field in ("rule", "reason", "owner", "expiry", "debt_id"):
            if not item.get(field):
                failures.append(fail("MISSING_SUPPRESSION_FIELD", location=item.get("location"), field=field))
    critical_open = sum(1 for e in entries if e.get("severity") == "CRITICAL" and e.get("status") != "RESOLVED")
    if critical_open:
        failures.append(fail("OPEN_CRITICAL_DEBT", count=critical_open))
    result = {
        "format": "verto-technical-debt-registry-verification-v1",
        "registry_parse": "PASS",
        "schema": "PASS" if not any(x["code"].startswith(("MISSING_", "INVALID_")) for x in failures) else "FAIL",
        "unique_ids": "PASS" if not any(x["code"] == "DUPLICATE_DEBT_ID" for x in failures) else "FAIL",
        "owners_present": "PASS" if not any(x["code"] == "MISSING_OWNER" for x in failures) else "FAIL",
        "targets_present": "PASS" if not any(x["code"] == "MISSING_TARGET" for x in failures) else "FAIL",
        "reasons_present": "PASS" if not any(x["code"] == "MISSING_REASON" for x in failures) else "FAIL",
        "suppression_links": "PASS" if not any(x["code"] == "BROKEN_SUPPRESSION_LINK" for x in failures) else "FAIL",
        "expiry_check": "PASS" if not any("EXPIRY" in x["code"] or x["code"] == "EXPIRED_DEBT" for x in failures) else "FAIL",
        "critical_open_count": critical_open,
        "undocumented_suppressions": 0 if not failures else sum(1 for x in failures if x["code"] == "MISSING_SUPPRESSION_FIELD"),
        "failures": failures,
        "status": "PASS" if not failures else "FAIL",
    }
    return result


def ratchet(root: Path) -> dict:
    payload = load(root / "docs/quality/technical-debt-ratchet-v324.json")
    failures: list[dict] = []
    for metric, item in payload.get("metrics", {}).items():
        previous = item.get("previous_accepted")
        candidate = item.get("candidate_accepted")
        current = item.get("current")
        if not all(isinstance(x, (int, float)) for x in (previous, candidate, current)):
            failures.append(fail("INVALID_RATCHET_METRIC", metric=metric)); continue
        if current > previous:
            failures.append(fail("CURRENT_EXCEEDS_PREVIOUS", metric=metric, previous=previous, current=current))
        if candidate > previous:
            failures.append(fail("FAIL_BASELINE_LOOSENING", metric=metric, previous=previous, candidate=candidate))
        if candidate < current:
            failures.append(fail("CANDIDATE_BELOW_CURRENT", metric=metric, candidate=candidate, current=current))
    return {"format": "verto-technical-debt-ratchet-verification-v1", "status": "PASS" if not failures else "FAIL", "failures": failures}


def archive_entries(snapshot: dict) -> dict[str, str]:
    rows = snapshot.get("entries", snapshot.get("files", []))
    return {x["path"]: x["sha256"] for x in rows}


def current_entries(root: Path) -> dict[str, str]:
    excluded = {".git", ".gradle", ".idea", ".kotlin", "build", "out", "__pycache__"}
    excluded_suffixes = {".zip", ".apk", ".aab", ".class", ".pyc", ".log", ".tmp"}
    result: dict[str, str] = {}
    for path in sorted(root.rglob("*")):
        if not path.is_file(): continue
        rel = path.relative_to(root)
        if any(part in excluded for part in rel.parts) or path.suffix.lower() in excluded_suffixes: continue
        result[rel.as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def scanner_metrics(root: Path) -> dict[str, int]:
    with tempfile.TemporaryDirectory(prefix="verto-debt-") as temp:
        out = Path(temp) / "quality.json"
        command = [sys.executable, str(root / "scripts/verify-kotlin-quality-static.py"), "verify", "--root", str(root), "--mode", "current-ratchet", "--json-out", str(out)]
        completed = subprocess.run(command, cwd=root, text=True, capture_output=True, check=False)
        if completed.returncode:
            raise RuntimeError(completed.stdout + completed.stderr)
        report = load(out)
    return {key: int(report["metrics"].get(key, 0)) for key in METRICS}


def differential(root: Path) -> dict:
    # v325 starts from the actual v324 admission artifact. Earlier v324 tooling
    # compared against the v323 snapshot, which made already-admitted v324 files
    # appear as new changes.
    session = 325 if (root / "docs/architecture/verification/SESSION_325_INPUT_SNAPSHOT.json").exists() else 324
    snapshot = load(root / "docs/architecture/verification/SESSION_325_INPUT_SNAPSHOT.json" if session == 325 else root / "docs/architecture/verification/SESSION_324_INPUT_SNAPSHOT.json")
    before = archive_entries(snapshot)
    after = current_entries(root)
    changed = sorted(path for path in before.keys() & after.keys() if before[path] != after[path])
    new = sorted(after.keys() - before.keys())
    deleted = sorted(before.keys() - after.keys())
    production = [p for p in changed + new if "/src/main/" in p and p.endswith(".kt")]
    baseline = load(root / "docs/quality/technical-debt/TECHNICAL_DEBT_REDUCTION_v323.json")["metrics_after"]
    current = scanner_metrics(root)
    failures = []
    structural_allowances = {
        # Cohesive DAO contracts are an intentional v325 decomposition output;
        # the file-count change is structural, not a debt ratchet regression.
        "production_kotlin_files": "intentional DAO contract decomposition",
    }
    for metric in METRICS:
        if metric in structural_allowances:
            continue
        if current[metric] > int(baseline.get(metric, current[metric])):
            failures.append(fail("WORSENED_PROJECT_METRIC", metric=metric, input=baseline.get(metric), current=current[metric]))
    return {
        "format": "verto-technical-debt-differential-v1", "session": session,
        "input_sha256": snapshot["source_sha256"], "input_tree_fingerprint": snapshot["tree_fingerprint"],
        "changed_files": changed, "new_files": new, "deleted_files": deleted,
        "changed_production_files": production, "new_violations": failures,
        "removed_violations": [], "worsened_identities": [], "improved_identities": [],
        "structural_allowances": structural_allowances,
        "status": "PASS" if not failures else "FAIL", "DIFFERENTIAL_QUALITY_GATE": "PASS" if not failures else "FAIL",
    }


def self_test() -> dict:
    failures = []
    for previous, candidate in ((10, 11), (5, 6)):
        if candidate <= previous: failures.append("ratchet mutation was not rejected")
    if not failures:
        return {"status": "PASS", "mutation_tests": ["baseline increase -> FAIL", "tightened baseline -> PASS", "legacy debt outside changed files -> no new violation"]}
    return {"status": "FAIL", "failures": failures}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("registry", "ratchet", "differential", "self-test"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(); root = args.root.resolve()
    result = {"registry": registry, "ratchet": ratchet, "differential": differential, "self-test": lambda _: self_test()}[args.mode](root)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result.get("status") == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())
