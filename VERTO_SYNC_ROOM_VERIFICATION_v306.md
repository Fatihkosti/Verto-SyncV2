# VERTO Sync Room Verification — v306

- **Status:** `PASS_STATIC_ROOM_STATE_FOUNDATION / RUNTIME_DISABLED / BUILD_NOT_VERIFIED`
- **Owner decision:** `VERTO_SYNC_306_STATIC_ACCEPTANCE_2026_08_21`
- **Input:** `Verto-v305-server-static.zip` — SHA `6144c6273dd8c5e4d6ecd211ea753f9aff29231c1fabce12009852a4586769a5`, entries `2553`.
- **Raw v305:** `SERVER_STATIC_COMPLETE / DATABASE_EXECUTION_BLOCKED`, raw `handoff306Authorized=false`; owner policy converts this to effective `PASS_STATIC_ACCEPTED` for static-only 306.
- **Room:** `77 → 78`; historical `77.json` unchanged (`63ec65fd1eedb9c29a585cb43d9a8afed42a429fb0ad749da85b305e5ccf8b1a`).
- **New tables:** `sync_outbox`, `sync_inbox`, `sync_cursor`, `sync_sequence_state`; no fifth unified table and no legacy backfill.
- **Cursor:** `scope_id` + principal/org/contract metadata; opaque token only; advancement is CAS and stale writes fail closed.
- **Sequences:** durable Room counters for global and aggregate order; allocation occurs inside the same `@Transaction` as Outbox insert.
- **Outbox/Inbox:** semantic fields protected by migration triggers; delivery/apply metadata remain mutable through bounded DAO surfaces.
- **Atomicity:** statically representable as one Room transaction for domain+sequence+outbox and inbox+domain+cursor. Runtime rollback/process-death is not claimed.
- **Runtime isolation:** SyncManager/SyncWorker/SyncPreferencesStore and server migrations remain unchanged; V2 runtime remains OFF.
- **Schema 78:** static-derived artifact validated against migration/entities. KSP generation was not run and is not reported as PASS.
- **Build/compile/unit/instrumentation:** `NOT_RUN_ENVIRONMENT_UNAVAILABLE` and non-gating under the revised static-only contract.
- **Static fixtures:** `62/62 PASS`; deterministic verifier hash `292f2c777bac6fa61bcda07c5fc071377c62927efe4efccb4eebe79ada6bb229`.
- **307/308 handoff:** authorized by static-only owner policy.

The PASS above proves only the local Room protocol-state foundation and static contract gates; it does not claim APK compilation, executed Room instrumentation, process-death behavior, V2 network activity, producer cutover, Push/Pull, or legacy removal.
