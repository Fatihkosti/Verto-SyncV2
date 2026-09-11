#!/usr/bin/env python3
"""Execute real isolated behavioral mutations for Verto Session 333.

A mutation is DETECTED only when the focused test passes on the clean isolated
copy and fails after the production mutation. Merely finding strings or test
files is never evidence. Environment failures are reported as
BLOCKED_ENVIRONMENT and never promoted to detection.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any


IGNORED_COPY_DIRS = {".git", ".gradle", ".idea", ".kotlin", "build", "out", "__pycache__"}
ENVIRONMENT_PATTERNS = (
    "UnknownHostException",
    "services.gradle.org",
    "Could not resolve all files",
    "Could not resolve host",
    "Could not download gradle",
    "Unable to tunnel through proxy",
    "No route to host",
    "Connection timed out",
)


@dataclass(frozen=True)
class MutationSpec:
    id: str
    risk: str
    target: str
    source_path: str
    old: str
    new: str
    task: str
    test_filter: str
    detecting_test: str
    replace_count: int = 1


MUTATIONS: tuple[MutationSpec, ...] = (
    MutationSpec(
        "M1", "invoice validation bypass", "InvoiceWriteCoordinator",
        "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt",
        "val validated = preparation.validate(normalizedCommand)",
        "val validated = if (normalizedCommand.items.isEmpty()) ValidatedInvoiceSave(emptyList(), com.verto.app.money.Money.ofMinor(1L, normalizedCommand.transactionCurrencyCode)) else preparation.validate(normalizedCommand)",
        ":feature:invoice:testDebugUnitTest",
        "com.verto.app.feature.invoice.application.InvoiceWriteCoordinatorTest.validation failure blocks persistence side effects",
        "InvoiceWriteCoordinatorTest.validation failure blocks persistence side effects",
    ),
    MutationSpec(
        "M2", "invoice authorization bypass", "InvoiceWriteCoordinator",
        "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt",
        "        if (allowed) return\n        val action = if (creatingNew) \"ترحيل\" else \"تعديل\"",
        "        return // MUTATION M2: authorization bypass\n        val action = if (creatingNew) \"ترحيل\" else \"تعديل\"",
        ":feature:invoice:testDebugUnitTest",
        "com.verto.app.feature.invoice.application.InvoiceWriteCoordinatorTest.authorization rejection blocks persistence and post commit effects",
        "InvoiceWriteCoordinatorTest.authorization rejection blocks persistence and post commit effects",
    ),
    MutationSpec(
        "M3", "post-commit before persistence", "InvoiceWriteCoordinator",
        "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt",
        "    ): InvoiceSaveResult {\n        val outcome = persistNewInvoice(",
        "    ): InvoiceSaveResult {\n        postCommitEffects.afterCreate(InvoiceCreateEffects(command, actor.id, draft.invoice.id, draft.invoice, emptyList()))\n        val outcome = persistNewInvoice(",
        ":feature:invoice:testDebugUnitTest",
        "com.verto.app.feature.invoice.application.InvoiceWriteCoordinatorTest.create path preserves persistence then post commit ordering",
        "InvoiceWriteCoordinatorTest.create path preserves persistence then post commit ordering",
    ),
    MutationSpec(
        "M4", "inventory effect skipped", "RecordShipmentReceivingBatchUseCase",
        "feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/RecordShipmentReceivingBatchUseCase.kt",
        "            postings = postings,\n            batch = batch,",
        "            postings = emptyList(), // MUTATION M4: skip inventory effect\n            batch = batch,",
        ":feature:shipment:testDebugUnitTest",
        "com.verto.app.feature.shipment.application.LogisticsV242EndToEndIntegrationTest.accepted invoice quantity reaches inventory then landed cost settles and shipment closes idempotently",
        "LogisticsV242EndToEndIntegrationTest.accepted invoice quantity reaches inventory then landed cost settles and shipment closes idempotently",
    ),
    MutationSpec(
        "M5", "required payment cash effect skipped", "RecordPaymentCoordinator",
        "feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/RecordPaymentCoordinator.kt",
        "                    PaymentInvoiceCategory.SALE -> cash.onPaymentReceived(movedCash, payment.id)",
        "                    PaymentInvoiceCategory.SALE -> Unit // MUTATION M5: skip required cash effect",
        ":feature:payment:testDebugUnitTest",
        "com.verto.app.feature.payment.application.PaymentCoordinatorsTest.sale payment is persisted and enters cash",
        "PaymentCoordinatorsTest.sale payment is persisted and enters cash",
    ),
    MutationSpec(
        "M6", "sync wake before persistence", "SyncManager",
        "data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt",
        "        val generation = orchestration.persistRequest(scope)\n        logger.fine(\"sync request committed reason=$reason generation=$generation\")\n        if (wake) orchestration.wakeNow(scope)",
        "        if (wake) orchestration.wakeNow(scope) // MUTATION M6: wake before durable intent\n        val generation = orchestration.persistRequest(scope)\n        logger.fine(\"sync request committed reason=$reason generation=$generation\")",
        ":data:sync:testDebugUnitTest",
        "com.verto.app.data.sync.SyncManagerTest.request sync persists generation before wake and wake failure cannot erase it",
        "SyncManagerTest.request sync persists generation before wake and wake failure cannot erase it",
    ),
    MutationSpec(
        "M7", "sync continuation dropped", "SyncManager",
        "data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt",
        "                    RecoveryRunOutcome.CONTINUATION_REQUIRED -> {\n                        orchestration.enqueueContinuation(scope)\n                        return@withLock SyncDrainResult.CONTINUATION_SCHEDULED\n                    }",
        "                    RecoveryRunOutcome.CONTINUATION_REQUIRED -> {\n                        // MUTATION M7: continuation intentionally dropped\n                        return@withLock SyncDrainResult.CONTINUATION_SCHEDULED\n                    }",
        ":data:sync:testDebugUnitTest",
        "com.verto.app.data.sync.SyncManagerTest.continuation scheduling is routed through scheduler seam",
        "SyncManagerTest.continuation scheduling is routed through scheduler seam",
    ),
    MutationSpec(
        "M8", "invalid logistics transition accepted", "LogisticsLifecyclePolicy",
        "feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/domain/policy/LogisticsLifecyclePolicy.kt",
        "    fun canTransition(from: LogisticsShipmentState, to: LogisticsShipmentState): Boolean =\n        to in allowedTransitions.getValue(from)",
        "    fun canTransition(from: LogisticsShipmentState, to: LogisticsShipmentState): Boolean =\n        true // MUTATION M8: accept invalid lifecycle transition",
        ":feature:shipment:testDebugUnitTest",
        "com.verto.app.feature.shipment.domain.policy.LogisticsPoliciesTest.shipment lifecycle allows only declared transitions",
        "LogisticsPoliciesTest.shipment lifecycle allows only declared transitions",
    ),
)

CONTRACT_SEMANTIC_MUTATION = MutationSpec(
    "C1", "critical stable contract default semantic drift", "LogisticsUnifiedReadPort",
    "feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/port/ShipmentPresentationPorts.kt",
    "    suspend fun events(organizationId: String, shipmentId: String): List<ShipmentEventReadRecord> = emptyList()",
    "    suspend fun events(organizationId: String, shipmentId: String): List<ShipmentEventReadRecord> = error(\"MUTATION C1: changed not-found/default event semantics\")",
    ":feature:shipment:testDebugUnitTest",
    "com.verto.app.feature.shipment.application.port.LogisticsUnifiedReadPortContractTest.default events for unknown shipment are empty",
    "LogisticsUnifiedReadPortContractTest.default events for unknown shipment are empty",
)


def _ignore(_: str, names: list[str]) -> set[str]:
    return {name for name in names if name in IGNORED_COPY_DIRS or name.endswith((".zip", ".apk", ".aab", ".pyc"))}


def copy_project(root: Path, destination: Path) -> None:
    shutil.copytree(root, destination, ignore=_ignore, symlinks=True)


def apply_mutation(root: Path, spec: MutationSpec) -> tuple[bool, str]:
    path = root / spec.source_path
    if not path.is_file():
        return False, f"missing mutation target: {spec.source_path}"
    text = path.read_text(encoding="utf-8")
    count = text.count(spec.old)
    if count != spec.replace_count:
        return False, f"mutation anchor count={count}, expected={spec.replace_count}"
    mutated = text.replace(spec.old, spec.new, spec.replace_count)
    if mutated == text:
        return False, "mutation was a no-op"
    path.write_text(mutated, encoding="utf-8")
    return True, "applied"


def focused_command(work_root: Path, spec: MutationSpec) -> list[str]:
    return [
        str(work_root / "gradlew"), "--no-daemon", "--stacktrace",
        spec.task, "--tests", spec.test_filter,
    ]


def environment_blocked(output: str) -> bool:
    return any(marker.lower() in output.lower() for marker in ENVIRONMENT_PATTERNS)


def test_result_xmls(work_root: Path, spec: MutationSpec) -> list[Path]:
    module = spec.task.split(":test", 1)[0].lstrip(":").replace(":", "/")
    base = work_root / module / "build" / "test-results"
    return sorted(base.rglob("TEST-*.xml")) if base.exists() else []


def xml_test_count(paths: list[Path]) -> int:
    total = 0
    for path in paths:
        try:
            root = ET.parse(path).getroot()
            total += int(root.attrib.get("tests", "0"))
        except Exception:
            continue
    return total


def clear_test_results(work_root: Path, spec: MutationSpec) -> None:
    module = spec.task.split(":test", 1)[0].lstrip(":").replace(":", "/")
    for rel in ("build/test-results", "build/reports/tests"):
        shutil.rmtree(work_root / module / rel, ignore_errors=True)


def run_test(work_root: Path, spec: MutationSpec, timeout_seconds: int) -> dict[str, Any]:
    clear_test_results(work_root, spec)
    cmd = focused_command(work_root, spec)
    started = time.monotonic()
    try:
        proc = subprocess.run(
            cmd, cwd=work_root, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, timeout=timeout_seconds,
            env={**os.environ, "CI": "1"},
        )
        output = proc.stdout or ""
        xmls = test_result_xmls(work_root, spec)
        executed = xml_test_count(xmls) > 0
        return {
            "command": cmd,
            "process_exit_code": proc.returncode,
            "test_executed": executed,
            "environment_blocked": environment_blocked(output) and not executed,
            "duration_seconds": round(time.monotonic() - started, 3),
            "result_xmls": [str(p.relative_to(work_root)) for p in xmls],
            "output_tail": "\n".join(output.splitlines()[-80:]),
        }
    except subprocess.TimeoutExpired as exc:
        output = ((exc.stdout or "") + "\n" + (exc.stderr or "")) if isinstance(exc.stdout, str) else ""
        return {
            "command": cmd, "process_exit_code": 124, "test_executed": False,
            "environment_blocked": False, "timed_out": True,
            "duration_seconds": round(time.monotonic() - started, 3),
            "result_xmls": [], "output_tail": output[-12000:],
        }


def execute_spec(root: Path, spec: MutationSpec, timeout_seconds: int) -> dict[str, Any]:
    row: dict[str, Any] = {
        "id": spec.id, "risk": spec.risk, "target": spec.target,
        "source_path": spec.source_path, "detecting_test": spec.detecting_test,
        "mutation_applied": False, "clean_test_executed": False,
        "clean_test_passed": False, "test_executed": False,
        "test_exit_code": None, "failure_causally_related": False,
        "detected": False, "status": "NOT_DETECTED", "cleanup_succeeded": False,
    }
    with tempfile.TemporaryDirectory(prefix=f"verto333-{spec.id.lower()}-") as td:
        work = Path(td) / "project"
        copy_project(root, work)
        clean = run_test(work, spec, timeout_seconds)
        row["clean_execution"] = clean
        row["clean_test_executed"] = bool(clean.get("test_executed"))
        row["clean_test_passed"] = clean.get("process_exit_code") == 0 and row["clean_test_executed"]
        if clean.get("environment_blocked"):
            # Preserve truthful mutation-application evidence even when Gradle cannot
            # execute the focused test. Detection remains false because no test ran.
            applied, detail = apply_mutation(work, spec)
            row["mutation_applied"] = applied
            row["mutation_detail"] = detail
            row["status"] = "BLOCKED_ENVIRONMENT"
            row["failure_reason"] = "focused Gradle test blocked by external environment"
            row["cleanup_succeeded"] = True
            return row
        if not row["clean_test_passed"]:
            row["status"] = "NOT_DETECTED"
            row["failure_reason"] = "clean focused test did not execute and pass"
            row["cleanup_succeeded"] = True
            return row

        applied, detail = apply_mutation(work, spec)
        row["mutation_applied"] = applied
        row["mutation_detail"] = detail
        if not applied:
            row["failure_reason"] = detail
            row["cleanup_succeeded"] = True
            return row

        mutated = run_test(work, spec, timeout_seconds)
        row["mutated_execution"] = mutated
        row["test_executed"] = bool(mutated.get("test_executed"))
        row["test_exit_code"] = mutated.get("process_exit_code")
        if mutated.get("environment_blocked"):
            row["status"] = "BLOCKED_ENVIRONMENT"
        elif row["test_executed"] and row["test_exit_code"] != 0:
            row["failure_causally_related"] = True  # clean PASS + same focused test mutated FAIL
            row["detected"] = True
            row["status"] = "DETECTED"
        else:
            row["failure_reason"] = "mutated focused test did not fail"
        row["cleanup_succeeded"] = True
    return row


def verify(root: Path, timeout_seconds: int) -> dict[str, Any]:
    rows = [execute_spec(root, spec, timeout_seconds) for spec in MUTATIONS]
    contract_row = execute_spec(root, CONTRACT_SEMANTIC_MUTATION, timeout_seconds)
    blocked = any(row["status"] == "BLOCKED_ENVIRONMENT" for row in [*rows, contract_row])
    detected = sum(1 for row in rows if row["detected"])
    status = "PASS" if detected == len(MUTATIONS) and contract_row["detected"] else ("BLOCKED_ENVIRONMENT" if blocked else "FAIL")
    return {
        "format": "verto-behavioral-mutation-matrix-v333",
        "session": 333,
        "status": status,
        "required_behavioral_mutations": len(MUTATIONS),
        "behavioral_mutations_applied": sum(1 for row in rows if row["mutation_applied"]),
        "detecting_tests_executed": sum(1 for row in rows if row["test_executed"]),
        "behavioral_mutations_detected": detected,
        "undetected_behavioral_mutations": len(MUTATIONS) - detected,
        "false_positive_noop_mutations": 0,
        "contract_semantic_mutation": contract_row,
        "mutations": rows,
    }


def _run_python_test(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, "-m", "unittest", "-q", "test_subject.py"],
        cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    )


def self_test() -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="verto333-runner-selftest-") as td:
        root = Path(td)
        subject = root / "subject.py"
        subject.write_text(textwrap.dedent("""\
            def authorize(allowed: bool) -> str:
                if allowed:
                    return "authorized"
                raise PermissionError("denied")
        """), encoding="utf-8")
        (root / "test_subject.py").write_text(textwrap.dedent("""\
            import unittest
            import subject
            class AuthorizationContractTest(unittest.TestCase):
                def test_denied_is_rejected(self):
                    with self.assertRaises(PermissionError):
                        subject.authorize(False)
            if __name__ == "__main__": unittest.main()
        """), encoding="utf-8")
        clean = _run_python_test(root)
        original = subject.read_text(encoding="utf-8")
        mutated_text = original.replace('raise PermissionError("denied")', 'return "authorized"  # real mutation')
        mutation_applied = mutated_text != original
        subject.write_text(mutated_text, encoding="utf-8")
        mutated = _run_python_test(root)
        detected = mutation_applied and clean.returncode == 0 and mutated.returncode != 0

        subject.write_text(original, encoding="utf-8")
        noop_before = subject.read_text(encoding="utf-8")
        subject.write_text(noop_before, encoding="utf-8")
        noop = _run_python_test(root)
        noop_detected = clean.returncode == 0 and noop.returncode != 0
        cleanup_ok = subject.read_text(encoding="utf-8") == original
        status = "PASS" if detected and not noop_detected and cleanup_ok else "FAIL"
        return {
            "BEHAVIORAL_MUTATION_SELF_TEST": status,
            "known_real_mutation": {
                "mutation_applied": mutation_applied,
                "clean_test_exit_code": clean.returncode,
                "mutated_test_exit_code": mutated.returncode,
                "detected": detected,
            },
            "noop_mutation": {"test_exit_code": noop.returncode, "detected": noop_detected},
            "cleanup_succeeded": cleanup_ok,
        }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("verify", "self-test", "report"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    parser.add_argument("--timeout-seconds", type=int, default=900)
    args = parser.parse_args()

    if args.command == "self-test":
        result = self_test()
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0 if result["BEHAVIORAL_MUTATION_SELF_TEST"] == "PASS" else 2

    if args.command == "report":
        report = args.output or (args.root / "docs/quality/maintainability/BEHAVIORAL_MUTATION_MATRIX_v333.json")
        if not report.is_file():
            print(json.dumps({"status": "FAIL", "reason": "report not found", "path": str(report)}, sort_keys=True))
            return 2
        print(report.read_text(encoding="utf-8"))
        payload = json.loads(report.read_text(encoding="utf-8"))
        return 0 if payload.get("status") == "PASS" else 2

    result = verify(args.root.resolve(), args.timeout_seconds)
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if result["status"] == "PASS" else (3 if result["status"] == "BLOCKED_ENVIRONMENT" else 2)


if __name__ == "__main__":
    raise SystemExit(main())
