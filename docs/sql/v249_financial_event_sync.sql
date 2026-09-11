-- Verto F249 — durable financial event synchronization.
-- Apply after v248_invoice_lifecycle_reversal.sql.
-- Transition rule: old row-sync remains readable/writable; new clients must write this event contract first.

create sequence if not exists public.financial_sync_server_revision_seq;

create table if not exists public.financial_sync_events (
    event_id text primary key,
    organization_id uuid not null,
    write_id text not null,
    aggregate_id text not null,
    aggregate_version integer not null check (aggregate_version >= 1),
    aggregate_sequence bigint not null check (aggregate_sequence >= 1),
    operation_type text not null check (operation_type in (
        'INVOICE_CREATED','INVOICE_UPDATED','INVOICE_VOIDED','PAYMENT_RECORDED','PAYMENT_REVERSED'
    )),
    payload_version integer not null check (payload_version >= 1),
    schema_version integer not null check (schema_version = 1),
    payload jsonb not null,
    occurred_at bigint not null,
    recorded_at bigint not null,
    server_recorded_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
    server_revision bigint not null default nextval('public.financial_sync_server_revision_seq'),
    created_by uuid,
    constraint uq_financial_sync_operation_write unique (organization_id, operation_type, write_id),
    constraint uq_financial_sync_aggregate_sequence unique (organization_id, aggregate_id, aggregate_sequence),
    constraint uq_financial_sync_server_revision unique (server_revision)
);

create index if not exists ix_financial_sync_org_revision
    on public.financial_sync_events (organization_id, server_revision);
create index if not exists ix_financial_sync_org_aggregate_version
    on public.financial_sync_events (organization_id, aggregate_id, aggregate_version);

alter table public.financial_sync_events enable row level security;

drop policy if exists financial_sync_events_select_own_org on public.financial_sync_events;
create policy financial_sync_events_select_own_org
on public.financial_sync_events
for select
to authenticated
using (
    organization_id = (
        select au.organization_id
        from public.app_users au
        where au.id = auth.uid() and coalesce(au.is_active, true)
        limit 1
    )
);

-- No INSERT/UPDATE/DELETE policy is intentional. Writes go through the SECURITY DEFINER RPC so
-- identity, ordering and optimistic conflict checks cannot be bypassed by a new client.

