-- Must return unresolved=0.
select public.verto_m02_assert_snapshot_feed_consistency();
-- Synthetic quarantine must contain only the three guarded smoke revisions.
select revision,reason from public.verto_sync_change_suppressions order by revision;
