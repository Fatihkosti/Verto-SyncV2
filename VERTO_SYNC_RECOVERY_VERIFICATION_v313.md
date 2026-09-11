# Verto Sync Recovery Verification — v313

**Static gates:** FAIL
**Final verdict:** `BLOCKED_STATIC_VERIFICATION`

- Room: 80→81; schema80 unchanged; schema81 SHA `a0bd8fb6981a5b05abe180bff06b18aaf8ec8a95e0e34ebb45e8e8e21e2329c5`.
- Bootstrap coverage: 34/34; owner310: 17/17; outbox owners: 7.
- v313 model fixtures: 494/494 PASS.
- Regressions: v312 300, v311 325, v310 499, v309 272, v308 137 + MODEL_10K PASS.
- V2 default: OFF; Realtime default: OFF.
- PostgreSQL v313 migration: defined, NOT EXECUTED.
- Bootstrap/device runtime: NOT VERIFIED.

## Blocker

`BLOCKED_RUNTIME_FAILURE: Gradle wrapper could not resolve services.gradle.org before compilation; no code/build verdict inferred.`

Static recovery correctness is model/static verified; runtime server/device execution remains separately gated.
