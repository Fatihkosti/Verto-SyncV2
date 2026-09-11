# Verto v245 — Implementation Report

## Session

**245 — Atomic transaction, concurrency protection, and local idempotency**

## Status

**Implemented.** All six F245 behavioral acceptance scenarios pass in the executable local verification harness. The Room migration SQL verifier also passes.

## What changed

1. **One invoice transaction**
   - Invoice/items, payment, cash, inventory, revaluation audit, audit log, and Outbox are coordinated under the existing `AppDatabase.withTransaction` boundary.
   - Audit writes were moved from post-commit into the transaction.

2. **Atomic inventory mutation**
   - Sale deduction is a conditional SQL update with affected-row validation.
   - Stock addition uses SQL arithmetic.
   - Failures are propagated so Room can roll back the whole invoice operation.

3. **Local idempotency**
   - Added `invoice_write_guard` with unique `(organization_id, operation_type, write_id)`.
   - Save flow keeps the same `writeId` across retries and suppresses concurrent double-submit.

4. **Supplier invoice duplicate protection**
   - Added organization ID and supplier external-reference fields to invoice persistence.
   - External reference is normalized before persistence.
   - Unique constraint applies to organization + supplier + normalized reference when present.
   - Invoice insert now uses conflict `ABORT`, so a collision cannot replace an existing financial record.

5. **Derived-write traceability**
   - Payment, cash, inventory movement, and audit records carry source/write metadata.

6. **Inventory identity and policy auditing**
   - Explicit inventory ID remains authoritative; silent name matching is not used.
   - Purchase price revaluation is audited inside the transaction.
   - Negative stock is auditable only when the policy permits it.

## Migration

- `Room schema 62 -> 63`.
- Additive migration only.
- No new library or dependency version change.

## Verification

- `V245_ATOMIC_INVARIANTS_PASS`
- `V245_MIGRATION_SQL_PASS`
- Six required acceptance scenarios: **PASS**.
- No files deleted from v244.

See `verification-invoice-F245.md` for detailed evidence.

## Environment limitation

The Android Gradle task could not start because Gradle 8.9 is absent from the local wrapper cache and network resolution is disabled (`UnknownHostException: services.gradle.org`). This is an environment limitation, not a reported test failure.

## Scope exceptions required by F245

F245 requires transactional audit/cash traceability. The following project contracts/storage files were therefore necessarily touched in addition to the invoice/inventory paths:

- `core/audit/.../AuditModels.kt`
- `core/audit/.../WriteAuditPort.kt`
- `data/database/.../AuditAndNotesEntities.kt`
- `data/operations/.../CashRegisterManager.kt`

No unrelated feature redesign was performed.
