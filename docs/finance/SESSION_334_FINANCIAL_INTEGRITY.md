---
status: supporting
scope: finance
owner: "finance-integrity"
last_verified_against: v334
---
# Session 334 Financial Integrity

## Implemented

- `ExpenseEntity.amount_minor: Long` is the financial authority; legacy `amount: Double` remains compatibility-only. The Room field is inherited from a constructor-compatible field carrier so the existing `ExpenseEntity` primary-constructor contract remains unchanged.
- Expense range sums use `SUM(amount_minor)` with `[startInclusive, endExclusive)` bounds.
- Expense insert/update/void execute inside Room transactions with mandatory cash effects and unified outbox writes.
- Update differences use exact minor-unit subtraction.
- Update refunds use `EXPENSE_UPDATE_REFUND`; void refunds use `EXPENSE_VOID_REFUND`; persisted VOID state makes repeat void a no-op.
- Manual cash adjustment permission denial throws `PermissionDeniedException` and is surfaced by `ExpensesViewModel`/Snackbar state.
- Linked expense + landed-cost updates execute under one outer Room transaction; any missing/apply failure throws and rolls back.
- Landed-cost allocation conserves exact minor units or fails closed when the per-unit model cannot represent the exact amount.
- Month queries use first-of-current-month inclusive to first-of-next-month exclusive.

## Verification truth

Static architecture/dependency/compatibility/persistence/transaction/quality/documentation/design-system gates: PASS. The migration-schema guard was generalized without weakening the schema-81 baseline: it recognizes the contiguous `81 -> 82` migration source/catalog/test path and now reports `BLOCKED_ENVIRONMENT` only because the genuine Room compiler export `82.json` cannot be generated in this environment. Manual schema artifacts were deliberately not fabricated.

Session-334 source-extracted behavioral mutation evidence is PASS (`8/8` mutations detected plus exact landed-cost allocator check), and its recorded production-source hashes still match the current tree. Room schema changed `81 -> 82`; source migration and migration instrumentation test were added. Gradle `testDebugUnitTest`, `detekt`, `lintDebug`, and `assembleDebug` were each attempted and are `BLOCKED_ENVIRONMENT` because the wrapper must download Gradle 8.9 from `services.gradle.org` while network name resolution is unavailable.

## Session status

```text
SESSION_334 = BLOCKED_ENVIRONMENT

input_v333_verified = PASS
financial_integrity = PASS
expense_minor_unit_authority = PASS
cash_minor_unit_authority = PASS
expense_insert_atomicity = PASS
expense_update_atomicity = PASS
expense_void_atomicity = PASS
expense_void_idempotency = PASS
update_refund_void_refund_separation = PASS
linked_expense_atomicity = PASS
landed_cost_conservation = PASS
permission_fail_closed = PASS
month_boundary_correctness = PASS
ui_failure_propagation = PASS

room_schema_before = 81
room_schema_after = 82
migration_test = BLOCKED_ENVIRONMENT

focused_tests = BLOCKED_ENVIRONMENT
behavioral_mutations = PASS
architecture_guard = PASS
persistence_guard = PASS
contract_compatibility_guard = PASS
maintainability_testability = PASS
technical_debt_ratchet = PASS
kotlin_quality = PASS

detekt = BLOCKED_ENVIRONMENT
lint = BLOCKED_ENVIRONMENT
full_unit_tests = BLOCKED_ENVIRONMENT
assemble_debug = BLOCKED_ENVIRONMENT
source_of_truth_admission = BLOCKED_ENVIRONMENT

handoff335Authorized = false
```
