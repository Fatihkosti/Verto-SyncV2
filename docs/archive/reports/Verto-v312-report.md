# Verto v312 Report

## Result

`PASS_STATIC_REALTIME_HINT_TARGETED_LIFECYCLE / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / V2_DISABLED / REALTIME_DEFAULT_DISABLED / BUILD_NOT_VERIFIED / POSTGRES_NOT_EXECUTED / V312_REALTIME_SQL_STATIC_ONLY`

## Implemented

- Typed `Flow<SyncRealtimeHint>` organization Realtime boundary.
- Generation/subscription-owned lifecycle replacing global channel-stop ownership.
- Tenant/user/session-epoch/lifecycle stale-callback rejection.
- Bounded 32-target Realtime coalescer with max advisory revision and overflow fallback.
- Durable v311 `requestSync(scope, REALTIME)` wake path; no legacy `fullSync` on V2 Realtime.
- Minimal `verto_sync_realtime_hints` server projection with RLS and explicit publication allowlist.
- v312 verification, lifecycle, publication, source coverage, and hint-policy artifacts.

## Verification

- 300/300 v312 model fixtures PASS.
- v311 325/325, v310 499/499, v309 272/272, v308 137/137 + MODEL_10K PASS.
- Room 80 and v305/v309/v310 historical migrations unchanged.
- No new waiver.

## Not claimed

- Gradle build/runtime verification.
- PostgreSQL migration application.
- Realtime delivery/replay guarantees.
- V2 or Realtime default-on cutover.
