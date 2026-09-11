#!/usr/bin/env python3
"""Static production-wiring gate for the transaction/retry invariants owned by B06."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def source(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")

financial = source("data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialOutboxWriter.kt")
factory = source("data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FinancialSnapshotFactoryV2.kt")
invoice_atomic = source("data/operations/src/main/kotlin/com/verto/app/data/operations/invoice/DefaultInvoiceAtomicPersistenceCoordinator.kt")
payment_atomic = source("data/operations/src/main/kotlin/com/verto/app/data/operations/payment/DefaultPaymentAtomicPersistenceCoordinator.kt")
route = source("data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedFinancialOwner310Route.kt")
inventory = source("feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriter.kt")
purchase = source("feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt")
expense = source("data/operations/src/main/kotlin/com/verto/app/data/repository/ExpenseRepository.kt")
cash = source("data/operations/src/main/kotlin/com/verto/app/utils/CashMovementSyncWriter.kt")
reconciliation = source("data/operations/src/main/kotlin/com/verto/app/data/repository/CashReconciliationRepository.kt")

assert "check(database.inTransaction())" in financial
assert financial.index("snapshotFactory.capture") < financial.index("insertFinancialOutbox") < financial.rindex("captureFrozenIntent(event, snapshot)")
assert "batchCoordinator.seal(" in financial and financial.rindex("captureFrozenIntent(event, snapshot)") < financial.rindex("sealSingleMemberBatch(event)")
for required in (
    "getInvoiceItemsSync", "getDueInstallments", "getPaymentsForInvoiceSync",
    "getPaymentAllocationsForInvoiceSync", "getRealizedFxEventsForInvoiceSync",
    "getForInvoice", "getLines", "getPaymentAllocations",
    "effectReferences", "explicitTombstones",
):
    assert required in factory, f"financial snapshot factory misses {required}"
assert invoice_atomic.index("appendInvoiceEvent") < invoice_atomic.index("financialOutboxWriter.appendInvoice")
assert payment_atomic.index("appendPaymentEvent") < payment_atomic.index("financialOutboxWriter.appendPayment")
assert "AppDatabase" not in route and "database." not in route
assert "parsePayload(row.payloadJson)" in route
assert inventory.count("specializedCapture.capture(") == 2
assert inventory.index("insertInventoryStockOutbox") < inventory.index("specializedCapture.capture(")
assert purchase.count('"purchaseRequest"') >= 3
for frozen_owner in (expense, cash, reconciliation):
    assert '"materialization"' in frozen_owner
assert "getDenominationsForSessionSync" in reconciliation

print("B06_PRODUCER_GATE=PASS financial=full owner310=frozen inventoryPackets=2")
