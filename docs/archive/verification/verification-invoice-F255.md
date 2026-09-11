# verification-invoice-F255

**Date:** 2026-08-19  
**Session:** 255 — التحليلات والتنبيهات التشغيلية

## 1. Source of Truth

- Input: `Verto-v254-source-of-truth.zip`
- Input SHA-256: `d39cdf19ccb3bbf165b3345c392dea2d5ab1f662c413d8b9809aab9dd432a5ac`
- Output: `Verto-v255-source-of-truth.zip`

The supplied SHA-256 sidecar matched the input ZIP before modification.

## 2. Scope

F255 was limited to invoice/purchase/logistics analytics, operational alerts, read-only report projections, and indexes needed for those reads.

No Money Core rule, posted-invoice write path, inventory Last Purchase Price policy, `unitCostAtSale` history, Void/Reversal behavior, Outbox write contract, PO/GRN posting contract, or server schema was intentionally changed.

Principal files added/changed:

- `feature/reports/.../InvoiceOperationalAnalyticsModels.kt`
- `feature/reports/.../ReportsReadModel.kt`
- `feature/reports/.../ReportsModels.kt`
- `feature/reports/.../ReportsViewModel.kt`
- `feature/reports/.../AgedPayablesCard.kt`
- `feature/reports/.../InvoiceAnalyticsF255Card.kt`
- `feature/reports/.../FinancialTab.kt`
- `feature/reports/.../OperationsTab.kt`
- `feature/reports/src/main/res/values/strings.xml`
- `data/operations/.../ReportsInvoiceAnalyticsF255.kt`
- `data/operations/.../ReportsInvoiceAnalyticsAlertsF255.kt`
- `data/operations/.../ReportsInvoiceAnalyticsF255Support.kt`
- `data/operations/.../ReportsReadModelBuilders.kt`
- `data/operations/.../ReportsReadModelQueryAdapter.kt`
- `data/operations/.../ReportsReadModelMappers.kt`
- `data/database/.../ReportsAnalyticsDao.kt`
- `data/database/.../AppDatabaseMigrations71To72.kt`
- `data/database/.../MigrationCatalog.kt`
- entity index declarations and database DI wiring
- `ReportsInvoiceAnalyticsF255Test.kt`
- `tools/verify_v255_analytics_alerts.py`

## 3. Target invariants

1. Realized Gross Margin is historical and cannot change because of a later purchase price.
2. Current Replacement Margin is visibly and semantically separate from realized profit.
3. No KPI silently sums heterogeneous currencies.
4. Void invoices are excluded according to the KPI definition.
5. Returns/debit notes affect each KPI only by the documented policy and event time.
6. A future payment or return cannot rewrite an earlier Aging/alert cutoff.
7. Supplier payment timing uses chronological payment/debit-note events; fully returned invoices do not pollute payment-time averages.
8. A/P Aging and DPO use stored functional snapshots; PPV stays in the PO/invoice transaction currency.
9. F255 is read-only with respect to accounting facts.

## 4. Root cause before F255

v254 already contained the historical realized margin and the separately named replacement margin from F250, plus historical FX/payment snapshots and PO/GRN/logistics facts. However, the reporting layer did not yet provide a complete operational analytics surface for:

- A/P Aging/DPO;
- PPV;
- landed-cost variance by shipment/item;
- realized FX by supplier/period;
- supplier payment timing/variance;
- unified due/price/FX/receipt-match/conflict alerts;
- auditable KPI governance definitions.

During F255 review, two temporal risks were also identified and closed: future settlement facts could otherwise influence historical cutoffs, and purchase debit notes had to participate at their real `occurredAt` time rather than being backdated into supplier-payment timing.

## 5. Actual changes

### KPI governance

Added `InvoiceAnalyticsKpiCatalog` definitions for:

- `AR_AGING_DSO`
- `AP_AGING_DPO`
- `PPV`
- `REALIZED_GROSS_MARGIN`
- `CURRENT_REPLACEMENT_MARGIN`
- `LANDED_COST_VARIANCE`
- `REALIZED_FX`
- `SUPPLIER_PAYMENT_TIME`

