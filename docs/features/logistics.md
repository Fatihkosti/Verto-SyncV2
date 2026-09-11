---
status: canonical
scope: feature
owner: "feature:shipment"
last_verified_against: v315
---
# Logistics

## Purpose

Shipment/logistics planning, legs/milestones/custody, receiving, shortages/recovery, costs and documents.

## User workflows

- Plan shipment and sources
- Track legs/milestones/custody
- Record receiving/shortage/recovery
- Allocate landed/late costs
- Manage logistics documents/routes/customs

## Entry points

- `feature:shipment` screens/use cases
- app logistics bridge adapters
- logistics Room DAOs/entities

## Business rules

- Actual module is `feature:shipment`; canonical feature page is named logistics.
- Remote Logistics V2 table transport is hard-disabled until server contract verification.
- Receiving/recovery/cost operations use explicit ports to inventory/cash/payment boundaries.

## Domain model

24+ logistics Room tables covering shipment, lines, legs, events, receiving, costs, shortages, recovery, templates and customs.

## Data ownership

Canonical business ownership: `feature:shipment`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room logistics entities; app-private document storage for logistics files.

## Server dependencies

`allocate_logistics_shipment_number`; 24 dynamic Logistics V2 PostgREST table contracts, currently hard-gated OFF.

## Permissions

Shipment presentation/application permission ports plus consuming inventory/payment permissions.

## Offline behavior

Most planning/execution state is Room-backed. Server number allocation and any future-enabled Logistics V2 transport require connectivity.

## Sync behavior

Shipment sync participant plus app `LogisticsV2SyncAdapter`; hard gate prevents unverified remote activation.

## Validation

State transitions, organization ownership, document/receiving/recovery invariants in use cases/adapters.

## Error states

Invalid transitions/cross-org rows/disabled server contract fail instead of being silently accepted.

## Notifications/events

Shipment activity/pending actions can feed Home/notification boundaries.

## Critical invariants

- `SyncLogisticsV2` rejects cross-organization materialization.
- `SERVER_CONTRACT_VERIFIED` is false in v315.
- Recovery/receiving lines must remain within owning shipment/organization scope.

## Testing notes

Extensive shipment module tests; v316 only documents current source.

## Known limitations

Remote logistics table definitions may exist as packaged SQL but are intentionally not claimed active by the client gate.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/shipment/src/main/kotlin/com/verto/app/feature/shipment`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/dto/LogisticsV2Dtos.kt`
