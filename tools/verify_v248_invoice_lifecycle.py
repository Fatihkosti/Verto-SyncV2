#!/usr/bin/env python3
from pathlib import Path
import re, sqlite3, sys

ROOT = Path(__file__).resolve().parents[1]

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def require(rel, needle, label):
    if needle not in text(rel):
        raise AssertionError(f'{label}: missing {needle!r} in {rel}')

# Room lifecycle/migration contract.
schema_match = re.search(r'ROOM_SCHEMA_VERSION: Int = (\d+)', text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt'))
if not schema_match or int(schema_match.group(1)) < 66:
    raise AssertionError('schema-66: current Room schema is older than required')
require('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt', 'MIGRATION_65_66', 'migration-registered')
for needle in [
    'lifecycle_status', 'lifecycle_version', 'posted_at', 'voided_at', 'void_reason', 'void_write_id',
    'item_sku_snapshot', 'unit_snapshot', 'prevent_posted_invoice_hard_delete'
]:
    require('data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations65To66.kt', needle, f'migration-{needle}')

# Domain/state separation and idempotent void.
models = 'feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/domain/model/InvoiceWriteModels.kt'
for needle in [
    'enum class InvoiceLifecycleStatus { DRAFT, POSTED, VOID }',
    'enum class InvoiceVoidPaymentDisposition { REFUND_TO_CASH }',
    'data class InvoiceVoidRequest(',
    'enum class InvoiceWriteOperation { CREATE, UPDATE, VOID }',
    'val lifecycleVersion: Int = 1',
    'val reversedPaymentId: String? = null',
]:
    require(models, needle, f'domain-{needle[:24]}')

void = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceVoidCoordinator.kt')
for needle in [
    'InvoiceWriteOperation.VOID',
    'paymentDisposition == InvoiceVoidPaymentDisposition.REFUND_TO_CASH',
    'getPaymentAllocations(invoiceId)',
    'getRealizedFxEvents(invoiceId)',
    'reversedPaymentId = original.id',
    'original.functionalCashAmountMinor',
    'store.markVoided(',
    'current.lifecycleVersion == expectedVersion',
    'current.shipmentId.isNullOrBlank()',
    'auditLogger.logUpdate(',
]:
    if needle not in void:
        raise AssertionError(f'void contract missing {needle!r}')
if 'invoice.totalAmount,' in void:
    raise AssertionError('void must not reverse cash from recomputed invoice.totalAmount')

edit = text('feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceEditPolicy.kt')
for needle in ['InvoiceLifecycleStatus.POSTED', 'enforceFinancialFieldsUnchanged', 'notes = draft.invoice.notes']:
    if needle not in edit:
        raise AssertionError(f'posted immutability missing {needle!r}')
if 'commission = draft.invoice.commission' in edit:
    raise AssertionError('commission must not be rewritten by posted descriptive edit')


if 'suspend fun void(invoiceId: String)' in void:
    raise AssertionError('void reason/payment handling must not be bypassed by a legacy overload')
if 'historicalPayments.isEmpty()' not in void:
    raise AssertionError('cash void must fail closed when no historical payment exists')

party_port = text('feature/party/src/main/kotlin/com/verto/app/feature/party/application/port/PartyInvoiceVoidPort.kt')
if 'reason: String, refundPayments: Boolean' not in party_port:
    raise AssertionError('party invoice void path must carry explicit reason/payment handling')
party_screen = text('feature/party/src/main/kotlin/com/verto/app/feature/party/presentation/client/ClientScreen.kt')
for needle in ['سبب الإلغاء', 'refundPayments', 'voidReason.trim().length >= 3']:
    if needle not in party_screen:
        raise AssertionError(f'party void UI missing {needle!r}')

commission_query = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt')
if "updateCommission(id: String, commission: Double): Int" not in commission_query or "lifecycle_status = 'DRAFT'" not in commission_query:
    raise AssertionError('direct commission write must be limited to DRAFT invoices')
if "UPDATE invoices SET status = :status, isDirty = 1 WHERE id = :id AND lifecycle_status = 'DRAFT'" not in commission_query:
    raise AssertionError('payment terms/status direct write must be limited to DRAFT invoices')

# Historical stock/cost reversal is append-only.
invdao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InventoryDao.kt')
for needle in ['INVOICE_VOID_REVERSAL:', 'sourceType = "INVOICE_VOID"', 'getLatestCostRevaluationEventForItem', 'original.oldUnitCostMinor']:
    if needle not in invdao:
        raise AssertionError(f'inventory reversal missing {needle!r}')

# Local hard-delete protection and optimistic transitions.
invoice_dao = text('data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt')
for needle in ["DELETE FROM invoices WHERE id = :id AND lifecycle_status = 'DRAFT'", 'markInvoiceVoidedOptimistic', 'updatePostedDescriptionOptimistic', "lifecycle_status = 'DRAFT'"]:
    if needle not in invoice_dao:
        raise AssertionError(f'invoice DAO missing {needle!r}')

# Server additive contract exists.
sql = text('docs/sql/v248_invoice_lifecycle_reversal.sql')
for needle in ['lifecycle_status', 'lifecycle_version', 'trg_prevent_posted_invoice_delete', 'uq_payments_reversed_payment_once']:
    if needle not in sql:
        raise AssertionError(f'server contract missing {needle!r}')

if "where lifecycle_status not in" in sql.lower():
    raise AssertionError('server lifecycle backfill must also populate posted_at for default POSTED legacy rows')

# Execute the key 65->66 migration behavior against a minimal SQLite schema.
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE invoices (
  id TEXT PRIMARY KEY, createdAt INTEGER NOT NULL, voided INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE invoice_items (id TEXT PRIMARY KEY);
INSERT INTO invoices(id, createdAt, voided) VALUES ('posted', 1000, 0), ('void-old', 2000, 1);
''')
db.executescript('''
ALTER TABLE invoices ADD COLUMN lifecycle_status TEXT NOT NULL DEFAULT 'POSTED';
ALTER TABLE invoices ADD COLUMN lifecycle_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE invoices ADD COLUMN posted_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN voided_at INTEGER NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN void_reason TEXT NOT NULL DEFAULT '';
ALTER TABLE invoices ADD COLUMN void_write_id TEXT NOT NULL DEFAULT '';
UPDATE invoices SET lifecycle_status = CASE WHEN voided = 1 THEN 'VOID' ELSE 'POSTED' END,
 posted_at = CASE WHEN voided = 0 THEN createdAt ELSE 0 END;
ALTER TABLE invoice_items ADD COLUMN item_sku_snapshot TEXT NOT NULL DEFAULT '';
ALTER TABLE invoice_items ADD COLUMN unit_snapshot TEXT NOT NULL DEFAULT '';
CREATE TRIGGER prevent_posted_invoice_hard_delete BEFORE DELETE ON invoices
WHEN OLD.lifecycle_status <> 'DRAFT'
BEGIN SELECT RAISE(ABORT, 'posted invoices must be voided, not deleted'); END;
''')
assert db.execute("select lifecycle_status, posted_at from invoices where id='posted'").fetchone() == ('POSTED', 1000)
assert db.execute("select lifecycle_status from invoices where id='void-old'").fetchone()[0] == 'VOID'
try:
    db.execute("delete from invoices where id='posted'")
    raise AssertionError('POSTED hard delete unexpectedly succeeded')
except sqlite3.IntegrityError:
    pass
db.execute("insert into invoices(id, createdAt, voided, lifecycle_status) values('draft', 0, 0, 'DRAFT')")
db.execute("delete from invoices where id='draft'")
assert db.execute("select count(*) from invoices where id='draft'").fetchone()[0] == 0

print('V248_INVOICE_LIFECYCLE_PASS')
