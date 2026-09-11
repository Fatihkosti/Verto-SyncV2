# Live sync server definition export

Read-only catalog capture from Supabase project `Verto-app` (`madkfvggyolmdberzmtb`) on 2026-09-10. No business rows, organization identifiers, auth identities, or secrets are included.

- `functions.json`: exact `pg_get_functiondef`, identity arguments, result type, owner, `SECURITY DEFINER`, function config, ACL, and definition MD5 for the six unified sync RPCs. The requested legacy names `sync_pull_v2` and `sync_ack_v2` were included in the query but returned no live functions.
- `tables.json`: columns/defaults/types, constraints, indexes, triggers, RLS policies, grants, and owners for all `public.verto_sync_%` tables plus `financial_sync_events`, `inventory_movements`, and `inventory_cost_revisions`.
- `contract-and-migrations.json`: complete live migration version/name history, active sync contract configuration, and global rollout control. It intentionally excludes organization-scoped rollout rows.
- `ownership-and-coverage.json`: server stronger-adapter registry and all bootstrap coverage rows; these are contract/configuration data, not tenant business rows.
- `storage.json`: bucket configuration and exact `storage.objects` RLS policy expressions; the intended future `verto-sync-documents` bucket is absent, and only the private `optimal-verto-private` bucket exists.

Observed catalog summary:

- PostgreSQL `17.6`; project health was `ACTIVE_HEALTHY`.
- Contract `verto-unified-sync` v1 / schema 1 / scope definition 1 is `EXPAND_ONLY`; production pruning is disabled.
- Rollout is fail-closed: production wave 0, kill switch enabled, legacy fallback enabled.
- All six unified RPCs are owned by `postgres`, use `SECURITY DEFINER`, set `search_path=public`, grant execute to `authenticated` and `service_role`, and do not grant it to `anon`/`PUBLIC`.
- Live migration history has 214 entries and ends at `20260909194457 / verto_compressed_backup_archive_payload`.

These files are direct catalog exports and are the comparison input. They do not prove that the incomplete local historical migration set can reconstruct the live schema.
