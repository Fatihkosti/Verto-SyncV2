# Verification — Invoice F244

## 1. Source of Truth

Input: `Verto-v243-source-of-truth.zip`.
Output: `Verto-v244-source-of-truth.zip`.

Session 244 was implemented only on top of v243. No rollback to v238 and no logistics redesign was performed.

## 2. Scope / allowed files

Implemented only the F244 scope established by the F243 allowlist:

- central Money / Quantity / ExchangeRate core
- invoice create/edit validation and calculations
- local-purchase price semantics
- invoice/payment/cash fixed-point persistence bridge
- Room 61→62 additive migration
- AddDebt invoice-entry UI calculation/validation needed to keep UI and Domain on the same rule
- focused tests and verification tools

No shipment planning/execution behavior, auth, dashboard, design-system, or unrelated report behavior was modified.

## 3. F244 invariants

1. New invoice financial calculation uses integer minor units (`Long`), not binary floating point.
2. `SaveInvoiceCommand` carries `Money` / `ExchangeRate` internally; presentation `Double` values are converted at the compatibility boundary.
3. Local purchase supplier total uses `buyPrice` only.
4. Local purchase `buyPrice` is required and positive.
5. Local purchase `sellPrice` is optional and never contributes to supplier invoice total.
6. Invalid/blank quantity never becomes 1.
7. Invalid/blank required price never becomes 0.
8. Arabic-Indic and English numeric input share one parser.
9. Piece quantity is a positive whole-number `Quantity`, matching the current inventory model.
10. Cash balance arithmetic for invoice payment paths is performed from `balance_minor` after schema 62.
11. Legacy REAL/Double columns remain only as compatibility projections while later F246/F249/F250 contracts are still on the old shape.
12. Overflow is fail-closed through exact integer arithmetic plus database range guards.

## 4. Root cause before repair

v243 proved that the save pipeline parsed quantities/prices with silent `?: 1` / `?: 0.0` fallbacks and stored financial truth as `Double`. The AddDebt UI also calculated local purchases from `sellPrice`, while the required supplier truth is `buyPrice`.

## 5. Actual changes

### Money Core

Added:
- `core/common/src/main/kotlin/com/verto/app/money/Money.kt`
  - `Money(amountMinor, currencyCode)`
  - `Quantity`
  - fixed-point `ExchangeRate`
  - shared Arabic/English `NumberText`
  - HALF_UP rounding and exact overflow checks

Changed:
- `core/common/src/main/kotlin/com/verto/app/utils/MoneyMath.kt`
  - retained only as a compatibility facade for code not yet migrated
  - no silent division-by-zero result

### Invoice Domain/Application

Changed:
- `InvoiceWriteModels.kt`
- `SaveInvoiceUseCase.kt`
- `InvoiceSaveValidator.kt`
- `InvoiceDraftFactory.kt`
- `InvoiceInventoryWriter.kt`
- `InvoicePaymentWriter.kt`
- `InvoiceEditPolicy.kt`
- `InvoiceWriteCoordinator.kt`
- `InvoiceFinancialState.kt`
- `InvoiceFinancialStateMapper.kt`
- `FinancialPendingActionProvider.kt`

Key result:
- validation parses once to Money/Quantity and reuses the validated values for totals, persistence, and inventory writes.
- local purchase totals use only `buyPrice`.
- purchase reversal uses the original buy price rather than sell price.

### UI parity

Changed the AddDebt invoice-entry files so valid drafts use the same Money/Quantity rule as Domain. For a local purchase, buy price is now the required primary field and sell price is optional.

### Persistence

Changed:
- `InvoicePaymentEntities.kt`
- `InventoryEntity.kt`
- `InvoiceDao.kt`
- `InventoryDao.kt`
- `CashRegisterDao.kt`
- `InvoiceAppAdapters.kt`
- `MigrationCatalog.kt`

Added:
- `AppDatabaseMigrations61To62.kt`

Schema 62 adds additive INTEGER minor-unit columns for invoice totals/lines, payments, cash, inventory prices and inventory movement unit price. Existing REAL columns are retained for compatibility with later sync/currency sessions.

## 6. Schema / contract changes

Room schema: **61 → 62**.

Migration behavior:
- additive columns only
- deterministic backfill from legacy values using `ROUND(value * 100)`
- no destructive migration
- range guard triggers for fixed-point fields

No Supabase contract was changed in F244. Server DTO migration is intentionally deferred to the dedicated currency/sync sessions so v243's existing sync contract is not broken prematurely.

## 7. Focused tests added/updated

Added:
- `core/common/src/test/kotlin/com/verto/app/money/MoneyCoreTest.kt`
- `feature/invoice/src/test/kotlin/com/verto/app/feature/invoice/application/InvoiceMoneyCoreTest.kt`

Updated:
- `MoneyMathTest.kt`
- `InvoicePurchaseScopeInventoryTest.kt`

Covered acceptance cases:
- `2 × 100 buy + sell 150 = supplier total 200`
- invalid quantity `abc` is rejected
- blank buy price is rejected
- zero buy price is rejected
- `3 × 0.1` = exactly 30 minor units
- sale total still uses sell price
- Arabic/English numeral normalization
- exchange-rate fixed precision
- overflow fail-closed

## 8. Actual verification results

1. `python3 tools/verify_v244_money_core.py`
   - **PASS** — `V244_MONEY_CORE_PASS`

2. Direct Kotlin compiler harness for `Money + InvoiceWriteModels + InvoiceSaveValidator`
   - **PASS** — `V244_KOTLIN_ACCEPTANCE_PASS`
   - exercised local purchase 2×100, 3×0.1, invalid/blank/zero values and Arabic numerals.

3. `python3 tools/verify_v244_migration_sql.py`
   - **PASS** — `V244_MIGRATION_SQL_PASS`
   - executed the additive/backfill SQL against a v61-shaped in-memory SQLite database.

4. `python3 scripts/verify-kotlin-quality-static.py scan`
   - no quality-debt metric increased from v243.
   - v243 and v244 both: architecture violations 18, excessive parameter lists 467, large files >500 lines 14, long functions 342, not-null assertions 1.
   - production Kotlin file count increased only because F244 added Money Core files.

5. Targeted Gradle tests/build:
   - **environment-blocked before any task started**.
   - wrapper requires Gradle 8.9, it is not cached in this sandbox, and `services.gradle.org` cannot resolve here.
   - actual exception: `UnknownHostException: services.gradle.org`.

## 9. Atomicity / rollback evidence

F244 does not redesign the F245 transaction boundary. It preserves the v243 single-AppDatabase transaction owner. New Money calculations feed the existing transaction path; no partial-save workaround was introduced.

The 61→62 migration is additive and does not delete or reinterpret rows beyond deterministic minor-unit backfill.

## 10. Merge / other-feature protection

- v243 remained the base.
- no logistics source file changed.
- all modified production paths are permitted by `docs/invoice/invoice-files-allowlist-244-251.md`.
- no dependency upgrade was introduced.

## 11. Remaining limitations / risks

- Full Android/Room compilation and generated schema export could not be executed in this sandbox because the required Gradle distribution is unavailable offline.
- Existing network/server contracts still expose legacy decimal projections; F246/F249 own their contract migration.
- F245 still owns transaction idempotency, conditional writes and concurrency protection.
- F247 still owns historical cost snapshots/revaluation events/Landed Cost policy completion.

## 12. Decision

**F244 implementation complete with focused static/Kotlin/SQLite verification PASS.**

The only unresolved verification item is the environment-blocked full Gradle build; no code-level acceptance failure was found.
