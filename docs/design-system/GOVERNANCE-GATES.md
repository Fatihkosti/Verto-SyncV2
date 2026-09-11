# Design System Governance Gates

`migration` prevents debt growth against `BASELINE.json`; reductions are allowed. Pending MUST_WRAP components are counted before activation.

`final` uses only `config/design-system/final-zero-targets.json`. It never reads Baseline allowances.

Exit codes: `0` PASS, `1` gate FAIL, `2` invalid governance input, `64` invalid CLI usage.
