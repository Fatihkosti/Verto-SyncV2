---
status: canonical
scope: system
owner: "documentation-governance"
last_verified_against: v318
---
# Documentation Governance and Drift-Prevention Policy

Documentation is part of the implementation contract.  
A code change is incomplete when it changes documented behavior without updating its canonical documentation.

## Authority model

- **Canonical** — the single current owner for a documented responsibility. Canonical ownership is registered in [`../CANONICAL_DOCUMENT_MAP.md`](../CANONICAL_DOCUMENT_MAP.md).
- **Supporting** — retained evidence, verification, closeout, or explanatory material. It may support a decision but does not replace a Canonical owner.
- **Deprecated** — compatibility or superseded material retained for traceability. It must not be presented as current authority.
- **Archived** — historical material under the archive or explicitly classified as archived. It must not own current responsibilities.

Code, SQL, migrations, and runtime state remain implementation authority. A historical report, filename, generated artifact, or verbal claim cannot override the current source tree or a failed gate.

## Required metadata

Every governed current document requires non-empty frontmatter:

```yaml
status: canonical
scope: system|feature|...
owner: "responsible-owner"
last_verified_against: vNNN
```

Current governed lifecycle values are `canonical`, `draft`, `deprecated`, and `archived`. Existing Supporting/Historical evidence may use `supporting`; `supporting` cannot substitute for a Canonical owner.

## Documentation Impact contract

Every future implementation contract/change records exactly one of:

```text
Documentation Impact: REQUIRED
Affected docs:
- <canonical path>
```

or:

```text
Documentation Impact: NONE
Reason: <non-empty reason>
```

A bare `NONE` is invalid. `REQUIRED` must list affected Canonical documents.

Documentation Impact is mandatory when a change affects API/RPC, authentication/authorization, error contracts, idempotency, retry behavior, database schema, SQL functions, sync contracts, architecture boundaries, module responsibilities, feature business rules, critical invariants, release/deployment processes, or recovery/rollback behavior.

Static tooling cannot safely infer every semantic business-rule change. D1–D9 detect structural/documentable drift; the explicit Documentation Impact declaration governs semantic changes that cannot be inferred safely from source structure alone.

## Ownership and update requirements

The owner of a changed implementation surface must update its Canonical documentation in the same accepted change when behavior changes. In particular:

- API/RPC changes update the API Canonical set, including `docs/api/rpc-reference.md` where applicable.
- Architecture/module changes update architecture ownership and module maps.
- Production-critical feature behavior changes update the owning feature document.
- Database/schema/SQL changes update the applicable Canonical database/server contract when one exists and must not use historical SQL evidence as current authority.
- Sync contract changes update the owning sync architecture/feature/cutover documentation as applicable.
- Release/deployment/recovery/rollback changes update their current Canonical policy or runbook owner.

New Canonical owners must be registered once in the Canonical map and be reachable from [`../INDEX.md`](../INDEX.md).

## Automated drift checks

The mandatory documentation gate is [`../../scripts/run-documentation-gate.sh`](../../scripts/run-documentation-gate.sh). It executes these fail-closed checks before expensive Gradle gates:

| ID | Invariant |
|---|---|
| D1_CANONICAL_REGISTRY | Canonical registry existence, uniqueness, ownership, and no archive/deprecated owner |
| D2_METADATA | Required metadata and lifecycle validity |
| D3_INTERNAL_LINKS | Governed internal links resolve; unsupported governed forms fail |
| D4_DEPRECATED_REFERENCES | Deprecated/archived material is not promoted as current authority |
| D5_RPC_DRIFT | Production Kotlin RPC names equal active RPC documentation |
| D6_MODULE_MAP_DRIFT | `settings.gradle.kts` modules equal the current module map |
| D7_FEATURE_COVERAGE | Explicit production-critical feature documentation remains fully owned |
| D8_CANONICAL_NAMING | Canonical filenames contain no lifecycle noise |
| D9_INDEX_INTEGRITY | Canonical documentation remains reachable from the documentation index |

Parser errors, unreadable governed files, unsupported authoritative structure, and ambiguous extraction are failures, not warnings or skips.

## Production-critical feature admission set

The machine-readable v317 critical-feature baseline is [`../../scripts/documentation/critical-features.json`](../../scripts/documentation/critical-features.json). It intentionally contains the 16 production-critical documentation pages and is not derived one-to-one from Gradle feature modules.

Admitting or removing a production-critical feature requires `Documentation Impact: REQUIRED`, an owning Canonical page/map entry, and an intentional update to this baseline. A newly created feature page cannot silently become current critical-feature authority.

## Gate semantics

The release status vocabulary and blocking rules are defined by [`release-gates.md`](release-gates.md). `NOT RUN` and `BLOCKED` are never `PASS`, and no textual or verbal statement can override an observed gate result.
