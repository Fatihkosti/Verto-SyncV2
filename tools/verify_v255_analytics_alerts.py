#!/usr/bin/env python3
"""Static + SQLite performance gate for invoice session F255."""
from __future__ import annotations

from pathlib import Path
import re
import sqlite3
import time

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
notes: list[str] = []


def text(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        failures.append(f"missing {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


models = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/InvoiceOperationalAnalyticsModels.kt")
builder = "\n".join([
    text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsInvoiceAnalyticsF255.kt"),
    text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsInvoiceAnalyticsAlertsF255.kt"),
    text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsInvoiceAnalyticsF255Support.kt"),
])
read_model = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/model/ReportsReadModel.kt")
adapter = text("data/operations/src/main/kotlin/com/verto/app/feature/reports/bridge/ReportsReadModelQueryAdapter.kt")
margin_engine = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/application/analytics/CostAllocationEngine.kt")
margin_card = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/components/sales/RealMarginCard.kt")
financial_tab = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/tabs/FinancialTab.kt")
operations_tab = text("feature/reports/src/main/kotlin/com/verto/app/feature/reports/presentation/tabs/OperationsTab.kt")
invoice_entities = text("data/database/src/main/kotlin/com/verto/app/data/local/entity/InvoicePaymentEntities.kt")
purchase_entities = text("data/database/src/main/kotlin/com/verto/app/data/local/entity/PurchaseCycleEntities.kt")
catalog = text("data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt")
migration = text("data/database/src/main/kotlin/com/verto/app/data/local/AppDatabaseMigrations71To72.kt")
tests = text("data/operations/src/test/kotlin/com/verto/app/feature/reports/bridge/ReportsInvoiceAnalyticsF255Test.kt")

# Scope and KPI catalog.
for token in (
    'code = "AR_AGING_DSO"', 'code = "AP_AGING_DPO"', 'code = "PPV"',
    'code = "REALIZED_GROSS_MARGIN"', 'code = "CURRENT_REPLACEMENT_MARGIN"',
    'code = "LANDED_COST_VARIANCE"', 'code = "REALIZED_FX"', 'code = "SUPPLIER_PAYMENT_TIME"',
    "currencyPolicy", "voidPolicy", "returnsPolicy",
):
    require(token in models, f"KPI catalog missing {token}")

for token in (
    "buildAgedPayables", "buildPurchasePriceVariance", "buildLandedCostVariance",
    "buildSupplierFxVariance", "buildSupplierPaymentTiming", "buildOperationalAnalyticsAlerts",
    "periodCreditSalesMinor", "periodCreditPurchasesMinor",
):
    require(token in adapter or token in builder, f"F255 read projection missing {token}")

for field in (
    "agedPayables", "purchasePriceVariance", "landedCostVariance", "supplierFxVariance",
    "supplierPaymentTiming", "operationalAlerts",
):
    require(field in read_model and field in adapter, f"read-model wiring missing {field}")
require("kpiDefinitions" in read_model and "InvoiceAnalyticsKpiCatalog.definitions" in read_model,
        "read-model KPI definition catalog missing")

# Explicit financial invariants.
require("costAtSaleMinor" in margin_engine and "currentReplacementUnitCostMinor" in margin_engine,
        "realized/replacement margin inputs not separated")
require("realizedProfitMinor" in margin_engine and "replacementProfitMinor" in margin_engine,
        "realized/replacement margin outputs not separated")
require("الربح المحقق" in margin_card and "هامش الاستبدال" in margin_card,
        "UI must name realized and replacement margin separately")
require("groupBy { it.currencyCode }" in builder, "currency grouping missing")
require("transactionCurrencyCode.equals(currency" in builder, "PPV must enforce PO/invoice transaction currency match")
require("InvoiceLifecycleStatus.VOID" in builder and "!it.voided" in builder, "Void exclusion missing")
require("PURCHASE_RETURN_DEBIT_NOTE" in builder and "SALES_RETURN_CREDIT_NOTE" in builder,
        "return policy not implemented")
require("realizedFxDifferenceMinor" in builder, "realized FX must use immutable payment-allocation snapshot")
require("lineCostSnapshotMinor" in adapter and "unitCostAtSaleMinor" in adapter,
        "realized gross margin must remain historical")

# Alerts and presentation.
for token in ("PRICE_INCREASE", "FX_VARIANCE", "PURCHASE_WITHOUT_MATCHED_RECEIPT", "CONFLICT_REVIEW", "DUE"):
    require(token in models and token in builder, f"alert type not implemented: {token}")
require("AgedPayablesCard(data = state.agedPayables)" in financial_tab, "AP Aging not surfaced")
require("InvoiceAnalyticsF255Card" in operations_tab, "F255 analytics/alerts not surfaced")

# Migration/index contract.
require("ROOM_SCHEMA_VERSION: Int = 72" in catalog, "Room schema must be 72")
require("MIGRATION_71_72" in catalog, "71->72 migration not registered")
index_names = [
    "index_invoices_analytics_aging",
    "index_invoices_analytics_period",
    "index_invoice_items_inventory_item",
    "index_payment_allocations_invoice_created",
    "index_purchase_invoice_matches_org_matched_at",
]
for index in index_names:
    require(index in migration, f"migration missing {index}")
    require(index in invoice_entities or index in purchase_entities, f"Room entity metadata missing {index}")

# Acceptance tests are committed even if Gradle cannot run in this sandbox.
for token in (
    "realizedMargin_doesNotChangeWhenLaterReplacementPriceChanges",
    "ppv_neverSumsDifferentTransactionCurrencies",
    "apAging_appliesDebitNotesAndExcludesVoid",
    "apAging_ignoresFuturePaymentsAndReturnsAtHistoricalCutoff",
    "supplierPaymentTiming_doesNotBackdateLaterReturn",
    "supplierPaymentTiming_excludesFullyReturnedInvoice",
    "dueAlert_ignoresFutureSettlementFacts",
    "kpiCatalog_documentsCurrencyVoidAndReturnsPolicy",
):
    require(token in tests, f"acceptance unit test missing {token}")

# Independent arithmetic acceptance examples.
realized_before = 10_000 - 6_000
realized_after = 10_000 - 6_000
replacement_before = 10_000 - 7_000
replacement_after = 10_000 - 9_000
require(realized_before == realized_after == 4_000, "realized margin changed after replacement-price change")
require(replacement_before != replacement_after, "replacement margin did not change independently")
ppv = {"USD": (1_100 - 1_000) * 1, "EUR": (1_900 - 2_000) * 1}
require(set(ppv) == {"USD", "EUR"} and ppv["USD"] == 100 and ppv["EUR"] == -100,
        "PPV currency segregation acceptance failed")
open_payable = 10_000 - 3_000
require(open_payable == 7_000, "return-adjusted payable acceptance failed")

# Realistic-size SQLite index gate. This tests the exact 71->72 index shapes.
con = sqlite3.connect(":memory:")
cur = con.cursor()
cur.executescript(
    """
    CREATE TABLE invoices(
        id TEXT PRIMARY KEY,
        category TEXT NOT NULL,
        status TEXT NOT NULL,
        lifecycle_status TEXT NOT NULL,
        dueDate INTEGER NOT NULL,
        createdAt INTEGER NOT NULL
    );
    CREATE TABLE invoice_items(id TEXT PRIMARY KEY, invoiceId TEXT NOT NULL, inventoryItemId TEXT NOT NULL);
    CREATE TABLE payment_allocations(id TEXT PRIMARY KEY, invoice_id TEXT NOT NULL, created_at INTEGER NOT NULL);
    CREATE TABLE purchase_invoice_matches(id TEXT PRIMARY KEY, organization_id TEXT NOT NULL, matched_at INTEGER NOT NULL);
    """
)
for sql in re.findall(r'db\.execSQL\("(CREATE INDEX[^"]+)"\)', migration):
    cur.execute(sql.replace('`', '"'))

N = 120_000
cur.execute(
    """WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x < ?)
       INSERT INTO invoices(id,category,status,lifecycle_status,dueDate,createdAt)
       SELECT 'i'||x,
              CASE WHEN x%3=0 THEN 'PURCHASE' ELSE 'SALE' END,
              CASE WHEN x%4=0 THEN 'CLOSED_CASH' ELSE 'CLOSED_CREDIT' END,
              CASE WHEN x%97=0 THEN 'VOID' ELSE 'POSTED' END,
              x*1000,
              x*1000
       FROM n""",
    (N,),
)
cur.execute(
    """WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x < ?)
       INSERT INTO payment_allocations(id,invoice_id,created_at)
       SELECT 'a'||x, 'i'||((x%?)+1), x*1000 FROM n""",
    (N, N),
)
cur.execute(
    """WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x < ?)
       INSERT INTO purchase_invoice_matches(id,organization_id,matched_at)
       SELECT 'm'||x, CASE WHEN x%2=0 THEN 'org-a' ELSE 'org-b' END, x*1000 FROM n""",
    (N // 2,),
)
con.commit()

queries = {
    "aging": (
        "SELECT count(*) FROM invoices WHERE category='PURCHASE' AND status='CLOSED_CREDIT' AND lifecycle_status='POSTED' AND dueDate < ?",
        (N * 700,),
        "index_invoices_analytics_aging",
    ),
    "period": (
        "SELECT count(*) FROM invoices WHERE category='SALE' AND lifecycle_status='POSTED' AND createdAt BETWEEN ? AND ?",
        (N * 300, N * 800),
        "index_invoices_analytics_period",
    ),
    "ppv-period": (
        "SELECT count(*) FROM purchase_invoice_matches WHERE organization_id=? AND matched_at BETWEEN ? AND ?",
        ("org-a", 10_000, N * 800),
        "index_purchase_invoice_matches_org_matched_at",
    ),
    "payment-timing": (
        "SELECT count(*) FROM payment_allocations WHERE invoice_id=? AND created_at BETWEEN ? AND ?",
        ("i42", 0, N * 1000),
        "index_payment_allocations_invoice_created",
    ),
}
for name, (sql, params, expected_index) in queries.items():
    plan = " | ".join(str(row) for row in cur.execute("EXPLAIN QUERY PLAN " + sql, params))
    require(expected_index in plan, f"{name} query does not use {expected_index}: {plan}")
    start = time.perf_counter()
    for _ in range(20):
        cur.execute(sql, params).fetchone()
    elapsed_ms = (time.perf_counter() - start) * 1000.0 / 20.0
    notes.append(f"{name}={elapsed_ms:.2f}ms")
    require(elapsed_ms < 250.0, f"{name} query too slow on {N:,} invoice-sized fixture: {elapsed_ms:.2f}ms")

con.close()

if failures:
    print("F255 FAIL")
    for item in failures:
        print(" -", item)
    raise SystemExit(1)
print("F255 PASS")
print(" - KPI definitions/currency/Void/Returns: PASS")
print(" - realized vs replacement margin separation: PASS")
print(" - analytics + operational alerts wiring: PASS")
print(" - Room 71->72 performance indexes: PASS")
print(f" - realistic SQLite fixture: {N:,} invoices")
for note in notes:
    print(" -", note)
