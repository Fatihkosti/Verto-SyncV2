---
status: canonical
scope: feature
owner: "feature:expenses"
last_verified_against: v315
---
# Expenses

## Purpose

Expense entry/listing and its cash/sync/Home integration.

## User workflows

- Create/edit/delete expense
- Browse expense history
- Launch expense from Home quick action

## Entry points

- expense screens/view model/application gateway
- `ExpensesOperationsAdapter`
- expense quick-action provider

## Business rules

- Expense writes are organization-scoped and routed through operations/repository boundaries.

## Domain model

`ExpenseEntity` / `expenses` table.

## Data ownership

Canonical business ownership: `feature:expenses`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room `expenses`.

## Server dependencies

Direct PostgREST `expenses` legacy sync path.

## Permissions

Expense UI/operations use configured employee permission checks in adapters/consumer navigation.

## Offline behavior

Local expense persistence/read works through Room; remote convergence requires sync.

## Sync behavior

Expense legacy participant/runtime sync; stronger/unified behavior only where registry/rollout assigns it.

## Validation

Expense amount/fields and organization are validated by application/repository paths.

## Error states

Persistence/sync failure is surfaced; delete is not treated as remotely complete until sync succeeds.

## Notifications/events

Home quick-action integration; no expense-specific server notification contract.

## Critical invariants

- Expense cash effects must follow the owning operations transaction path when configured.

## Testing notes

Feature/operations tests where present.

## Known limitations

No dedicated expense RPC discovered in production.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/expenses/src/main/kotlin/com/verto/app/feature/expenses/application`
- `app/src/main/kotlin/com/verto/app/feature/expenses/bridge/ExpensesOperationsAdapter.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncMisc.kt`
