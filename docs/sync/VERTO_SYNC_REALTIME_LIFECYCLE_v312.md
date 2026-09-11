# Verto Realtime Lifecycle — v312

`RealtimeManager` owns an opaque monotonic listener generation and one subscription id bound to the trusted `SyncWorkScope(org,user,sessionEpoch)`.

State machine: `STOPPED → STARTING(g) → ACTIVE(g,scope,subscription) → CLOSING(g) → STOPPED`. A newer `start` changes the generation first, making the prior lifecycle `REPLACED/STALE`. Optional channel failure is `FAILED_OPTIONAL` and never a sync-correctness failure.

Legal behavior:
- start/start: only the latest generation may install/accept a listener.
- stop/start: the old subscription id is closed independently; `stop(oldId)` cannot remove new-id channels.
- late callback: rejected unless generation, subscription id, organization, user and session epoch all still match.
- logout/org/user switch/reauth: generation invalidation makes old callbacks no-ops; trusted scope is re-read before durable intent.
- source cancellation rethrows `CancellationException`; optional source failures preserve periodic/manual fallback.

The source never calls a global `stop()` that clears every remover. Removers are partitioned by subscription id.
