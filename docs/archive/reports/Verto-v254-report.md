# Verto v254 — Session Report

F254 is implemented on top of v253.

## Implemented

- Durable Room invoice drafts; schema 70 → 71.
- Atomic draft header/lines/maintenance-image metadata persistence.
- Draft restoration after process recreation path.
- Stable `writeId` through `SavedStateHandle` and persisted draft.
- Draft retained after failed Post and deleted only after successful Post.
- Duplicate-save protection and explicit save states.
- Exit warning with save/discard actions.
- International FX equation always visible.
- Local purchase price/sale price distinction.
- Real force-stop device test script and two-phase instrumentation test.
- F254 changes add no regression to v253 excessive-parameter, >500-line-file, or long-function counts.

## Verification

F244–F254 static/SQLite verifiers: **PASS**.

## External gate still required

- Gradle/KSP build: blocked here because Gradle 8.9 is unavailable locally and network access is disabled.
- Real Process Death instrumentation: requires device/emulator.

See `verification-invoice-F254.md` for full evidence.
