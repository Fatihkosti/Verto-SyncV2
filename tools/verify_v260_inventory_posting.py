#!/usr/bin/env python3
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
checks = []

def text(path):
    return (ROOT / path).read_text(encoding='utf-8')

def check(name, cond):
    checks.append((name, bool(cond)))

invoice_writer = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceInventoryWriter.kt')
coordinator = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt')
purchase = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/PurchaseCycleCoordinator.kt')
shipment = text('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/RecordShipmentReceivingBatchUseCase.kt')
validation = text('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/domain/validation/LogisticsValidation.kt')
inv_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt')
stock_writer = text('feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriter.kt')
draft_entity = text('data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoiceDraftEntities.kt')
invoice_models = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/model/InvoiceWriteModels.kt')
purchase_models = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/model/PurchaseCycleModels.kt')
paid_cost = text('feature/shipment/src/main/kotlin/com/verto/app/feature/shipment/application/ConfirmLogisticsPaidCostUseCase.kt')
test = text('feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/InvoicePurchaseScopeInventoryTest.kt')

# Drafts remain editor-local and do not post accounting/inventory facts.
check('editor drafts explicitly local-only/non-posting', 'local-only recoverable invoice editor draft' in draft_entity and 'Never synced or posted as accounting data' in draft_entity)
check('draft->post path does not reverse nonexistent draft stock', 'inventoryWriter.reverseForEdit(request' not in coordinator)
check('invoice lifecycle exposes draft posted void', 'enum class InvoiceLifecycleStatus { DRAFT, POSTED, VOID }' in invoice_models)
check('purchase receipt lifecycle exposes partial receipt', 'PARTIALLY_RECEIVED' in purchase_models)

# Invoice posting must use line identities, not the invoice-level writeId for every line.
check('sale posting uses line-aware stock API', 'InvoiceLinePostingStockPort' in invoice_writer and 'deductPostingLine(mutation, identity)' in invoice_writer)
check('invoice line command is derived from invoice + source line', '"invoice-post:$invoiceId:$sourceLineId"' in invoice_writer)
check('invoice posting group is stable per invoice', '"invoice-post:$invoiceId"' in invoice_writer)
check('coordinator passes persisted invoice lines into inventory posting', 'writeForCreate(' in coordinator and 'persistedLines,' in coordinator and 'writeForEdit(request, persistedLines)' in coordinator)
check('purchase movement carries sourceLineId', 'sourceLineId = sourceLineId' in invoice_writer)
check('purchase movement carries postingGroupId', 'postingGroupId = postingGroupId(request.invoiceId)' in invoice_writer)


transaction_owner = text('data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/FeatureTransactionAdapters.kt')
transaction_runner = text('data/operations/src/main/kotlin/com/verto/app/data/operations/transaction/RoomDatabaseTransactionRunner.kt')
check('invoice post owns one Room transaction', 'runner.inTransaction(block)' in transaction_owner and 'database.withTransaction { block() }' in transaction_runner)
check('invoice and stock writes occur inside transaction block', 'transactionPort.inTransaction {' in coordinator and 'inventoryWriter.writeForCreate(' in coordinator and 'inventoryWriter.writeForEdit(request, persistedLines)' in coordinator)

# Local GRN: one idempotency identity per accepted receipt line.
check('GRN derives a line-specific write id', 'stableId("grn-post", receiptId, line.id)' in purchase)
check('GRN posts accepted quantity only', 'receiptLines.filter { it.acceptedQuantity > 0 }' in purchase and 'quantity = line.acceptedQuantity' in purchase)
check('GRN prevents accepted quantity beyond order quantity', 'acceptedBefore' in purchase and '<= orderLine.orderedQuantity' in purchase)
check('GRN carries source line and posting group', 'sourceLineId = line.id' in purchase and 'postingGroupId = receiptId' in purchase)
check('international GRN does not own stock posting', 'if (order.purchaseScope == PurchaseScope.LOCAL)' in purchase)
check('logistics payment is separate from stock quantity', 'cash.postExpense(' in paid_cost and 'savePayment(' in paid_cost and 'quantity' not in paid_cost and 'InventoryStock' not in paid_cost)

