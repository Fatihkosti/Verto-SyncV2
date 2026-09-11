-- M02 forward-only sync contract closeout.
-- Does NOT enable global V2, delete legacy paths, or rewrite immutable financial/domain history.

create table if not exists public.verto_sync_change_suppressions (
    revision bigint primary key,
    reason text not null,
    created_at timestamptz not null default pg_catalog.now(),
    constraint verto_sync_change_suppressions_reason_not_blank check (pg_catalog.btrim(reason) <> '')
);

alter table public.verto_sync_change_suppressions enable row level security;
revoke all on table public.verto_sync_change_suppressions from public, anon, authenticated;
grant select, insert, update, delete on table public.verto_sync_change_suppressions to service_role;

-- Guard the only three rows suppressed by M02. They are synthetic V393 smoke events,
-- not production domain facts. Failing closed prevents suppressing an unrelated revision.
do $m02$
begin
    if not exists (
        select 1 from public.verto_sync_change_log
        where revision=420 and aggregate_type='INVOICE' and origin_mutation_id='v393-smoke-invoice-1'
          and source_kind='BRIDGE'
    ) then raise exception 'M02_GUARD_FAILED: revision 420 identity mismatch'; end if;
    if not exists (
        select 1 from public.verto_sync_change_log
        where revision=421 and aggregate_type='PAYMENT' and origin_mutation_id='v393-smoke-payment-1'
          and source_kind='BRIDGE'
    ) then raise exception 'M02_GUARD_FAILED: revision 421 identity mismatch'; end if;
    if not exists (
        select 1 from public.verto_sync_change_log
        where revision=422 and aggregate_type='CLIENT_CREDIT' and origin_mutation_id='v393-smoke-credit-1'
          and source_kind='BRIDGE'
    ) then raise exception 'M02_GUARD_FAILED: revision 422 identity mismatch'; end if;
end
$m02$;

insert into public.verto_sync_change_suppressions(revision, reason)
values
  (420, 'M02_V393_SYNTHETIC_SMOKE'),
  (421, 'M02_V393_SYNTHETIC_SMOKE'),
  (422, 'M02_V393_SYNTHETIC_SMOKE')
on conflict (revision) do update set reason=excluded.reason;

-- Pull remains revision/cursor based, but ignores explicitly quarantined synthetic evidence rows.
-- Transaction groups remain indivisible and advisory revision serialization remains unchanged.
create or replace function public.verto_pull_sync_changes(
    p_scope_id uuid,
    p_after_cursor text default null,
    p_limit integer default 100
) returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
    v_org uuid;
    v_uid uuid;
    v_after bigint := 0;
    v_limit integer := coalesce(p_limit, 100);
    v_min bigint;
    v_high bigint;
    v_first_group_bytes bigint;
    v_last bigint;
    v_changes jsonb := '[]'::jsonb;
    v_has_more boolean := false;
    v_next_cursor text;
