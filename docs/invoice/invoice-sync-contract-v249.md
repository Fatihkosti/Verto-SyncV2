# Invoice Financial Sync Contract — F249

## Invariants

- Room remains the offline source of truth.
- Invoice create/update/void and payment record/reversal append `financial_outbox` inside the same owner transaction.
- Delivery is at-least-once; server uniqueness `(organization, operationType, writeId)` gives exactly-once event effect.
- Aggregate event sequence is monotonic and uses both local Outbox and received Inbox history, so a second device continues the same sequence.
- `POSTED`, paid payment facts and `VOID` never use timestamp Last-Write-Wins. Unresolved conflicts stay `REQUIRES_REVIEW` and block legacy financial row writes.
- Remote events are deduplicated in `financial_inbox`; child events stay `WAITING_DEPENDENCY` until the compatibility parent row exists.
- `server_revision/server_recorded_at` define remote ordering; `occurredAt/recordedAt` remain audit facts.

## Transition

1. Apply `docs/sql/v249_financial_event_sync.sql` while legacy row sync is still accepted.
2. Deploy the F249 client: it reads the legacy rows plus the event feed, but every new financial write emits schema v1 events first.
3. Run `financial_sync_backfill_invoice_baselines_v1()` as database owner. It is rerunnable and skips aggregates that already have event history.
4. Keep legacy row constraints permissive until the supported old-client window ends; tighten/removal is a later deployment decision.

The compatibility writers are gated by unresolved Outbox/Inbox state, so an event conflict cannot be bypassed by invoice, payment, inventory, or cash-register `upsert` paths.
