---
status: canonical
scope: feature
owner: "feature:party"
last_verified_against: v315
---
# Customers

## Purpose

Customer role/profile workflows over the shared Party model.

## User workflows

- Create/edit customer
- Browse/search customer
- View balance/ledger/reminders/credit
- Use customer in invoices/payments/messages

## Entry points

- `feature:party` customer presentation and application services
- `ClientRepository` / Party role use cases
- Home search/activity providers

## Business rules

- Customer is a Party role/projection, not an independent module.
- Tenant scope is required for remote/local customer operations.

## Domain model

`clients` identity plus `party_roles` and `customer_profiles`; reminders/credits/ledger projections.

## Data ownership

Canonical business ownership: `feature:party`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room party/customer/profile/reminder/credit tables and party outbox/conflict records.

## Server dependencies

`clients`, client credits/reminders and `verto_upsert_client_v1`; some related payment/invoice tables.

## Permissions

Party/customer presentation/command gates use session employee permissions where applicable.

## Offline behavior

Room-backed customer reads/writes can operate locally where repository path supports it; server-only related commands still require network.

## Sync behavior

Party sync V2/legacy client sync plus unified ownership.

## Validation

Party role mapping and customer form/domain validation.

## Error states

Conflict/outbox/tenant errors are preserved rather than merging cross-tenant rows.

## Notifications/events

Customer-related reminders/activity feed integrations are provider-driven.

## Critical invariants

- Customer and supplier roles may share one party identity.
- Do not invent a `feature:customers` module; ownership is `feature:party`.

## Testing notes

Party tests and closeout evidence cover migration/role behavior.

## Known limitations

Historical `clients` direct access coexists with Party V2 structures.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/party/src/main/kotlin/com/verto/app/feature/party/application/PartyApplicationService.kt`
- `feature/party/src/main/kotlin/com/verto/app/data/repository/ClientRepository.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt`
