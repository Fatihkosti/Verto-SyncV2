with funcs as (
 select p.proname, pg_get_function_identity_arguments(p.oid) args, md5(pg_get_functiondef(p.oid)) def_md5
 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
 where n.nspname='public' and p.proname in (
 'verto_apply_sync_mutation','verto_append_sync_change','verto_assign_sync_change_revision',
 'verto_pull_sync_changes','verto_begin_sync_bootstrap','verto_pull_bootstrap_page',
 'verto_get_reconciliation_manifest','verto_resolve_sync_scope','verto_expected_payload_version',
 'verto_apply_sync_adapter_v403','verto_apply_team_observation_v403','verto_validate_push_policy_v403',
 'verto_can_read_sync_realtime_hint','verto_sync_row_visible_to_current_principal',
 'verto_m02_assert_snapshot_feed_consistency','verto_m02_hold_revision_lock_test',
 'financial_sync_apply_event_v1','inventory_apply_commands_v2','inventory_apply_cost_revisions_v2','optimal_apply_sync_operation_v2')
)
select md5(string_agg(proname||':'||args||':'||def_md5,E'\n' order by proname,args)) as m02_surface_md5,
       count(*) as entries
from funcs;
-- Executed result after M02: 862ae50f015f920da7aaf0361a8d952b / 20.
