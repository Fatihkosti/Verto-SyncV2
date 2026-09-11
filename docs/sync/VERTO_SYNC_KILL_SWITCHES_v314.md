# Verto Sync Kill Switches — v314

Static/pre-cutover defaults are fail-safe. `SyncRolloutPolicy` is the single decision authority; manual, Worker and Realtime paths may not invent independent ownership.

| Flag | Scope | Default | Dependency | Safe-on prerequisite | Safe-off behavior | Correctness authority |
|---|---|---:|---|---|---|---|
| `V2_PULL` / `isV2PullEnabled` | global + environment + org allow/deny | OFF | V2 master | eligible wave and org | remain Legacy or V2_PAUSED_SAFE after V2 commit | server revision + opaque cursor |
| `V2_PUSH` / `isV2PushEnabled` | same | OFF | V2 master | eligible wave and org | preserve durable outboxes | idempotent mutation/receipt |
| `V2_FINANCIAL` / `isV2FinancialSyncEnabled` | sensitive registry slices | OFF | V2_PULL + V2_PUSH + Wave≥4 | stronger semantics runtime gate | V2_PAUSED_SAFE after committed V2 effect | financial stronger outbox |
| `V2_INVENTORY` / `isV2InventorySyncEnabled` | inventory-ledger slices | OFF | V2_PULL + V2_PUSH + Wave≥4 | stronger semantics runtime gate | V2_PAUSED_SAFE after committed movement/cost | inventory stronger outboxes |
| `REALTIME_HINTS` / `isRealtimeHintsEnabled` | org/session listener | OFF | V2_PULL + Wave≥5 + compatibility realtime flag | periodic/manual convergence proven | latency degrades; correctness unchanged | never correctness authority |
| `LEGACY_FALLBACK` / `isLegacySyncFallbackEnabled` | ownership slices | ON | may be OFF only at final gate | Wave6 + observation + old-client + zero-use | only legacy-owned slices may use it | ownership matrix |

Invalid combinations fail closed. Before any V2 write the slice remains `LEGACY_AUTHORITATIVE`; after a committed V2 write it becomes `V2_PAUSED_SAFE`, never blind Legacy replay.
