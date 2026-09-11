---
status: canonical
scope: feature
owner: "feature:auth"
last_verified_against: v315
---
# Authentication

## Purpose

Sign-in, sign-up, organization entry, password management and trusted session bootstrap.

## User workflows

- Login and restore an existing session
- Create a new organization/admin account
- Join an organization using an invite code
- Change/reset/set a password
- Logout and revoke the device push token

## Entry points

- `feature:auth` presentation screens/view models
- `AuthGateway` / `AuthSessionCoordinator`
- `AuthRepository` and Supabase Auth adapters

## Business rules

- Login/register validation policies run before submission.
- A usable authenticated profile must carry a valid organization ID.
- Push-token revocation is attempted before sign-out.

## Domain model

`AuthenticatedUser`, invite details, Supabase `AppUserDto`, session role/permissions.

## Data ownership

Canonical business ownership: `feature:auth`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Session/preferences cache; no Room business table is owned by `feature:auth`.

## Server dependencies

Supabase Auth; `app_users`; create/join/invite/member-management RPCs.

## Permissions

Profile/organization and admin/employee permission paths are resolved after authentication.

## Offline behavior

Existing cached session/local data may be readable, but fresh sign-in/sign-up/reset/join requires server access.

## Sync behavior

Successful/restored auth prepares organization-scoped sync and cancels stale Realtime/Optimal work.

## Validation

Email/password/phone/org fields are validated by auth policies and server/profile checks.

## Error states

Distinguishes validation/auth/network/profile-missing/profile-verification failures.

## Notifications/events

FCM token upload is triggered after completed authentication.

## Critical invariants

- A trusted organization ID is required before organization-scoped work.
- Logout does not sign out before attempting token revocation.

## Testing notes

Auth validation policy tests and integration adapters cover client validation/session sequencing.

## Known limitations

Server definitions for several membership/invite RPCs are absent from repository SQL.

## Related docs

- [Authentication API](../api/auth.md)
- [Security Boundaries](../architecture/security-boundaries.md)

## Evidence

- `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/AuthViewModel.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt`
- `app/src/main/kotlin/com/verto/app/feature/auth/integration/DefaultAuthSessionCoordinator.kt`
