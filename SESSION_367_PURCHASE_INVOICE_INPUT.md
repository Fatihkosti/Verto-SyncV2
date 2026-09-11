# SESSION 367 — Purchase Invoice Input

- Base: Verto-v366.zip
- Purchase invoice quick action is always visible on Home quick actions.
- `purchases_create` remains the execution permission; visibility does not grant authorization.
- Local purchase item input shows Quantity + Purchase Price + Sale Price.
- Sale Price is input-only for the local purchase flow and is not added as a column in invoice item rows.
- Purchase invoice calculations and item rows continue to use Purchase Price.
- Existing inventory selection continues to prefill current purchase/sale prices when available.

Verification:
- Static contract checks: PASS.
- Targeted Gradle unit test: BLOCKED_ENVIRONMENT because Gradle 8.9 distribution is not cached and offline execution cannot download services.gradle.org.
