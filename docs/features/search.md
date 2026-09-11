---
status: canonical
scope: feature
owner: "feature:dashboard + contributors"
last_verified_against: v315
---
# Search

## Purpose

Home-wide search aggregation across feature-provided local search providers.

## User workflows

- Enter Home search query
- Receive grouped/typed results
- Navigate to owning feature result

## Entry points

- `UnifiedHomeSearchUseCase`
- `HomeSearchProviderRegistry`
- provider implementations from inventory/invoice/party/payment

## Business rules

- Search is an aggregation system, not a single owning feature database.
- Each provider owns its result semantics/navigation.

## Domain model

Dashboard API search contracts and feature-specific result models.

## Data ownership

Canonical business ownership: `feature:dashboard + contributors`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Provider queries read owning feature data, primarily Room.

## Server dependencies

No direct server search RPC is required by the aggregation path.

## Permissions

`HomePermissionContext`/provider rules can suppress results the current user cannot access.

## Offline behavior

Search works on locally available provider data; freshness equals local data freshness.

## Sync behavior

Search does not sync; source features do.

## Validation

Query normalization/minimum criteria are owned by unified use case/providers.

## Error states

A provider must not fabricate data; provider behavior remains locally bounded.

## Notifications/events

Not applicable.

## Critical invariants

- No `feature:search` module exists.
- Provider result ownership remains with the contributing feature.

## Testing notes

Dashboard/provider tests where present.

## Known limitations

Results are local and can be stale until source sync completes.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/UnifiedHomeSearchUseCase.kt`
- `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/HomeSearchProviderRegistry.kt`
- `feature/inventory/src/main/kotlin/com/verto/app/feature/inventory/application/search/InventoryHomeSearchProvider.kt`
