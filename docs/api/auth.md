---
status: canonical
scope: system
owner: "feature:auth + data:network"
last_verified_against: v315
---
# Authentication / Session Contract

## Supported client flows

- Email/password sign-in: `AuthAccountRemoteSource.login` → `client.auth.signInWith(Email)` → current profile lookup.
- Sign-up with new organization: Supabase Auth sign-up, then `create_organization_with_admin` RPC, then local session/profile settings.
- Join existing organization: invite lookup/validation, Auth sign-up, then `join_organization_with_code` RPC.
- Password change: re-authenticate current email with current password, then `auth.updateUser { password = newPassword }`.
- Password reset email: `resetPasswordForEmail(..., redirectUrl = "com.verto://reset-password")`.
- Deep-link recovery: `VertoSupabase.handleDeeplinks` imports the recovery session and `MainActivity` routes to reset-password UI.
- Set new password: `auth.updateUser` after the recovery session is imported.
- Current session/user: `currentSessionOrNull` / `currentUserOrNull`; profile is read from `app_users`.
- Organization/team access: profile `organizationId`, `employee_permissions`, invite/admin RPCs.
- Logout: revoke current-device push token first, then Supabase Auth `signOut`.

## Session writer updates

After successful registration/join/profile resolution, the client writes user/organization-adjacent state through `SessionWriter`/preferences. `DefaultAuthSessionCoordinator` stops stale Realtime, prepares sync for the organization, schedules Optimal sync, uploads FCM token, and refreshes role/permissions.

## Tenant validation

`getMyProfile` / `fetchActiveProfile` validate the returned `organizationId` through `TenantIsolationPolicy`. `fetchActiveProfile` intentionally distinguishes a successful query with no row (member removed / no profile) from a network/query failure.

## Server verification limits

Client flows are verified. Server definitions for `create_organization_with_admin`, `join_organization_with_code`, invite admin RPCs and push-token RPCs are not present in repository SQL unless separately listed in [RPC Reference](rpc-reference.md); their server authorization/transactions must therefore not be claimed.

## Evidence

- `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationAccessRemoteSource.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseClient.kt`.
- `app/src/main/kotlin/com/verto/app/MainActivity.kt`.
- `app/src/main/kotlin/com/verto/app/feature/auth/integration/DefaultAuthSessionCoordinator.kt`.
