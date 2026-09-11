# Verto v255 — Session Report

F255 is implemented on top of the verified `Verto-v254-source-of-truth.zip`.

## Implemented

- A/R Aging + DSO hardened to historical period-end facts.
- A/P Aging + DPO in functional currency.
- Purchase Price Variance (PPV), grouped by transaction currency without cross-currency summation.
- Realized Gross Margin remains historical from `unitCostAtSale`/cost snapshots.
- Current Replacement Margin remains a separately named metric from Last Purchase Price.
- Landed Cost variance by shipment and accepted item.
- Realized FX gain/loss by supplier and selected period.
- Supplier payment-time averages and supplier variance, with chronological payment/debit-note settlement.
- Operational alerts for due/overdue invoices, purchase-price increases, FX variance, unmatched receipts, and review conflicts.
- Auditable KPI catalog: source, formula, currency policy, Void policy, and Returns policy.
- Room schema **71 → 72** with analytics indexes.
- Read-only `ReportsAnalyticsDao` for PO/GRN/logistics analytics.
- F255 reports UI wired into Financial and Operations tabs.
- New UI strings are resource-backed; Design System debt is unchanged from v254.
- No increase in Kotlin quality-debt metrics versus v254.

## Verification

- F244–F255 static/SQLite regression verifiers: **15/15 PASS**.
- F255 realistic SQLite fixture: **120,000 invoices**, required indexes selected by query planner; measured queries ~0–2 ms in this environment.
- F255 temporal tests committed for future payment/return cutoffs and chronological supplier settlement.
- Kotlin static quality metrics: exact parity with v254.
- Design System scan: exact parity with v254 (`rawComponentDebt=222`, `hardcodedUiStringDebt=1679`, 74 historical failures).

## External gate still required

Gradle/KSP compilation could not start because Gradle 8.9 is not cached locally and network access is disabled. The generated Room schema 72 and compiled Room query validation therefore require the laptop/CI build.

See `verification-invoice-F255.md` for full evidence.
