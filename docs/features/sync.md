---
status: canonical
scope: feature
owner: "data:sync"
last_verified_against: v315
---
# Sync

## Purpose

User-visible synchronization behavior and expectations; internals are owned by architecture docs.

## User workflows

- Automatic/periodic sync wake
- Manual sync
- Initial recovery/bootstrap when local state is empty/inconsistent
- Optional Realtime acceleration when rollout permits

## Entry points

- `SyncViewModel`
- `SyncManager.requestOrRun/requestSync`
- `SyncWorker`

## Business rules

- Realtime is never truth.
- Durable generation is written before wake.
- Rollout policy owns Legacy/V2 routing.

## Domain model

User-visible sync status/report plus durable orchestration state.

## Data ownership

Canonical business ownership: `data:sync`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room sync state and business tables.

## Server dependencies

Legacy/direct sync plus unified scope/pull/push/bootstrap RPCs.

## Permissions

Requires a current trusted authenticated profile/scope.

## Offline behavior

Offline writes remain pending where outbox-backed; convergence resumes on later successful sync.

## Sync behavior

See [Sync Architecture](../architecture/sync-architecture.md) for internal engine detail.

## Validation

Current profile, `SyncWorkScope`, rollout snapshot, cursor/contract/revision checks.

## Error states

Retry/collected failure/recovery/auth-blocked/continuation states; UI must not report NOT RUN as PASS.

## Notifications/events

Sync may trigger local notification refresh/work; Realtime hint itself is not a user notification.

## Critical invariants

- A stale session scope cannot commit work.
- Cursor changes belong to durable pull/apply, not Realtime.

## Testing notes

Static/model sync evidence exists through v314; final runtime cutover evidence remains not-run.

## Known limitations

Default V2 and Realtime flags are OFF; Legacy fallback ON.

## Related docs

- [Sync Architecture](../architecture/sync-architecture.md)
- [Idempotency](../api/idempotency.md)

## Evidence

- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt`
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt`
- `core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt`
