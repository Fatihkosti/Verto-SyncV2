---
status: canonical
scope: feature
owner: "feature:inventory"
last_verified_against: v315
---
# Inventory

## Purpose

Catalog, units/categories, stock movement, costing, reconciliation and inventory presentation.

## User workflows

- Browse/search/filter stock
- Create/edit/archive inventory items
- Manage units/categories and prices
- Perform stock writes/counts
- Reconcile inventory and apply landed-cost effects

## Entry points

- `InventoryViewModel` / inventory screens
- inventory application coordinators and stock writer
- `InventorySyncParticipant`

## Business rules

- Trusted organization scope is required for repository writes.
- Edit/price/import/export have separate client permission gates.
- Stock/cost mutations use dedicated write/outbox paths rather than arbitrary quantity edits.

## Domain model

Inventory item/unit/category plus movement, cost revision, reconciliation and outbox/conflict records.

## Data ownership

Canonical business ownership: `feature:inventory`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room: `inventory_items`, `inventory_movements`, `inventory_cost_revisions`, reconciliation tables, stock/cost outboxes, units/categories.

## Server dependencies

Direct inventory/item/category tables plus inventory ledger/reconciliation RPCs and archive RPC.

## Permissions

`InventoryPermissionGate` separates EDIT, PRICE, IMPORT and EXPORT; denied actions are audit-logged.

## Offline behavior

Local inventory reads are Room-backed; eligible writes persist locally/outbox. Server archive/reconciliation/pull requires connectivity.

## Sync behavior

Legacy participant plus V2 inventory ledger/reconciliation and unified sync registry ownership.

## Validation

Item/unit/category and stock commands validate organization, identifiers and feature-specific fields.

## Error states

Write guard/conflict/reconciliation quarantine and sync retry state preserve failure instead of silently applying.

## Notifications/events

Inventory contributes Home pending actions/activity/search; notification behavior is consumer-specific.

## Critical invariants

- Repository `trustedOrganizationId()` must be non-blank.
- Cost/stock synchronization is sequence/cursor validated.
- Cross-organization materialization is rejected by sync/domain guards.

## Testing notes

Inventory module tests plus historical verification artifacts; v316 does not rerun runtime tests.

## Known limitations

V2 rollout is default OFF at v315; repository server definitions do not imply active cutover.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/inventory/src/main/kotlin/com/verto/app/data/repository/InventoryRepository.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/data/InventoryStockWriter.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt`
