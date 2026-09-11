# Verto sync repair evidence index

This index belongs to plan `VERTO-SYNC-REPAIR-BACKLOG-01`. It records evidence
locations and runnable project commands; it does not promote an unexecuted Txx
test to PASS.

## B13-V02

- Session evidence: `docs/sync-repair/evidence/B13/V02/`.
- Product-scope hash is unchanged from B13-V01: `158fccae5d886f47b1101453d64406522512e8666a2b5cbcf0be6cdfad5a9711`.
- Static safety gate: 48/48 PASS; native SQLite and B06/B11/B12 local regressions PASS.
- Runtime acceptance remains BLOCKED: no cached Gradle 8.9 distribution and no usable Android SDK/adb.
- B08 server bootstrap seal integration remains unproven; no live server/SQL action was performed.
- G-B13 remains BLOCKED and B14 was not started.

## B13-V01

- Session evidence: `docs/sync-repair/evidence/B13/V01/`
- Static safety gate: `static-results.txt` — 48/48 PASS.
- Native SQLite migration/rollback precursor: `sqlite-results.txt` — PASS.
- Regression precursors: `regression-results.txt` — B06/B11/B12 PASS.
- Gradle attempt: `gradle-first-failure.txt` — BLOCKED because Gradle 8.9 wrapper distribution is unavailable without network.
- Server contract check: checked-in migrations contain no B13 `snapshot_digest_sha256` / `coverage_aggregate_types` / `delta_token`; live acceptance remains blocked by B08.
- `verification-summary.json`, `product-tree.json`, `changed-files.json`, `change.patch`, and `artifact-hashes.sha256` preserve exact B13-V01 evidence.
- G-B13 is BLOCKED; T18/T31–T34/T46 remain NOT_RUN.

## B01-V01

- Session evidence: `docs/sync-repair/evidence/B01/V01/`
- Session report: `docs/sync-repair/evidence/B01/V01/session-report.md`
- Commands and exit codes: `docs/sync-repair/evidence/B01/V01/commands-and-exit-codes.md`
- Fixed artifact checksums: `docs/sync-repair/evidence/B01/V01/artifact-hashes.sha256`
- Source candidate fingerprint: `SOURCE_BASELINE.json`
- Source blocker/diff status: `SOURCE_DIFF.md`
- Contract integrity: embedded contract and standalone contract both SHA-256
  `34963f943ecc8d359711aaf1f2c4fc3805085dd61c988107edd2c2875a7609e0`

## B01-V02

- User source designation and closure report:
  `docs/sync-repair/evidence/B01/V02/session-report.md`
- Structural verification commands:
  `docs/sync-repair/evidence/B01/V02/commands-and-exit-codes.md`
- Closure artifact checksums:
  `docs/sync-repair/evidence/B01/V02/artifact-hashes.sha256`
- Accepted baseline identity remains the pre-execution tree fingerprint in
  `SOURCE_BASELINE.json`; the absent ZIP-container hash is explicitly not
  reported as computed.

## B02-V01

- Session report and gate status:
  `docs/sync-repair/evidence/B02/V01/session-report.md`
- Device-data backup status:
  `docs/sync-repair/evidence/B02/V01/BACKUP_VERIFICATION.md`
- Direct live catalog export:
  `docs/sync-repair/evidence/B02/V01/SYNC_SERVER_DEFINITIONS/`
- Live/source differences and effect ownership:
  `docs/sync-repair/evidence/B02/V01/SERVER_SOURCE_DRIFT.md`
- Isolated environment blocker, fence/rollback plan, and signing status:
  `docs/sync-repair/evidence/B02/V01/TEST_ENVIRONMENT_AND_FENCE.md`
- Fixed artifact checksums:
  `docs/sync-repair/evidence/B02/V01/artifact-hashes.sha256`
- Commands and observed results:
  `docs/sync-repair/evidence/B02/V01/commands-and-results.md`
- Server access was catalog-only and read-only; no tenant business rows or
  organization/user identifiers were exported.

## B03-V01

- Session evidence: `docs/sync-repair/evidence/B03/V01/`
- Covers additive Room schema 97, version/generation/packet/reference/batch
  authority stores, migration fixtures, commands, and fixed artifact hashes.
- G-B03 passed locally; T48 remains `NOT_RUN` until its full B20 scenario.

## B04-V01

- Session evidence: `docs/sync-repair/evidence/B04/V01/`
- Covers the 35/35 ownership matrix, centralized pending-protection call map,
  commands/results, session report, and fixed artifact hashes.
- G-B04 passed locally; T10/T33 remain `NOT_RUN`.

## B05-V01

