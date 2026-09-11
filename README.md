---
status: canonical
scope: system
owner: "repository-maintainers"
last_verified_against: v318
---
# Verto

Verto is an Android ERP application organized as a 31-module modular monolith. The production tree is split across `app`, shared `core` modules, `data` infrastructure modules, and feature modules. Room is the local persistence layer; Supabase is the server integration surface used by the current client.

## Current baseline

This source tree includes the v318 documentation drift-prevention governance layer on top of the byte-verified v317 baseline. Session 318 changes documentation/governance tooling only; production Kotlin, SQL, Room schemas, Supabase migrations, APIs, and runtime behavior remain unchanged.

The v314 sync cutover state remains pre-runtime-cutover: rollout Wave 0, V2 pull/push and Realtime hints default OFF, and Legacy fallback ON. Final runtime cutover is not claimed by this documentation release.

## Repository layout

- `app` — Android shell, navigation, composition root, and platform integration.
- `core/*` — common, crash, session, audit, notification, export, and design-system capabilities.
- `data/*` — database, preferences, network, sync, and operations infrastructure.
- `feature/*` — auth, inventory, party, invoice, payment, shipment, reports, organization, profile, settings, notifications, commission, messages, dashboard, expenses, management, and Optimal integration.
- `supabase/migrations` — server migration history used by the project.
- `sql` and `docs/sql` — retained SQL/verification history; do not remove due to age alone.
- `functions` — local Edge Function source/provenance retained by existing verification tooling.
- `docs` — current documentation map, contracts, evidence, and historical archive.

The authoritative module list is `settings.gradle.kts`.

## Build requirements

The repository pins Gradle 8.9 through the wrapper, Android Gradle Plugin 8.7.3, Kotlin 2.1.0, compile/target SDK 35, and min SDK 26. The build logic uses a JVM 17 toolchain; Android source compatibility is Java 11.

Use the wrapper rather than a system Gradle installation:

```bash
./gradlew --no-daemon assembleDebug
```

The repository quality entry point is:

```bash
scripts/ci/run-quality-gate.sh all 318
```

The documentation gate is available standalone as `./scripts/run-documentation-gate.sh` and through `scripts/ci/run-quality-gate.sh documentation <session>`. The `all` pipeline runs documentation first, followed by Design System enforcement and the existing Gradle quality stages. A gate is `PASS` only when it was executed successfully.

## Server and database

Supabase migrations live in `supabase/migrations/`. Historical SQL that must be retained is under `sql/` and `docs/sql/`. Android-side server expectations and older verification evidence remain available. Current API/server authority is indexed under `docs/api/`; historical evidence is not promoted to current authority.

## Documentation

Start at [`docs/INDEX.md`](docs/INDEX.md). It distinguishes Canonical documentation from supporting evidence and historical archive material.

Primary current contracts:

- [`docs/architecture/ARCHITECTURE_CONTRACT.md`](docs/architecture/ARCHITECTURE_CONTRACT.md)
- [`docs/architecture/FEATURE_ADMISSION_CONTRACT.md`](docs/architecture/FEATURE_ADMISSION_CONTRACT.md)
- [`docs/design-system/DESIGN_SYSTEM_CONTRACT.md`](docs/design-system/DESIGN_SYSTEM_CONTRACT.md)
- [`docs/UX_UI_QUALITY_CONTRACT.md`](docs/UX_UI_QUALITY_CONTRACT.md)
- [`docs/kotlin/KOTLIN_QUALITY_CONTRACT.md`](docs/kotlin/KOTLIN_QUALITY_CONTRACT.md)
- [`docs/kotlin/KOTLIN_EXCEPTION_POLICY.md`](docs/kotlin/KOTLIN_EXCEPTION_POLICY.md)
- [`docs/sync/VERTO_SYNC_CUTOVER_POLICY_v314.md`](docs/sync/VERTO_SYNC_CUTOVER_POLICY_v314.md)
- [`docs/benchmarks/home-performance-budget-v114.md`](docs/benchmarks/home-performance-budget-v114.md)
- [`docs/quality/documentation-policy.md`](docs/quality/documentation-policy.md)
- [`docs/quality/release-gates.md`](docs/quality/release-gates.md)

Canonical ownership is resolved in [`docs/CANONICAL_DOCUMENT_MAP.md`](docs/CANONICAL_DOCUMENT_MAP.md), and complete Markdown traceability is in [`docs/DOCUMENTATION_INVENTORY.md`](docs/DOCUMENTATION_INVENTORY.md).

## Source packaging

The repository's deterministic source packager is:

```bash
scripts/package-source.sh ../Verto-v318-source-of-truth.zip
```

The package script defines its own exclusions and requires `docs/INDEX.md` plus core source paths.
