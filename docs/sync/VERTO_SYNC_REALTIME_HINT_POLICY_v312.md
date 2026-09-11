# Verto Realtime Hint Policy — v312

Realtime carries acceleration hints only. The accepted shape remains `SyncRealtimeHint(organizationId, aggregateType?, aggregateId?, serverRevision?)`.

Normalization:
- wrong organization/user/session/generation: drop with no durable request.
- partial or unknown aggregate target: discard target metadata and request normal organization-wide revision pull.
- missing revision: normal revision pull; no timestamp fallback.
- duplicate targets: set-deduped.
- out-of-order revisions: keep only the maximum positive revision as advisory metadata.
- target budget: 32 unique `(aggregateType,aggregateId)` pairs. Overflow clears targets and degrades to normal pull.

Coalescing uses a 250 ms trailing-edge window only to reduce wakes. It never orders data. One accepted batch calls `SyncManager.requestSync(scope, REALTIME)`, which persists the v311 generation before WorkManager wake. If target metadata disappears on process death, that durable generation still drives normal unified pull.

`serverRevision` is never written into `SyncCursorEntity`, never reconstructs `cursorToken`, and never forces a chase loop. `UnifiedSyncPullEngine` remains the sole cursor/apply authority and may declare the visible scope caught up even when an advisory target is not visible.

The v312 server migration projects only revision, organization id, aggregate type and aggregate id from the append-only change log. It contains no business payload and uses an explicit `supabase_realtime` table allowlist with RLS visibility checks. PostgreSQL application is not claimed by static verification.
