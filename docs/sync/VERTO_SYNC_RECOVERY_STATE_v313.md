# Verto Sync Recovery State — v313

Authority is scope-bound Room durability, not Room emptiness or wall-clock timestamps.

```text
NOT_STARTED -> IN_PROGRESS -> READY
READY -> RECOVERY_REQUIRED -> IN_PROGRESS
IN_PROGRESS -> IN_PROGRESS (durable page resume)
IN_PROGRESS -> IN_PROGRESS (expired bootstrap session restart)
IN_PROGRESS -> READY (single Room cutover transaction)
IN_PROGRESS -> RECOVERY_REQUIRED (fail closed)
```

Recovery reasons: INITIAL_BOOTSTRAP, CURSOR_EXPIRED, CURSOR_CORRUPT, SCOPE_CHANGED, LOCAL_ANCHOR_MISSING, RECONCILIATION_MISMATCH, MANUAL_SAFE_RESYNC, BOOTSTRAP_SESSION_RESTART.

`scope_id + organization_id + sync_principal_id + contract + scope_definition_version` are revalidated before page commit/cutover. Session epoch is guarded by SyncManager. Process death resumes using persisted session/page token. Outboxes are never recovery-clear targets.
