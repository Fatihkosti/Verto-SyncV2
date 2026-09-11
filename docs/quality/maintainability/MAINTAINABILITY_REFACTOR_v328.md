---
status: supporting
scope: system
owner: "maintainability-testability"
last_verified_against: v328
---
# Maintainability Refactor — Session 328

## Verdict

Session 328 implemented the deterministic orchestration seams required by the contract. All locally executable static gates pass. Mandatory Gradle verification is blocked by the execution environment because the Gradle 8.9 distribution is not cached and `services.gradle.org` cannot be resolved; therefore the session cannot be promoted to full PASS.

## Before / after

| Metric | v327 | v328 | Required |
|---|---:|---:|---|
| InvoiceWriteCoordinator LOC | 548 | 546 | no material regression |
| Invoice coordinator dependencies | 15 | 13 | decrease preferred |
| Invoice direct wall-clock calls | 2 | 0 | MUST |
| Invoice direct random UUID calls | 2 | 0 | MUST |
| Invoice coordinator direct tests | 0 | 8 | MUST |
| SyncManager LOC | 511 | 500 | no material regression |
| SyncManager dependencies | 10 | 5 | decrease preferred |
| SyncManager Context dependency | 1 | 0 | MUST |
| SyncManager direct SyncWorker calls | 5 | 0 | MUST |
| SyncManager direct orchestration DB access | 6 | 0 | MUST |
| SyncManager direct wall-clock calls | 7 | 0 | MUST |
| SyncManager direct tests | 0 | 9 | MUST |

## Invoice result

`InvoiceWriteCoordinator` no longer directly generates wall-clock timestamps or UUIDs. `InvoiceWriteIdentityFactory` owns deterministic time/ID generation, while `InvoiceWritePreparation` groups validation and draft preparation without hiding unrelated dependencies. The primary constructor decreases from 15 to 13 dependencies.

Eight direct characterization tests cover new identity/time, supplied identity preservation, create ordering, edit routing, authorization rejection, validation rejection, post-commit failure boundary, and duplicate/idempotent create behavior.

## Sync result

`SyncManager` no longer imports or directly depends on Android `Context`, `AppDatabase`, `SyncWorker`, or wall-clock time. Environment and nondeterministic behavior are behind narrow ports/adapters: runtime principal/session access, scheduler, orchestration store, clock, rollout authority, and V2 engine boundary. The primary constructor decreases from 10 to 5 dependencies.

Nine direct isolated tests cover profile readiness, invalid epoch/work scope, persist-before-wake with wake failure, wake-disabled periodic intent, V2 routing, Legacy routing, continuation scheduling, cancel/epoch semantics, and deterministic timestamps.

## Logistics handoff

`RoomLogisticsV2ShipmentStoreAdapter` was characterized without broad decomposition. The Session 329 matrix is in `LOGISTICS_STORE_CHARACTERIZATION_v328.md` and covers transaction/outbox capture, shipment creation, state transitions, document write/delete, cost/payment writes, receiving, and route-template writes.

## Static verification

- Architecture guard: PASS
- Dependency gate: PASS
- Persistence guard: PASS
- Technical-debt ratchet: PASS
- Differential quality: PASS
- Kotlin quality current-ratchet: PASS
- Change contract: PASS
- Unified gate self-test: PASS
- Documentation gate: PASS
- Design-system gate: PASS
- Design-system differential: PASS

No architecture, dependency-cycle, foreign DAO, foreign Room Entity, or unclassified persistence regression was introduced.

## Unified-gate execution note

`bash scripts/ci/run-quality-gate.sh all 328` was attempted. The execution harness timed out while repeating the expensive static scans before reaching Gradle. The mandatory static stages were therefore rerun individually and passed; the focused Gradle invocation independently proves the environment block below. No unified PASS or Source-of-Truth admission is claimed.

## Environment block

Focused Gradle command attempted:

```bash
./gradlew --no-daemon :feature:invoice:testDebugUnitTest :data:sync:testDebugUnitTest
```

The wrapper attempted to download `gradle-8.9-bin.zip` and failed with `java.net.UnknownHostException: services.gradle.org`. Consequently module tests, full unit tests, detekt, lint, debug assembly, and Source-of-Truth admission are `BLOCKED_ENVIRONMENT`, not PASS.

## Required final status

```text
SESSION_328 = BLOCKED_ENVIRONMENT

invoice_direct_tests = 8
invoice_direct_clock_calls = 0
invoice_direct_uuid_calls = 0
invoice_constructor_dependencies = 13

sync_manager_direct_tests = 9
sync_manager_context_dependency = 0
sync_manager_direct_worker_calls = 0
sync_manager_direct_orchestration_database_access = 0
sync_manager_direct_clock_calls = 0
sync_manager_constructor_dependencies = 5

architecture_guard = PASS
dependency_gate = PASS
persistence_guard = PASS
technical_debt_ratchet = PASS
differential_quality = PASS
kotlin_quality = PASS

invoice_module_tests = BLOCKED_ENVIRONMENT
sync_module_tests = BLOCKED_ENVIRONMENT
full_unit_tests = BLOCKED_ENVIRONMENT
detekt = BLOCKED_ENVIRONMENT
lint = BLOCKED_ENVIRONMENT
assemble_debug = BLOCKED_ENVIRONMENT
source_of_truth_admission = BLOCKED_ENVIRONMENT
```
