---
status: canonical
scope: system
owner: "documentation-governance"
last_verified_against: v321
---
# Verto Documentation Index

This index is the current navigation map for the v321 dependency-repair tree. Code/SQL evidence remains authoritative when documentation conflicts with implementation.

## Documentation entry

- [Repository README](../README.md)
- [Contributing](../CONTRIBUTING.md)
- [Changelog](../CHANGELOG.md)
- [Canonical Document Map](CANONICAL_DOCUMENT_MAP.md)
- [Documentation Inventory](DOCUMENTATION_INVENTORY.md)

## Architecture

- [Architecture Contract](architecture/ARCHITECTURE_CONTRACT.md)
- [Feature Admission Contract](architecture/FEATURE_ADMISSION_CONTRACT.md)
- [Architecture ADR policy](architecture/adr/README.md)
- [Architecture contracts foundation (v319)](architecture/verification/ARCHITECTURE_CONTRACTS_FOUNDATION_v319.md)
- [Architecture enforcement closeout (v320)](architecture/verification/ARCHITECTURE_ENFORCEMENT_v320.md)
- [Dependency & modularity repair (v321)](architecture/verification/DEPENDENCY_MODULARITY_REPAIR_v321.md)
- [ADR-0001: Optimal composition boundaries](architecture/adr/ADR-0001-optimal-composition-boundaries.md)

- [Architecture Overview](architecture/overview.md)
- [Module Map](architecture/module-map.md)
- [Data Flow](architecture/data-flow.md)
- [Sync Architecture](architecture/sync-architecture.md)
- [Security Boundaries](architecture/security-boundaries.md)

## API / Server

- [API / Server Integration](api/README.md)
- [RPC Reference](api/rpc-reference.md)
- [Authentication Contract](api/auth.md)
- [Error Contract](api/errors.md)
- [Idempotency and Retry](api/idempotency.md)

## Production-critical features

- [Authentication](features/authentication.md)
- [Inventory](features/inventory.md)
- [Invoices](features/invoices.md)
- [Customers](features/customers.md)
- [Suppliers](features/suppliers.md)
- [Cash](features/cash.md)
- [Expenses](features/expenses.md)
- [Logistics](features/logistics.md)
- [Sync](features/sync.md)
- [Reports](features/reports.md)
- [Employees](features/employees.md)
- [Notifications](features/notifications.md)
- [Settings](features/settings.md)
- [Search](features/search.md)
- [Home](features/home.md)
- [Design System](features/design-system.md)

## Sync rollout

- [Sync Cutover Policy v314](sync/VERTO_SYNC_CUTOVER_POLICY_v314.md) — rollout-state authority; V2/Realtime defaults remain OFF and Legacy fallback ON in the retained v317 runtime source.

## Design System / UX

- [Design System Contract](design-system/DESIGN_SYSTEM_CONTRACT.md)
- [UX/UI Quality Contract](UX_UI_QUALITY_CONTRACT.md)

## Quality

- [Documentation Governance and Drift-Prevention Policy](quality/documentation-policy.md)
- [Release and Documentation Quality Gates](quality/release-gates.md)
- [Kotlin Quality Contract](kotlin/KOTLIN_QUALITY_CONTRACT.md)
- [Kotlin Exception Policy](kotlin/KOTLIN_EXCEPTION_POLICY.md)
- [Home Performance Budget](benchmarks/home-performance-budget-v114.md)

## Historical/supporting evidence

- [Session 336 final finance report](finance/SESSION_336_FINAL_REPORT.md)
- [Session 336 cash convergence evidence](finance/SESSION_336_CONVERGENCE_EVIDENCE.md)
- [Session 336 expense dependency evidence](finance/SESSION_336_EXPENSE_DEPENDENCY_EVIDENCE.md)
- [Session 336 server evidence](finance/SESSION_336_SERVER_EVIDENCE.md)

Historical/versioned material remains evidence only unless listed as Canonical above. Path-coupled verification documents remain at their legacy paths when tools require them.

- [Archive Manifest](archive/ARCHIVE_MANIFEST.md)
- `archive/reports/` — reports and closeouts
- `archive/verification/` — verification evidence
- `archive/superseded-contracts/` — superseded plans/contracts
