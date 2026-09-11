# SESSION 364 — Reports Number Integrity

## Source of Truth
- Input: `Verto-v363-error-presentation-closeout.zip`
- Scope: reports numeric integrity only. No report visual redesign, no server/SQL changes.

## Implemented
1. **Deterministic report ranges**
   - TODAY starts at exact local midnight.
   - WEEK starts at the locale's current calendar-week boundary.
   - MONTH is month-to-date, never future month-end.
   - Previous-period comparisons use the matching prior interval.
   - CUSTOM now has a real date-range picker and validates `from <= to`.

2. **Refresh contract repaired**
   - Refresh now increments a generation trigger and re-subscribes the report query.
   - Removed the previous no-op self-assignment behavior.

3. **Invoice-level discount reconciliation**
   - Recognized invoice revenue is allocated back to invoice lines proportionally and exactly.
   - Category/item revenue therefore reconciles to the immutable invoice recognition amount after discounts and FX.
   - Rounding uses cumulative allocation so the line sum equals the invoice amount exactly.

4. **Filter integrity**
   - Payment method and cashier filters scope the sales invoices themselves, not only secondary facts.
   - Cashier scoping prefers stable employee IDs, with payment/invoice fallback for legacy records.
   - Category filters now apply consistently to revenue, COGS, returns, commissions, heatmap, top items and category totals.
   - `غير مصنف` explicitly maps to blank item categories.

5. **Currency integrity**
   - Invoice commissions are converted from transaction currency to functional currency using the immutable recognition ratio.
   - Profit is marked unreliable if sales/returns cannot be represented in one trustworthy functional currency.

6. **Current vs previous comparison integrity**
   - Previous net profit now uses the same recipe as current: recognized revenue, returns, historical COGS, functional commissions and operating expenses.
   - Profit change is suppressed unless both periods are reliable.
   - Sales change is suppressed when currency comparability is incomplete.

7. **Do-not-guess profit policy**
   - Net profit is not presented as a fact when historical cost is incomplete.
   - Net profit is not presented under category/cashier/payment-method segmentation because shared operating expenses are not allocated to those segments.
   - The hero, P&L statement, summary view and text/WhatsApp export all respect this reliability state.
   - No synthetic allocation of shared expenses was introduced.

8. **Insight denominator repairs**
   - Previous sales average uses the actual previous-period duration instead of a hardcoded 30-day divisor.
   - Top-item growth is calculated from the same item in the matching previous period instead of a hardcoded `0.0`.
   - Organization-wide expenses and forecast are not compared against segmented sales denominators.

9. **Budget comparison integrity**
   - Cashier/payment-method segmented reports do not compare against an organization-wide sales budget.
   - Category reports use a matching category target when available.

10. **Regression tests added**
   - Exact discount allocation/reconciliation.
   - Category recognition after discount.
   - Stable cashier scoping + legacy fallback.
   - Functional commission conversion.
   - Equal-duration custom comparison.
   - Unclassified category behavior.
   - Segmented-profit reliability guard.
   - Currency incompleteness reliability guard.
   - Month-to-date and midnight range boundaries.

## Verification
- `tools/quality/session364_verify.py`: **30/30 PASS**
- Session 361 regression gate: **13/13 PASS**
- Session 362 regression gate: **25/25 PASS**
- Session 363 regression gate: **19/19 PASS**
- Standalone Kotlin DateUtils smoke compile/run: **PASS**

## Build / Unit-test execution status
`./gradlew ... --offline --build-cache` could not start because Gradle 8.9 is not present in the environment's wrapper cache. The wrapper attempted to resolve `services.gradle.org`, but network access is unavailable.

Therefore:
- `PASS_STATIC`: yes.
- Gradle unit tests added but **NOT RUN** in this environment.
- Full Android build: **NOT RUN — environment blocker**.
- No dependency versions were changed and no cache/build cleanup was performed.

## Acceptance status
**SESSION_364 = PASS_STATIC / BUILD_NOT_RUN_ENVIRONMENT**
