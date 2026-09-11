---
status: canonical
scope: system
owner: "data:network + application"
last_verified_against: v315
---
# Error Contract

| Class | Source boundary | Current handling |
|---|---|---|
| Local validation | feature validation policies, `require`/`check`, command validators | rejected before/around persistence/network; UI maps domain/input errors to user-visible messages |
| Auth | Supabase Auth sign-in/sign-up/update/reset | returned through `Result`/exceptions; current-password change explicitly maps failed re-authentication to a local message |
| Network/PostgREST | Supabase transport/decoding | propagated or wrapped by repository/feature gateway; cancellation is rethrown in retry wrappers |
| Server RPC | RPC response/error | exact server code taxonomy is **NOT VERIFIED** unless repository SQL exposes it; client must not invent HTTP/status semantics |
| Sync protocol | scope/cursor/contract/revision/aggregate validation | fail-closed, recovery/bootstrap, conflict/retry state or collected participant failure depending engine |
| Tenant/session | `TenantIsolationPolicy`, `SyncWorkScope`, missing profile/session | invalid/cross-tenant/stale scope rejected before commit where guards run |
| Permission | `PermissionProvider` and feature permission gates | denied client-side; permission denial may be audit-logged; server enforcement remains separate |
| Unknown server | uncategorized exception | retained as failure; never translated into a fabricated successful state |

## Retryability

Retryability is caller-specific. Push-token registration retries three times. Unified/domain outboxes persist retry metadata. Withdrawal commands re-read authoritative state after an RPC attempt so timeout is not automatically interpreted as failure or success. Direct writes without an explicit request/outbox identity are `retry safety NOT VERIFIED`.

## User-visible translation boundary

Presentation/view-model code owns user-facing strings. Transport code should retain diagnostic type/reason without exposing credentials or direct identifiers; `SensitiveDataRedactor` exists for log/crash boundaries.

## Evidence

- `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/LoginValidationPolicy.kt`.
- `feature/auth/src/main/kotlin/com/verto/app/feature/auth/presentation/RegisterValidationPolicy.kt`.
- `core/common/src/main/kotlin/com/verto/app/core/security/SecurityPolicies.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncReliability.kt`.
