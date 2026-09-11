# Verto Sync Bootstrap Policy — v313

1. Resolve trusted server scope; missing/corrupt/expired cursor creates durable recovery obligation.
2. Begin one trusted server handshake returning snapshot session, expected row count, opaque baseline cursor, and diagnostic baseline revision.
3. Validate and persist every page to `sync_bootstrap_stage`; no domain mutation occurs while paging.
4. Resume from durable token after process death; expired sessions discard only their stage and restart without reusing baseline cursor.
5. Cutover only after `unique staged rows == expected rows` and snapshot-complete evidence.
6. One Room transaction materializes the authoritative snapshot, applies allowlisted absence-pruning, preserves pending local overlays/outboxes, installs the opaque cursor, and marks READY.
7. Snapshot materialization uses `SyncSnapshotMaterialization`, never fake `SyncChange`, never inbox replay, never business command replay. Financial/inventory writers remain protected.
8. Pending local intent retains its original durable identity; recovery freezes network push only while mirror authority is unresolved.
9. Baseline revision is diagnostic. Timestamps and local max revisions never construct/resume a cursor.
10. V2 and Realtime remain default OFF until v314 runtime evidence.
