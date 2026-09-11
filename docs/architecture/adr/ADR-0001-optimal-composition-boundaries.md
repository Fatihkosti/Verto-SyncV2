---
status: supporting
scope: system
owner: "architecture"
last_verified_against: v321
---
# ADR-0001 — Optimal Cross-Feature Composition Boundaries

**Status:** accepted  
**Date:** 2026-08-22  
**Verified against:** v321

## Context

In v320, `:feature:integration:optimal` directly depended on the implementation modules `:feature:invoice`, `:feature:management`, `:feature:messages`, `:feature:party`, and `:feature:payment`. Optimal Domain also imported provider-owned feature types. This produced the v318/v320 `VARCH-003` and `VARCH-012` debt and made provider implementation details part of Optimal's dependency surface.

## Decision

Optimal owns the Ports and stable DTOs required by its Domain/Application code. Cross-feature translation and wiring live in the `:app` composition root. Provider Features remain owners of their Public contracts and persistence. Explicit API-only modules, such as `:feature:dashboard:api`, may be consumed only through manifest-declared Public API admission.

```text
Optimal Domain/Application -> Optimal-owned Port/DTO
                           <- app composition bridge
                           -> provider Public contract
```

## Alternatives

- Keep direct implementation-module dependencies: rejected because it preserves feature coupling and storage/internal leakage risk.
- Add a generic shared-contract Gradle module: rejected because the semantics are feature-specific and it would create a dumping ground.
- Create new provider API modules in v321: not required; existing provider Public contracts are sufficient when adapted in `:app`.

## Consequences

- Optimal no longer has direct Gradle dependencies/imports on the five provider implementation modules.
- Translation logic is explicit and testable at composition boundaries.
- `:app` intentionally depends on both sides as the composition root.
- Historical v318 debt remains evidence while the forward admitted set is zero.

## Affected Rule IDs

`VARCH-003`, `VARCH-012`, `VARCH-015`, `VARCH-020`, `VARCH-022`, `VARCH-023`.

## Affected module boundaries

`:feature:integration:optimal`, `:app`, and the existing Public contracts of Invoice, Management, Messages, Party, Payment, and Dashboard API.

## Data ownership impact

None. Room entities, DAOs, repositories, and tables retain their existing owners. No Room schema/version change is made.

## Dependency impact

Five direct Optimal implementation-module edges are removed. No new Gradle module is created. Valid Dashboard API edges are classified as Public-contract edges rather than legacy implementation debt.

## Baseline / threshold impact

The historical v318 technical-debt baseline is unchanged. `technical-debt-governance-v321.json` admits zero remaining identities for the targeted rules and makes reappearance fail closed. Complexity budgets are not raised.

## Migration / remediation plan

Completed in Session 321 by introducing Optimal-owned messaging/timeline/integration models and Ports, app-level adapters, Inventory Quick Stock data/rendering boundaries, and Party balance domain inputs.

## Evidence

See [`../verification/DEPENDENCY_MODULARITY_REPAIR_v321.md`](../verification/DEPENDENCY_MODULARITY_REPAIR_v321.md) and the matching JSON artifact.

## Related docs

- [`../ARCHITECTURE_CONTRACT.md`](../ARCHITECTURE_CONTRACT.md)
- [`../FEATURE_ADMISSION_CONTRACT.md`](../FEATURE_ADMISSION_CONTRACT.md)
- [`README.md`](README.md)
