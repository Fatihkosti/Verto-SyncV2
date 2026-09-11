---
status: canonical
scope: system
owner: "architecture"
last_verified_against: v315
---
# Verto Data Flow

Each flow below separates local authority, transport, durable state and server dependency.

| Flow | Trigger / entry | Local boundary | Durable/outbox state | Network/server boundary | Retry / failure | Final local authority |
|---|---|---|---|---|---|---|
| Authentication/session | Login/register/join/splash | `AuthViewModel` → `AuthGateway`/session coordinator | Session/preferences cache after successful profile resolution | Supabase Auth + profile/RPC calls | Network/auth failure remains an auth error; session restoration distinguishes missing profile from verification failure | Session contracts plus validated profile/organization |
| Normal Room-backed read | Feature screen/observer | Feature repository/DAO | Room | none for the read itself | Stale/missing local data remains local until sync | Room |
| Local write | Feature coordinator/repository | Feature validator/coordinator → DAO | Room transaction; some paths also enqueue outbox | later sync or direct server path depending feature | Local transaction failure aborts local write | Room after commit |
| Outbox-backed write | Inventory/party/unified registered mutation | Owning feature/write adapter | domain outbox or `sync_outbox` before wake | V2/legacy push transport | retry metadata persists; request generation is durable | Room + durable outbox until ACK/materialization |
| Direct server/RPC command | Payment, membership, notification, withdrawal, allocation, etc. | Feature/app gateway → `:data:network` | feature-specific local reconciliation may follow | PostgREST RPC | caller-specific; some reconcile after timeout or retry | Room only after caller materializes/reconciles result; server is command authority |
| Unified sync pull | durable generation/manual/periodic/Reatime request | `SyncManager` → `UnifiedSyncPullEngine` | `sync_cursor`, inbox/recovery state | `verto_resolve_sync_scope`, `verto_pull_sync_changes` | bootstrap/recovery on invalid cursor/scope; continuation when more work remains | Room after validated apply and cursor commit |
| Unified sync push | drain generation | `UnifiedSyncPushEngine` + registry | immutable `sync_outbox` and domain stronger-source records | `verto_apply_sync_mutation` and stronger bridge RPCs | server conflict/retry/ack decisions are persisted | Room/outbox state until authoritative ACK |
| Realtime sync hint | insert on `public.verto_sync_realtime_hints` | `SupabaseOrganizationRealtimeSource` → `RealtimeManager` | only resulting sync generation is durable; coalescer is memory-only | Supabase Realtime | connect/listener failure falls back to periodic/manual durable sync | No business truth in Realtime; Room after later pull |
| Notification | app/system event or server notification | notification gateway/repository/Room cache | `notifications` Room cache | notification RPCs + optional `send-notification-fcm` Edge Function | FCM send failure is logged separately; cache can be refreshed | Room notification cache for UI; server record remains remote authority |
| Search aggregation | Home search input | `UnifiedHomeSearchUseCase` → provider registry | provider-local Room reads | none required for aggregation | provider omission/failure must not fabricate results | Aggregated read model from local providers |
| Logistics operation | shipment planning/execution/receiving/recovery UI | shipment use cases + app bridge adapters | logistics Room entities, documents in app-private storage, inventory/cash effects via ports | shipment-number RPC; legacy Logistics V2 remote transport is hard-disabled until verified | validation/state transition failures abort operation; sync handles eligible persisted changes | Room/logistics entities |
| Financial posting | invoice/payment/cash action | invoice/payment coordinators and financial posting gateway | invoices/payments/allocations, financial outbox/inbox where used | `post_payment_v2`, `reverse_payment_v2`, financial sync RPCs | request IDs/outbox retry where code supplies them; unknown server failures remain explicit | Room financial state after authoritative local transaction/reconciliation |

## Ordering constraints

- `AuthAccountRemoteSource.logout` attempts FCM token revocation **before** `signOut`, because the authenticated server context may be required for revocation.
- `SyncManager.requestSync` persists the orchestration generation **before** calling `SyncWorker.wakeNow`; a wake failure does not roll back the generation.
- Realtime never commits business data/cursors; it only requests a durable sync generation.
- Logistics V2 `SyncLogisticsV2` is guarded by `SERVER_CONTRACT_VERIFIED = false`; its direct remote table loop is not an active authority by default.

## Evidence

- `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/SyncManager.kt` — `requestSync`, `drainOrchestration`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/pull/UnifiedSyncPullEngine.kt`.
- `data/sync/src/main/kotlin/com/verto/app/data/sync/push/UnifiedSyncPushEngine.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/remote/SupabaseOrganizationRealtimeSource.kt`.
- `feature/dashboard/src/main/kotlin/com/verto/app/feature/dashboard/application/search/UnifiedHomeSearchUseCase.kt`.
- `data/network/src/main/kotlin/com/verto/app/data/sync/SyncLogisticsV2.kt`.
