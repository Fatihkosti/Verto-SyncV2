---
status: canonical
scope: system
owner: "architecture"
last_verified_against: v321
---
# Verto Architecture Contract

Verto is a modular monolith. This document is the single Canonical owner of architecture admission policy. The machine-readable contracts in [`contracts/`](contracts/technical-debt-ratchet.md) are normative dependencies; `architecture-rules.json` is historical compatibility evidence only; Architecture Guard v2 defaults to the machine-readable contracts and never selects the legacy file as admission authority.

## Layer direction

```text
Presentation
    ↓
Application / Domain
    ↓
Repository / Port
    ↓
Data / Infrastructure
```

Forbidden for new code: Presentation -> DAO/Room/data implementation; Domain -> Android/Data/Infrastructure; Application -> DAO/Room Entity/transport implementation; Feature A -> Feature B data/storage implementation.

## Feature boundaries

Cross-feature access is default-deny. A provider exposes only packages/symbols declared by its feature manifest. Everything else is INTERNAL. Allowed integration uses a consumer-owned Port, Use Case/Application Service, stable DTO/Contract, or an explicitly approved API module. Direct foreign Entity/DAO/storage implementation access is forbidden.

## Data ownership

Every governed Room table/entity, DAO and Repository declaration has exactly one architectural owner in `contracts/data-ownership.json`. `SHARED` is not an owner. Shared access must still pass through an explicitly owned abstraction.

## Dependency admission

Current direct project and external/library dependencies are frozen in `contracts/dependency-contract.json`. A new dependency is denied unless declared in both the dependency contract and the consuming feature manifest where applicable, then admitted by the architecture policy. Session v321 distinguishes explicit `:feature:*:api` modules from provider implementation modules: an API edge is valid only when the consumer manifest admits it, the provider is API-only with a declared Public surface, and no cycle/internal-storage access is introduced. The seven Dashboard API consumer edges are therefore `ALLOWED_PUBLIC_API`; the five former Optimal implementation-module edges were removed.

## Severity and stable Rule IDs

`VARCH-001..VARCH-021` preserve their existing meanings. v319 adds `VARCH-022..VARCH-030`. Rule meanings cannot be reused or silently changed. Severity is one of ZERO_TOLERANCE, RATCHET, WARNING and every rule has a deterministic measurement/failure semantic in `contracts/architecture-contracts.json`.

ZERO_TOLERANCE means no new unregistered violation identity; a clean rule stays exactly zero. RATCHET means numeric debt cannot increase and stable identity debt must be a subset of the previous admitted set. WARNING is measured evidence only and cannot replace a stricter rule.

## Legacy debt

The 18 v318 architecture findings (`VARCH-003=4`, `VARCH-005=2`, `VARCH-012=12`) remain immutable historical evidence in `contracts/technical-debt-baseline.json`; they are not approved architecture. Session v321 reduced the currently admitted set for those rules to zero in `contracts/technical-debt-governance-v321.json`. Reappearance of any removed historical identity is a hard failure.

## ADR policy

Changes affecting module boundaries, data ownership, cross-feature communication, persistence, public contracts, dependency direction, Rule meaning/severity, or architecture-policy baseline exceptions require an ADR before admission. See [`adr/README.md`](adr/README.md).

## Executable enforcement (v320)

Architecture Guard v2 consumes `architecture-contracts.json`, `dependency-contract.json`, `data-ownership.json`, `technical-debt-baseline.json`, all feature manifests, the rule-governance anchor, temporary-exception registry, and the v319 complexity baseline. `VARCH-001..030` retain their v319 meanings. The 18 historical identities remain exact-identity Ratchet debt.

Every mutating session requires a machine-readable Change Contract. `scripts/ci/run-quality-gate.sh all <session>` is the unified admission path: Change Contract → Architecture → Kotlin/Complexity Ratchet → Documentation → Design System → Detekt → Lint → Tests → Debug Build → Source-of-Truth Admission. Any failure, blocked stage, stale fingerprint, or missing evidence prevents source-of-truth packaging.

`contracts/complexity-baseline-v319.json` is measured from the frozen v319 source with `verto-kotlin-complexity-lexical-v1`; historical identities may only improve, while new identities are constrained by deterministically derived p95 budgets. Baseline expansion, Rule meaning reuse, severity downgrade, or malformed temporary exceptions fail closed.
