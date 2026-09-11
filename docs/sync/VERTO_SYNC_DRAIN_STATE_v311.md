# Verto Sync Drain State — v311

- **IDLE**: requested == drained, no eligible-now generic outbox work, unified pull caught up, scope current.
- **REQUESTED**: durable requested generation exceeds drained.
- **DRAINING**: current epoch owns bounded push→pull pass.
- **CONTINUATION_SCHEDULED**: bounded budget exhausted; successor enqueued with APPEND_OR_REPLACE.
- **AUTH_BLOCKED**: transport auth stops orchestration without rejecting immutable business intent.
- **RECOVERY_REQUIRED**: bootstrap/cursor recovery is deferred fail-closed to Session 313.
- **STALE_SCOPE**: org/user/session epoch mismatch; no network/domain mutation is allowed.

Correctness authority is Room `sync_sequence_state`; WorkManager is only a wake mechanism.
