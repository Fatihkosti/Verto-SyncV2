# SESSION 365 — Reports Decision Dashboard

## Result
`PASS_STATIC`

## Implemented
- Rebuilt the Reports landing screen as a Decision Dashboard.
- Six approved KPIs:
  1. Net sales.
  2. Profit + gross margin.
  3. Net cash flow.
  4. Overdue customer receivables as-of the selected period end.
  5. Inventory risk.
  6. Purchases during the selected period with same-length previous-period comparison.
- Two visual hierarchy levels: 120dp primary cards and 96dp compact cards.
- Restrained semantic colors, no dashboard gradients, no auto-rotating insight carousel.
- Added a fixed “يتطلب انتباهك” section showing up to the three highest-priority actionable items.
- Each KPI opens a full-screen drill-down while preserving selected period, filters, and dashboard scroll position.
- Purchase drill-down contains supplier analysis, payables, international supplier statement, invoice analytics, and shipments.
- Purchase KPI deliberately does not mix supplier payables into the purchase amount.
- Added explicit previous-period availability handling; no fake 0% comparison when the previous purchase period is zero/unavailable.
- Added a dedicated net-sales field and previous-period comparison so “صافي المبيعات” is not backed by gross sales.
- Removed the legacy tab-based Reports navigation and superseded presentation components.
- Removed shift open/close actions from Reports presentation; Reports is read/analysis only for shift data.

## Removed legacy presentation
- `FinancialTab.kt`
- `OperationsTab.kt`
- `HeroNetProfitCard.kt`
- `SmartInsightCarousel.kt`
- `CashReconciliationCard.kt`
- `CashCountSheet.kt`
- Legacy `ReportTab`/`availableReportTabs` navigation model and unused duplicate permission-rules file.

## Verification
- Session 365 static gate: **37/37 PASS**.
- Kotlin syntax scan on the rewritten screen/dashboard: no syntax errors detected.
- No SQL, schema, migration, Supabase, Edge Function, or server changes.

## Build gate
Attempted:

```bash
./gradlew :feature:reports:compileDebugKotlin --offline --build-cache
```

Could not execute Gradle compilation because Gradle 8.9 is not present in the local wrapper cache. The wrapper attempted to fetch `gradle-8.9-bin.zip`, but network access is unavailable (`UnknownHostException: services.gradle.org`).

Therefore this session is **PASS_STATIC**, not a claimed build PASS.
