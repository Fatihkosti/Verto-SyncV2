---
status: supporting
scope: finance
owner: "finance-sync"
last_verified_against: v336
---
# Session 336 Server Evidence

No new SQL migration is required for Session 336.

- `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql:2826-2858` locks the server cash register, derives `v_before`/`v_after` from server state, writes authoritative movement projections, updates the register, and emits a `CASH_REGISTER` secondary change in the same PostgreSQL transaction.
- `...:3021-3028` serializes by organization+mutation id and replays the immutable receipt when the request hash matches.
- `...:3083-3100` persists the APPLIED/NO_OP receipt and appends secondary read-model changes plus the primary stronger fact transactionally.
- The client command remains limited to immutable intent fields; `CashMovementSyncWriter.payload()` does not send balance snapshots.

Therefore the P0-A defect is client reconciliation, not server balance authority.

```text
server_computes_causal_balances = PASS
server_immutable_receipt_replay = PASS
cash_register_secondary_change = PASS
new_server_sql = NOT_REQUIRED
```