create or replace function public.financial_sync_apply_event_v1(
    p_event_id text,
    p_write_id text,
    p_aggregate_id text,
    p_aggregate_version integer,
    p_aggregate_sequence bigint,
    p_operation_type text,
    p_payload_version integer,
    p_schema_version integer,
    p_payload text,
    p_occurred_at bigint,
    p_recorded_at bigint
)
returns table (
    event_id text,
    write_id text,
    aggregate_id text,
    server_revision bigint,
    server_recorded_at bigint,
    status text,
    replayed boolean,
    conflict_reason text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid;
    v_existing public.financial_sync_events%rowtype;
    v_last public.financial_sync_events%rowtype;
    v_invoice_status text;
    v_invoice_version integer;
    v_logical_version integer := 0;
    v_parent_exists boolean := false;
    v_payload jsonb;
begin
    select au.organization_id
      into v_org
      from public.app_users au
     where au.id = auth.uid() and coalesce(au.is_active, true)
     limit 1;

    if v_org is null then
        raise exception 'financial sync requires an active organization';
    end if;
    if coalesce(p_event_id, '') = '' or coalesce(p_write_id, '') = '' or coalesce(p_aggregate_id, '') = '' then
        raise exception 'financial sync identity is required';
    end if;
    if p_aggregate_version < 1 or p_aggregate_sequence < 1 then
        raise exception 'financial sync version/sequence must be positive';
    end if;
    if p_schema_version <> 1 or p_payload_version < 1 then
        raise exception 'unsupported financial sync schema/payload version';
    end if;
    if p_operation_type not in ('INVOICE_CREATED','INVOICE_UPDATED','INVOICE_VOIDED','PAYMENT_RECORDED','PAYMENT_REVERSED') then
        raise exception 'unsupported financial operation';
    end if;

    begin
        v_payload := p_payload::jsonb;
    exception when others then
        raise exception 'financial sync payload must be JSON';
    end;

    -- Exactly-once effect: same organization + operation + writeId returns the original result.
    select * into v_existing
      from public.financial_sync_events e
     where e.organization_id = v_org
       and e.operation_type = p_operation_type
       and e.write_id = p_write_id
     limit 1;

    if found then
        if v_existing.event_id <> p_event_id or v_existing.aggregate_id <> p_aggregate_id then
            return query select
                v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
                v_existing.server_revision, v_existing.server_recorded_at,
                'CONFLICT'::text, false, 'idempotency identity reused for different event'::text;
        else
            return query select
                v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
                v_existing.server_revision, v_existing.server_recorded_at,
                'REPLAYED'::text, true, ''::text;
        end if;
        return;
    end if;

    -- Aggregate ordering. During the compatibility window an existing legacy invoice may establish
    -- the baseline even if it has no historical event rows yet; after the first event, sequence is strict.
    select * into v_last
      from public.financial_sync_events e
     where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
     order by e.aggregate_sequence desc
     limit 1;

    if found then
        if p_aggregate_sequence <= v_last.aggregate_sequence then
            return query select p_event_id, p_write_id, p_aggregate_id, v_last.server_revision,
                v_last.server_recorded_at, 'CONFLICT'::text, false,
                'stale or competing aggregate sequence'::text;
            return;
        elsif p_aggregate_sequence <> v_last.aggregate_sequence + 1 then
            return query select p_event_id, p_write_id, p_aggregate_id, v_last.server_revision,
                v_last.server_recorded_at, 'WAITING_DEPENDENCY'::text, false,
                'previous aggregate event has not arrived'::text;
            return;
        end if;
        v_logical_version := v_last.aggregate_version;
        v_parent_exists := true;
    end if;

    select i.lifecycle_status, i.lifecycle_version
      into v_invoice_status, v_invoice_version
      from public.invoices i
     where i.organization_id = v_org and i.id::text = p_aggregate_id
     limit 1;

    if found then
        v_parent_exists := true;
        v_logical_version := greatest(v_logical_version, coalesce(v_invoice_version, 1));
    end if;

    -- Parent-before-child. Invoice creation itself may lead the compatibility row upsert.
    if p_operation_type <> 'INVOICE_CREATED' and not v_parent_exists then
        return query select p_event_id, p_write_id, p_aggregate_id, 0::bigint, 0::bigint,
            'WAITING_DEPENDENCY'::text, false, 'parent invoice has not arrived'::text;
        return;
    end if;

    -- Posted/voided lifecycle never uses timestamp LWW. Invoice mutations advance one version.
    if p_operation_type in ('INVOICE_UPDATED','INVOICE_VOIDED') then
        if p_aggregate_version <> v_logical_version + 1 then
            -- Transition compatibility: an old row-sync may already have materialized this exact version.
            if not (v_last.event_id is null and p_aggregate_version = v_logical_version) then
                return query select p_event_id, p_write_id, p_aggregate_id,
                    coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                    'CONFLICT'::text, false, 'optimistic aggregate version conflict'::text;
                return;
            end if;
        end if;
    elsif p_operation_type = 'INVOICE_CREATED' then
        if v_last.event_id is not null then
            return query select p_event_id, p_write_id, p_aggregate_id,
                v_last.server_revision, v_last.server_recorded_at,
                'CONFLICT'::text, false, 'invoice aggregate already has event history'::text;
            return;
        end if;
    else
        -- Payment/reversal events do not rewrite invoice lifecycle version, but they must not claim
        -- a future or stale parent version.
        if v_logical_version > 0 and p_aggregate_version <> v_logical_version then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'payment parent version conflict'::text;
            return;
        end if;
    end if;

    -- Two-device void: once the logical stream contains VOID at this or a later version, another void
    -- cannot create a second financial reversal even if the legacy invoice row has not arrived yet.
    if p_operation_type = 'INVOICE_VOIDED' and exists (
        select 1 from public.financial_sync_events e
         where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
           and e.operation_type = 'INVOICE_VOIDED'
    ) then
        return query select p_event_id, p_write_id, p_aggregate_id,
            coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
            'CONFLICT'::text, false, 'invoice was already voided'::text;
        return;
    end if;

    -- Payment reversal must reference an existing payment row or a preceding PAYMENT_RECORDED event.
    if p_operation_type = 'PAYMENT_REVERSED' then
        if coalesce(v_payload->>'reversedPaymentId', '') = '' then
            raise exception 'payment reversal requires reversedPaymentId';
        end if;
        if not exists (
            select 1 from public.payments p
             where p.organization_id = v_org and p.id::text = v_payload->>'reversedPaymentId'
        ) and not exists (
            select 1 from public.financial_sync_events e
             where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
               and e.operation_type = 'PAYMENT_RECORDED'
               and e.payload->>'paymentId' = v_payload->>'reversedPaymentId'
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'WAITING_DEPENDENCY'::text, false, 'original payment has not arrived'::text;
            return;
        end if;
    end if;

    begin
        insert into public.financial_sync_events (
            event_id, organization_id, write_id, aggregate_id, aggregate_version,
            aggregate_sequence, operation_type, payload_version, schema_version, payload,
            occurred_at, recorded_at, created_by
        ) values (
            p_event_id, v_org, p_write_id, p_aggregate_id, p_aggregate_version,
            p_aggregate_sequence, p_operation_type, p_payload_version, p_schema_version, v_payload,
            p_occurred_at, p_recorded_at, auth.uid()
        )
        returning * into v_existing;
    exception when unique_violation then
        -- A concurrent device won the aggregate sequence or idempotency race. Re-check identity so
        -- retries get the same answer and competing operations become an explicit review conflict.
        select * into v_existing from public.financial_sync_events e
         where e.organization_id = v_org and e.operation_type = p_operation_type and e.write_id = p_write_id
         limit 1;
        if found and v_existing.event_id = p_event_id and v_existing.aggregate_id = p_aggregate_id then
            return query select v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
                v_existing.server_revision, v_existing.server_recorded_at,
                'REPLAYED'::text, true, ''::text;
        else
            return query select p_event_id, p_write_id, p_aggregate_id, 0::bigint, 0::bigint,
                'CONFLICT'::text, false, 'concurrent aggregate event conflict'::text;
        end if;
        return;
    end;

    return query select v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
        v_existing.server_revision, v_existing.server_recorded_at,
        'APPLIED'::text, false, ''::text;
end;
$$;

create or replace function public.financial_sync_pull_events_v1(
    p_since_revision bigint default 0,
    p_limit integer default 250
)
returns table (
    event_id text,
    organization_id text,
    aggregate_id text,
    aggregate_version integer,
    aggregate_sequence bigint,
    operation_type text,
    payload_version integer,
    schema_version integer,
    payload text,
    occurred_at bigint,
    recorded_at bigint,
    server_revision bigint
)
language sql
security definer
set search_path = public, pg_temp
stable
as $$
    select e.event_id, e.organization_id::text, e.aggregate_id, e.aggregate_version,
           e.aggregate_sequence, e.operation_type, e.payload_version, e.schema_version,
           e.payload::text, e.occurred_at, e.recorded_at, e.server_revision
      from public.financial_sync_events e
     where e.organization_id = (
         select au.organization_id from public.app_users au
          where au.id = auth.uid() and coalesce(au.is_active, true)
          limit 1
     )
       and e.server_revision > greatest(coalesce(p_since_revision, 0), 0)
     order by e.server_revision
     limit least(greatest(coalesce(p_limit, 250), 1), 500);
$$;

revoke all on function public.financial_sync_apply_event_v1(text,text,text,integer,bigint,text,integer,integer,text,bigint,bigint) from public;
revoke all on function public.financial_sync_pull_events_v1(bigint,integer) from public;
grant execute on function public.financial_sync_apply_event_v1(text,text,text,integer,bigint,text,integer,integer,text,bigint,bigint) to authenticated;
grant execute on function public.financial_sync_pull_events_v1(bigint,integer) to authenticated;

-- Optional transition step, run by the database owner only after the event-capable app is deployed.
-- It establishes one deterministic baseline event for untouched legacy invoice aggregates. Aggregates
-- that already received any v249 event are intentionally skipped, so this backfill is rerunnable.
create or replace function public.financial_sync_backfill_invoice_baselines_v1()
returns bigint
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_inserted bigint := 0;
begin
    insert into public.financial_sync_events (
        event_id, organization_id, write_id, aggregate_id, aggregate_version,
        aggregate_sequence, operation_type, payload_version, schema_version, payload,
        occurred_at, recorded_at, created_by
    )
    select
        'legacy-invoice-baseline:' || i.organization_id::text || ':' || i.id::text,
        i.organization_id,
        'legacy-invoice-baseline:' || i.id::text,
        i.id::text,
        greatest(coalesce(i.lifecycle_version, 1), 1),
        1,
        'INVOICE_CREATED',
        1,
        1,
        jsonb_build_object(
            'invoiceId', i.id::text,
            'lifecycleStatus', coalesce(nullif(i.lifecycle_status, ''), case when coalesce(i.voided, false) then 'VOID' else 'POSTED' end),
            'lifecycleVersion', greatest(coalesce(i.lifecycle_version, 1), 1),
            'legacyBaseline', true
        ),
        coalesce(nullif(i.posted_at, 0), (extract(epoch from i.created_at) * 1000)::bigint),
        (extract(epoch from clock_timestamp()) * 1000)::bigint,
        null::uuid
    from public.invoices i
    where i.organization_id is not null
      and not exists (
          select 1 from public.financial_sync_events e
          where e.organization_id = i.organization_id and e.aggregate_id = i.id::text
      )
    on conflict do nothing;

    get diagnostics v_inserted = row_count;
    return v_inserted;
end;
$$;

revoke all on function public.financial_sync_backfill_invoice_baselines_v1() from public;
revoke all on function public.financial_sync_backfill_invoice_baselines_v1() from authenticated;
