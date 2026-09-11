-- Visibility predicate used by pull/bootstrap/manifest/realtime after M02.
-- Execute as the observation author and then as an unrelated authenticated principal in the same org.
-- Expected: author=true; unrelated=false unless the unrelated principal has team_observations_manage.
select public.verto_sync_row_visible_to_current_principal(
  'TEAM_OBSERVATION',
  jsonb_build_object('authorUserId',auth.uid()::text),
  null,
  'team_observations_manage'
) as author_can_read;
