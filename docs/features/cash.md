---
status: canonical
scope: feature
owner: "feature:payment + data:operations"
last_verified_against: v315
---
# Cash

## Purpose

Cash register balances/movements, reconciliation and payment-linked cash effects.

## User workflows

- Read cash register state
- Record/reconcile movements
- Post payment-linked cash effects
- Use denominations/reconciliation sessions

## Entry points

- payment feature/application ports
- `data:operations` payment/transaction coordinators
- cash DAOs/repositories

## Business rules

- Cash effects are transactionally coordinated with payment/financial operations where defined.
- Organization scope is required for cash rows.

## Domain model

`cash_register`, `cash_register_movements`, `cash_reconciliation_sessions`, `cash_denominations` plus payments.

## Data ownership

Canonical business ownership: `feature:payment + data:operations`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room cash/payment tables.

## Server dependencies

Direct PostgREST cash tables and payment/financial RPCs where operations require server authority.

## Permissions

Payment/cash actions inherit payment/financial employee permissions and feature gates.

## Offline behavior

Room read/reconciliation paths may be local; direct financial server commands require connectivity.

## Sync behavior

Legacy direct cash sync plus stronger financial/unified paths.

## Validation

Amounts, organization scope and reconciliation state are validated by coordinators/DAOs.

## Error states

Financial/transaction failures abort coordinated changes; sync retries remain durable where outbox-backed.

## Notifications/events

Not applicable as a standalone cash notification owner.

## Critical invariants

- Cash movement and financial posting must not diverge across a coordinated transaction.

## Testing notes

Payment/operations tests; no v316 runtime execution.

## Known limitations

Cash is cross-module ownership, not a standalone `feature:cash` module.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `data/operations/src/main/kotlin/com/verto/app/data/operations/payment`
- `data/database/src/main/kotlin/com/verto/app/data/local/dao/CashRegisterDao.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncCash.kt`
