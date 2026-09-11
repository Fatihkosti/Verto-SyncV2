# Verto B01–B13 handoff

Date: 2026-09-11 UTC

## Outcome

The supplied B13 V02 work-in-progress archive was audited against
`VERTO_SYNC_REPAIR_BACKLOG_AR.md`. The Android project now compiles with Gradle 8.9,
Kotlin 2.1, KSP2, Room 2.7.2, and JDK 17. The release configuration accepts the
Supabase project URL and publishable/anon key from environment variables and never
requires a `service_role` key in the Android client.

## Backlog status

| Backlog | Audited status | Evidence / remaining boundary |
|---|---|---|
| B01 | DONE in supplied baseline | Existing evidence retained. |
| B02 | PARTIAL | Repository/server definitions exist; a real affected-device backup and isolated PostgreSQL acceptance environment were not supplied. |
| B03–B06 | DONE in supplied baseline | Existing evidence retained; B06 schema and producer gates pass. |
| B07 | NOT COMPLETE | Required authenticated v2 capabilities, atomic batch/receipt RPCs, production PostgreSQL concurrency/idempotency tests, and live deployment are absent. |
| B08 | NOT COMPLETE | Required legal aggregate/delta/bootstrap v2 server RPCs, immutable session seal/digest/coverage, size-boundary tests, and PostgreSQL→Room round trip are absent. |
| B09–B13 | IMPLEMENTED LOCALLY; SERVER GATES BLOCKED | Kotlin/Room compilation and local JVM/static/SQLite tests are now runnable. Their live acceptance gates remain dependent on B07/B08 and device/instrumentation scenarios described in the backlog. |

No production SQL migration was applied from this handoff: B07/B08 explicitly
require an isolated PostgreSQL acceptance run before deployment, and replacing the
current v1 server contract without that evidence could break synchronization.

## Repairs made during this handoff

- Enabled KSP2 and moved Room to the Kotlin-2.1-compatible 2.7.2 line.
- Removed KSP per-round diagnostic copies before javac to prevent duplicate Hilt sources.
- Fixed nullable entity-version handling and a Kotlin array reification warning in sync code.
- Added the missing Kotlin test dependency to the app module.
- Made structured remote-error extraction work for non-public provider exception classes.
- Corrected coroutine JUnit tests to return `Unit` and updated stale test fixtures to
  the current password, ledger, company-segment, clock-port, and structured-failure contracts.
- Corrected deterministic activity-ranking and duplicate-event assertions.
- Added a safe `DISABLE_FIREBASE_BUILD_PLUGINS=true` build switch so an APK can be
  produced without uploading R8/Crashlytics artifacts to an external service.

## Verification completed

- Full Gradle test suite: `BUILD SUCCESSFUL` (1,301 tasks; debug and release unit tests).
- B06 schema and producer gates: pass.
- B09 offline materializer gate: pass (612 assertions).
- B10 SQLite gate: 15/15 pass.
- B11, B12, and B13 static/SQLite gates: pass.
- Signed minified release APK: `BUILD SUCCESSFUL`.
- APK signature: verified with APK Signature Scheme v2; one RSA-4096 signer.
- Supabase project endpoint: confirmed embedded in the release APK.

## Supabase verification

- Dashboard account sign-in succeeded.
- Project `Verto-app` (`madkfvggyolmdberzmtb`) is visible and reports `Healthy`.
- Release inputs use `https://madkfvggyolmdberzmtb.supabase.co` plus its publishable key.
- The publishable key is intentionally not stored in this archive. Supply it through
  `SUPABASE_ANON_KEY`; supply the URL through `SUPABASE_URL`.
- No `service_role` or database password is included in the source archive or APK build inputs.

The requested archive name `Verto-b01b13done.zip` is retained for handoff
compatibility; it does not override the audited B07/B08 limitations recorded above.

## Continuation order

1. Create an isolated Supabase/PostgreSQL branch or disposable project from the current production schema.
2. Implement and test B07 RPCs and concurrency/idempotency scenarios T14–T16/T47.
3. Implement and test B08 aggregate/delta/bootstrap RPCs, T19/T20/T35/T36/T45/T49.
4. Re-run the blocked live/Room/device gates for B09–B13, especially T18 and T31–T34.
5. Only after those gates pass, deploy the v2 migration and change the unified client contract from v1 to v2 without fallback.
