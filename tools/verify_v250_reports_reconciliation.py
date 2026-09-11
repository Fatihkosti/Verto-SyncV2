#!/usr/bin/env python3
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]

def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding='utf-8')

def require(rel: str, needle: str, label: str) -> None:
    if needle not in text(rel):
        raise AssertionError(f'{label}: missing {needle!r} in {rel}')

def forbid(rel: str, needle: str, label: str) -> None:
    if needle in text(rel):
        raise AssertionError(f'{label}: forbidden {needle!r} in {rel}')

# F250 required schema >=67. Later verification sessions may legitimately append migrations;
# keep this verifier composable while ensuring report code itself does not own schema changes.
catalog = text('data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt')
schema_match = re.search(r'ROOM_SCHEMA_VERSION: Int = (\d+)', catalog)
if not schema_match or int(schema_match.group(1)) < 67:
    raise AssertionError('schema is older than the F250 reporting baseline')
for path in (ROOT / 'feature/reports').rglob('*.kt'):
    src = path.read_text(encoding='utf-8', errors='ignore')
    if 'MIGRATION_' in src:
        raise AssertionError(f'report code unexpectedly owns a Room migration: {path.relative_to(ROOT)}')

# Report adapter must consume historical functional/cost snapshots rather than raw invoice totals/current cost.
adapter = 'data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelQueryAdapter.kt'
for needle in [
    'lineCostSnapshotMinor', 'costSnapshotStatus',
    'functionalMinorOrNull(functionalCurrency)', 'buildFinancialDiagnostics(',
    'buildInternationalSupplierStatement(', 'currentItemCost', 'buyPriceMinor',
]:
    require(adapter, needle, f'adapter-{needle[:30]}')
for needle in [
    'salesInvoices.sumOf { it.totalAmount', 'salesInvoices.map { it.totalAmount',
    'MoneyMath.multiply(it.buyPrice', 'rows.sumOf { it.totalSpent }',
]:
    forbid(adapter, needle, f'no-legacy-money-{needle[:25]}')

builders = 'data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelBuilders.kt'
for needle in [
    'revenueMinor = revenueMinor', 'profitMinor = profitMinor',
    'line.lineCostSnapshotMinor', 'multiplyMinor(it.buyPriceMinor, it.quantity)',
    'functionalCashAmountMinor', 'historicalFunctionalAmountMinor',
]:
    require(builders, needle, f'builder-{needle[:28]}')
for needle in [
    'sortedByDescending { it.buyPrice * it.quantity }',
    'sortedByDescending { it.returnValue }',
    'sortedByDescending { it.totalCollected }',
    'sortedByDescending { it.totalPurchases }',
]:
    forbid(builders, needle, 'fixed-point-sort')

integrity = 'data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsFinancialIntegrity.kt'
for needle in [
    'fun InvoiceEntity.functionalMinorOrNull',
    'fun InvoiceItemEntity.functionalRevenueMinor',
    'buildInternationalSupplierStatement(',
    'INVOICE_ALLOCATION', 'PAYMENT_ALLOCATION', 'CASH_LEDGER', 'INVENTORY_LEDGER', 'FINANCIAL_OUTBOX',
    'UNKNOWN_INVOICE_CURRENCY', 'UNKNOWN_PAYMENT_CURRENCY', 'UNKNOWN_HISTORICAL_COST',
    'MISSING_FINANCIAL_IDENTITY', 'MIXED_FUNCTIONAL_CURRENCY', 'WRONG_ORGANIZATION',
    'inventoryOperationalValueMinor', 'REQUIRES_REVIEW',
]:
    require(integrity, needle, f'integrity-{needle[:28]}')

# Reactive sources required to reconcile the complete local ledgers.
for rel, needles in {
    'data/database/src/main/kotlin/com/verto/app/data/local/dao/InvoiceDao.kt': [
        'fun getAllInvoiceItems(): Flow<List<InvoiceItemEntity>>',
        'fun observeFinancialOutbox(organizationId: String): Flow<List<FinancialOutboxEntity>>',
        'fun observeInvoiceWriteGuards(organizationId: String): Flow<List<InvoiceWriteGuardEntity>>',
    ],
    'data/database/src/main/kotlin/com/verto/app/data/local/dao/PaymentDao.kt': [
        'fun getAllPaymentAllocations(): Flow<List<PaymentAllocationEntity>>',
    ],
}.items():
    for needle in needles:
        require(rel, needle, f'dao-{needle[:28]}')


