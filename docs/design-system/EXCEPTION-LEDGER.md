# Exception Ledger

The only active temporary ledger is `config/design-system/v291-exceptions.json`. `config/design-system/v226-enforcement-exceptions.json` is immutable historical provenance only.

Each v291 entry has exactly: `file`, `rule`, `owner`, `reason`, `removalCondition`, `createdAt`, `expiresAt`, `allowedCount`, `currentCount`, `sourceSha256`, `legacySource`, `permanent`.

Imported entries use `legacySource="v226"`, `permanent=false`, and fixed expiry `2026-09-30`. Owner is the nearest ancestor module containing `build.gradle.kts`. Source SHA is captured from the current Production file at ledger creation.

The scanner recomputes current counts. Active keys must be a subset of v226, and `allowedCount` may never exceed the v226 allowance. A changed source hash is accepted only when debt is reduced; the stored hash is never silently refreshed to bless unchanged debt.

Missing legacy sources are stale and are not imported. Zero-current legacy rules are resolved and are not imported. No new exception key may be invented.

Migration Gate enforces no growth and active-ledger validity. Final Gate ignores Baseline allowance and evaluates absolute zero targets.
