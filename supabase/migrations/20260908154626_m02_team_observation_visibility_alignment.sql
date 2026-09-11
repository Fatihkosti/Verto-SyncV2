-- M02 TEAM_OBSERVATION visibility alignment.
-- Preserves legacy and rollout flags; only aligns unified read/recovery visibility with product RLS.

-- Align Unified V2 visibility with the product RLS contract for TEAM_OBSERVATION:
-- admins/managers see the manager-scoped feed; authors can also recover their own submissions.
create or replace function public.verto_sync_row_visible_to_current_principal(
    p_aggregate_type text,
    p_payload jsonb,
    p_visibility_principal_id uuid,
    p_required_permission text
) returns boolean
language sql
stable
security definer
set search_path to 'public'
as $function$
select
    (p_visibility_principal_id is null or p_visibility_principal_id = auth.uid())
    and (
        p_required_permission is null
        or public.has_perm(p_required_permission)
        or (
            p_aggregate_type='TEAM_OBSERVATION'
            and coalesce(p_payload->>'authorUserId','') = coalesce(auth.uid()::text,'')
        )
    );
$function$;
revoke all on function public.verto_sync_row_visible_to_current_principal(text,jsonb,uuid,text) from public, anon, authenticated;
grant execute on function public.verto_sync_row_visible_to_current_principal(text,jsonb,uuid,text) to service_role;

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
       and public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission);

    with visible as (
        select cl.*
          from public.verto_sync_change_log cl
         where cl.organization_id = v_org
           and cl.revision > v_after
           and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
           and public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission)
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
           and public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission)
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
           and public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission)
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

create or replace function public.verto_begin_sync_bootstrap(p_scope_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
    v_org uuid;
    v_uid uuid;
    v_session uuid := gen_random_uuid();
    v_baseline bigint;
    v_cursor text;
    v_count bigint;
    v_first_token uuid;
    v_ttl integer;
begin
    select v.organization_id, v.principal_id into v_org, v_uid
      from public.verto_validate_sync_scope(p_scope_id) v;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('verto-sync-revision:' || v_org::text, 0)
    );

    select coalesce(max(cl.revision), 0) into v_baseline
      from public.verto_sync_change_log cl
     where cl.organization_id = v_org
       and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
       and public.verto_sync_row_visible_to_current_principal(
             cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission
           );

    v_cursor := public.verto_encode_sync_cursor(p_scope_id, v_baseline);
    select bootstrap_session_ttl_seconds into v_ttl
      from public.verto_sync_contract
     where contract_family = 'verto-unified-sync' and contract_version = 1;

    insert into public.verto_sync_bootstrap_sessions (
        bootstrap_session_id, scope_id, organization_id, principal_id,
        contract_family, contract_version, scope_definition_version,
        baseline_revision, baseline_cursor, snapshot_row_count,
        expires_at, state
    ) values (
        v_session, p_scope_id, v_org, v_uid,
        'verto-unified-sync', 1, 1,
        v_baseline, v_cursor, 0,
        now() + make_interval(secs => v_ttl), 'IN_PROGRESS'
    );

    insert into public.verto_sync_bootstrap_rows (
        bootstrap_session_id, ordinal, aggregate_type, aggregate_id,
        entity_version, payload_version, payload, partition_key
    )
    select v_session,
           row_number() over (order by s.aggregate_type, s.partition_key, s.aggregate_id),
           s.aggregate_type, s.aggregate_id, s.entity_version, s.payload_version, s.payload, s.partition_key
      from public.verto_sync_snapshot_state s
     where s.organization_id = v_org
       and s.updated_revision <= v_baseline
       and public.verto_sync_row_visible_to_current_principal(
             s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission
           )
     order by s.aggregate_type, s.partition_key, s.aggregate_id;

    get diagnostics v_count = row_count;
    update public.verto_sync_bootstrap_sessions
       set snapshot_row_count = v_count, state = 'READY'
     where bootstrap_session_id = v_session;

    insert into public.verto_sync_bootstrap_page_tokens (bootstrap_session_id, after_ordinal)
    values (v_session, 0)
    returning page_token into v_first_token;

    return jsonb_build_object(
        'contract_family', 'verto-unified-sync',
        'contract_version', 1,
        'scope_id', p_scope_id,
        'bootstrap_session_id', v_session,
        'baseline_revision', v_baseline,
        'baseline_cursor', v_cursor,
        'snapshot_row_count', v_count,
        'first_page_token', v_first_token::text,
        'expires_at_epoch_millis', floor(extract(epoch from (now() + make_interval(secs => v_ttl))) * 1000)::bigint
    );