Every definition records data source, formula, currency policy, Void policy, and Returns policy.

### Aging / DSO / DPO

- A/R Aging now applies allocations and sales credit notes only up to the requested `asOf` cutoff.
- A/P Aging was added with fixed-point minor units and functional-currency filtering.
- DSO/DPO numerators use total open eligible receivable/payable at period end; Aging buckets remain overdue balances by the documented F255 definition.
- Selected-period denominators use only credit sales/purchases created inside the requested period and subtract matching return documents inside that period.

### PPV

- PPV uses three-way-match line snapshots.
- Formula: `(invoiceUnitPriceMinor - poUnitPriceMinor) × invoicedQuantity`.
- Positive = unfavorable; negative = favorable.
- PO currency must equal the supplier invoice transaction currency.
- Results are grouped per currency and never cross-summed.

### Landed Cost variance

- Estimated and actual logistics base costs are compared per shipment.
- Estimated item allocation uses accepted quantity × base purchase-value basis with deterministic largest-remainder allocation.
- Actual item allocation uses persisted landed/late-cost allocation facts.
- Cancelled, never-started shipments are excluded.

### Realized FX

- Uses immutable `PaymentAllocationEntity.realizedFxDifferenceMinor` and historical functional snapshots.
- Aggregates by supplier and functional currency for allocations occurring in the selected period.
- Void/unknown-currency purchase invoices are excluded.

### Supplier payment timing

- Measures invoice recognition → chronological settlement event.
- Payment allocations and purchase debit notes are merged by their actual event times.
- Future events are ignored for a selected-period cutoff.
- Fully returned invoices are excluded from payment-time averages.
- Supplier variance is measured against the portfolio average.

### Operational alerts

Added read-only alerts for:

- due/overdue credit invoices;
- purchase price increase (>= 5%);
- realized FX variance (>= 2% of historical functional amount);
- purchase invoice without sufficient matched receipt backing;
- financial Outbox records in `REQUIRES_REVIEW`.

Due alerts explicitly ignore payment/return facts occurring after the alert cutoff.

### Read-model/UI

- Added F255 analytics fields to `ReportsReadModel` and `ReportsUiState`.
- Added A/P Aging to Financial reports.
- Added PPV/Landed/FX/payment timing/alerts to Operations reports.
- New F255 UI strings use Android resources.
- The existing F250 realized/replacement margin card was preserved rather than duplicated.

## 6. Schema/contract changes

Room schema: **71 → 72**.

Added indexes:

- `index_invoices_analytics_aging`
- `index_invoices_analytics_period`
- `index_invoice_items_inventory_item`
- `index_payment_allocations_invoice_created`
- `index_purchase_invoice_matches_org_matched_at`

Added read-only `ReportsAnalyticsDao` for PO/match/receipt/logistics report projections.

No new financial table and no server/Supabase migration was introduced.

## 7. Tests and actual results

### F255 acceptance tests committed

`ReportsInvoiceAnalyticsF255Test` covers:

- `realizedMargin_doesNotChangeWhenLaterReplacementPriceChanges`
- `ppv_neverSumsDifferentTransactionCurrencies`
- `apAging_appliesDebitNotesAndExcludesVoid`
- `apAging_ignoresFuturePaymentsAndReturnsAtHistoricalCutoff`
- `supplierPaymentTiming_doesNotBackdateLaterReturn`
- `supplierPaymentTiming_excludesFullyReturnedInvoice`
- `dueAlert_ignoresFutureSettlementFacts`
- KPI currency/Void/Returns documentation.

These JUnit tests are committed but could not be executed through Gradle because the wrapper cannot obtain Gradle 8.9 in this environment.

### Static/SQLite regression gates

Final run:

- F244 migration SQL — PASS
- F244 money core — PASS
- F245 atomic invariants — PASS
- F245 migration SQL — PASS
- F246 currency truth — PASS
- F247 inventory costing — PASS
- F248 invoice lifecycle — PASS
- F249 financial sync — PASS
- F250 reports/reconciliation — PASS
- F251 client-credit money — PASS
- F251 local contracts — PASS
- F252 invoice returns — PASS
- F253 purchase cycle — PASS
- F254 invoice draft UX — PASS
- F255 analytics/alerts — **PASS**

