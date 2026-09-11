---
status: canonical
scope: feature
owner: "feature:organization"
last_verified_against: v315
---
# Employees

## Purpose

Organization team membership, employee permissions and performance/access management.

## User workflows

- List organization employees
- View/edit employee permissions
- Create/revoke invite code
- Deactivate/remove member
- Read employee performance

## Entry points

- organization team screens/view models
- `OrganizationTeamGatewayAdapter`
- `AuthRepository` organization access/invite sources

## Business rules

- No standalone employee module exists; ownership spans `feature:organization`, app bridges and network auth access.
- Missing employee permissions fail closed client-side.

## Domain model

`app_users`, `employee_permissions`, employee performance projections and invite metadata.

## Data ownership

Canonical business ownership: `feature:organization`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Permission/session cache plus any performance/read-model Room data; membership authority is server-backed.

## Server dependencies

`app_users`, `employee_permissions`, invite/member RPCs.

## Permissions

Admin/management gates and `PermissionProvider`; client checks are not server authorization proof.

## Offline behavior

Cached permissions/team data may be readable; membership/permission changes require server.

## Sync behavior

Organization sync participant handles selected settings/price-list/notification data; membership commands are direct server paths.

## Validation

Tenant IDs, target user IDs, admin/self-targeting rules and permission payloads.

## Error states

Permission denied, member/profile missing and network/RPC errors remain explicit.

## Notifications/events

Employee/audience notification RPCs can target direct/all/manager audiences.

## Critical invariants

- Employee changes must stay in current organization scope.
- Client admin gate does not replace server RLS/RPC authorization.

## Testing notes

Organization/auth tests and server evidence where available.

## Known limitations

Repository SQL for several invite/member RPCs is absent.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `feature/organization/src/main/kotlin/com/verto/app/feature/organization`
- `app/src/main/kotlin/com/verto/app/feature/organization/bridge/OrganizationTeamGatewayAdapter.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationAccessRemoteSource.kt`
