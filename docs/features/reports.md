---
status: canonical
scope: feature
owner: "feature:reports"
last_verified_against: v315
---
# Reports

## Purpose

Business reporting/analytics read models and export presentation.

## User workflows

- Open report/analytics views
- Apply filters/date ranges
- Export/share supported reports

## Entry points

- report screens/view models/use cases
- report presentation/application ports
- app report bridge adapters

## Business rules

- Reports consume read models; they do not become the owner of source transactions.
- Permission checks must occur before exposing restricted report categories.

## Domain model

Report-specific domain/read-model DTOs derived from underlying Room/business sources.

## Data ownership

Canonical business ownership: `feature:reports`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

No single report table authority; sources are feature/business Room tables and caches.

## Server dependencies

No dedicated report RPC is required by the module graph; app bridges may consume repositories already backed by local data.

## Permissions

Report/app bridge permission rules and employee permissions gate access/export.

## Offline behavior

Reports can use locally available data; freshness depends on last successful sync/source updates.

## Sync behavior

Reports do not own sync; freshness comes from source domain synchronization.

## Validation

Filter/date/model validation is report-specific.

## Error states

Empty/stale/error read-model states are presentation concerns; server claims are not invented.

## Notifications/events

Not applicable.

## Critical invariants

- A report is a projection, not transaction authority.

## Testing notes

Report/analytics tests where present.

## Known limitations

Freshness is bounded by local source state and sync status.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/reports/src/main/kotlin/com/verto/app/feature/reports`
- `app/src/main/kotlin/com/verto/app/feature/reports/bridge`
