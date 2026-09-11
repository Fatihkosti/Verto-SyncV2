---
status: canonical
scope: system
owner: "architecture"
last_verified_against: v321
---
# Architecture Decision Records

ADRs preserve durable rationale for architectural decisions; they do not restate implementation facts.

## ADR required before admission

An ADR is mandatory for changes to module boundaries, data ownership, cross-feature communication, persistence architecture, public contracts, dependency direction, Rule severity, Rule meaning, or a baseline exception that changes architecture policy. Typographical/documentation-only corrections do not require an ADR.

## Required fields

Use [`ADR_TEMPLATE.md`](ADR_TEMPLATE.md). Every architectural ADR records Context, Decision, Alternatives, Consequences, Status, Date, affected Rule IDs/module boundaries, data/dependency impact, baseline/threshold impact when applicable, migration/remediation plan, and evidence.

## Authority

The canonical architecture policy remains [`../ARCHITECTURE_CONTRACT.md`](../ARCHITECTURE_CONTRACT.md). Machine-readable contracts under `../contracts/` are normative dependencies of that policy, not competing Canonical owners.

## Accepted decisions

- [ADR-0001 — Optimal Cross-Feature Composition Boundaries](ADR-0001-optimal-composition-boundaries.md) — accepted in v321.
