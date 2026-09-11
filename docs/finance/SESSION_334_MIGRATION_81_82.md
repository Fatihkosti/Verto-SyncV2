---
status: supporting
scope: finance
owner: "finance-integrity"
last_verified_against: v334
---
# Session 334 Migration 81 to 82

`MIGRATION_81_82` adds `expenses.amount_minor INTEGER NOT NULL DEFAULT 0` and backfills every legacy row through the same `Money.fromLegacyDouble(...).amountMinor` conversion used by production. This avoids SQLite floating-point `ROUND(amount * 100)` drift on half-cent edge values such as `1.005`.

Identity, lifecycle, void metadata, dates, category/item/note, and dirty state are untouched. `MigrationCatalog` now targets schema 82.

`ExpenseMigration334Test` seeds ACTIVE/VOID rows across categories plus a `1.005` HALF_UP edge, validates no row loss, IDs/lifecycle, exact minor backfill, and a post-migration minor-unit write.

```text
migration_source = PASS_STATIC
schema_82_export = BLOCKED_ENVIRONMENT
migration_instrumentation_test = BLOCKED_ENVIRONMENT
reason = Gradle 8.9 wrapper download cannot resolve services.gradle.org
```