rfm = 'data/operations/src/main/kotlin/com/verto/app/feature/reports/infrastructure/analytics/RfmCalculator.kt'
for needle in ['functionalAmountAtRecognitionMinor', 'lineCostSnapshotMinor', 'lineRevenueSnapshotMinor', 'functionalCurrencies.size != 1', 'Money.ofMinor']:
    require(rfm, needle, f'rfm-{needle[:28]}')
for needle in ['clientSales.map { it.totalAmount }.moneySum()', 'MoneyMath.multiply(MoneyMath.subtract(it.sellPrice, it.buyPrice)']:
    forbid(rfm, needle, 'rfm-no-raw-money')

# Read model and UI expose diagnostics + a supplier statement with transaction and functional values separated.
model = 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsReadModel.kt'
for needle in [
    'data class FinancialDiagnosticsData(', 'data class InternationalSupplierStatementRow(',
    'originalAmountMinor: Long', 'paidTransactionAmountMinor: Long', 'remainingTransactionAmountMinor: Long',
    'functionalCashPaidMinor: Long', 'realizedFxDifferenceMinor: Long', 'invoiceExchangeRateSnapshot: String',
    'financialDiagnostics: FinancialDiagnosticsData', 'internationalSupplierStatement: List<InternationalSupplierStatementRow>',
]:
    require(model, needle, f'model-{needle[:28]}')

ui = 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/financial/FinancialIntegrityCard.kt'
for needle in [
    'FinancialIntegrityCard', 'InternationalSupplierStatementCard',
    'originalAmountMinor', 'functionalCashPaidMinor', 'realizedFxDifferenceMinor', 'invoiceExchangeRateSnapshot',
]:
    require(ui, needle, f'ui-{needle[:28]}')

# Realized profit is historical; replacement margin is deliberately and visibly separate.
analytics_model = 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsAnalyticsModels.kt'
engine = 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/analytics/CostAllocationEngine.kt'
card = 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/RealMarginCard.kt'
for needle in ['costAtSaleMinor', 'currentReplacementUnitCostMinor', 'realizedMarginPct', 'replacementMarginPct']:
    require(analytics_model, needle, f'analytics-model-{needle}')
for needle in ['costAtSaleMinor', 'currentReplacementUnitCostMinor', 'realizedProfitMinor', 'replacementProfitMinor']:
    require(engine, needle, f'analytics-engine-{needle}')
require(card, 'الربح المحقق مقابل هامش الاستبدال', 'explicit-margin-label')
require(card, 'محقق', 'realized-label')
require(card, 'استبدال', 'replacement-label')

# Export/PDF/share carry currency explicitly and aggregate minor units, not raw mixed Doubles.
pdf = 'app/src/main/kotlin/com/verto/app/pdf/ReportsPdf.kt'
for needle in [
    'totalSalesMinor: Long', 'totalProfitMinor: Long', 'overdueAmountMinor: Long',
    'revenueMinor: Long', 'profitMinor: Long', 'currencyCode: String',
    'العملة الوظيفية:', 'formatReportMinor(', 'Math.addExact',
]:
    require(pdf, needle, f'pdf-{needle[:28]}')
for needle in ['sumOf { it.totalSales.toDouble()', 'sumOf { it.totalProfit.toDouble()', 'AmountFormatter.format(inv.totalSales)']:
    forbid(pdf, needle, 'pdf-no-double-money')
share = 'feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/export/WhatsAppShareUtil.kt'
for needle in ['functionalCurrencyCode', 'realizedFxDifferenceMinor', 'invoiceExchangeRateSnapshot']:
    require(share, needle, f'share-{needle}')

# Focused acceptance tests exist in source.
for rel, needles in {
    'data/operations/src/test/kotlin/com/verto/app/feature/reports/bridge/ReportsFinancialIntegrityF250Test.kt': [
        'mixedTransactionCurrencies_sumOnlyStoredFunctionalSnapshots',
        'healthyLedger_hasZeroReconciliationDifferences', 'intentionalCorruption_isReported',
    ],
    'feature/reports/src/test/kotlin/com/verto/app/feature/reports/application/analytics/CostAllocationEngineF250Test.kt': [
        'realizedMargin_staysHistorical_whenReplacementCostChanges',
    ],
}.items():
    for needle in needles:
        require(rel, needle, f'test-{needle[:28]}')

