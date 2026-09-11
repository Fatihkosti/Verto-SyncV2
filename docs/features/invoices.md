---
status: canonical
scope: feature
owner: "feature:invoice"
last_verified_against: v315
---
# Invoices

## Purpose

Sales/purchase invoice lifecycle, returns/voids, purchase-cycle coordination and financial/inventory effects.

## User workflows

- Create/edit sales or purchase invoice
- Allocate invoice number
- Post linked payment/inventory effects
- Void/return invoice
- Manage purchase order/receipt/match cycle

## Entry points

- Invoice screens/view models
- `InvoiceWriteCoordinator`, return/void use cases
- purchase-cycle coordinator and sync participant

## Business rules

- Invoice save validation precedes coordinated writes.
- View/edit/export permissions differ for SALE vs PURCHASE.
- Post-commit inventory/payment effects go through declared ports/coordinators.

## Domain model

Invoice/header/items, editor drafts, returns, purchase orders/receipts/matches and financial state.

## Data ownership

Canonical business ownership: `feature:invoice`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room invoice/item/draft/return/purchase-cycle tables plus financial outbox/inbox.

## Server dependencies

`allocate_invoice_number`, invoice/item/payment direct tables, purchase-cycle push RPCs and financial sync RPCs.

## Permissions

`EmployeePermissions` maps sales/purchase view/edit/export separately.

## Offline behavior

Draft/read/local coordinated work is Room-backed; server numbering/payment/RPC sync requires network.

## Sync behavior

`InvoiceSyncParticipant`, financial event sync and unified aggregate ownership.

## Validation

`InvoiceSaveValidator`, edit policy, return/void coordinators and purchase-cycle rules.

## Error states

Validation, permission, transaction, financial posting and sync failures remain explicit.

## Notifications/events

Invoice activity/pending-action providers feed Home and notification boundaries.

## Critical invariants

- A completed local write must preserve invoice/item/payment/inventory ordering defined by coordinators.
- Sale and purchase permissions are not interchangeable.
- Returns/voids use dedicated reversal paths, not destructive rewrites.

## Testing notes

Invoice unit/static verification artifacts exist; no v316 runtime execution.

## Known limitations

Some direct financial RPC server bodies are absent from repository SQL.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceWriteCoordinator.kt`
- `feature/invoice/src/main/kotlin/com/verto/app/feature/invoice/application/InvoiceSaveValidator.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt`
