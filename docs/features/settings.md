---
status: canonical
scope: feature
owner: "feature:settings"
last_verified_against: v315
---
# Settings

## Purpose

Appearance, printing and operational/settings workflows with local versus organization-backed settings separation.

## User workflows

- Change theme/font appearance
- Configure printing/templates
- View/modify allowed operational settings
- Backup/settings operations through app gateway

## Entry points

- settings screens/view models
- settings domain repository contracts
- app settings bridge adapters

## Business rules

- Feature contracts do not own persistence implementation.
- Management-restricted settings fail closed when permission is absent.

## Domain model

Appearance/printing/settings models plus `organization_settings_local` / remote `organization_settings` where applicable.

## Data ownership

Canonical business ownership: `feature:settings`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Preferences/DataStore plus Room organization-settings local cache.

## Server dependencies

Direct `organization_settings` read/upsert where organization settings are synchronized.

## Permissions

`SettingsOperationsGatewayAdapter` uses management permissions and audits denied actions.

## Offline behavior

Appearance/printing local preferences work offline; organization/server operations require sync/network as applicable.

## Sync behavior

Organization settings can participate in sync; local UI preferences are device-local.

## Validation

Preference enums/ranges and organization/permission checks.

## Error states

Permission/storage/backup/server errors remain at owning gateway boundary.

## Notifications/events

Not applicable.

## Critical invariants

- Device-local appearance settings must not be conflated with server organization settings.

## Testing notes

Settings view-model/repository tests where present.

## Known limitations

Server organization-setting enforcement depends on server RLS/policies not fully reconstructed for every table.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/settings/src/main/kotlin/com/verto/app/feature/settings`
- `app/src/main/kotlin/com/verto/app/feature/settings/bridge/SettingsOperationsGatewayAdapter.kt`
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncMisc.kt`