- Session evidence: `docs/sync-repair/evidence/B05/V01/`
- Packet/ownership/lease/ACK map:
  `docs/sync-repair/evidence/B05/V01/packet-and-ownership-map.md`
- Exact frozen-wire fixture and SHA-256:
  `docs/sync-repair/evidence/B05/V01/golden-fixtures.md`
- Commands and results:
  `docs/sync-repair/evidence/B05/V01/commands-and-results.md`
- Session report and fixed artifact hashes:
  `docs/sync-repair/evidence/B05/V01/session-report.md` and
  `docs/sync-repair/evidence/B05/V01/artifact-hashes.sha256`
- G-B05 passed locally; T08/T15/T17/T23/T39 remain `NOT_RUN` pending their
  full producer/server/repair scenarios.

## B06-V01

- Session evidence: `docs/sync-repair/evidence/B06/V01/`
- Contract/schema and fixed golden hash:
  `docs/sync-repair/evidence/B06/V01/schema-and-golden.md`
- Producer transaction/retry map and ownership semantic delta:
  `docs/sync-repair/evidence/B06/V01/producer-transaction-map.md` and
  `docs/sync-repair/evidence/B06/V01/ownership-matrix-delta.md`
- Commands/results, session report, and fixed artifact hashes:
  `docs/sync-repair/evidence/B06/V01/commands-and-results.md`,
  `session-report.md`, and `artifact-hashes.sha256`
- G-B06 passed locally: schema and producer gates pass, JVM 195/195, Room
  commit/rollback 2/2 on Pixel_8 AVD, and app compile pass. T09/T12/T19/T20 remain `NOT_RUN`; no PostgreSQL/production
  action occurred.

## B09-V01 — implemented; G-B09 BLOCKED

- Current source lineage, expanded product scope and fingerprints: `docs/sync-repair/evidence/B09/V01/product-tree.json`.
- Implementation/scope/blocker report: `session-report.md`; runnable acceptance commands and actual exit results: `commands-and-results.md`.
- Strict field map, fact ownership and production Hilt/pull trace: `materialization-map.md`.
- Actual offline observations: `kotlin-smoke.log` (347 mapping assertions, signature-only collaborators), `sqlite-static-results.json` / `sqlite-static.log` (612 source/SQLite checks), and `sqlite-model-dump.sql` (PYTHON MODEL, not Room data).
- Gradle wrapper failure and inspected tooling: `gradle-offline.log`, `environment.json`.
- New tests: 31 actual Room instrumentation methods plus nine JVM methods. NOT_RUN; not promoted to PASS by offline smoke/model results.
- `change.patch`, `changed-files.json`, `artifact-hashes.sha256` preserve current provenance. All filenames in this section resolve under `docs/sync-repair/evidence/B09/V01/` unless explicitly rooted.
- Historical B04/B05/B06/app compile results need recheck for the touched code. No Txx, live server, device case, signing or release status was advanced.

## B09-V02

- Resume/audit report: `docs/sync-repair/evidence/B09/V02/session-report.md`.
- Input ZIP hash and matching B09-V01 product manifest: `input-validation.json`; immutable archive snapshot: `input-tree.json`.
- Observed false conflict and fixed result: `shared-write-before.log`, `kotlin-smoke-after.log`. The semantic validator is real Kotlin, but its codec collaborator is stubbed; NOT a JSON/JVM/Room acceptance run.
- Source/Room98-schema SQLite model: `sqlite-static.log`, `sqlite-static-results.json`; 612 checks.
- Offline environment preflight: `runtime-preflight.py`, `runtime-preflight.log`, `runtime-environment.json`; no network or wrapper download attempted.
- Scope/documentation checks: `change-contract.json`, `change-contract-result.json`, `documentation-gate.json`; documentation FAIL compared to `documentation-baseline.json` via `documentation-delta.json` (no new errors). Integrated documentation permission failure is recorded, not waived.
- Product/archive lineage: `product-tree.json`, `changed-files.json`, `change.patch`, `artifact-hashes.sha256`.
- Three new JVM and two new Room regression methods: total 12 JVM / 33 Room supplied, NOT_RUN. NEXT B09.01; G-B09 BLOCKED; B10 untouched.
- Filenames in this section are under `docs/sync-repair/evidence/B09/V02/` unless fully rooted.

## Verified command map from project files

