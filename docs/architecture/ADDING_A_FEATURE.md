---
status: supporting
scope: architecture
owner: "architecture"
last_verified_against: v331
---
# Adding a Feature

Feature admission is fail-closed. A new `:feature:*` module is accepted only when its module, manifest, owner, data boundary, contracts, tests, budgets, rollout, rollback, and documentation agree.

## Admission sequence

1. Define the smallest feature purpose and owner.
2. Add one manifest for the Gradle module and declare all project and external dependencies.
3. Declare owned persistence assets; foreign DAO, Entity, repository implementation, and schema access are prohibited.
4. Reuse a stable contract when semantics match; otherwise add the smallest versioned provider contract.
5. Add contract, negative, mutation, and risk-appropriate integration evidence.
6. Record scalability metadata and stay within the ratcheted budget.
7. Define staged rollout and explicit rollback for HIGH and CRITICAL features.
8. Run change-contract, architecture, dependency, compatibility, admission, persistence, maintainability, documentation, and build gates.

The feature admission registry and scalability budget registry are the machine-readable decision record. Source changes remain separate from admission metadata and cannot be smuggled in through a manifest.
