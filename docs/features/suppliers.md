---
status: canonical
scope: feature
owner: "feature:party"
last_verified_against: v315
---
# Suppliers

## Purpose

Supplier role/profile workflows over the shared Party model.

## User workflows

- Create/edit supplier role/profile
- Use supplier in purchase invoices/orders
- Read supplier history/balance where exposed

## Entry points

- `feature:party` supplier/party presentation
- Party role use cases
- invoice purchase-cycle consumers

## Business rules

- Supplier is a role/profile on shared Party identity.
- Supplier/customer duplication must not create fake feature-module boundaries.

## Domain model

`clients` identity plus `party_roles` and `supplier_profiles`.

## Data ownership

Canonical business ownership: `feature:party`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room Party identity/role/supplier-profile tables.

## Server dependencies

Party/client sync surfaces; purchase-cycle server surfaces when supplier participates in purchases.

## Permissions

Party/purchase permissions apply through consuming feature contracts.

## Offline behavior

Party profile reads are Room-backed; remote synchronization requires network.

## Sync behavior

Party outbox/conflict/unified sync ownership.

## Validation

Role/profile identifiers and organization scope are validated by Party/repository boundaries.

## Error states

Role migration/conflict records preserve unresolved state.

## Notifications/events

Not applicable as a supplier-specific transport contract.

## Critical invariants

- One party may have multiple roles.
- Supplier ownership remains `feature:party`.

## Testing notes

Party role/migration tests/evidence.

## Known limitations

No independent supplier Gradle module exists.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/party/src/main/kotlin/com/verto/app/feature/party/application/PartyRoleUseCases.kt`
- `data/database/src/main/kotlin/com/verto/app/data/local/entity/PartyEntities.kt`