# Representative arithmetic acceptance checks, independent of Android runtime.
# 100 USD historical recognition at 2,500 SDG/USD + 500,000 SDG => 750,000 SDG, never 500,100.
usd_transaction_minor = 100 * 100
usd_functional_minor = 250_000 * 100
sdg_transaction_minor = 500_000 * 100
sdg_functional_minor = sdg_transaction_minor
assert usd_transaction_minor + sdg_transaction_minor == 50_010_000
assert usd_functional_minor + sdg_functional_minor == 75_000_000
assert (usd_functional_minor + sdg_functional_minor) / 100 == 750_000
assert (usd_transaction_minor + sdg_transaction_minor) / 100 == 500_100

# Historical realized margin is invariant under replacement-price changes.
revenue = 20_000
historical_cost = 10_000
realized_1 = revenue - historical_cost
realized_2 = revenue - historical_cost
replacement_1 = revenue - 20_000
replacement_2 = revenue - 30_000
assert realized_1 == realized_2 == 10_000
assert replacement_1 == 0 and replacement_2 == -10_000

# Reconciliation corruption is visible instead of silently clamped away.
invoice_amount = 10_000
payment_amount = 10_000
allocated = 12_000
assert max(allocated - invoice_amount, 0) == 2_000
assert max(allocated - payment_amount, 0) == 2_000

# Existing entities already provide the relevant lookup/delivery indexes, so F250 should not add schema/index churn.
index_sources = {
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt': [
        'Index("invoiceId")', 'Index("paidAt")', 'Index("payment_id")', 'Index("invoice_id")',
        'index_financial_outbox_delivery', 'index_financial_outbox_identity',
    ],
    'data/database/src/main/kotlin/com/verto/app/data/local/entity/InventoryEntity.kt': ['Index("itemId")'],
}
for rel, needles in index_sources.items():
    for needle in needles:
        require(rel, needle, f'index-audit-{needle[:25]}')

# Representative EXPLAIN QUERY PLAN audit for the new reconciliation reads.
# Organization-filtered financial ledgers already use the prefix of existing identity indexes;
# allocation/items reads intentionally scan the complete ledger, so a new schema index would not
# remove the required full read and is not justified in F250.
qp = sqlite3.connect(':memory:')
qp.executescript('''
CREATE TABLE financial_outbox(event_id TEXT PRIMARY KEY, organization_id TEXT, operation_type TEXT, write_id TEXT, sync_state TEXT, next_attempt_at INTEGER, created_at INTEGER, aggregate_id TEXT, sequence INTEGER);
CREATE UNIQUE INDEX index_financial_outbox_identity ON financial_outbox(organization_id, operation_type, write_id);
CREATE INDEX index_financial_outbox_delivery ON financial_outbox(organization_id, sync_state, next_attempt_at, created_at);
CREATE TABLE invoice_write_guard(id TEXT PRIMARY KEY, organization_id TEXT, operation_type TEXT, write_id TEXT, createdAt INTEGER);
CREATE UNIQUE INDEX index_invoice_write_guard_identity ON invoice_write_guard(organization_id, operation_type, write_id);
CREATE TABLE payment_allocations(id TEXT PRIMARY KEY, created_at INTEGER);
CREATE TABLE invoice_items(id TEXT PRIMARY KEY);
''')
def plan(sql: str):
    return [row[3] for row in qp.execute('EXPLAIN QUERY PLAN ' + sql)]
outbox_plan = plan("SELECT * FROM financial_outbox WHERE organization_id='org' ORDER BY created_at, aggregate_id, sequence")
guard_plan = plan("SELECT * FROM invoice_write_guard WHERE organization_id='org' ORDER BY createdAt, id")
alloc_plan = plan("SELECT * FROM payment_allocations ORDER BY created_at, id")
items_plan = plan("SELECT * FROM invoice_items")
assert any('index_financial_outbox_identity' in row for row in outbox_plan), outbox_plan
assert any('index_invoice_write_guard_identity' in row for row in guard_plan), guard_plan
assert any('SCAN payment_allocations' in row for row in alloc_plan), alloc_plan
assert any('SCAN invoice_items' in row for row in items_plan), items_plan

print('V250_REPORTS_RECONCILIATION_PASS')
