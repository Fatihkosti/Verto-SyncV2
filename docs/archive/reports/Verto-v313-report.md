# Verto v313 — Recovery / Bootstrap / Cursor / Reconciliation Report

## Status

- Session: `313`
- Static verification: `PASS`
- Final verdict: `BLOCKED_RUNTIME_FAILURE / STATIC_RECOVERY_GATES_PASS`
- Room: `80 → 81`
- V2 default: `OFF`
- Realtime default: `OFF`
- PostgreSQL v313 migration: `DEFINED / NOT EXECUTED`
- Android/Room runtime: `NOT VERIFIED`

## Implemented

- Durable recovery state, bootstrap staging, and sync-health persistence.
- Additive Room migration `80→81` with schema 81.
- Bootstrap client bindings for begin/page/reconciliation manifest.
- Missing/expired/corrupt cursor recovery routing without timestamp authority.
- Durable page resume/restart and process-death-safe recovery state.
- Atomic recovery cutover with baseline cursor installation in the same Room transaction.
- Preservation of all unacked outbox/attachment intent across full resync.
- Snapshot materialization path without fake `SyncChange` and without replaying financial/inventory commands.
- Explicit recovery disposition and bootstrap coverage for `34/34` aggregates.
- Stronger owner310 snapshot continuity coverage `17/17`, including secondary change continuity.
- Existing-data bootstrap materialization/backfill strategy `34/34`.
- Reconciliation as anti-entropy only; pending-local defer; no global cursor advancement.
- Privacy-safe sync health/observability surface.
- Exactly one additive v313 server migration for bootstrap snapshot completeness.

## Verification

- v313 model fixtures: `494/494 PASS`
- v312 regression fixtures: `300/300 PASS`
- v311 regression fixtures: `325/325 PASS`
- v310 regression fixtures: `499/499 PASS`
- v309 regression fixtures: `272/272 PASS`
- v308 regression fixtures: `137/137 PASS + MODEL_10K_PASS`
- Bootstrap aggregate coverage: `34/34`
- owner310 aggregate coverage: `17/17`
- Outbox preservation owners: `7`
- New v313 waivers: `0`
- Historical v305/v309/v310/v312 migrations: unchanged by static gate
- Schema 80: unchanged by static gate
- Feature flags: V2 `OFF`, Realtime `OFF`

## Runtime blocker

A Gradle wrapper invocation was actually attempted, but Gradle `8.9` could not be resolved from `services.gradle.org` in the available environment before compilation. Per SESSION_313, that attempted runtime failure remains an explicit blocker and is not converted into a clean static PASS.

Recorded blocker:

`BLOCKED_RUNTIME_FAILURE: Gradle wrapper could not resolve services.gradle.org before compilation; no code/build verdict inferred.`

Therefore this report does **not** claim Android build success, Room 80→81 device migration verification, PostgreSQL migration execution, bootstrap runtime verification, or multi-device convergence.

## Server/runtime truth

- `postgresRequiredForStaticPass = false`
- `postgresExecuted = false`
- `serverMigrationDefined313 = true`
- `serverMigrationApplied313 = false`
- `bootstrapRuntimeVerified = false`
- `handoff314Authorized = false` while the recorded runtime blocker remains.

Static recovery correctness is model/static verified; runtime server/device execution remains separately gated.
