# Verification — Invoice F248

## Result
PASS for source/static/migration-contract verification. Full Gradle compilation could not start because this environment has no cached Gradle 8.9 distribution and network access is unavailable.

## Acceptance evidence
- Stored lifecycle is explicit: `DRAFT / POSTED / VOID`; legacy `CLOSED_CASH / CLOSED_CREDIT` remains payment terms compatibility only.
- POSTED financial fields are immutable: lines, party, payment terms, total and commission cannot be rewritten through the edit path.
- Safe POSTED edits are limited to notes and, while not fully paid, due date; the write is guarded by `lifecycleVersion`.
- Direct commission and direct payment-term DAO writes are limited to DRAFT.
- Void requires explicit permission and a mandatory reason.
- Paid invoices require explicit `REFUND_TO_CASH`; payments never silently disappear.
- Reversal rows reference original payments and negate the original allocation and realized-FX values.
- Cash reversal uses `functionalCashAmountMinor` captured on the original payment; an international cash invoice is never reversed using a newly calculated exchange value.
- A cash invoice with no trustworthy historical payment record fails closed instead of inventing a reversal value.
- Inventory reversal appends inverse movements linked to the Void request and restores prior latest-purchase cost only when the voided purchase is still the latest cost event.
- Repeating the same Void `requestId` is idempotent; the lifecycle transition is additionally protected by optimistic versioning.
- Shipment/receipt-linked invoices are blocked from direct Void to prevent a broken dependency chain.
- POSTED/VOID invoices cannot be hard-deleted: Room has a delete guard and the server contract has a matching trigger.
- Invoice and client-detail Void entry points both collect the mandatory reason and explicit paid-invoice handling.
- SKU/unit descriptive snapshots are persisted and included in invoice-line sync fields.

## Verification commands/results
- `python3 tools/verify_v244_migration_sql.py` => `V244_MIGRATION_SQL_PASS`
- `python3 tools/verify_v245_migration_sql.py` => `V245_MIGRATION_SQL_PASS`
- `python3 tools/verify_v246_currency_truth.py` => `V246_CURRENCY_TRUTH_PASS`
- `python3 tools/verify_v247_inventory_costing.py` => `V247_INVENTORY_COSTING_PASS`
- `python3 tools/verify_v248_invoice_lifecycle.py` => `V248_INVOICE_LIFECYCLE_PASS`
- Conflict-marker scan => clean.
- Old Void-bypass signature scan => none.
- Structural delimiter scan across changed Kotlin files => pass.

## Build limitation
Attempted:
`./gradlew :feature:invoice:compileDebugKotlin :feature:party:compileDebugKotlin :feature:inventory:compileDebugKotlin :data:database:compileDebugKotlin :data:network:compileDebugKotlin :app:compileDebugKotlin --offline --no-daemon`

The Gradle wrapper attempted to fetch `gradle-8.9-bin.zip` and failed with `UnknownHostException: services.gradle.org`. Compilation therefore did not begin; this is not reported as a source compile pass.

## Room
- Schema version: 65 -> 66.
- Migration: `MIGRATION_65_66`.
- New invoice lifecycle/version/audit columns: `lifecycle_status`, `lifecycle_version`, `posted_at`, `voided_at`, `void_reason`, `void_write_id`.
- New immutable line-description snapshot columns: `item_sku_snapshot`, `unit_snapshot`.
- Existing rows are backfilled to POSTED or VOID from the legacy `voided` truth.
- A SQLite trigger prevents hard deletion of any non-DRAFT invoice.

## Server contract
Apply `docs/sql/v248_invoice_lifecycle_reversal.sql` before enabling F248 lifecycle fields against Supabase. It is additive, backfills legacy lifecycle truth, protects posted history from hard delete, and enforces one reversal row per original payment.

## Deliberately deferred to F249
Offline Outbox/Inbox delivery, remote event ordering, multi-device financial conflict resolution and full server-side idempotency are Session 249 scope. F248 carries the lifecycle fields but does not claim F249 network guarantees.
