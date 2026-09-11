# Verto v289 Tests Report

Date: 2026-08-20

## Passed

- `:core:common:testDebugUnitTest`
- `:data:network:testDebugUnitTest`
- `:feature:auth:testDebugUnitTest`
- `:feature:inventory:testDebugUnitTest`
- `python3 scripts/design-system-scan.py --check`
- `python3 tools/design_system_diff_gate.py --root . --baseline docs/design-system/BASELINE.json`
- `python3 tools/verify_design_system_contract.py --root .`

The unit-test command completed successfully offline with Gradle build cache enabled:

```text
BUILD SUCCESSFUL in 44s
190 actionable tasks: 80 executed, 99 from cache, 11 up-to-date
```

## Not executed

- Real-device/emulator login and runtime smoke tests: no device or emulator was connected.
- Full aggregate test suite, Android lint, Detekt, screenshot tests, semantics tests, and Room/device tests were not required to validate the repaired build and were not run.

## Non-blocking warnings

The executed tasks reported existing Room foreign-key index warnings, deprecated/experimental API warnings, and a Kotlin inference warning. No test failed.
