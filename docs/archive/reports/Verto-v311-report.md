# Verto v311 Report

## Result

`PASS_STATIC_DURABLE_DRAIN_RETRY_TENANT_ORCHESTRATION / INHERITED_307_EXCEPTIONS=9 / ROOM_80_UNCHANGED / SERVER_SQL_UNCHANGED / RUNTIME_V2_DISABLED / BUILD_NOT_VERIFIED`

Session 311 implements a durable sync-request generation before WorkManager wake, successor-safe immediate work (`APPEND_OR_REPLACE`), race-safe drained-generation CAS, bounded continuation, retry taxonomy, delayed-retry no-spin visibility, and org/user/session-epoch stale-work rejection. Tenant transitions now fail closed: old work is cancelled, realtime is stopped by the auth coordinator, local clear must succeed before the new `lastOrgId` is committed, then a new opaque epoch is activated.

## Contract boundaries preserved

- Room remains 80; no Room migration was added.
- v305/v309/v310 server migrations are byte-identical.
- V2 and Realtime defaults remain OFF.
- No bootstrap/full-resync or targeted Realtime semantics were implemented.
- Stronger v310 domain intent remains source-owned; no duplicate generic durable intent was introduced.

## Verification evidence

- v311: 325/325 static/model PASS.
- v310: 499/499 regression PASS.
- v309: 272/272 regression PASS.
- v308: 137/137 regression PASS + MODEL_10K_PASS.
- New waivers: 0.
- Blockers: none.

## Runtime truth

Gradle/Android runtime tests were not run under the static-only acceptance basis. PostgreSQL was not required. These are not represented as runtime PASS.
