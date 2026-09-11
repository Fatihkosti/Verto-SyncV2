# VERTO — Invoice F245 Verification

## Result

**F245 acceptance verification: PASS** for the executable local transaction/concurrency harness and migration SQL harness.

## Verified implementation invariants

- Room version confirmed from the project: **2.6.1**.
- The invoice transaction is owned by the existing `AppDatabase` transaction runner using `AppDatabase.withTransaction`.
- Invoice/items, payment, cash movement, inventory movement, revaluation audit, audit log, and Outbox are executed inside the same invoice transaction.
- Audit was removed from post-commit effects; post-commit now contains only non-transactional follow-up effects.
- Inventory sale deduction uses a single conditional SQL `UPDATE ... quantity = quantity - :quantity ... AND quantity >= :quantity` when negative stock is disabled.
- Inventory add uses SQLite arithmetic instead of an unprotected read-then-write quantity update.
- Unexpected affected-row counts throw and therefore roll back the Room transaction.
- Item-loop failures are propagated; no silent `return@forEach` remains in the invoice inventory writer.
- Local idempotency guard is persisted with unique `(organization_id, operation_type, write_id)`.
- UI save flow keeps one `writeId` during retry/in-flight work and blocks concurrent double-submit.
- Derived invoice payment/cash/inventory/audit records carry source metadata (`sourceType`, `sourceId`, `sourceVersion`, `writeId`).
- Supplier invoice reference is normalized and uniquely constrained per organization + supplier when present.
- Local invoice insertion uses `ABORT`, preventing a duplicate supplier reference from replacing an existing invoice.
- Explicit `inventoryItemId` is authoritative; name matching is not used as a silent substitute.
- Purchase price changes create a transactional inventory revaluation audit event.
- Allowed negative-stock outcomes are explicitly audited when the policy is enabled.

## Acceptance cases

| Case | Result |
|---|---|
| Stock 3, sale 5, negative forbidden => no invoice/payment/cash/outbox | PASS |
| Second line fails => first line effect rolls back | PASS |
| Two concurrent sales for last unit => exactly one succeeds | PASS |
| Same `writeId` repeated 10 times => one financial effect | PASS |
| Outbox write failure => invoice and all side effects roll back | PASS |
| Negative stock only when policy enabled + audit row | PASS |

Executable verifier:

```text
python3 tools/verify_v245_atomic_invariants.py
V245_ATOMIC_INVARIANTS_PASS
```

Migration verifier:

```text
python3 tools/verify_v245_migration_sql.py
V245_MIGRATION_SQL_PASS
```

The migration verifier also confirms:

- duplicate normalized supplier invoice reference for the same organization/supplier is rejected;
- multiple `NULL` supplier references remain allowed;
- duplicate local idempotency guard keys are rejected.

## Database migration

- Schema: **62 -> 63**.
- Migration is additive; no destructive migration was introduced.
- No dependency was added or upgraded.

## Static quality comparison

| Metric | v244 | v245 |
|---|---:|---:|
| architecture violations | 18 | 18 |
| broad catches | 20 | 20 |
| dependency cycles | 0 | 0 |
| global scope | 0 | 0 |
| exposed mutable state | 0 | 0 |
| large files >500 | 14 | 14 |
| lateinit | 4 | 4 |
| not-null assertions | 1 | 1 |
| manual coroutine scopes | 1 | 1 |
| excessive parameter lists | 467 | 481 |
| long functions | 342 | 343 |

The increases are localized to the required write/source metadata and atomic coordination work; the pre-existing architecture/safety counts did not regress.

## Build limitation

A full Android/Room compilation could not start in this sandbox because the Gradle wrapper distribution **8.9** is not cached and the environment cannot resolve `services.gradle.org`:

```text
./gradlew :feature:invoice:testDebugUnitTest --offline --no-daemon
Downloading https://services.gradle.org/distributions/gradle-8.9-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Therefore generated Room schema output for version 63 could not be produced here. The source/migration acceptance harnesses above were executed successfully.