# Logistics receipt: request idempotency + cumulative cap + accepted-only posting.
check('shipment retry is request-idempotent', 'store.isRequestProcessed(organizationId, command.requestId)' in shipment)
check('shipment posting includes accepted quantities only', 'command.lines.filter { it.acceptedQuantity > 0 }' in shipment)
check('shipment cumulative receipt cannot exceed expected', 'cumulative <= line.expectedQuantitySnapshot.toLong()' in validation)
check('shipment movement stores receipt line identity', 'sourceLineId = receivingLineId' in inv_dao)
check('shipment movement stores receiving batch as posting group', 'postingGroupId = receivingBatchId' in inv_dao)

# v259 writer remains the gateway and now propagates line identity.
check('writer propagates line identity on issue', 'sourceLineId = meta.sourceLineId' in stock_writer and 'postingGroupId = meta.postingGroupId' in stock_writer)
check('writer propagates purchase line identity', 'sourceLineId = command.sourceLineId' in stock_writer and 'postingGroupId = command.postingGroupId' in stock_writer)

# Regression test explicitly covers the v259 multi-line idempotency defect.
check('purchase regression covers distinct line identities', 'local_purchase_posts_every_line_with_distinct_line_identity' in test and 'assertNotEquals(stock.purchaseCommands[0].writeId' in test)
check('sale regression covers distinct line identities', 'local_sale_posts_every_line_with_distinct_line_identity' in test and 'assertNotEquals(stock.saleIdentities[0].writeId' in test)

# Contract simulation for the v260 invariant: one stable command per line, one transaction per posting group.
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE stock(item TEXT PRIMARY KEY, quantity INTEGER NOT NULL);
CREATE TABLE guards(command TEXT PRIMARY KEY);
CREATE TABLE movements(command TEXT PRIMARY KEY, source_line TEXT NOT NULL, posting_group TEXT NOT NULL, delta INTEGER NOT NULL);
INSERT INTO stock VALUES('item', 100);
""")

def post_group(invoice_id, lines, fail_line=None):
    try:
        db.execute('BEGIN')
        for source_line, delta in lines:
            command_id = f"invoice-post:{invoice_id}:{source_line}"
            claimed = db.execute('INSERT OR IGNORE INTO guards VALUES(?)', (command_id,)).rowcount
            if claimed == 0:
                continue
            db.execute("UPDATE stock SET quantity = quantity + ? WHERE item='item'", (delta,))
            db.execute(
                'INSERT INTO movements VALUES(?,?,?,?)',
                (command_id, source_line, f"invoice-post:{invoice_id}", delta),
            )
            if source_line == fail_line:
                raise RuntimeError('forced line failure')
        db.commit()
        return 'ok'
    except Exception:
        db.rollback()
        return 'failed'

check('two invoice lines both post under one group', post_group('inv-a', [('line-a', -2), ('line-b', -3)]) == 'ok')
quantity = db.execute("SELECT quantity FROM stock WHERE item='item'").fetchone()[0]
movements = db.execute("SELECT COUNT(*) FROM movements WHERE posting_group='invoice-post:inv-a'").fetchone()[0]
check('multi-line posting applies every line once', quantity == 95 and movements == 2)
check('retrying same invoice lines is idempotent', post_group('inv-a', [('line-a', -2), ('line-b', -3)]) == 'ok')
quantity_retry = db.execute("SELECT quantity FROM stock WHERE item='item'").fetchone()[0]
movements_retry = db.execute("SELECT COUNT(*) FROM movements WHERE posting_group='invoice-post:inv-a'").fetchone()[0]
check('retry creates no second movement or stock delta', quantity_retry == 95 and movements_retry == 2)
check('forced second-line failure aborts posting group', post_group('inv-b', [('line-c', -4), ('line-d', -5)], fail_line='line-d') == 'failed')
quantity_failed = db.execute("SELECT quantity FROM stock WHERE item='item'").fetchone()[0]
failed_moves = db.execute("SELECT COUNT(*) FROM movements WHERE posting_group='invoice-post:inv-b'").fetchone()[0]
failed_guards = db.execute("SELECT COUNT(*) FROM guards WHERE command LIKE 'invoice-post:inv-b:%'").fetchone()[0]
check('line failure rolls back stock movements and guards', quantity_failed == 95 and failed_moves == 0 and failed_guards == 0)
db.close()

failed = [name for name, ok in checks if not ok]
for i, (name, ok) in enumerate(checks, 1):
    print(f'{i:02d}. {"PASS" if ok else "FAIL"} — {name}')
print(f'\nResult: {len(checks)-len(failed)}/{len(checks)} checks passed')
if failed:
    print('Failed:')
    for name in failed:
        print(f'- {name}')
    raise SystemExit(1)
