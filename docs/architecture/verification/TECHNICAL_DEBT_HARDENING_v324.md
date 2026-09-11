# V324 Technical Debt Hardening

The v323 handoff was frozen as `SESSION_324_INPUT_SNAPSHOT.json` before v324 governance changes. V324 adds a machine-readable registry, linked suppression metadata, a monotonic ratchet, a non-Git ZIP differential gate, regression fixtures, and explicit unified-gate admission requirements.

| Metric | Input 324 | Final 324 | Delta | Verdict |
|---|---:|---:|---:|---|
| Production Kotlin files | 1240 | 1240 | 0 | PASS |
| Files >500 | 25 | 25 | 0 | PASS |
| Long functions | 472 | 472 | 0 | PASS |
| Excessive parameters | 641 | 641 | 0 | PASS |
| Broad catches | 32 | 32 | 0 | PASS |
| `!!` | 7 | 7 | 0 | PASS |
| Exposed mutable state | 2 | 2 | 0 | PASS |
| Open CRITICAL debt | 0 | 0 | 0 | PASS |
| Open HIGH debt | 3 | 3 | 0 | PASS |
| Undocumented suppressions | 0 | 0 | 0 | PASS |
| Architecture violations | 0 | 0 | 0 | PASS |
| Dependency cycles | 0 | 0 | 0 | PASS |

All static governance and architecture gates pass. Detekt, lint, tests, and the debug build are recorded as `BLOCKED_ENVIRONMENT` because Gradle distribution resolution cannot reach `services.gradle.org`; Source-of-Truth Admission therefore remains FAIL.
