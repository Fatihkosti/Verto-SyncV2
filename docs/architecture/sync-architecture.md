---
status: canonical
scope: system
owner: "data:sync + data:network"
last_verified_against: v315
---
# Verto Sync Architecture

## Current authority model

**Truth:** validated Room state for local feature reads, plus server revision/contract for remote convergence.  
**Transport:** Supabase PostgREST RPC/table calls and optional Realtime hints.  
**Durable client state:** Room business tables, domain outboxes, unified `sync_outbox`/`sync_inbox`/cursor/conflict/recovery/bootstrap/health tables, and selected preferences/session epoch.  
**Hint-only:** Realtime hint records and in-memory coalescer targets/revisions.  
**Process-death survival:** Room orchestration/outbox/cursor/recovery state and WorkManager work; Realtime coalescer metadata does not survive and is not required for correctness.

## Orchestration

`SyncManager` is the coordinator. It gathers `SyncParticipant` operations for legacy/stronger producers, validates `SyncWorkScope`, persists orchestration generations, invokes unified push/pull engines, delegates recovery, and publishes observable run state. It deliberately does not own feature DAOs/DTOs directly.

`SyncWorker` is the WorkManager execution/wake boundary. `SyncWorkScope` carries organization, user and session epoch so stale work can be rejected. `SyncReliability` centralizes operation execution/retry classification for participant operations.

## Unified push

`UnifiedOutboxWriter` persists immutable mutation intent in `sync_outbox`. `UnifiedSyncPushEngine` loads eligible entries through `UnifiedSyncPushRegistry`, selects ownership using `SyncRolloutPolicy`, and calls the registered remote transport. `UnifiedSyncConflictEngine` classifies server outcomes/conflicts. Stronger sources bridge existing domain outboxes/financial/inventory producers before generic outbox drain where configured.

The generic remote endpoint is `verto_apply_sync_mutation`. Idempotency identity is carried by the unified mutation contract; domain-specific financial/inventory paths also have their own request/outbox identities.

## Unified pull

`UnifiedSyncPullEngine` resolves trusted scope and pulls versioned pages. `UnifiedSyncPullRegistry` maps aggregate types to local appliers. `UnifiedSyncInboxMapper`/change appliers validate materialization before advancing local state. Cursor ownership belongs to the durable pull path, not Realtime.

`verto_pull_sync_changes` and the scope contract are repository-defined by the v305 server migration. The current runtime rollout remains separate from the existence of those definitions.

## Bootstrap, recovery and reconciliation

`UnifiedSyncRecoveryEngine` detects required recovery reasons and coordinates bootstrap/snapshot replacement. `UnifiedSyncReconciliationEngine` compares registered aggregate state using server manifests/digests. Bootstrap RPCs include `verto_begin_sync_bootstrap`, `verto_pull_bootstrap_page` and `verto_get_reconciliation_manifest`. Recovery continuation is scheduled through WorkManager when one invocation cannot finish safely.

## Realtime

`SupabaseOrganizationRealtimeSource` subscribes only to `public.verto_sync_realtime_hints` inserts filtered by `organization_id`. The payload carries only advisory organization/aggregate/revision metadata. `RealtimeManager` validates current lifecycle/session scope, coalesces hints for 250 ms, and requests a durable sync generation. Missing, duplicate or out-of-order metadata degrades to an organization-wide durable pull request.

Messaging Realtime is separate: `SupabaseMessagesRealtimeSource` observes `conversations` and `internal_messages` for chat refresh/message delivery. It must not be cited as unified-sync authority.

## Rollout / legacy boundary

`SyncRolloutPolicy` defines seven waves (`0..6`) and aggregate ownership states. In the v315 source defaults from `FeatureFlags` are:

- versioned sync: **OFF**
- V2 pull: **OFF**
- V2 push: **OFF**
- V2 financial: **OFF**
- V2 inventory: **OFF**
- Realtime sync/hints: **OFF**
- legacy fallback: **ON**
- rollout wave: **0**
- environment: **PRODUCTION**

Therefore repository capability is not the same as active runtime cutover. The existing [Sync Cutover Policy](../sync/VERTO_SYNC_CUTOVER_POLICY_v314.md) remains the canonical rollout-state owner.

`SyncLogisticsV2` is additionally hard-gated by `SERVER_CONTRACT_VERIFIED = false`, so calling `activateAfterVerifiedServerContract` fails until source is deliberately changed in a future runtime/server session.

## Organization scope and idempotency

Every V2 work item is tied to `SyncWorkScope`; `SyncManager.validateScope` rechecks the active profile/session. Unified outbox identity plus server mutation request identity prevents treating a wake/retry as a new logical mutation. Where domain RPC idempotency cannot be proven from source/SQL, [Idempotency](../api/idempotency.md) labels it `NOT VERIFIED` rather than assuming safety.

## Failure and retry surfaces

- participant failures are classified by `SyncFailureMode`/reliability executor;
- outbox rows retain retry eligibility/backoff metadata;
- pull can return caught-up/more/bootstrap/recovery outcomes;
- recovery can return ready/continuation/auth-blocked;
- Realtime failures do not block periodic/manual convergence;
- invalid rollout combinations fail closed to legacy or V2-paused-safe ownership depending whether V2 has already committed.

## Evidence

- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorker.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncWorkScope.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/UnifiedOutboxWriter.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncConflictEngine.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncRecoveryEngine.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/recovery/UnifiedSyncReconciliationEngine.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/RealtimeManager.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseOrganizationRealtimeSource.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/rollout/SyncRolloutPolicy.kt`.
- `core/common/src/main/kotlin/com/verto/app/utils/FeatureFlags.kt`.
