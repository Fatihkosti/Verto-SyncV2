# Verto Sync Server Baseline — v305

- input_dump_filename: `schema(2).sql`
- input_dump_sha256: `fb83bf253fefe7f7b684a41dc5df6e06b0aa933a2c13e26cb835842e1c7f9ea8`
- input_dump_bytes: `568177`
- input_dump_lines: `16352`
- postgres_version_if_present: `17.6`
- pg_dump_version_if_present: `17.11`
- captured_at_if_known: `not encoded as authoritative capture timestamp in schema-only dump`
- schema_objects_count: `118`
- functions_count: `135`
- triggers_count: `73`
- policies_count: `221`
- rls_enabled_tables_count: `104`
- migration_files_count_in_project_before_v305: `0 under supabase/migrations`

## Tenant / principal authority

`public.app_users.id = auth.uid()` is the Verto principal mapping. `organization_id` is the tenant authority and `is_active` is mandatory. `public.get_my_org_id()` resolves organization server-side. Effective permission inputs are `app_users.role` plus `employee_permissions.permissions`; `public.has_perm(text)` enforces tenant alignment and active membership.

Permission literals observed in the exact dump and therefore included indirectly by hashing the complete permissions JSON plus role: `cash_adjust, clients_add_payment, clients_delete, clients_edit, clients_view, commission_credit, commission_manage, expenses_create, expenses_delete, expenses_view, inventory_edit, inventory_view, payments_reverse, sales_create, sales_delete, sales_edit, sales_view, shipments_manage, shipments_view`.

## Existing sync infrastructure preserved

- `sync_tombstones` exists and is not removed or pruned by 305.
- `optimal_sync_change_log`, `optimal_sync_change_revision_seq`, `optimal_sync_receipts`, `optimal_apply_sync_operation_v2`, and `optimal_pull_sync_changes` exist and remain untouched.
- Existing financial/inventory objects that REVISION 2 proves absent are recorded as deferred in `VERTO_SYNC_SERVER_WRITE_PATH_COVERAGE_v305.csv`; no names are invented.
- Logistics server tables are present but the Android remote path remains inert behind its existing gate; 305 does not activate it.

## 305 boundary

The migration is additive server infrastructure only. No current Android writer is cut over, no production business backfill is performed, and no current business table receives a new Verto capture trigger in this session.
