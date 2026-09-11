---
status: supporting
scope: finance
owner: "persistence"
last_verified_against: v335
---
# Session 335 Migration 82 → 83

Room database version advances from 82 to 83.

`MIGRATION_82_83` adds:

- `index_expenses_lifecycle_date` on `(lifecycle_state, date)`;
- `index_expenses_lifecycle_category_date` on `(lifecycle_state, category, date)`;
- `index_cash_register_movements_type_reference` on `(movementType, referenceId)`.

It also retires pre-v335 pending client `CASH_REGISTER` Outbox commands as `REJECTED / SERVER_AUTHORITATIVE_NO_CLIENT_PUSH`, clearing any lease. It does not rewrite expense/cash history or rebuild financial tables.

`FinanceMigration335Test` was authored to verify index creation and retirement of invalid register commands. The genuine Room compiler export `83.json` cannot be generated because Gradle 8.9 bootstrap fails with `UnknownHostException: services.gradle.org`. The migration-schema gate therefore correctly reports `BLOCKED_ENVIRONMENT`; no manual schema export was fabricated.
