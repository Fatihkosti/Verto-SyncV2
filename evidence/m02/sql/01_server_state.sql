-- Read-only M02 live-state assertion. No secrets required.
select jsonb_build_object(
 'migration_head',(select jsonb_build_object('version',version,'name',name) from supabase_migrations.schema_migrations order by version desc limit 1),
 'contract',(select to_jsonb(c) - 'id' from public.verto_sync_contract c where contract_family='verto-unified-sync' and contract_version=1),
 'coverage_count',(select count(*) from public.verto_sync_bootstrap_coverage_v313),
 'coverage_has_team',(select exists(select 1 from public.verto_sync_bootstrap_coverage_v313 where aggregate_type='TEAM_OBSERVATION')),
 'consistency',public.verto_m02_assert_snapshot_feed_consistency(),
 'suppressed_count',(select count(*) from public.verto_sync_change_suppressions),
 'min_available_revision',(select min_available_revision from public.verto_sync_contract where contract_family='verto-unified-sync' and contract_version=1)
) as m02_state;
