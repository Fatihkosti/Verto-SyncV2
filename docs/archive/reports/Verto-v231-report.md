# Verto v231 — Execution Report

Baseline: `Verto-v230-source-of-truth.zip`  
Contract: `VERTO_LOGISTICS_REBUILD_MASTER_PLAN_v229-v233_REVISED.md` — Session v231 only.

## Implemented

- Preserved the v230 planning/execution split: Start Journey remains separate from Start Movement.
- Completed receipt facts with handover/received carton counts, discrepancy reason, opened cartons and damaged cartons. Opened/damaged may overlap, and each is independently bounded by received cartons.
- Ensured operational events written by arrival, departure, handling, custody, customs and documents preserve separate `occurredAt` and `recordedAt` facts.
- Added explicit customs lifecycle: carrier → customs broker custody, customs start, customs completion, then normal explicit handoff to the next carrier.
- Customs start requires arrival + unload, a Customs Broker partner, one current whole-shipment custodian, and confirmed carton counts.
- Customs completion requires the broker to retain custody and cannot predate customs start.
- Existing Friday-off logistics calendar remains the customs working-calendar policy and is covered by v231 contract tests.
- Customs duty/clearance costs are restricted to SDG; cost recording and payment confirmation remain separate operations.
- Repack/carton-count changes remain explicit operations; execution corrections remain available through the existing correction use cases.
- Centralized logistics attachment policy now accepts JPG/JPEG, PNG, MP4, DOC, DOCX, PDF and Markdown.
- MP4 maximum is 100 MiB; files are streamed into app-private storage with incremental size enforcement and SHA-256 calculation.
- Attachment validation includes MIME normalization, resolver/declared MIME consistency and file-signature checks.
- Logistics document metadata now supports milestone/source/leg/handoff/cost/recovery scope plus employee actor snapshot.
- UI now exposes customs broker pickup/completion and opened/damaged carton capture without adding new raw Material debt.

## Persistence / migration

- Room schema `57 → 58`.
- Added `customs_started_at` and `customs_completed_at` to `logistics_milestones`.
- Added `employee_id` and `employee_name_snapshot` to `logistics_documents`.
- Added atomic Room persistence for customs state + milestone + custody handoff + event.
- Remote mirror SQL added at `docs/sql/logistics_v2/012_v231_execution.sql`; verification SQL at `013_v231_execution_verification.sql`.
- Logistics V2 remote runtime remains OFF.

## Added tests / contracts

- `MigrationCatalogV231Test` — migration catalog reaches schema 58 through 57→58.
- `LogisticsV231ExecutionContractTest` — opened/damaged overlap, discrepancy reason, customs timestamp order, occurred/recorded separation, Friday exclusion, attachment formats/MP4 cap/signature validation.

## Verification

| Gate | Result |
|---|---|
| Design System scanner | **PASS — 0 violations** |
| Architecture topology | **PASS — 31 modules, 0 dependency cycles, Room 58** |
| Architecture delta vs v230 | **PASS — no new measured violations** |
| Kotlin static quality vs v230 | **PASS — no measured debt increase** |
| Domain/customs/document `kotlinc` smoke | **PASS** |
| SQLite migration 57→58 smoke | **PASS** |
| Android resource XML parse | **PASS** |
| Gradle unit/UI tests and Android compile | **BLOCKED BY ENVIRONMENT** |
| Logistics session verifier | **BLOCKED only by missing generated Room 58 schema JSON** |

### Static quality comparison

- Architecture violations: v230 `18` → v231 `18`.
- Broad catches: `20` → `20`.
- Excessive parameter lists: `457` → `457`.
- Exposed mutable state: `0` → `0`.
- Files over 500 lines: `10` → `10`.
- Long functions: `340` → `340`.

### Gradle / Room schema-export blocker

The wrapper requires Gradle `8.9`. The distribution is not cached and this environment has no network access, so Gradle fails at `services.gradle.org` with `UnknownHostException` before KSP/Room can generate `app/schemas/.../58.json` or execute Android tests.

The Room migration itself was smoke-tested directly with SQLite. No generated Room schema JSON was fabricated manually, and no Gradle/build PASS is claimed.

## Gate assessment

v231 implementation is complete in source and packaged. Local static/domain/migration gates pass; the remaining Android build/schema-export verification requires an environment with Gradle 8.9 available.
