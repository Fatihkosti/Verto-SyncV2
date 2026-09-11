---
status: canonical
scope: system
owner: "architecture"
last_verified_against: v334
---
# Verto Architecture Overview

## System identity

Verto is a native Android ERP application implemented as a 31-module Gradle modular monolith. Jetpack Compose is used for presentation, Room is the durable on-device database, Supabase provides Auth/PostgREST/Realtime/Storage/Functions integration, and WorkManager is the durable background wake mechanism for synchronization. The current Room authority is schema **82** (`MigrationCatalog.ROOM_SCHEMA_VERSION`).

The architecture is not documented as an idealized layer diagram. `:app` directly depends on most feature and data modules, `:data:operations` depends on several feature modules, and `:data:network` depends on Room/preferences. Those couplings are part of the current production truth and are enumerated in [Module Map](module-map.md).

## Runtime boundaries

| Boundary | Current responsibility | Authority |
|---|---|---|
| Compose / feature presentation | Screens, view models, UI validation and permission-gated interactions | Feature modules plus `:app` navigation/composition |
| Feature application/domain | Business workflows, coordinators, policies, ports and invariants | Owning `:feature:*` module |
| Cross-feature operations | Transactions/adapters spanning multiple feature contracts | `:data:operations` and `:app` bridges |
| Local persistence | Room entities, DAOs, schema/migrations | `:data:database` |
| Local settings/session cache | Preferences/DataStore-backed values behind shared contracts | `:data:preferences` and `:app` session adapters |
| Network integration | Supabase Auth, PostgREST RPC/table access, Realtime, Storage, Functions | `:data:network` plus limited integration-feature adapters |
| Durable sync orchestration | WorkManager wake, generations, outbox/inbox, pull/push, recovery, reconciliation | `:data:sync` |
| Server | PostgreSQL/Supabase behavior actually represented by repository SQL or retained runtime evidence | Server; client code is not server authority |

## Data authority

For Room-backed business domains, UI reads should be understood as local-state reads; network transport is not automatically the local Source of Truth. Unified sync writes durable orchestration/outbox/inbox/cursor state in Room and materializes accepted server changes into local tables. Realtime sync events are **hints only** and cannot mutate Room or advance a cursor by themselves.

Some commands remain direct server/RPC operations. These require server availability and must not be described as offline-complete merely because surrounding screens are Room-backed. See [Data Flow](data-flow.md) and [API Reference](../api/README.md).

## Offline and server-required behavior

- Room-backed reads remain available when the required local data is present.
- Local write behavior is feature-specific: some writes persist locally and enqueue sync/outbox work; some financial, membership, notification, commission, withdrawal and allocation commands call server RPCs directly.
- Authentication, registration/join, password reset, server-generated identifiers, and direct RPC commands require Supabase/server access.
- Periodic/manual durable synchronization can converge without Realtime; Realtime only accelerates the decision to request a durable sync generation.

## Session and organization scope

Supabase Auth identifies the current user. The current application profile supplies `organizationId`; `TenantIsolationPolicy` rejects blank/invalid tenant IDs and cross-tenant mismatches. Session state is exposed through `core:session` contracts and persisted by app/preferences implementations. Sync work captures `SyncWorkScope(organizationId, userId, sessionEpoch)` and validates the scope before committing work.

Client-side tenant guards are not equivalent to server authorization. RLS/grants are server concerns and are documented as verified only where repository SQL or retained runtime evidence supports the claim. See [Security Boundaries](security-boundaries.md).

## WorkManager and Realtime

`SyncWorker` is the background wake path for durable synchronization. `SyncManager.requestSync` commits an orchestration generation before waking WorkManager. `RealtimeManager` coalesces organization-scoped hints and then requests the same durable generation; hint metadata is discardable across process death.

Messaging uses a separate Realtime surface for `conversations` and `internal_messages`; it is not the unified-sync hint channel.

## Related docs

- [Module Map](module-map.md)
- [Data Flow](data-flow.md)
- [Sync Architecture](sync-architecture.md)
- [Security Boundaries](security-boundaries.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `settings.gradle.kts` — 31 included modules.
- `data/database/src/main/kotlin/com/verto/app/data/local/MigrationCatalog.kt` — `ROOM_SCHEMA_VERSION = 82`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseClient.kt` — installed Supabase capabilities and reset-password deep link.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt` — orchestration and durable request generation.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt` — hint-only acceleration.
- `core/common/src/main/kotlin/com/verto/app/core/security/SecurityPolicies.kt` — tenant isolation policy.
