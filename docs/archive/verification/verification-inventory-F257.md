# Verification Invoice — F257 Inventory Ledger / Cost Contract

## Acceptance

- [x] Official movement kinds exist.
- [x] Signed base quantity exists and canonical zero-quantity movements are rejected.
- [x] Source/line/command/posting/idempotency/reversal identity exists.
- [x] occurredAt / recordedAt / serverAcceptedAt / serverSequence exist.
- [x] Organization identity exists on canonical movement/cost records.
- [x] Canonical `inventory_cost_revisions` exists separately from quantity movements.
- [x] Unique idempotency and one-effective-reversal indexes exist for Room and Postgres SQL.
- [x] `note.startsWith()` / note-prefix meaning is removed from Inventory DAO decision paths.
- [x] Room migration is additive 72→73 and does not run reconciliation/backfill.
- [x] Static/SQLite contract verifier: 42/42 PASS.
- [ ] JVM/Room generated-schema tests executed — blocked by unavailable Gradle distribution/network.
- [ ] Postgres migration executed against Supabase — not available in this environment.

## Decision

**F257 PASS for source implementation and executable static/SQLite gates.** External build/Room instrumentation/Postgres execution remain explicit verification tasks; no false PASS is claimed for them.
