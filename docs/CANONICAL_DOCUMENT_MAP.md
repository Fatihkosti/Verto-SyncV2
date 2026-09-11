---
status: canonical
scope: system
owner: "documentation-governance"
last_verified_against: v321
---
# Canonical Document Map

A Canonical document is the single current documentation owner for the responsibility shown below. Code/SQL remains ultimate implementation authority. Existing Canonical responsibilities are retained; v319 updates the architecture owner to govern the source-bound machine-readable contracts without creating a competing Canonical owner.

| Responsibility | Canonical document | Code authority | Scope | Owner | Last verified | Supporting evidence | Supersedes |
|---|---|---|---|---|---|---|---|
| Repository documentation entry | `README.md` | `settings.gradle.kts`, repository tree | system | repository-maintainers | v314 | `docs/INDEX.md` | none |
| Contribution and documentation change governance | `CONTRIBUTING.md` | repository tooling + current contracts | system | repository-maintainers | v314 | `scripts/ci/run-quality-gate.sh`, `scripts/package-source.sh` | none |
| Changelog ownership | `CHANGELOG.md` | verified release/session evidence | system | release-governance | v314 | `docs/archive/reports/` | none |
| Documentation navigation | `docs/INDEX.md` | final documentation tree | system | documentation-governance | v314 | `docs/DOCUMENTATION_INVENTORY.md` | v227 index organization |
| Markdown inventory and classification | `docs/DOCUMENTATION_INVENTORY.md` | final Markdown tree + v314 baseline hashes | system | documentation-governance | v314 | `docs/archive/ARCHIVE_MANIFEST.md` | none |
| Canonical responsibility ownership | `docs/CANONICAL_DOCUMENT_MAP.md` | current code + verified Canonical set | system | documentation-governance | v314 | `docs/INDEX.md` | none |
| Documentation governance and drift-prevention policy | `docs/quality/documentation-policy.md` | documentation gate tooling + current Canonical registry | system | documentation-governance | v318 | `scripts/run-documentation-gate.sh`, `scripts/documentation/` | none |
| Release/quality documentation gate policy | `docs/quality/release-gates.md` | `scripts/ci/run-quality-gate.sh`, documentation gate execution evidence | system | release-governance | v318 | `docs/DOCUMENTATION_DRIFT_PREVENTION_CLOSEOUT_v318.md` | none |
| Modular-monolith architecture rules and architecture admission contracts | `docs/architecture/ARCHITECTURE_CONTRACT.md` | `settings.gradle.kts`, Architecture Guard v2, unified admission tooling | system | architecture | v321 | `docs/architecture/contracts/architecture-contracts.json`, `dependency-contract.json`, `data-ownership.json`, `complexity-baseline-v319.json` | `docs/architecture/architecture-rules.json` historical compatibility only |
| Feature admission rules | `docs/architecture/FEATURE_ADMISSION_CONTRACT.md` | current module graph + architecture rules | system | architecture | v321 | `docs/architecture/contracts/feature-manifest.schema.json`, `docs/architecture/contracts/features/`, `docs/architecture/features/` | none |
| Design System contract | `docs/design-system/DESIGN_SYSTEM_CONTRACT.md` | `core/designsystem`, design-system scanner/configuration | system | core:designsystem | v314 | tool-coupled files under `docs/design-system/` | older design-system migration contracts |
| UX/UI quality contract | `docs/UX_UI_QUALITY_CONTRACT.md` | UI source + Design System rules | system | core:designsystem + owning feature presentation | v314 | design-system verification evidence | none |
| Kotlin quality contract | `docs/kotlin/KOTLIN_QUALITY_CONTRACT.md` | Kotlin source + quality tooling | system | engineering-quality | v314 | `scripts/verify-kotlin-quality-static.py` | none |
| Kotlin exception policy | `docs/kotlin/KOTLIN_EXCEPTION_POLICY.md` | Kotlin quality tooling/config | system | engineering-quality | v314 | exception configuration and static checks | none |
| Sync rollout/cutover authority state | `docs/sync/VERTO_SYNC_CUTOVER_POLICY_v314.md` | `SyncRolloutPolicy`, `FeatureFlags`, v314 sync verification tooling | system | data:sync + data:network | v314 | `VERTO_SYNC_CUTOVER_VERIFICATION_v314.md`, `VERTO_SYNC_RUNTIME_EVIDENCE_v314.md` | earlier rollout-state evidence |
| Home performance budget | `docs/benchmarks/home-performance-budget-v114.md` | `scripts/benchmark-v114-home.sh` + Home runtime | feature | performance-engineering | v314 | device benchmark output when executed | none |
| Current system architecture overview | `docs/architecture/overview.md` | current module/runtime source | system | architecture | v315 | current v315 source | none |
| Current module ownership/dependency map | `docs/architecture/module-map.md` | `settings.gradle.kts` + module build/source | system | architecture | v315 | current v315 source | none |
| Current system data-flow ownership | `docs/architecture/data-flow.md` | feature/data/sync source | system | architecture | v315 | current v315 source | none |
| Unified/legacy sync internal architecture | `docs/architecture/sync-architecture.md` | `:data:sync`, `:data:network` sync source | system | data:sync + data:network | v315 | current v315 source | none |
| Client/server security boundary documentation | `docs/architecture/security-boundaries.md` | security/session/network source + repository SQL | system | security + architecture | v315 | current v315 source | none |
| Architecture decision-record index/policy | `docs/architecture/adr/README.md` | current canonical architecture set | system | architecture | v315 | current v315 source | none |
| API/server integration entry and direct-table inventory | `docs/api/README.md` | production Supabase call sites | system | data:network | v315 | current v315 source | none |
| Production RPC reference | `docs/api/rpc-reference.md` | production RPC call sites + repository SQL | system | data:network | v315 | current v315 source | none |
| Authentication transport/session API contract | `docs/api/auth.md` | Auth source + app deep-link/session code | system | feature:auth + data:network | v315 | current v315 source | none |
| API/application error taxonomy | `docs/api/errors.md` | validation/network/sync/security source | system | data:network + application | v315 | current v315 source | none |
| Idempotency and retry contract | `docs/api/idempotency.md` | outbox/request/retry source + SQL | system | data:sync + data:network | v315 baseline; B09-V02 payment identity source-only | B09-V02 evidence; runtime BLOCKED | none |
| Authentication feature behavior | `docs/features/authentication.md` | owning feature/application/data source | feature | feature:auth | v315 | current v315 source | none |
| Inventory feature behavior | `docs/features/inventory.md` | owning feature/application/data source | feature | feature:inventory | v315 | current v315 source | none |
| Invoices feature behavior | `docs/features/invoices.md` | owning feature/application/data source | feature | feature:invoice | v315 | current v315 source | none |
| Customers feature behavior | `docs/features/customers.md` | owning feature/application/data source | feature | feature:party | v315 | current v315 source | none |
| Suppliers feature behavior | `docs/features/suppliers.md` | owning feature/application/data source | feature | feature:party | v315 | current v315 source | none |
| Cash feature behavior | `docs/features/cash.md` | owning feature/application/data source | feature | feature:payment + data:operations | v315 | current v315 source | none |
| Expenses feature behavior | `docs/features/expenses.md` | owning feature/application/data source | feature | feature:expenses | v315 | current v315 source | none |
| Logistics feature behavior | `docs/features/logistics.md` | owning feature/application/data source | feature | feature:shipment | v315 | current v315 source | none |
| Sync feature behavior | `docs/features/sync.md` | owning feature/application/data source | feature | data:sync | v315 | current v315 source | none |
| Reports feature behavior | `docs/features/reports.md` | owning feature/application/data source | feature | feature:reports | v315 | current v315 source | none |
| Employees feature behavior | `docs/features/employees.md` | owning feature/application/data source | feature | feature:organization | v315 | current v315 source | none |
| Notifications feature behavior | `docs/features/notifications.md` | owning feature/application/data source | feature | feature:notifications + data:network | v315 | current v315 source | none |
| Settings feature behavior | `docs/features/settings.md` | owning feature/application/data source | feature | feature:settings | v315 | current v315 source | none |
| Search feature behavior | `docs/features/search.md` | owning feature/application/data source | feature | feature:dashboard + contributors | v315 | current v315 source | none |
| Home feature behavior | `docs/features/home.md` | owning feature/application/data source | feature | app + feature:dashboard | v315 | current v315 source | none |
| Design System feature behavior | `docs/features/design-system.md` | owning feature/application/data source | feature | core:designsystem | v315 | current v315 source | none |

## Compatibility aliases

- `docs/design-system/DESIGN-SYSTEM-CONTRACT.md` is a deprecated compatibility-only path retained for `tools/verify_design_system_contract.py`. It is not Canonical; authority remains `docs/design-system/DESIGN_SYSTEM_CONTRACT.md`.

## Ownership rule

Shared internals are documented once: sync internals in `architecture/sync-architecture.md`, RPC details in `api/rpc-reference.md`, auth transport in `api/auth.md`, feature workflows in `features/*.md`, and module ownership in `architecture/module-map.md`. Feature pages link instead of duplicating those contracts.
