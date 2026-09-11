#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
checks = []

def add(name, ok, detail=""):
    checks.append((name, bool(ok), detail))

def text(rel):
    p = ROOT / rel
    return p.read_text(encoding="utf-8") if p.exists() else ""

# Critical presentation paths migrated away from Throwable.message.
critical = {
    "organization": "feature/organization/src/main/kotlin/com/verto/app/feature/organization/presentation/team/OrganizationTeamViewModel.kt",
    "education": "feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/presentation/education/EducationalTopicsViewModel.kt",
    "invoice": "feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/presentation/invoice/InvoiceViewModel.kt",
    "expenses": "feature/expenses/src/main/kotlin/com/verto/app/ui/screens/expenses/ExpensesViewModel.kt",
    "party": "feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientViewModel.kt",
    "optimal_codes": "feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/OptimalCodesViewModel.kt",
    "optimal_invoices": "feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/presentation/CompanyInvoicesViewModel.kt",
    "commission": "app/src/main/kotlin/com/verto/app/feature/commission/bridge/CommissionControllerAdapter.kt",
}
raw_ui_patterns = [
    re.compile(r"\b(?:error|it|e|exception|throwable)\??\.message\s*\?:"),
    re.compile(r"\b(?:error|it|e|exception|throwable)\.message\?\.takeIf"),
]
for name, rel in critical.items():
    s = text(rel)
    add(f"no_raw_throwable_message:{name}", s and not any(p.search(s) for p in raw_ui_patterns), rel)

# Payment exception boundaries must humanize; explicit business strings remain allowed.
for rel in [
    "feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/RecordPaymentCoordinator.kt",
    "feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/ReversePaymentCoordinator.kt",
    "feature/payment/src/main/kotlin/com/verto/app/feature/payment/application/BulkPaymentCoordinator.kt",
]:
    s = text(rel)
    add(f"payment_classified:{Path(rel).name}", "ErrorHumanizer.humanize" in s and not re.search(r"\b(?:it|error)\.message\s*\?:", s))

# Invoice save classification is structural, not message-token matching.
invoice_editor = text("feature/payment/src/main/kotlin/com/verto/app/feature/payment/presentation/invoiceeditor/InvoiceEditorViewModel.kt")
add("invoice_save_uses_classifier", "ErrorClassifier.classify(error)" in invoice_editor)
add("invoice_save_no_message_heuristic", "generateSequence(error)" not in invoice_editor and "raw::contains" not in invoice_editor)

# Logistics has stable business codes at producer and consumer boundaries.
plan_ui = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2PlanningActionsB.kt")
doc_ui = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/presentation/logisticsv2/LogisticsV2ExecutionActions.kt")
validation = text("feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/domain/validation/LogisticsValidation.kt")
journey = text("app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsJourneyAdapters.kt")
doc_adapter = text("app/src/main/kotlin/com/verto/app/feature/shipment/bridge/LogisticsDocumentAdapters.kt")
add("shipment_inventory_link_typed", "SHIPMENT_INVENTORY_LINK_REQUIRED" in validation and "SHIPMENT_INVENTORY_LINK_REQUIRED" in plan_ui)
add("shipment_invoice_conflict_typed", "SHIPMENT_PURCHASE_INVOICE_CONFLICT" in journey and "SHIPMENT_PURCHASE_INVOICE_CONFLICT" in plan_ui)
add("shipment_document_typed", all(code in doc_adapter and code in doc_ui for code in ["LOGISTICS_DOCUMENT_TOO_LARGE", "LOGISTICS_DOCUMENT_UNSUPPORTED"]))
add("shipment_no_message_contains", ".message?.contains" not in plan_ui and ".message?.contains" not in doc_ui)

# Sync orchestration sentinel is typed.
sync_manager = text("data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt")
sync_vm = text("app/src/main/kotlin/com/verto/app/feature/sync/presentation/SyncViewModel.kt")
add("sync_skipped_typed", 'BusinessRuleFailureException("SYNC_SKIPPED")' in sync_manager and "isSyncSkipped()" in sync_vm)
add("sync_vm_no_message_sentinel", 'message?.startsWith("sync_skipped")' not in sync_vm)

# Optimal no longer persists / interprets arbitrary throwable text as user-facing classification.
opt_recovery = text("feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/OptimalSyncRecoveryPolicy.kt")
opt_human = text("feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/application/OptimalSyncErrorHumanizer.kt")
opt_exec = text("feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/ContractGuardedOptimalOutboxEventExecutor.kt")
add("optimal_recovery_uses_classifier", "ErrorClassifier.classify(throwable).diagnosticCode" in opt_recovery and "throwable.message" not in opt_recovery)
add("optimal_humanizer_exact_codes", "containsAny" not in opt_human and "when (rawReason?.trim()?.uppercase())" in opt_human)
add("optimal_remote_reason_not_persisted", "result.reason.normalizedReason" not in opt_exec and "REMOTE_RETRYABLE_FAILURE" in opt_exec)

# Core 361 invariants remain intact.
humanizer = text("core/crash/src/main/kotlin/com/verto/app/utils/ErrorHumanizer.kt")
classifier = text("core/common/src/main/kotlin/com/verto/app/core/error/ErrorClassifier.kt")
add("core_classifier_still_authoritative", "ErrorClassifier.classify" in humanizer)
add("no_arabic_trust_heuristic", "hasArabic" not in humanizer)
add("generic_io_not_network", "chain.any { it is IOException }" in classifier and "NetworkUnavailable" in classifier)

failed = [c for c in checks if not c[1]]
for name, ok, detail in checks:
    print(f"{'PASS' if ok else 'FAIL'} {name}{(': ' + detail) if detail else ''}")
print(f"\nSESSION_362_STATIC={len(checks)-len(failed)}/{len(checks)} {'PASS' if not failed else 'FAIL'}")
raise SystemExit(1 if failed else 0)