end
$function$;

create or replace function public.verto_get_reconciliation_manifest(p_scope_id uuid, p_partition_token text default null)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
    v_org uuid;
    v_uid uuid;
    v_token uuid;
    v_aggregate text;
    v_partition text;
    v_next_aggregate text;
    v_next_partition text;
    v_next_token uuid;
    v_count bigint;
    v_digest text;
    v_revision bigint;
begin
    select v.organization_id, v.principal_id into v_org, v_uid
      from public.verto_validate_sync_scope(p_scope_id) v;

    if p_partition_token is null or btrim(p_partition_token) = '' then
        select s.aggregate_type, s.partition_key into v_aggregate, v_partition
          from public.verto_sync_snapshot_state s
         where s.organization_id = v_org
           and public.verto_sync_row_visible_to_current_principal(
                 s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission
               )
         group by s.aggregate_type, s.partition_key
         order by s.aggregate_type, s.partition_key limit 1;
    else
        begin
            v_token := p_partition_token::uuid;
        exception when invalid_text_representation then
            raise exception 'VALIDATION: malformed manifest token' using errcode = '22023';
        end;
        select t.aggregate_type, t.partition_key into v_aggregate, v_partition
          from public.verto_sync_manifest_partition_tokens t
         where t.partition_token = v_token and t.scope_id = p_scope_id;
        if not found then
            if exists (select 1 from public.verto_sync_manifest_partition_tokens t where t.partition_token = v_token) then
                raise exception 'SCOPE_MISMATCH: manifest token belongs to another scope' using errcode = '22023';
            end if;
            raise exception 'VALIDATION: unknown manifest token' using errcode = '22023';
        end if;
    end if;

    if v_aggregate is null then
        return jsonb_build_object('scope_id', p_scope_id, 'manifest', null, 'has_more', false, 'next_partition_token', null);
    end if;

    select count(*),
           encode(extensions.digest(convert_to(coalesce(string_agg(
               concat_ws('|', s.aggregate_id, coalesce(s.entity_version::text,'null'), s.payload_version::text, s.payload::text),
               E'\\n' order by s.aggregate_id
           ), ''), 'UTF8'), 'sha256'), 'hex'),
           coalesce(max(s.updated_revision), 0)
      into v_count, v_digest, v_revision
      from public.verto_sync_snapshot_state s
     where s.organization_id = v_org
       and s.aggregate_type = v_aggregate
       and s.partition_key = v_partition
       and public.verto_sync_row_visible_to_current_principal(
             s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission
           );

    select q.aggregate_type, q.partition_key into v_next_aggregate, v_next_partition
      from (
        select s.aggregate_type, s.partition_key
          from public.verto_sync_snapshot_state s
         where s.organization_id = v_org
           and public.verto_sync_row_visible_to_current_principal(
                 s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission
               )
         group by s.aggregate_type, s.partition_key
      ) q
     where (q.aggregate_type, q.partition_key) > (v_aggregate, v_partition)
     order by q.aggregate_type, q.partition_key limit 1;

    if v_next_aggregate is not null then
        insert into public.verto_sync_manifest_partition_tokens (scope_id, aggregate_type, partition_key)
        values (p_scope_id, v_next_aggregate, v_next_partition)
        on conflict (scope_id, aggregate_type, partition_key)
        do update set aggregate_type = excluded.aggregate_type
        returning partition_token into v_next_token;
    end if;

    return jsonb_build_object(
        'scope_id', p_scope_id,
        'manifest', jsonb_build_object(
            'aggregate_type', v_aggregate,
            'partition_key', v_partition,
            'row_count', v_count,
            'content_hash_or_version_digest', v_digest,
            'manifest_revision', v_revision,
            'scope_id', p_scope_id
        ),
        'has_more', v_next_aggregate is not null,
        'next_partition_token', case when v_next_aggregate is null then null else v_next_token::text end
    );
end
$function$;

create or replace function public.verto_can_read_sync_realtime_hint(p_revision bigint, p_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path to 'public'
as $function$
select exists (
    select 1
      from public.verto_sync_change_log cl
      join public.app_users au
        on au.id = auth.uid()
       and au.organization_id = cl.organization_id
       and coalesce(au.is_active, true)
     where cl.revision = p_revision
       and cl.organization_id = p_organization_id
       and not exists (select 1 from public.verto_sync_change_suppressions q where q.revision=cl.revision)
       and public.verto_sync_row_visible_to_current_principal(
             cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission
           )
);
$function$;
