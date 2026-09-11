---
status: canonical
scope: system
owner: "security + architecture"
last_verified_against: v315
---
# Verto Security Boundaries

## Boundary matrix

| Boundary | Client-enforced evidence | Server-enforced evidence status |
|---|---|---|
| Supabase Auth session | `currentUserOrNull/currentSessionOrNull`, sign-in/sign-up/sign-out, reset deep link | Supabase Auth service behavior is external; repository config proves client usage, not policy configuration |
| Current organization | `AppUserDto.organizationId`, `TenantIsolationPolicy.requireTenantId` | Per-table/RPC RLS/grants are verified only where repository SQL defines them; otherwise **NOT VERIFIED** |
| Cross-organization access | `TenantIsolationPolicy.requireSameTenant`; many PostgREST filters include `organization_id`/`org_id` | Client filters are not authorization; RLS enforcement must be proven independently |
| Employee permissions | `PermissionProvider`, feature permission rules/gates; missing employee permissions fail closed client-side | Server permission checks vary by RPC; **NOT VERIFIED** unless explicit SQL definition is cited |
| Admin operations | client role/permission gates and audit logging | Admin-only server enforcement is **NOT VERIFIED** for RPCs whose SQL definition is absent |
| Push token lifecycle | current user required to register; revoke attempted before sign-out | `register_push_token_v2`/`revoke_push_token_v2` SQL definitions are absent from repository, so server authorization is **NOT VERIFIED** |
| Sync scope | `SyncWorkScope`, profile revalidation, scope mismatch guards | v305 SQL defines trusted scope/bootstrap functions; live deployment evidence exists for v305-v313 migrations, but final V2 cutover runtime evidence remains not-run |
| Password recovery | client accepts `com.verto://reset-password` and imports Supabase deep-link session | Redirect allowlist/configuration on Supabase is external and not verified by Android source |

## Client security policies

`TenantIsolationPolicy` rejects blank/malformed organization/user identifiers and uses constant-time comparison for tenant equality. It also provides explicit admin and no-self-targeting guards. `PermissionProvider` gives admins full client permissions, decodes employee permissions from session cache, and rejects when employee permissions are unavailable.

These guards improve safety and UX but **must not be described as substitutes for RLS, grants or server-side authorization**.

## Server assumption boundary

A PostgREST call that contains `eq("organization_id", orgId)` proves query scoping intent only. It does not prove that a malicious client cannot omit/change the filter. A server function name such as `admin_*` likewise does not prove server authorization. [RPC Reference](../api/rpc-reference.md) therefore records server-definition presence separately from client usage.

Repository SQL is grouped by authority tier:

1. `supabase/migrations/**` — migration/evolution artifacts; deployment still requires runtime evidence.
2. `docs/sql/**` — reference/deployment/repair SQL retained with documentation history; presence does not prove application.
3. `sql/**` — manual/supporting SQL; presence does not prove application.

The retained `docs/archive/reports/Verto-v314-SQL-deployment-report.md` proves live application/testing of the v305/v309/v310/v312/v313 migration sequence, not every unrelated RPC in the client.

## Logout and token ordering

`AuthAccountRemoteSource.logout` attempts `PushTokenRepository.deleteCurrentUserToken()` before `client.auth.signOut()`. The comment and ordering are explicit: revocation may need the authenticated RLS context. Revocation failure is wrapped so sign-out still proceeds.

## Deep-link recovery

`VertoSupabase` configures Auth with scheme `com.verto` and host `reset-password`; `MainActivity` passes incoming intents to `handleDeeplinks` and navigates to the reset screen only after the session-import callback.

## Evidence

- `core/common/src/main/kotlin/com/verto/app/core/security/SecurityPolicies.kt` — `TenantIsolationPolicy`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/PermissionProvider.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseClient.kt`.
- `app/src/main/kotlin/com/verto/app/MainActivity.kt`.
- `docs/archive/reports/Verto-v314-SQL-deployment-report.md` — retained server deployment evidence.
