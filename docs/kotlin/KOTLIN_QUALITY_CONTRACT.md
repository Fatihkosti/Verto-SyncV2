---
status: canonical
scope: system
owner: "engineering-quality"
last_verified_against: v320
---
# Verto Kotlin Quality Contract

**Historical evidence:** Verto v188  
**Current admission baseline:** v319 technical-debt contract  
**Execution model:** offline, static, exact-identity/count Ratchet

## Purpose

This contract turns the Kotlin quality rules of the v189–v195 execution plan into an offline gate. It protects current behavior and architecture while allowing debt to move in only one direction: down.

## Non-negotiable invariants

- Production behavior, business rules, UI, database schema, sync protocol, server contracts, feature boundaries, and dependency direction are preserved.
- No new external dependency is required by this quality gate.
- The gate does not require Gradle, network, Git, Android SDK, or server access.
- Production Kotlin is `*/src/main/**/*.kt`; tests, generated output, build directories, and tooling code are excluded.
- The committed v188 baseline is immutable. It may not be edited to make a later session pass.
- A metric may decrease; it may not increase.
- `GlobalScope` and dependency cycles are hard-zero invariants.
- Existing tests affected by later refactors must not be deleted.

## Metrics

The gate records and compares:

1. `not_null_assertions` — source-level `!!` occurrences in production Kotlin; this intentionally includes executable assertions inside Kotlin string templates.
2. `broad_catches` — explicit `catch (...: Exception)` or `catch (...: Throwable)`.
3. `manual_coroutine_scopes` — direct `CoroutineScope(...)` construction.
4. `large_files_over_500` — production Kotlin files with more than 500 physical lines.
5. `long_functions` — named functions longer than 60 lines.
6. `excessive_parameter_lists` — functions with more than 6 parameters or primary constructors with more than 7.
7. `exposed_mutable_state` — non-private class/top-level properties exposing known mutable collection/Flow types or factories.
8. `global_scope` — `GlobalScope` references.
9. `lateinit_var` — production `lateinit var`; v188 entries are legacy allowance only, and no increase is permitted.
10. `architecture_violation_count` — total violations reported by the existing Verto architecture guard.
11. `dependency_cycles` — dependency cycles reported by the existing architecture guard.

Thresholds are part of the scanner contract and are serialized into the baseline. Silent threshold changes fail verification.

## Required commands

Current admission:

```bash
python3 scripts/verify-kotlin-quality-static.py verify --mode current-ratchet
```

Historical v188 comparison remains available and may legitimately fail against the much newer source:

```bash
python3 scripts/verify-kotlin-quality-static.py verify --mode historical-v188
```

Unified admission requires `KOTLIN_QUALITY_RATCHET_GATE=PASS mode=current-ratchet`.

A non-zero exit status means the quality baseline or hard invariant was violated.

## Session progression

- **v189:** establish this contract, the v188 machine-readable baseline, the exception policy, and the static guard. No Production Code changes.
- **v190:** reduce `!!` to zero and prevent unnecessary mutable-state exposure.
- **v191:** preserve `GlobalScope = 0`, centralize long-lived coroutine ownership, and enforce cancellation/exception policy.
- **v192–v194:** reduce complexity by the exact decompositions defined in the execution contract without changing behavior.
- **v195:** enable the final Detekt quality rules and close the plan.

## Baseline governance

`config/kotlin-quality/baseline-v188.json` remains immutable evidence and is never rewritten to pass current code. `docs/architecture/contracts/technical-debt-baseline.json` is the current Ratchet source; its numeric ceilings and evidence identities may only tighten relative to the v319 governance anchor. Complexity uses `complexity-baseline-v319.json` with a versioned deterministic measurement algorithm.
