# B02.02–B02.03 — live/source comparison

## Confirmed alignment

The active Android unified path names match the six live RPCs:

- `UnifiedSyncBootstrapRemote.kt`: scope resolution, bootstrap begin/page, reconciliation manifest.
- `UnifiedSyncPullRemote.kt`: scope resolution and unified pull.
- `UnifiedSyncPushRemote.kt`: unified mutation apply.

The live catalog proves exact definitions and hashes in `SYNC_SERVER_DEFINITIONS/functions.json`; this is stronger than comparing names alone. The server contract remains expand-only and pruning is disabled.

## Confirmed drift

1. `SyncProtocolV2Remote.kt` still names `sync_pull_v2` and `sync_ack_v2`; neither function exists live. Reachability and ownership must be proved in its implementation session before removal or replacement. It is not safe to infer that this class is dead.
2. The source contains 30 migration SQL files and ends at `20260909095614_m08_sync_v2_migration_state.sql`. Live history contains 214 entries and ends at `20260909194457`; the two post-M08 live migrations are absent locally:
   - `20260909194336 / verto_authenticated_backup_archive_recovery`
   - `20260909194457 / verto_compressed_backup_archive_payload`
3. The older audited gap remains: historical live migrations, including v391–v419-era definitions, are not fully materialized in this source. `evidence/m02/reports/SYNC_V2_M02_SERVER_CONTRACT_AUDIT.md` already recorded 23 live records without local SQL and no clean-rebuild proof.
4. Version `20260821062000` is named `v305_verto_unified_sync_server.sql` locally but `v305_verto_unified_sync_server_scope_ambiguity_repaired` in live history. Name equality cannot be used as definition parity.

Consequences: live catalog inspection is reproducible, but local migration replay is not a complete source of truth. No SQL may be promoted from this tree as a live repair until an isolated clean rebuild succeeds from an authoritative baseline.

## Effect/owner map established here

| Effect | Server authority / evidence | Receipt and scope boundary | Status |
|---|---|---|---|
| Unified mutation idempotency | `verto_apply_sync_mutation` + immutable `verto_sync_receipts` | receipt carries organization/scope/principal/request hash; function resolves authenticated scope | Confirmed live definition |
| Financial invoice/payment facts | `financial_sync_events`; registry names stronger server authorities | own-org authenticated SELECT policy; writes remain server-authoritative | Confirmed surface; end-to-end adversarial test deferred |
| Inventory movements | `inventory_movements` + reconciliation guard | four authenticated RLS policies | Confirmed surface; broad `anon` table grants require adversarial RLS verification before approval |
| Inventory cost revisions | `inventory_cost_revisions` | RLS enabled, no client policy, service-role grants only | Confirmed server-only surface |
| Commission payment | bootstrap coverage owner session 400 and stronger registry entry | unified receipt/scope plus canonical server producer | Confirmed registry; business-effect test deferred |
| Pull/bootstrap/reconciliation | six unified RPCs + 35 bootstrap coverage rows | scope resolved from authenticated principal; no org parameter accepted by resolver | Confirmed catalog contract |

This map identifies the current live owner surfaces without asserting T01–T50 PASS. The `inventory_movements` grant/policy combination is a review target, not a declared exploit: RLS is enabled and the policies target authenticated roles, but an adversarial anon/authenticated test is still required.
