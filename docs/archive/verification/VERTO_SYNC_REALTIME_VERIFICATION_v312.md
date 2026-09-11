# Verto Sync Realtime Verification — v312

**Verdict:** `PASS_STATIC_REALTIME_HINT_TARGETED_LIFECYCLE / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / V2_DISABLED / REALTIME_DEFAULT_DISABLED / BUILD_NOT_VERIFIED / POSTGRES_NOT_EXECUTED / V312_REALTIME_SQL_STATIC_ONLY`

- v312 model fixtures: **300/300 PASS**
- v311 regression: **325/325 PASS**
- v310 regression: **499/499 PASS**
- v309 regression: **272/272 PASS**
- v308 regression: **137/137 PASS + MODEL_10K_PASS**
- Room: **80 → 80**
- inherited exceptions: **9**; new v312 waivers: **0**
- Runtime V2: **DISABLED**
- Realtime default: **DISABLED**
- PostgreSQL v312 migration: **STATIC DEFINED / NOT EXECUTED**
- Build: **NOT VERIFIED**; Gradle 8.9 distribution was unavailable in the offline environment.

## Core guarantees

- Realtime emits `SyncRealtimeHint` only; no direct Room mutation or cursor write.
- Listener ownership is subscription-scoped; a stale stop cannot close a newer listener.
- Hints are validated against organization, user, session epoch, and listener generation.
- Bursts are bounded/coalesced; target metadata overflows to normal unified revision pull.
- `serverRevision` is advisory only; `UnifiedSyncPullEngine` remains cursor/apply authority.
- Minimal server publication exposes only revision, organization, aggregate type and aggregate id.
- Missing Realtime/publication never breaks durable periodic/manual convergence.

## Runtime truth

PostgreSQL application and Android build/runtime were not executed successfully in this environment; no runtime proof is claimed.