begin
    select v.organization_id, v.principal_id into v_org, v_uid
      from public.verto_validate_sync_scope(p_scope_id) v;

    if v_limit < 1 or v_limit > 200 then
        raise exception 'VALIDATION: p_limit must be between 1 and 200' using errcode = '22023';
    end if;
    if p_after_cursor is not null and btrim(p_after_cursor) <> '' then
        v_after := public.verto_decode_sync_cursor(p_scope_id, p_after_cursor);
    end if;

    select c.min_available_revision into v_min
      from public.verto_sync_contract c
     where c.contract_family = 'verto-unified-sync' and c.contract_version = 1;
    if v_after < v_min then
        raise exception 'CURSOR_EXPIRED: bootstrap required' using errcode = '22023';
    end if;

    select coalesce(max(cl.revision), v_after) into v_high
      from public.verto_sync_change_log cl
     where cl.organization_id = v_org
       and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
       and (cl.visibility_principal_id is null or cl.visibility_principal_id = v_uid)
       and (cl.required_permission is null or public.has_perm(cl.required_permission));

    with visible as (
        select cl.*
          from public.verto_sync_change_log cl
         where cl.organization_id = v_org
           and cl.revision > v_after
           and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
           and (cl.visibility_principal_id is null or cl.visibility_principal_id = v_uid)
           and (cl.required_permission is null or public.has_perm(cl.required_permission))
    ), groups as (
        select transaction_id, min(revision) as first_revision,
               sum(octet_length(to_jsonb(visible)::text))::bigint as group_bytes
          from visible group by transaction_id
    )
    select group_bytes into v_first_group_bytes
      from groups order by first_revision limit 1;

    if coalesce(v_first_group_bytes, 0) > 2097152 then
        raise exception 'CONTRACT_PAYLOAD_TOO_LARGE: transaction group exceeds 2 MiB' using errcode = '22023';
    end if;

    with visible as (
        select cl.*
          from public.verto_sync_change_log cl
         where cl.organization_id = v_org
           and cl.revision > v_after
           and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
           and (cl.visibility_principal_id is null or cl.visibility_principal_id = v_uid)
           and (cl.required_permission is null or public.has_perm(cl.required_permission))
    ), groups as (
        select transaction_id, min(revision) as first_revision, max(revision) as last_revision,
               count(*)::bigint as group_rows,
               sum(octet_length(to_jsonb(visible)::text))::bigint as group_bytes
          from visible group by transaction_id
    ), ranked as (
        select g.*,
               row_number() over (order by first_revision) as group_rank,
               sum(group_rows) over (order by first_revision rows unbounded preceding) as cumulative_rows,
               sum(group_bytes) over (order by first_revision rows unbounded preceding) as cumulative_bytes
          from groups g
    ), selected_groups as (
        select * from ranked
         where group_bytes <= 2097152
           and ((cumulative_rows <= v_limit and cumulative_bytes <= 1048576)
                or (group_rank = 1 and (group_rows > v_limit or group_bytes > 1048576)))
    ), page_rows as (
        select v.*,
               row_number() over (partition by v.transaction_id order by v.revision)::integer as transaction_order,
               count(*) over (partition by v.transaction_id)::integer as transaction_size
          from visible v
          join selected_groups g on g.transaction_id = v.transaction_id
    ), page_json as (
        select coalesce(jsonb_agg(
            jsonb_build_object(
                'revision', revision,
                'organizationId', organization_id,
                'syncScopeId', p_scope_id,
                'aggregateType', aggregate_type,
                'aggregateId', aggregate_id,
                'operationType', operation_type,
                'entityVersion', entity_version,
                'payloadVersion', payload_version,
                'payload', payload,
                'originMutationId', origin_mutation_id,
                'transactionId', transaction_id,
                'transactionOrder', transaction_order,
                'transactionSize', transaction_size,
                'deletedAtEpochMillis', case when deleted_at is null then null else floor(extract(epoch from deleted_at) * 1000)::bigint end,
                'changedAtEpochMillis', floor(extract(epoch from changed_at) * 1000)::bigint
            ) order by revision
        ), '[]'::jsonb) as changes,
        max(revision) as last_revision
        from page_rows
    )
    select changes, last_revision into v_changes, v_last from page_json;

    v_last := coalesce(v_last, v_after);
    select exists (
        select 1 from public.verto_sync_change_log cl
         where cl.organization_id = v_org
           and cl.revision > v_last
           and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
           and (cl.visibility_principal_id is null or cl.visibility_principal_id = v_uid)
           and (cl.required_permission is null or public.has_perm(cl.required_permission))
    ) into v_has_more;

    if v_last = v_after and p_after_cursor is not null and btrim(p_after_cursor) <> '' then
        v_next_cursor := p_after_cursor;
    else
        v_next_cursor := public.verto_encode_sync_cursor(p_scope_id, v_last);
    end if;

    return jsonb_build_object(
        'contract_family', 'verto-unified-sync',
        'contract_version', 1,
        'scope_id', p_scope_id,
        'coverage', 'GLOBAL_SCOPE',
        'changes', v_changes,
        'next_cursor', v_next_cursor,
        'has_more', v_has_more,
        'min_available_revision', v_min,
        'page_high_watermark', v_high,
        'ends_at_transaction_boundary', true,
        'advances_global_cursor', true
    );
