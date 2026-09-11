# Verto v236 Implementation Report

## Result

v236 implements the complete suppliers/invoices planning slice on top of v235 without changing Room schema, migrations, modules, or dependencies.

## Delivered

- Dedicated **الموردون والفواتير** screen separated from route planning.
- Multiple suppliers as independent groups.
- Searchable supplier selector and searchable multi-select invoice bottom sheet.
- International purchase invoice eligibility filtering and active-shipment conflict exclusion.
- Transaction-time conflict revalidation before purchase-plan persistence.
- Invoice cards with planned cartons, weight, expected readiness date, exact validation, and confirmation on removal.
- Invoice sheet metadata: number, date, total, readiness status; no item rows/images/names/prices.
- Existing source/line snapshots retained at save time.
- Partial purchase metadata persisted through existing Room fields and restored by the existing planning read path.
- Loading, empty, retry, save-progress, and error states.
- Narrow-screen / large-font metric reflow.
- v236 rule tests added for validation, search privacy, multiple suppliers/removal, totals, and ineligible/conflicting invoice rejection.

## Persistence / architecture

- Room schema: **61 → 61**.
- Migrations added: **none**.
- Modules/dependencies: **unchanged**.
- v235 country-visibility rules: **preserved**.

## Verification

### Passed

- Domain-model direct Kotlin compile.
- Pure v236 planning/rules direct Kotlin compile.
- v236 smoke execution: validation, hidden-item search isolation, multiple supplier behavior, removal, and planned totals.
- Purchase-plan use-case direct Kotlin compile with minimal local stubs.
- Static quality versus v235: no regression.
  - architecture violations: 18 → 18
  - broad catches: 20 → 20
  - dependency cycles: 0 → 0
  - excessive parameter lists: 451 → 451
  - large files: 12 → 12
  - long functions: 330 → 330
- Source audit confirms the v236 purchase UI does not render item names, item images, item rows, or item prices.
- Conflict guard exists in chooser eligibility and inside the persistence transaction.

### Environment-limited

- Full Gradle unit/instrumentation execution cannot run because Gradle 8.9 is not cached and the sandbox has no network access.
- Screenshot/golden and device-runtime checks therefore cannot execute here.
- The logistics repository guard still reports the pre-existing missing exported Room schema `app/schemas/com.verto.app.data.local.AppDatabase/61.json`; v236 has no schema change and does not fabricate it.

## v237 handoff

Read `docs/logistics/v236/V236_IMPLEMENTATION_CONTRACT.md`. v237 starts from this package and implements trip type, route building, and repeated station detail screens without reintroducing supplier/invoice UI.
