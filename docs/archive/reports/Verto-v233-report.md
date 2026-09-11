# Verto v233 — Final Detail + Legacy Removal Report

Baseline: `Verto-v232-source-of-truth.zip`  
Contract: `VERTO_LOGISTICS_REBUILD_MASTER_PLAN_v229-v233_REVISED.md` — session v233 only.

## Implemented

- Completed-shipment detail now exposes final costs/payments, shortage settlement, attachments, responsible employee, planned/actual timing, delay location/reason/duration, and journey history.
- Reports, Dashboard pending actions/activity, payment shipment bridge, unified reads, navigation, and document opening now use Logistics V2 only.
- Removed Legacy shipment navigation, presentation, application, repository/DAO, old sync participant/DTO/remote bridge, mappers, and preference deletion queue.
- Removed Legacy Room entities and converters from the active database model.
- Room schema `59 → 60` with a non-destructive retirement migration.
- Because the Legacy parent schema has no organization id, rows cannot be safely projected into organization-scoped V2. Migration 59→60 therefore archives every Legacy row locally in `retired_shipment_archive` as executable restore SQL before dropping the five active Legacy tables.
- Remote Logistics V2 remains hard-disabled in both composition-root and network kill switches.
- Added read-only SQL verification contract for v233; no remote mutation/deployment is performed.
- Extracted the Android document opener from the removed Legacy document adapter so V2 no longer depends on Legacy presentation/data code.
- Fixed the landed-cost allocator contract found by regression: fractional totals now fail with the tested `IllegalArgumentException`, not an incidental `ArithmeticException`.
- Updated stale landed-cost test construction to the current LogisticsCost contract.

## Verification

| Gate | Result |
|---|---|
| Logistics V2 JVM regression tests | **PASS — 33/33** |
| v229-v232 pure Kotlin contract harness | **PASS** |
| Design System scanner | **PASS — 0 violations** |
| Legacy runtime type references | **PASS — 0** |
| Legacy navigation route references | **PASS — 0** |
| Room migration catalog | **PASS — exact consecutive 1→60** |
| SQLite 59→60 retirement smoke | **PASS** |
| Legacy archive/restore smoke | **PASS — 5/5 sample rows archived and restored; V2 row preserved** |
| Migration Kotlin syntax compile with Android stubs | **PASS** |
| Kotlin static debt vs v232 | **NO REGRESSION; improved** |
| Full Gradle / Android instrumentation | **BLOCKED BY ENVIRONMENT** |
| `assembleDebug` | **BLOCKED BY ENVIRONMENT** |
| Generated Room `60.json` | **BLOCKED BY ENVIRONMENT** |

### Kotlin static comparison vs v232

- architecture violations: `18 → 18`
- dependency cycles: `0 → 0`
- excessive parameter lists: `457 → 443`
- large files >500: `10 → 10`
- long functions: `340 → 330`
- broad catches: `20 → 20`
- manual coroutine scopes: `1 → 1`
- not-null assertions: `0 → 0`

The repository-wide absolute Kotlin-quality command still returns FAIL because pre-existing v188/v195 ceilings are already exceeded; v233 introduces no regression and reduces two debt metrics materially.

## Environment blocker

The wrapper requires Gradle 8.9. No Gradle 8.9 distribution is cached here and network access to `services.gradle.org` fails with `UnknownHostException`. Consequently `assembleDebug`, Android/Room instrumentation, and legitimate generation of `app/schemas/com.verto.app.data.local.AppDatabase/60.json` cannot run in this environment. The schema file was intentionally not fabricated.

The source implementation and offline-verifiable v233 gates are complete; the final Android build/schema-export gate remains environment-blocked.
