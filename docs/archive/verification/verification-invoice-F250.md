# Verification — Invoice F250

## Result

**PASS for F250 source/static/fixed-point acceptance verification.**

Gradle compilation/tests could not start because the sandbox does not contain the Gradle 8.9 distribution and network access is unavailable. Targeted Kotlin compilation for the new fixed-point margin/RFM logic passed, and F249 financial-sync verification remains green.

## Source of Truth

- Input: `Verto-v249-source-of-truth.zip`
- Output: `Verto-v250-source-of-truth.zip`
- Scope: session 250 only — reports, reconciliation, diagnostics, report export/share.

## Implemented invariants

- Report totals never add raw transaction amounts from different currencies.
- Historical reporting uses persisted `functionalAmountAtRecognitionMinor`; no current FX lookup is used.
- Realized gross profit uses persisted `lineCostSnapshotMinor` / `unitCostAtSale`; later purchase-price changes do not rewrite historical profit.
- Current replacement margin uses latest `buyPriceMinor` and is labeled separately from realized profit.
- International supplier statement keeps supplier currency amounts separate from functional cash and realized FX difference.
- VOID invoices are excluded from active report totals; reversal/return ledger rows remain visible through their signed effects.
- PDF/export contracts carry minor units plus explicit currency instead of report-money `Double` aggregation.
- RFM cache monetary/profit calculations now use historical functional/cost snapshots and clear derivative cache rather than combine ambiguous functional currencies.

## Reconciliation and diagnostics

Added `FinancialDiagnosticsData` with checks for:

1. Invoice amount vs allocations/remaining.
2. Payment amount vs allocations/unallocated amount.
3. Cash register vs opening balance + signed cash movements.
4. Inventory snapshot quantity vs movement ledger.
5. Financial write guards vs durable Outbox coverage.

Also reports:

- operational inventory value = current quantity × latest `buyPriceMinor`;
- Outbox events in `REQUIRES_REVIEW`;
- unknown invoice/payment currency;
- unknown historical sale cost;
- missing financial identity;
- mixed functional currency;
- foreign-organization rows.

## International supplier statement

For international purchases the report exposes separately:

- original invoice amount + transaction currency;
- paid transaction amount;
- remaining transaction amount;
- functional cash paid;
- realized FX difference;
- historical invoice exchange-rate snapshot.

No supplier-currency amount is silently mixed with functional cash.

## Export / PDF / share

- Item/category export rows now carry `Long` minor values + `currencyCode`.
- Legacy invoice/client PDF report shapes were also converted to fixed-point minor units.
- PDFs label the functional currency and format from `Money.ofMinor`.
- WhatsApp share text labels functional currency and, when present, international invoice amount, local cash, realized FX difference, and historical rate.

## Room / schema / index decision

- Room schema remains **67**.
- No `67 -> 68` migration was introduced.
- No persistence fields/tables were required for F250 because F246–F249 already persist the necessary currency, historical-cost, allocation, cash, movement and Outbox facts.
- Added read-only DAO flows for all invoice items, payment allocations, financial Outbox and write guards.

Representative `EXPLAIN QUERY PLAN` audit:

- organization-filtered `financial_outbox` uses the existing organization-prefixed Outbox identity index;
- organization-filtered `invoice_write_guard` uses the existing organization-prefixed identity index;
- all-payment-allocation and all-invoice-item reconciliation reads intentionally scan the complete ledger;
- therefore no new Room index/migration was justified in F250.

## Focused acceptance verification

`python3 tools/verify_v250_reports_reconciliation.py`

Result:

`V250_REPORTS_RECONCILIATION_PASS`

The verifier confirms, among other things:

- `100 USD` recognized historically as `250,000 SDG` plus `500,000 SDG` reports `750,000 SDG`, not `500,100`;
- historical realized margin is unchanged when current replacement cost changes;
- intentional allocation corruption is detected;
- fixed-point/currency fields reach report read models, UI, PDF and share paths;
- Room stays at schema 67;
- query-plan/index decision is reproducible.

## Kotlin targeted checks

Standalone Kotlin compilation/execution of `CostAllocationEngine` + the F250 fixed-point model:

`F250_FIXED_POINT_MARGIN_PASS`

Standalone Kotlin compilation of the rewritten fixed-point `RfmCalculator` against contract stubs:

`F250_RFM_FIXED_POINT_COMPILE_PASS`

## Regression verification

- `python3 tools/verify_v246_currency_truth.py` => `V246_CURRENCY_TRUTH_PASS`
- `python3 tools/verify_v247_inventory_costing.py` => `V247_INVENTORY_COSTING_PASS`
- `python3 tools/verify_v249_financial_sync.py` => `V249_FINANCIAL_SYNC_PASS`
- `python3 tools/verify_v250_reports_reconciliation.py` => `V250_REPORTS_RECONCILIATION_PASS`

## Added tests

- `data/operations/src/test/kotlin/com/verto/app/feature/reports/bridge/ReportsFinancialIntegrityF250Test.kt`
  - stored functional snapshot aggregation across USD + SDG;
  - healthy reconciliation = zero differences;
  - deliberate allocation corruption is surfaced.
- `feature/reports/src/test/kotlin/com/verto/app/feature/reports/application/analytics/CostAllocationEngineF250Test.kt`
  - realized historical margin remains fixed after replacement-price changes.

## Build limitation

Attempted:

`./gradlew :feature:reports:testDebugUnitTest :data:operations:testDebugUnitTest :data:database:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon`

Result before Kotlin/Room compilation began:

- wrapper attempted `https://services.gradle.org/distributions/gradle-8.9-bin.zip`;
- failed with `UnknownHostException: services.gradle.org`;
- therefore the Android/Room Gradle tasks were **not executed**, not reported as passing.

## Main production files changed

- `data/database/.../dao/InvoiceDao.kt`
- `data/database/.../dao/PaymentDao.kt`
- `data/operations/.../reports/bridge/ReportsFinancialIntegrity.kt`
- `data/operations/.../reports/bridge/ReportsReadModelBuilders.kt`
- `data/operations/.../reports/bridge/ReportsReadModelQueryAdapter.kt`
- `data/operations/.../reports/infrastructure/analytics/RfmCalculator.kt`
- `feature/reports/.../application/model/ReportsReadModel.kt`
- `feature/reports/.../application/model/ReportsAnalyticsModels.kt`
- `feature/reports/.../application/analytics/CostAllocationEngine.kt`
- `feature/reports/.../presentation/components/financial/FinancialIntegrityCard.kt`
- `feature/reports/.../presentation/components/financial/ProfitLossStatement.kt`
- `feature/reports/.../presentation/components/sales/RealMarginCard.kt`
- `feature/reports/.../presentation/tabs/FinancialTab.kt`
- `feature/reports/.../presentation/export/WhatsAppShareUtil.kt`
- `feature/reports/.../application/model/ReportsExportModels.kt`
- `feature/reports/.../presentation/ReportsModels.kt`
- `feature/reports/.../presentation/ReportsViewModel.kt`
- `app/.../feature/reports/bridge/ReportsPresentationBridge.kt`
- `app/.../pdf/ReportsPdf.kt`

## Remaining gate

F250 is complete at source/contract level. Full Gradle compile, Android/Room tests and release-level migration/performance checks remain part of session 251 and require an environment with Gradle 8.9 available.