Total: **15/15 PASS**.

### F255 realistic-size query/index gate

`tools/verify_v255_analytics_alerts.py` builds an independent SQLite fixture with **120,000 invoices** and verifies query plans use the F255 indexes.

Representative final measurements in this environment were approximately:

- A/P aging lookup: ~1 ms
- invoice-period lookup: ~1–2 ms
- PPV matched-period lookup: ~1 ms
- payment-timing invoice lookup: <1 ms

The gate threshold is 250 ms per representative query.

### Static quality parity against v254

| Metric | v254 | v255 | Delta |
|---|---:|---:|---:|
| architecture violations | 18 | 18 | 0 |
| broad catches | 21 | 21 | 0 |
| excessive parameter lists | 564 | 564 | 0 |
| files >500 lines | 21 | 21 | 0 |
| long functions | 436 | 436 | 0 |
| not-null assertions | 1 | 1 | 0 |

The historical global Kotlin quality gate remains FAIL against older project ceilings, but F255 adds **zero regression** to these compared metrics.

### Design System parity against v254

| Metric | v254 | v255 | Delta |
|---|---:|---:|---:|
| raw component debt | 222 | 222 | 0 |
| hardcoded UI string debt | 1679 | 1679 | 0 |
| historical enforcement failures | 74 | 74 | 0 |

F255 initially exposed new hardcoded report labels during development; they were moved to `feature/reports` string resources before packaging, restoring exact parity.

### Syntax smoke

`kotlinc` 1.9.0 was used as a parser smoke test over the new core Kotlin files. It reported no syntax/`expecting` errors. Full type resolution is not meaningful without the Android/Room project classpath, so this is not counted as a build.

## 8. Rollback/atomicity evidence

F255 analytics and alerts are read-only projections. They do not post invoices, mutate inventory, allocate payments, or write financial Outbox events.

The only database migration adds indexes; no accounting data is rewritten. Existing transaction/atomicity ownership from F245–F254 remains unchanged.

Temporal calculations use immutable persisted facts and explicit event cutoffs, preventing retrospective KPI mutation by future payment/return events.

## 9. Build result

**BLOCKED BEFORE KOTLIN/KSP COMPILATION.**

Attempted:

```bash
./gradlew :data:operations:compileDebugKotlin :feature:reports:compileDebugKotlin --offline --no-daemon
```

The wrapper attempted to obtain:

`https://services.gradle.org/distributions/gradle-8.9-bin.zip`

and failed with:

`java.net.UnknownHostException: services.gradle.org`

Gradle 8.9 is not cached locally and this sandbox has no network access. Consequently:

- Kotlin project compilation did not start;
- Room/KSP query validation did not run;
- generated Room schema export 72 was not produced here.

## 10. Compatibility with earlier invoice work

All available F244–F254 regression verifiers remain PASS after F255.

The existing realized gross margin / replacement margin separation from F250 is reused unchanged. F255 does not replace `unitCostAtSale`, Last Purchase Price, lifecycle, return, PO/GRN, or sync ownership.

Static Kotlin quality and Design System debt both remain exactly at the v254 baseline, so F255 introduces no measured structural/UI-debt regression.

## 11. Remaining risks / mandatory external gate

1. Run the project with Gradle 8.9 available.
2. Execute the committed F255 JUnit tests.
3. Run KSP/Room compile validation for `ReportsAnalyticsDao` queries.
4. Generate/check Room schema export 72 and run migration validation in the normal laptop/CI environment.
5. Smoke-test the Financial/Operations report cards on device for RTL, scrolling, and realistic production data volumes.

## 12. Decision

**F255 implementation: PASS.**  
**Available static/SQLite acceptance and regressions: PASS (15/15).**  
**Quality/Design-System regression: PASS (delta 0).**  
**Final compiled Room/Kotlin gate: PENDING EXTERNAL GRADLE 8.9 ENVIRONMENT.**