| Layer | Command supplied by the project | Current B01 environment status |
|---|---|---|
| Gradle/JVM unit tests | `./gradlew --no-daemon --stacktrace testDebugUnitTest` | Command exists; not run as a product test in B01. Gradle 8.9 and Java 17 launch successfully. |
| Focused sync contract | `scripts/verify-v304-sync-contract.sh` | Command exists; not run in B01. Includes static checks and two offline `:data:network:testDebugUnitTest` cases. |
| Room static/package verification | `scripts/verify-v306-sync-room.sh <root> <Verto-v305-server-static.zip>` | Command exists; required input ZIP not established. |
| Room instrumentation | `./gradlew :data:database:connectedDebugAndroidTest :data:sync:connectedDebugAndroidTest` | Source tests exist; no connected Android device/emulator was detected. |
| PostgreSQL server checks | `scripts/verify-v305-sync-server.sh` | Static command exists; local `psql` exists, but `postgres` and `initdb` are absent. This cannot prove PostgreSQL runtime behavior. |
| Two-device/runtime staging | `scripts/run-v314-runtime-staging.sh` | Command exists; requires the documented staging runner, PostgreSQL URL, and two isolated test organizations. No device was connected in B01. |
| Debug build | `./gradlew --no-daemon --stacktrace assembleDebug` | Command exists; not run in B01. Android SDK environment variables and `local.properties` were absent. |
| Aggregate quality gate | `scripts/ci/run-quality-gate.sh all <session>` | Command exists; not run because the source baseline is not accepted. |
| Release/signing | `./gradlew --no-daemon --stacktrace assembleRelease` | No local signing file was found and release signing inputs were not established. |

## Evidence separation

- JVM and static results go under `test-results/jvm/`.
- Room instrumentation results go under `test-results/room/`.
- PostgreSQL results and sanitized definitions go under `test-results/postgresql/`.
- Independent A/B device evidence goes under `test-results/devices/`.
- Signing evidence contains certificate fingerprints and build identities only;
  keystores and credentials never enter this repository.
- Raw backups, user payloads, JWTs, and secrets remain outside the repository.

## B11-V01 — explicit conflict review implementation; G-B11 BLOCKED

- Report: `docs/sync-repair/evidence/B11/V01/session-report.md`.
- Actual command results and blocked runtime command: `docs/sync-repair/evidence/B11/V01/commands-and-results.md`.
- Host probes: `static-results.txt` and `sqlite-results.txt`; both PASS within source/native-SQLite scope only.
- Product identity/change evidence: `input-validation.json`, `product-tree.json`, `changed-files.json`, `change.patch`, `runtime-environment.json`, `verification-summary.json`.
- Runtime acceptance remains absent: no Gradle/KSP/Hilt/Room schema100 export, Compose/device flow, authoritative receipt/echo integration, or T30 PASS. B10 predecessor gate remains blocked.

## B12-V01 — mutable/versioned expense implementation; G-B12 BLOCKED

- Report: `docs/sync-repair/evidence/B12/V01/session-report.md`.
- Actual command results and blocked acceptance work: `docs/sync-repair/evidence/B12/V01/commands-and-results.md`.
- Local probes: `static-results.txt` (22/22 PASS), `sqlite-results.txt` (PASS), `schema-results.txt` (PASS), and `kotlin-contract-compile.txt` (PASS); these are not Room/PostgreSQL acceptance.
- Product identity/change evidence: `input-validation.json`, `product-tree.json`, `changed-files.json`, `change.patch`, `runtime-environment.json`, `verification-summary.json`.
- Gradle/Room/Android and full T21/T27 remain NOT_RUN; PostgreSQL migration/RPC and B07 atomic expense+cash batching remain NOT_RUN/BLOCKED. G-B12 is BLOCKED and R08 remains OPEN.
- No live SQL, production-data, APK, GitHub, or Drive action occurred.

## B10-V01 — current local implementation; G-B10 BLOCKED

- Report: `docs/sync-repair/evidence/B10/V01/session-report.md`.
- Actual command results / NOT_RUN commands: `docs/sync-repair/evidence/B10/V01/commands-and-results.md`.
- Server interface required by this client, not deployment evidence: `docs/sync-repair/evidence/B10/V01/wire-contract.md`.
- Local native SQLite migration/DAO tests: `sqlite-results.txt` (15 methods); policy/signature check: `kotlin-results.txt` (12 actual policy methods, explicit compile stubs).
- Prior local regression reruns: `regression-b09-sqlite.txt`, `regression-b09-kotlin.txt`, `regression-b06-schema.txt`, `regression-b06-producers.txt` in the same evidence directory.
- Baseline identity, product hash, patch and environment: `input-validation.json`, `product-tree.json`, `change.patch`, `changed-files.json`, `runtime-environment.json`.
- No current real Room/JSON/Gradle/KSP/Hilt/schema99 export, process kill, SQL integration or Txx PASS. Earlier B04–B06 runtime results remain historical, not verification of this changed tree.
