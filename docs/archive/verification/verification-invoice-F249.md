# Verification — Invoice F249

## Result

PASS for source/static/local-SQL contract verification. Gradle compilation did not start because Gradle 8.9 is not cached in this sandbox and network access is unavailable. The Supabase SQL contract was not applied to a live database from this environment.

## Acceptance evidence

- Every new invoice create/update/void and payment record/reversal writes a durable `financial_outbox` event from the existing owner Room transaction.
- Invoice Void also emits ordered `PAYMENT_REVERSED` events for reversal rows created directly by the Void transaction.
- Local Outbox identity is unique by organization + operation + writeId; aggregate sequence is unique per organization + invoice.
- Aggregate sequence is calculated from both Outbox and Inbox history, so a second device continues the remote sequence instead of restarting at 1.
- Only the earliest unacknowledged local event for an aggregate is eligible for delivery; a conflict/retry blocks child events.
- Delivery is at-least-once with bounded exponential backoff. A server replay is acknowledged as the same event result.
- Inbox deduplicates `eventId` and server revision and retains `WAITING_DEPENDENCY` children until the compatibility parent row exists.
- POSTED/VOID equal-version rows are compatibility echoes, not Last-Write-Wins merge signals. DRAFT uses lifecycle version conflict rules.
- Existing payment financial facts are immutable during pull; money/method/reversal mismatches are not overwritten and enter review handling.
- `REQUIRES_REVIEW` blocks legacy invoice, line, payment, inventory, and cash-register write paths so a rejected financial event cannot be bypassed by an old row upsert.
- Compatibility pull cursors are held behind unresolved financial aggregates; skipped financial rows do not silently disappear behind incremental timestamps.
- Server SQL adds durable event storage, server revision/order time, tenant RLS, idempotency uniqueness, strict aggregate sequence, dependency waiting, optimistic version conflicts, and one-winner concurrent Void behavior.
- `occurredAt` and client `recordedAt` are retained while `server_revision/server_recorded_at` are the remote ordering authority.
- Transition remains additive: old row sync is still accepted/read, new clients write schema-v1 events first, and an owner-only rerunnable legacy invoice baseline backfill is supplied.

## Focused acceptance simulations

`python3 tools/verify_v249_financial_sync.py` => `V249_FINANCIAL_SYNC_PASS`

The verifier executes the exact Room F249 table DDL in SQLite and checks:
- Outbox identity uniqueness;
- aggregate sequence uniqueness and predecessor blocking;
- second-device sequence continuation from Inbox history;
- Inbox server-revision dedupe;
- ten identical deliveries model one server event/effect and retain the same revision;
- a child event can persist as `WAITING_DEPENDENCY`;
- no duplicate global `SyncOperation(stage, order)` slot was introduced.

## Regression verification

- `verify_v244_migration_sql.py` => PASS.
- `verify_v245_migration_sql.py` => PASS.
- `verify_v246_currency_truth.py` => PASS.
- `verify_v247_inventory_costing.py` => PASS.
- Historical `verify_v244_money_core.py`, `verify_v245_atomic_invariants.py`, and `verify_v248_invoice_lifecycle.py` contain exact old schema-version assertions (62/63/66). Their non-version checks were rerun against schema 67 using temporary copies only and all passed. The historical verifier files were not rewritten.
- Changed-Kotlin lexical delimiter scan => PASS.
- Conflict-marker scan => clean.
- Global sync-slot collision check => PASS (inside F249 verifier).

## Build limitation

Attempted:

`./gradlew :data:database:compileDebugKotlin :data:operations:compileDebugKotlin :data:network:compileDebugKotlin :feature:invoice:compileDebugKotlin :feature:payment:compileDebugKotlin --offline --no-daemon`

The wrapper attempted to download `gradle-8.9-bin.zip` and failed with `UnknownHostException: services.gradle.org`; Kotlin/Room compilation therefore did not begin.

## Room

- Schema version: 66 -> 67.
- Migration: `MIGRATION_66_67`.
- New tables: `financial_outbox`, `financial_inbox`.
- Outbox states: `PENDING`, `RETRY`, `ACKNOWLEDGED`, `REQUIRES_REVIEW`.
- Inbox states used by F249: `RECEIVED`, `WAITING_DEPENDENCY`, `APPLIED`, `REQUIRES_REVIEW`.
- Event payload contract: payload v1 / schema v1.

## Server deployment contract

Apply `docs/sql/v249_financial_event_sync.sql` after the v248 SQL contract and before enabling F249 against Supabase. After the event-capable client rollout, run `financial_sync_backfill_invoice_baselines_v1()` as the database owner. It is idempotent and skips aggregates already carrying event history.

## Allowlist evidence note

F249 also changes these two production sync files outside the explicit invoice network allowlist:

- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt`

Evidence/necessity: invoice posting, payment, and Void create inventory/cash side effects. Without gating these generic compatibility writers and pulls, a financial event in `REQUIRES_REVIEW` could still mutate server/local financial state through legacy inventory/cash upserts, directly violating F249's no-bypass and exactly-once-effect contract. Changes are limited to financial-conflict gating/cursor preservation; no unrelated inventory or cash UX/domain behavior was changed.