end
$function$;

revoke all on function public.verto_pull_sync_changes(uuid,text,integer) from public, anon;
grant execute on function public.verto_pull_sync_changes(uuid,text,integer) to authenticated, service_role;

-- Machine-checkable current-state verifier. Historical PARTY_ROLE aliases are classified as
-- SUPERSEDED_ALIAS only when a later canonical partyId:ROLE change and canonical snapshot exist.
create or replace function public.verto_m02_assert_snapshot_feed_consistency()
returns jsonb
language sql
security definer
set search_path to 'public'
as $function$
with latest as (
  select distinct on (cl.organization_id,cl.aggregate_type,cl.aggregate_id)
    cl.organization_id,cl.aggregate_type,cl.aggregate_id,cl.revision,cl.operation_type,
    cl.entity_version,cl.payload_version,cl.payload
  from public.verto_sync_change_log cl
  where not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
  order by cl.organization_id,cl.aggregate_type,cl.aggregate_id,cl.revision desc
), classified as (
  select l.*,
    case
      when l.operation_type='DELETE' and s.aggregate_id is null then 'EXACT'
      when s.aggregate_id is not null
       and s.updated_revision=l.revision
       and s.entity_version is not distinct from l.entity_version
       and s.payload_version=l.payload_version
       and s.payload=l.payload then 'EXACT'
      when l.aggregate_type='PARTY_ROLE'
       and l.aggregate_id=coalesce(l.payload->>'partyId','')
       and coalesce(l.payload->>'role','')<>''
       and exists (
         select 1 from public.verto_sync_change_log c2
         where c2.organization_id=l.organization_id and c2.aggregate_type='PARTY_ROLE'
           and c2.aggregate_id=(l.payload->>'partyId')||':'||(l.payload->>'role')
           and c2.revision>l.revision
           and not exists (select 1 from public.verto_sync_change_suppressions q2 where q2.revision=c2.revision)
       )
       and exists (
         select 1 from public.verto_sync_snapshot_state s2
         where s2.organization_id=l.organization_id and s2.aggregate_type='PARTY_ROLE'
           and s2.aggregate_id=(l.payload->>'partyId')||':'||(l.payload->>'role')
       ) then 'SUPERSEDED_ALIAS'
      else 'UNRESOLVED'
    end as consistency_class
  from latest l
  left join public.verto_sync_snapshot_state s
    on s.organization_id=l.organization_id and s.aggregate_type=l.aggregate_type and s.aggregate_id=l.aggregate_id
)
select jsonb_build_object(
  'latest_identities', count(*),
  'exact_consistent', count(*) filter(where consistency_class='EXACT'),
  'superseded_party_aliases', count(*) filter(where consistency_class='SUPERSEDED_ALIAS'),
  'suppressed_synthetic_changes', (select count(*) from public.verto_sync_change_suppressions),
  'unresolved', count(*) filter(where consistency_class='UNRESOLVED')
) from classified;
$function$;

revoke all on function public.verto_m02_assert_snapshot_feed_consistency() from public, anon, authenticated;
grant execute on function public.verto_m02_assert_snapshot_feed_consistency() to service_role;

-- Test-only service helper used by the M02 delayed-COMMIT concurrency proof. It mutates no domain data.
create or replace function public.verto_m02_hold_revision_lock_test(p_organization_id uuid, p_seconds integer)
returns boolean
language plpgsql
security invoker
set search_path to 'pg_catalog','public'
as $function$
begin
  if p_organization_id is null or p_seconds not between 1 and 30 then
    raise exception 'M02_TEST_VALIDATION';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('verto-sync-revision:' || p_organization_id::text, 0)
  );
  perform pg_catalog.pg_sleep(p_seconds);
  return true;
end
$function$;

revoke all on function public.verto_m02_hold_revision_lock_test(uuid,integer) from public, anon, authenticated;
grant execute on function public.verto_m02_hold_revision_lock_test(uuid,integer) to service_role;
