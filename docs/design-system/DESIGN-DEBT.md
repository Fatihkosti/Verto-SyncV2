# Design Debt Closeout

The v278 source contains legacy presentation debt inherited from earlier repairs. The v279 baseline records it once so new work cannot hide regressions. The final target remains zero unapproved Material usage, zero hardcoded user-facing strings, zero expired exceptions, zero known duplicate shared components, and zero P0/P1 accessibility failures.

Current execution state:

- Static governance and diff tooling: IMPLEMENTED.
- Raw `MUST_WRAP` Material usage: 0 after the v279–v288 mechanical migration.
- Hardcoded user-facing strings: 88 remaining dynamic/domain cases; resource migration is not yet complete.
- Feature-wide zero-debt migration: PARTIAL until the remaining dynamic/domain strings and runtime screenshot gates are completed.
- Gradle compile, lint, Detekt, unit tests, screenshot tests, and device accessibility walkthrough: `NOT RUN`.

This file must be updated from scanner output, not edited to claim a lower count.
