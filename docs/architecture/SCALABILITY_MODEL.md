---
status: supporting
scope: architecture
owner: "architecture"
last_verified_against: v331
---
# Scalability Model

Verto treats scalability as a ratchet over the current modular-monolith baseline. Admission measures direct feature edges, transitive edges, external dependency identities, stable contracts, production file count, largest file size, persistence ownership, and risk-appropriate tests.

## Zero-tolerance invariants

- Dependency cycles: `0`.
- Foreign persistence accesses: `0`.
- Unknown or orphan feature admission metadata: `0`.
- Unknown contract providers or consumers: `0`.
- Active breaking contract drift: `0`.
- A budget increase requires a machine-readable justification and gate evidence.

LOW and MEDIUM features must remain within their current budgets. HIGH and CRITICAL features additionally require tests; CRITICAL features require contract, negative, mutation, rollout, and rollback evidence. The v331 registries freeze the measured baseline so later changes are deliberate and reviewable.
