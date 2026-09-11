# VERTO Sync Server Baseline v304

**Status:** `SERVER_BASELINE_NOT_REPRODUCIBLE`

No exact current server dump bytes were supplied to the Session 304 executor. Therefore no dump SHA-256 is invented and no SQL is created or modified.

## Reference evidence carried from the parent plan

- `optimal_sync_change_log`: append/change-log reference for Optimal only, not Verto-wide.
- `optimal_sync_receipts`: idempotency receipt reference for Optimal.
- `optimal_apply_sync_operation_v2`: idempotent command/RPC reference.
- `optimal_pull_sync_changes`: revision-ordered pull reference; plan evidence says it uses `revision > after_revision`, ascending order, bounded paging.
- `sync_tombstones`: server reference exists with a declared 90-day target, but entity coverage and final unified retention are not proven.

## Known Verto-wide gaps

- No reproducible evidence of a Verto-wide revision/change-log/receipt/bootstrap contract.
- No reproducible proof that all legacy/admin/service writes produce change-log entries.
- No reproducible bootstrap transaction-boundary or scope/principal cursor contract.
- Final retention and device-offline safety windows remain unmeasured.

## 305 prerequisite

Session 305 must rebind the exact current server dump bytes, compute their identity, and reconcile them with the v304 machine contract before creating migrations.
