---
status: canonical
scope: system
owner: "architecture"
last_verified_against: v321
---
# Verto Feature Admission Contract

Feature admission is default-deny and is governed by [`ARCHITECTURE_CONTRACT.md`](ARCHITECTURE_CONTRACT.md) plus the machine-readable manifests under `contracts/features/`.

## Required manifest

Every `:feature:*` module in `settings.gradle.kts` must have exactly one manifest conforming to `contracts/feature-manifest.schema.json`. The manifest declares module/owner/purpose, public API, internal packages, exposed ports, incoming/outgoing/forbidden project dependencies, external dependency identities, owned entities/tables/repositories, allowed cross-feature ports/public API dependencies, legacy cross-feature debt, source binding, and a deterministic public-API surface fingerprint.

## Admission rules

- Missing or orphan manifest: FAIL.
- Current Gradle dependency absent from the manifest: FAIL.
- New project/library dependency absent from the dependency contract/manifest: FAIL after v320 enforcement activation.
- Public API addition/change absent from the provider manifest: FAIL.
- Another feature's INTERNAL implementation, Entity, DAO or storage implementation: FAIL.
- Missing or duplicate governed data owner: FAIL.
- Cross-feature integration must use a declared Port/Contract/API surface.
- Dependency cycles remain zero.

## Public vs INTERNAL

Only exact symbols/package prefixes/API modules in `architecture.public_api` or `architecture.exposed_ports` are Public. All other provider implementation is INTERNAL by default. For a `:feature:*:api` edge to pass, the consumer must declare it in `allowed_public_api_dependencies`, the provider must be API-only with a non-empty Public surface, and the edge must remain cycle-free; a same-named implementation module never inherits that permission.

## Current debt

The historical v318 identities remain preserved as evidence, but v321 admits none of the former `VARCH-003`, `VARCH-005`, or `VARCH-012` identities. Declaring a manifest never cleans debt by itself; only source/graph repair or validated API-module classification can remove a current finding. A removed identity may not reappear.

## Verification meaning

Session 320 activates fail-closed executable admission. Missing manifests, undeclared project/external dependencies, Public API drift, foreign INTERNAL/storage imports, ownership gaps/duplicates, and dependency cycles are mutation-tested and block unified admission.
