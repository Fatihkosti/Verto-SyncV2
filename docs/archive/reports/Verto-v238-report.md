# Verto v238 Report

## Implemented

- Added details 5/6 customs screen with free-text checkpoint suggestions, after-station placement, positive expected duration, Friday rule message, and optional planning documents with stage/retry/open/delete.
- Customs persists through the existing v234 planning contract and never becomes a route milestone.
- Added details 6/6 review with shipment/purchase/trip/route/customs rows, city-only route display, compact timeline, clickable validation issues, and approval confirmation.
- Review edit links preserve the draft and return to review after save.
- Approval now uses authoritative v238 validation, rechecks purchase invoice eligibility, creates idempotent PlanRevision 1, and moves DRAFT -> READY atomically.
- Future planned-leg edits now require a reason, preserve started/completed history, and atomically record the leg update, audit event, and consecutive FUTURE_EDIT PlanRevision.
- Added v238 customs/Friday tests and durable workspace persistence for customs/edit-return state.

## Architecture

- Room schema: 61 unchanged.
- Modules: 31 unchanged.
- No migration, dependency catalog, settings, or build-logic changes.
- Architecture scope guard: PASS; 8 added + 16 modified files, zero changes outside the v238 allowlist.

## Verification

- v238 domain/application Kotlin compile: PASS (`kotlinc`; warnings are pre-existing deprecated legacy enum compatibility paths).
- v238 acceptance/static checks: PASS for required customs/review strings, city-only review, separate customs event, revision hooks, and future-edit audit path.
- Static quality scan: completed.
- Logistics session delta guard: scope/module/Room checks PASS. The source package still reports the same six inherited v237 baseline checks: missing Room schema `61.json` plus five legacy shipment-table declaration checks; v238 introduced none of them.
- Full Gradle build/tests: not runnable from this source package because `gradle/wrapper/gradle-wrapper.jar` is absent.
