---
status: canonical
scope: system
owner: "data:sync + data:network"
last_verified_against: v314
---
# Verto Sync Cutover Policy — v314

Authority: `SyncRolloutPolicy` + `SyncRolloutSnapshot` + registry sensitivity. Ownership states are exactly: `LEGACY_AUTHORITATIVE`, `V2_SHADOW_READ`, `V2_AUTHORITATIVE`, `V2_PAUSED_SAFE`, `RETIRED_LEGACY`. Dual authoritative writers are forbidden.

Waves advance only 0→1→2→3→4→5→6. Wave 2 shadow evidence is digest/count metadata only; `UnifiedSyncShadowComparator` has no Room, cursor, outbox or remote-mutation dependency. Waves 3/4 split ownership from `UnifiedSyncAggregateRegistry.financialSensitivity`; no duplicate hard-coded financial aggregate list is authoritative.

Static v314 deliberately remains Wave 0 with all V2/Realtime switches OFF and Legacy fallback ON. Final cutover requires runtime evidence for Room 80→81, v313 PostgreSQL/bootstrap/recovery, two clients, RLS, process death, timeout-after-commit, Realtime parity, staged waves and observation.

Rollback after committed V2 financial/inventory effects is `V2_PAUSED_SAFE + recovery/reconciliation`, not Legacy dirty replay.
