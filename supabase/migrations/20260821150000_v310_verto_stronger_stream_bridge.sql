-- Verto Session 310 — stronger financial/inventory/Optimal bridge.
-- EXPAND/bridge only. Room remains 80. Runtime V2 remains disabled.
-- Historical v305/v309 migrations are immutable. No unbounded backfill is executed here.


-- Financial lifecycle contract (without unbounded backfill)
-- Verto F248 — invoice lifecycle, optimistic versioning and immutable posting snapshots.
-- Additive contract; apply before enabling F248 lifecycle sync.

alter table public.invoices
    add column if not exists lifecycle_status text not null default 'POSTED',
    add column if not exists lifecycle_version integer not null default 1,
    add column if not exists posted_at bigint not null default 0,
    add column if not exists voided_at bigint not null default 0,
    add column if not exists void_reason text not null default '',
    add column if not exists void_write_id text not null default '';

-- v310: no unbounded backfill; legacy lifecycle is normalized lazily per target command.

alter table public.invoices drop constraint if exists invoices_lifecycle_status_check;
alter table public.invoices add constraint invoices_lifecycle_status_check
    check (lifecycle_status in ('DRAFT','POSTED','VOID'));
alter table public.invoices drop constraint if exists invoices_lifecycle_version_check;
alter table public.invoices add constraint invoices_lifecycle_version_check
    check (lifecycle_version >= 1);

create unique index if not exists uq_invoices_void_write_id
    on public.invoices (organization_id, void_write_id)
    where void_write_id <> '';

alter table public.invoice_items
    add column if not exists item_sku_snapshot text not null default '',
    add column if not exists unit_snapshot text not null default '';

-- One original payment may have at most one reversal row across all devices.
create unique index if not exists uq_payments_reversed_payment_once
    on public.payments (reversed_payment_id)
    where reversed_payment_id is not null;

create or replace function public.verto_prevent_posted_invoice_delete()
returns trigger
language plpgsql
as $$
begin
    if old.lifecycle_status <> 'DRAFT' then
        raise exception 'posted invoices must be voided, not deleted';
    end if;
    return old;
end;
$$;

drop trigger if exists trg_prevent_posted_invoice_delete on public.invoices;
create trigger trg_prevent_posted_invoice_delete
before delete on public.invoices
for each row execute function public.verto_prevent_posted_invoice_delete();

-- Financial event contract (backfill function intentionally omitted)
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

-- Financial return extension
-- Verto F252 — extend the F249 financial event contract with immutable invoice returns.
-- Apply after v249_financial_event_sync.sql. Safe for existing financial_sync_events rows.

do $$
declare
    v_constraint text;
begin
    select c.conname into v_constraint
      from pg_constraint c
     where c.conrelid = 'public.financial_sync_events'::regclass
       and c.contype = 'c'
       and pg_get_constraintdef(c.oid) like '%operation_type%'
     limit 1;
    if v_constraint is not null then
        execute format('alter table public.financial_sync_events drop constraint %I', v_constraint);
    end if;
    alter table public.financial_sync_events
        add constraint ck_financial_sync_operation_type
        check (operation_type in (
            'INVOICE_CREATED','INVOICE_UPDATED','INVOICE_VOIDED',
            'PAYMENT_RECORDED','PAYMENT_REVERSED','INVOICE_RETURN_POSTED'
        ));
end $$;

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
    if p_operation_type not in ('INVOICE_CREATED','INVOICE_UPDATED','INVOICE_VOIDED','PAYMENT_RECORDED','PAYMENT_REVERSED','INVOICE_RETURN_POSTED') then
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

    -- Return facts and a whole-invoice void are mutually exclusive: combining both would double-reverse.
    if p_operation_type = 'INVOICE_VOIDED' and exists (
        select 1 from public.financial_sync_events e
         where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
           and e.operation_type = 'INVOICE_RETURN_POSTED'
    ) then
        return query select p_event_id, p_write_id, p_aggregate_id,
            coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
            'CONFLICT'::text, false, 'invoice with returns cannot be voided'::text;
        return;
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

    -- F252 return is an immutable child of a POSTED invoice. Validate payload identity and every
    -- returned line against the original item plus all previously accepted return events.
    if p_operation_type = 'INVOICE_RETURN_POSTED' then
        if coalesce(v_payload->>'returnId', '') = ''
           or coalesce(v_payload->>'writeId', '') = ''
           or v_payload->>'originalInvoiceId' <> p_aggregate_id
           or v_payload->>'writeId' <> p_write_id
           or coalesce(v_payload->>'documentType', '') not in ('SALES_RETURN_CREDIT_NOTE','PURCHASE_RETURN_DEBIT_NOTE')
           or coalesce(v_payload->>'settlementMode', '') not in ('CREDIT_BALANCE','CASH_REFUND')
           or coalesce((v_payload->>'transactionAmountMinor')::bigint, 0) <= 0
           or coalesce((v_payload->>'functionalAmountMinor')::bigint, 0) <= 0
           or jsonb_typeof(v_payload->'lines') <> 'array'
           or jsonb_array_length(v_payload->'lines') = 0 then
            raise exception 'invalid invoice return payload';
        end if;

        if not exists (
            select 1 from public.invoices i
             where i.organization_id = v_org
               and i.id::text = p_aggregate_id
               and i.client_id::text = v_payload->>'clientId'
               and i.legacy_currency_status::text = 'KNOWN'
               and i.transaction_currency_code = v_payload->>'transactionCurrencyCode'
               and i.functional_currency_code = v_payload->>'functionalCurrencyCode'
               and i.transaction_amount_minor > 0
               and i.functional_amount_at_recognition_minor > 0
               and (v_payload->>'functionalAmountMinor')::bigint = round(
                    (v_payload->>'transactionAmountMinor')::numeric
                    * i.functional_amount_at_recognition_minor::numeric
                    / i.transaction_amount_minor::numeric
               )::bigint
               and (
                    (i.category::text = 'SALE' and v_payload->>'documentType' = 'SALES_RETURN_CREDIT_NOTE')
                 or (i.category::text = 'PURCHASE' and v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE')
               )
        ) then
            raise exception 'invoice return party/type/currency truth does not match original invoice';
        end if;

        if coalesce(v_invoice_status, '') <> 'POSTED' or exists (
            select 1 from public.financial_sync_events e
             where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
               and e.operation_type = 'INVOICE_VOIDED'
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return requires a posted non-void invoice'::text;
            return;
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             where coalesce(line->>'originalInvoiceItemId', '') = ''
                or coalesce((line->>'quantity')::integer, 0) <= 0
                or not exists (
                    select 1 from public.invoice_items ii
                     where ii.id::text = line->>'originalInvoiceItemId'
                       and ii.invoice_id::text = p_aggregate_id
                )
        ) then
            raise exception 'invoice return line does not belong to original invoice';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             where not exists (
                 select 1 from public.invoice_items ii
                  where ii.id::text = line->>'originalInvoiceItemId'
                    and ii.invoice_id::text = p_aggregate_id
                    and coalesce(ii.inventory_item_id::text, '') = coalesce(line->>'inventoryItemId', '')
                    and (
                        (
                            v_payload->>'documentType' = 'SALES_RETURN_CREDIT_NOTE'
                            and (line->>'unitTransactionAmountMinor')::bigint = ii.unit_sell_price_minor
                            and (line->>'unitCostAtSaleMinor')::bigint = ii.unit_cost_at_sale_minor
                            and ((coalesce(ii.inventory_item_id::text, '') = '') or ii.cost_snapshot_status = 'KNOWN')
                        )
                        or (
                            v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE'
                            and (line->>'unitTransactionAmountMinor')::bigint = round(ii.buy_price * 100)::bigint
                            and (line->>'originalPurchaseUnitCostMinor')::bigint = round(ii.buy_price * 100)::bigint
                        )
                    )
             )
        ) then
            raise exception 'invoice return monetary facts do not match original invoice line';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             where coalesce(line->>'id', '') = ''
                or coalesce((line->>'unitTransactionAmountMinor')::bigint, -1) < 0
                or coalesce((line->>'transactionAmountMinor')::bigint, 0) <= 0
                or coalesce((line->>'unitFunctionalAmountMinor')::bigint, -1) < 0
                or coalesce((line->>'functionalAmountMinor')::bigint, 0) <= 0
                or coalesce((line->>'unitCostAtSaleMinor')::bigint, -1) < 0
                or coalesce((line->>'historicalCostAmountMinor')::bigint, -1) < 0
                or coalesce((line->>'originalPurchaseUnitCostMinor')::bigint, -1) < 0
                or (line->>'transactionAmountMinor')::bigint <>
                   (line->>'unitTransactionAmountMinor')::bigint * (line->>'quantity')::bigint
                or (
                    v_payload->>'documentType' = 'SALES_RETURN_CREDIT_NOTE'
                    and (
                        (line->>'historicalCostAmountMinor')::bigint <>
                            (line->>'unitCostAtSaleMinor')::bigint * (line->>'quantity')::bigint
                        or (line->>'originalPurchaseUnitCostMinor')::bigint <> 0
                    )
                )
                or (
                    v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE'
                    and (
                        (line->>'unitCostAtSaleMinor')::bigint <> 0
                        or (line->>'historicalCostAmountMinor')::bigint <> 0
                    )
                )
        ) then
            raise exception 'invalid invoice return line monetary facts';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             group by line->>'originalInvoiceItemId'
            having count(*) > 1
        ) or exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             group by line->>'id'
            having count(*) > 1
        ) then
            raise exception 'duplicate invoice return line identity';
        end if;

        if (select sum((line->>'transactionAmountMinor')::bigint)
              from jsonb_array_elements(v_payload->'lines') line)
             <> (v_payload->>'transactionAmountMinor')::bigint
           or (select sum((line->>'functionalAmountMinor')::bigint)
                 from jsonb_array_elements(v_payload->'lines') line)
             <> (v_payload->>'functionalAmountMinor')::bigint then
            raise exception 'invoice return document totals do not match lines';
        end if;

        if exists (
            select 1 from public.financial_sync_events e
             where e.organization_id = v_org
               and e.operation_type = 'INVOICE_RETURN_POSTED'
               and e.payload->>'returnId' = v_payload->>'returnId'
               and e.write_id <> p_write_id
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return identity already belongs to another write'::text;
            return;
        end if;

        if exists (
            select 1
              from (
                    select line->>'originalInvoiceItemId' as original_item_id,
                           sum((line->>'quantity')::integer) as current_quantity
                      from jsonb_array_elements(v_payload->'lines') line
                     group by line->>'originalInvoiceItemId'
              ) current_return
              join public.invoice_items ii
                on ii.id::text = current_return.original_item_id
               and ii.invoice_id::text = p_aggregate_id
             where coalesce((
                    select sum((prior_line->>'quantity')::integer)
                      from public.financial_sync_events prior_event
                      cross join lateral jsonb_array_elements(prior_event.payload->'lines') prior_line
                     where prior_event.organization_id = v_org
                       and prior_event.aggregate_id = p_aggregate_id
                       and prior_event.operation_type = 'INVOICE_RETURN_POSTED'
                       and prior_line->>'originalInvoiceItemId' = current_return.original_item_id
                ), 0) + current_return.current_quantity > ii.quantity
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return quantity exceeds original invoice item'::text;
            return;
        end if;

        -- International purchase invoices do not post stock themselves. A goods return may only
        -- reverse quantity that has actually been accepted and posted by Logistics receiving.
        if v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE' and exists (
            select 1
              from (
                    select line->>'originalInvoiceItemId' as original_item_id,
                           sum((line->>'quantity')::bigint) as current_quantity
                      from jsonb_array_elements(v_payload->'lines') line
                     group by line->>'originalInvoiceItemId'
              ) current_return
              join public.invoice_items ii
                on ii.id::text = current_return.original_item_id
               and ii.invoice_id::text = p_aggregate_id
              join public.invoices i
                on i.organization_id = v_org
               and i.id::text = p_aggregate_id
             where i.purchase_scope::text = 'INTERNATIONAL'
               and coalesce((
                    select sum((prior_line->>'quantity')::bigint)
                      from public.financial_sync_events prior_event
                      cross join lateral jsonb_array_elements(prior_event.payload->'lines') prior_line
                     where prior_event.organization_id = v_org
                       and prior_event.aggregate_id = p_aggregate_id
                       and prior_event.operation_type = 'INVOICE_RETURN_POSTED'
                       and prior_line->>'originalInvoiceItemId' = current_return.original_item_id
               ), 0) + current_return.current_quantity > coalesce((
                    select sum(posting.quantity)::bigint
                      from public.logistics_inventory_postings posting
                      join public.logistics_receiving_lines receiving
                        on receiving.organization_id = posting.organization_id
                       and receiving.id = posting.receiving_line_id
                      join public.logistics_shipment_lines shipment_line
                        on shipment_line.organization_id = receiving.organization_id
                       and shipment_line.id = receiving.shipment_line_id
                      join public.logistics_shipments shipment
                        on shipment.organization_id = shipment_line.organization_id
                       and shipment.id = shipment_line.shipment_id
                     where posting.organization_id = v_org
                       and shipment_line.source_invoice_id = p_aggregate_id
                       and shipment_line.source_invoice_item_id = current_return.original_item_id
                       and shipment_line.inventory_item_id = coalesce(ii.inventory_item_id::text, '')
                       and posting.quantity > 0
                       and shipment.cancelled_at is null
                       and upper(shipment.state) not in ('CANCELLED','CANCELED')
               ), 0)
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'international purchase return exceeds posted receipt quantity'::text;
            return;
        end if;

        if jsonb_typeof(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) <> 'array' then
            raise exception 'invoice return paymentAllocations must be an array';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
             where coalesce(alloc->>'id', '') = ''
                or coalesce(alloc->>'paymentId', '') = ''
                or coalesce((alloc->>'allocatedFunctionalAmountMinor')::bigint, 0) <= 0
                or not exists (
                    select 1 from public.payments p
                     where p.organization_id = v_org
                       and p.id::text = alloc->>'paymentId'
                       and p.invoice_id::text = p_aggregate_id
                       and p.legacy_currency_status::text = 'KNOWN'
                       and not exists (
                           select 1 from public.payments reversal
                            where reversal.organization_id = v_org
                              and reversal.reversed_payment_id::text = p.id::text
                       )
                       and not exists (
                           select 1 from public.financial_sync_events reversal_event
                            where reversal_event.organization_id = v_org
                              and reversal_event.aggregate_id = p_aggregate_id
                              and reversal_event.operation_type = 'PAYMENT_REVERSED'
                              and reversal_event.payload->>'reversedPaymentId' = p.id::text
                       )
                )
        ) then
            raise exception 'invalid invoice return payment allocation';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
             group by alloc->>'paymentId'
            having count(*) > 1
        ) or exists (
            select 1
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
             group by alloc->>'id'
            having count(*) > 1
        ) then
            raise exception 'duplicate invoice return payment allocation identity';
        end if;

        if coalesce((
            select sum((alloc->>'allocatedFunctionalAmountMinor')::bigint)
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
        ), 0) > (v_payload->>'functionalAmountMinor')::bigint then
            raise exception 'return allocations exceed return functional amount';
        end if;

        if v_payload->>'settlementMode' = 'CASH_REFUND' and coalesce((
            select sum((alloc->>'allocatedFunctionalAmountMinor')::bigint)
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
        ), 0) <> (v_payload->>'functionalAmountMinor')::bigint then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'cash return exceeds effective paid amount'::text;
            return;
        end if;

        if exists (
            select 1
              from (
                    select alloc->>'paymentId' as payment_id,
                           sum((alloc->>'allocatedFunctionalAmountMinor')::bigint) as current_allocated
                      from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
                     group by alloc->>'paymentId'
              ) current_alloc
              join public.payments p
                on p.organization_id = v_org
               and p.id::text = current_alloc.payment_id
               and p.invoice_id::text = p_aggregate_id
             where coalesce((
                    select sum((prior_alloc->>'allocatedFunctionalAmountMinor')::bigint)
                      from public.financial_sync_events prior_event
                      cross join lateral jsonb_array_elements(
                          coalesce(prior_event.payload->'paymentAllocations', '[]'::jsonb)
                      ) prior_alloc
                     where prior_event.organization_id = v_org
                       and prior_event.aggregate_id = p_aggregate_id
                       and prior_event.operation_type = 'INVOICE_RETURN_POSTED'
                       and prior_alloc->>'paymentId' = current_alloc.payment_id
                ), 0) + current_alloc.current_allocated > case
                    when coalesce(p.functional_cash_amount_minor, 0) > 0 then p.functional_cash_amount_minor
                    when coalesce(p.historical_functional_amount_minor, 0) > 0 then p.historical_functional_amount_minor
                    else p.amount_minor
                end
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return payment allocation exceeds effective payment'::text;
            return;
        end if;
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

revoke all on function public.financial_sync_apply_event_v1(text,text,text,integer,bigint,text,integer,integer,text,bigint,bigint) from public;
grant execute on function public.financial_sync_apply_event_v1(text,text,text,integer,bigint,text,integer,integer,text,bigint,bigint) to authenticated;

-- Purchase cycle semantic contract
-- Verto F253 — controlled purchase cycle: PO -> GRN -> supplier invoice -> payment.
-- Additive/idempotent. Embedded in the Session 310 migration transaction; no nested BEGIN/COMMIT.

alter table if exists public.invoices
  add column if not exists supplier_invoice_ref text,
  add column if not exists supplier_invoice_ref_normalized text,
  add column if not exists purchase_order_id text;

-- The client sends the same NFKC/lower/alphanumeric canonical key stored by Room.
create unique index if not exists uq_invoices_org_supplier_external_ref
  on public.invoices(organization_id, client_id, supplier_invoice_ref_normalized)
  where supplier_invoice_ref_normalized is not null and supplier_invoice_ref_normalized <> '';
create index if not exists invoices_purchase_order_id_idx on public.invoices(purchase_order_id);

create table if not exists public.purchase_orders (
  id text primary key,
  organization_id uuid not null,
  order_number text not null,
  supplier_id uuid not null references public.clients(id) on delete restrict,
  purchase_scope text not null check (purchase_scope in ('LOCAL','INTERNATIONAL')),
  currency_code text not null,
  status text not null check (status in ('OPEN','PARTIALLY_RECEIVED','RECEIVED','CLOSED','CANCELLED')),
  created_at bigint not null,
  created_by text not null,
  created_by_name text not null,
  closed_at bigint,
  close_reason text,
  note text not null default '',
  write_id text not null,
  unique(organization_id, order_number),
  unique(organization_id, write_id)
);
create index if not exists purchase_orders_org_status_idx on public.purchase_orders(organization_id,status);

create table if not exists public.purchase_order_lines (
  id text primary key,
  purchase_order_id text not null references public.purchase_orders(id) on delete cascade,
  line_number integer not null check (line_number > 0),
  inventory_item_id text,
  item_name_snapshot text not null check (btrim(item_name_snapshot) <> ''),
  ordered_quantity integer not null check (ordered_quantity > 0),
  unit_price_minor bigint not null check (unit_price_minor >= 0),
  unique(purchase_order_id,line_number)
);
create index if not exists purchase_order_lines_order_idx on public.purchase_order_lines(purchase_order_id);

create table if not exists public.goods_receipts (
  id text primary key,
  organization_id uuid not null,
  purchase_order_id text not null references public.purchase_orders(id) on delete restrict,
  receipt_number text not null,
  received_at bigint not null check (received_at > 0),
  received_by text not null,
  received_by_name text not null,
  note text not null default '',
  write_id text not null,
  unique(organization_id,receipt_number),
  unique(organization_id,write_id)
);

create table if not exists public.goods_receipt_lines (
  id text primary key,
  goods_receipt_id text not null references public.goods_receipts(id) on delete cascade,
  purchase_order_line_id text not null references public.purchase_order_lines(id) on delete restrict,
  inventory_item_id text,
  received_quantity integer not null check (received_quantity > 0),
  accepted_quantity integer not null check (accepted_quantity >= 0),
  rejected_quantity integer not null check (rejected_quantity >= 0),
  unit_cost_minor bigint not null check (unit_cost_minor >= 0),
  check (accepted_quantity + rejected_quantity = received_quantity),
  unique(goods_receipt_id,purchase_order_line_id)
);
create index if not exists goods_receipt_lines_po_line_idx on public.goods_receipt_lines(purchase_order_line_id);

create table if not exists public.purchase_cycle_attachments (
  id text primary key,
  organization_id uuid not null,
  owner_type text not null check (owner_type in ('PURCHASE_ORDER','GOODS_RECEIPT')),
  owner_id text not null,
  uri text not null,
  mime_type text not null default '',
  display_name text not null default '',
  created_at bigint not null,
  write_id text not null,
  unique(organization_id,write_id,uri)
);

create table if not exists public.purchase_invoice_matches (
  id text primary key,
  organization_id uuid not null,
  invoice_id uuid not null references public.invoices(id) on delete restrict,
  purchase_order_id text not null references public.purchase_orders(id) on delete restrict,
  status text not null check (status in ('MATCHED','WITHIN_TOLERANCE','OVERRIDDEN')),
  quantity_variance_units integer not null check (quantity_variance_units >= 0),
  price_variance_minor bigint not null check (price_variance_minor >= 0),
  quantity_tolerance_units integer not null check (quantity_tolerance_units >= 0),
  price_tolerance_minor bigint not null check (price_tolerance_minor >= 0),
  invoice_amount_minor bigint not null check (invoice_amount_minor >= 0),
  payable_amount_minor bigint not null check (payable_amount_minor >= 0 and payable_amount_minor <= invoice_amount_minor),
  variance_reason text,
  approved_by text,
  approved_by_name text,
  matched_at bigint not null,
  write_id text not null,
  unique(invoice_id)
);

create table if not exists public.purchase_invoice_match_lines (
  id text primary key,
  match_id text not null references public.purchase_invoice_matches(id) on delete cascade,
  invoice_item_id uuid not null references public.invoice_items(id) on delete restrict,
  purchase_order_line_id text not null references public.purchase_order_lines(id) on delete restrict,
  ordered_quantity integer not null check (ordered_quantity > 0),
  accepted_quantity integer not null check (accepted_quantity >= 0),
  invoiced_quantity integer not null check (invoiced_quantity > 0),
  po_unit_price_minor bigint not null check (po_unit_price_minor >= 0),
  invoice_unit_price_minor bigint not null check (invoice_unit_price_minor >= 0),
  quantity_variance_units integer not null check (quantity_variance_units >= 0),
  price_variance_minor bigint not null check (price_variance_minor >= 0),
  payable_amount_minor bigint not null check (payable_amount_minor >= 0),
  unique(match_id,purchase_order_line_id)
);

create table if not exists public.purchase_invoice_receipt_allocations (
  id text primary key,
  organization_id uuid not null,
  match_line_id text not null references public.purchase_invoice_match_lines(id) on delete restrict,
  goods_receipt_line_id text not null references public.goods_receipt_lines(id) on delete restrict,
  allocated_quantity integer not null check (allocated_quantity > 0),
  created_at bigint not null check (created_at > 0),
  write_id text not null,
  unique(organization_id,match_line_id,goods_receipt_line_id)
);
create index if not exists purchase_invoice_receipt_allocations_match_idx on public.purchase_invoice_receipt_allocations(match_line_id);
create index if not exists purchase_invoice_receipt_allocations_receipt_idx on public.purchase_invoice_receipt_allocations(goods_receipt_line_id);

create table if not exists public.purchase_payment_overrides (
  id text primary key,
  organization_id uuid not null,
  invoice_id uuid not null references public.invoices(id) on delete restrict,
  payment_request_id text not null,
  requested_amount_minor bigint not null check (requested_amount_minor > 0),
  payable_before_override_minor bigint not null check (payable_before_override_minor >= 0),
  reason text not null check (btrim(reason) <> ''),
  approved_by text not null check (btrim(approved_by) <> ''),
  approved_by_name text not null default '',
  created_at bigint not null,
  unique(organization_id,payment_request_id)
);

create table if not exists public.purchase_order_shipment_sources (
  id text primary key,
  organization_id uuid not null,
  shipment_id text not null,
  purchase_order_id text not null references public.purchase_orders(id) on delete restrict,
  added_at bigint not null,
  write_id text not null,
  foreign key(organization_id,shipment_id)
    references public.logistics_shipments(organization_id,id) on update cascade on delete cascade,
  unique(organization_id,shipment_id,purchase_order_id),
  unique(organization_id,write_id)
);

-- PO tenant/supplier integrity is enforced even for security-definer sync writes.
create or replace function public.verto_guard_purchase_order_insert_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_existing public.purchase_orders%rowtype;
begin
  select * into v_existing from public.purchase_orders where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  if new.organization_id is distinct from public.get_my_org_id()
     or not exists (
       select 1 from public.clients c
       where c.id = new.supplier_id and c.organization_id = new.organization_id
     ) then
    raise exception 'INVALID_PURCHASE_ORDER_TENANT_OR_SUPPLIER' using errcode='42501';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_order_insert_v253 on public.purchase_orders;
create trigger trg_guard_purchase_order_insert_v253 before insert on public.purchase_orders
for each row execute function public.verto_guard_purchase_order_insert_v253();

-- Attachment metadata cannot point across tenants or to a non-existent purchase-cycle owner.
create or replace function public.verto_guard_purchase_attachment_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_existing public.purchase_cycle_attachments%rowtype;
begin
  select * into v_existing from public.purchase_cycle_attachments where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  if new.organization_id is distinct from public.get_my_org_id()
     or (new.owner_type = 'PURCHASE_ORDER' and not exists (
       select 1 from public.purchase_orders po where po.id = new.owner_id and po.organization_id = new.organization_id
     ))
     or (new.owner_type = 'GOODS_RECEIPT' and not exists (
       select 1 from public.goods_receipts gr where gr.id = new.owner_id and gr.organization_id = new.organization_id
     )) then
    raise exception 'INVALID_PURCHASE_CYCLE_ATTACHMENT' using errcode='42501';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_attachment_v253 on public.purchase_cycle_attachments;
create trigger trg_guard_purchase_attachment_v253 before insert on public.purchase_cycle_attachments
for each row execute function public.verto_guard_purchase_attachment_v253();

-- Only INTERNATIONAL POs from the same tenant may become logistics shipment sources.
create or replace function public.verto_guard_purchase_order_shipment_source_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_existing public.purchase_order_shipment_sources%rowtype;
begin
  select * into v_existing from public.purchase_order_shipment_sources where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  if new.organization_id is distinct from public.get_my_org_id()
     or not exists (
       select 1 from public.purchase_orders po
       where po.id = new.purchase_order_id
         and po.organization_id = new.organization_id
         and po.purchase_scope = 'INTERNATIONAL'
         and po.status <> 'CANCELLED'
     ) then
    raise exception 'INVALID_PURCHASE_ORDER_SHIPMENT_SOURCE' using errcode='42501';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_order_shipment_source_v253 on public.purchase_order_shipment_sources;
create trigger trg_guard_purchase_order_shipment_source_v253 before insert on public.purchase_order_shipment_sources
for each row execute function public.verto_guard_purchase_order_shipment_source_v253();

-- Same supplier/org/scope and never a cancelled PO.
create or replace function public.verto_guard_purchase_invoice_link_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_po public.purchase_orders%rowtype;
begin
  if new.purchase_order_id is null then return new; end if;
  select * into v_po from public.purchase_orders where id = new.purchase_order_id;
  if not found or new.category::text <> 'PURCHASE'
     or v_po.organization_id <> new.organization_id
     or v_po.supplier_id <> new.client_id
     or v_po.purchase_scope <> new.purchase_scope::text
     or v_po.status = 'CANCELLED' then
    raise exception 'INVALID_PURCHASE_ORDER_INVOICE_LINK';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_invoice_link_v253 on public.invoices;
create trigger trg_guard_purchase_invoice_link_v253
before insert or update of purchase_order_id,organization_id,client_id,purchase_scope,category on public.invoices
for each row execute function public.verto_guard_purchase_invoice_link_v253();

-- PO identity is immutable; only lifecycle status/close metadata may progress.
create or replace function public.verto_guard_purchase_order_update_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
begin
  if old.organization_id is distinct from new.organization_id
     or old.order_number is distinct from new.order_number
     or old.supplier_id is distinct from new.supplier_id
     or old.purchase_scope is distinct from new.purchase_scope
     or old.currency_code is distinct from new.currency_code
     or old.created_at is distinct from new.created_at
     or old.created_by is distinct from new.created_by
     or old.created_by_name is distinct from new.created_by_name
     or old.note is distinct from new.note
     or old.write_id is distinct from new.write_id then
    raise exception 'IMMUTABLE_PURCHASE_ORDER_FACT';
  end if;
  if old.status in ('CLOSED','CANCELLED') and new.status is distinct from old.status then
    raise exception 'PURCHASE_ORDER_TERMINAL_STATE';
  end if;
  if old.status = 'RECEIVED' and new.status in ('OPEN','PARTIALLY_RECEIVED') then
    raise exception 'PURCHASE_ORDER_STATUS_REGRESSION';
  end if;
  if old.status = 'PARTIALLY_RECEIVED' and new.status = 'OPEN' then
    raise exception 'PURCHASE_ORDER_STATUS_REGRESSION';
  end if;
  if new.status = 'CLOSED' and (new.closed_at is null or btrim(coalesce(new.close_reason,'')) = '') then
    raise exception 'PURCHASE_ORDER_CLOSE_REASON_REQUIRED';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_order_update_v253 on public.purchase_orders;
create trigger trg_guard_purchase_order_update_v253
before update on public.purchase_orders
for each row execute function public.verto_guard_purchase_order_update_v253();

-- A GRN must belong to the same tenant/PO and cannot be posted after closure/cancellation.
create or replace function public.verto_guard_goods_receipt_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_po public.purchase_orders%rowtype; v_existing public.goods_receipts%rowtype;
begin
  select * into v_existing from public.goods_receipts where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  select * into v_po from public.purchase_orders where id = new.purchase_order_id;
  if not found or v_po.organization_id is distinct from new.organization_id
     or v_po.status in ('CLOSED','CANCELLED') then
    raise exception 'INVALID_GOODS_RECEIPT';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_goods_receipt_v253 on public.goods_receipts;
create trigger trg_guard_goods_receipt_v253 before insert on public.goods_receipts
for each row execute function public.verto_guard_goods_receipt_v253();

-- Serialize receipts on the PO line so two devices cannot both consume the same remaining quantity.
create or replace function public.verto_guard_grn_line_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_ordered integer; v_used bigint; v_po text; v_grn_po text; v_existing public.goods_receipt_lines%rowtype;
begin
  select * into v_existing from public.goods_receipt_lines where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  select ordered_quantity,purchase_order_id into v_ordered,v_po
    from public.purchase_order_lines where id = new.purchase_order_line_id for update;
  select purchase_order_id into v_grn_po from public.goods_receipts where id = new.goods_receipt_id;
  if v_ordered is null or v_po is distinct from v_grn_po then raise exception 'INVALID_GOODS_RECEIPT_LINE'; end if;
  select coalesce(sum(accepted_quantity),0) into v_used
    from public.goods_receipt_lines where purchase_order_line_id = new.purchase_order_line_id;
  if v_used + new.accepted_quantity > v_ordered then raise exception 'GOODS_RECEIPT_EXCEEDS_ORDERED_QUANTITY'; end if;
  return new;
end $$;
drop trigger if exists trg_guard_grn_line_v253 on public.goods_receipt_lines;
create trigger trg_guard_grn_line_v253 before insert on public.goods_receipt_lines
for each row execute function public.verto_guard_grn_line_v253();

-- Receipt allocation is the auditable bridge between accepted GRN units and invoice lines.
create or replace function public.verto_guard_purchase_receipt_allocation_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare
  v_match_qty integer; v_match_po text; v_match_org uuid; v_match_used bigint;
  v_receipt_qty integer; v_receipt_po text; v_receipt_used bigint;
  v_existing public.purchase_invoice_receipt_allocations%rowtype;
begin
  select * into v_existing from public.purchase_invoice_receipt_allocations where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  select ml.invoiced_quantity, ml.purchase_order_line_id, m.organization_id
    into v_match_qty, v_match_po, v_match_org
    from public.purchase_invoice_match_lines ml
    join public.purchase_invoice_matches m on m.id = ml.match_id
   where ml.id = new.match_line_id
   for update of ml;
  select grl.accepted_quantity, grl.purchase_order_line_id
    into v_receipt_qty, v_receipt_po
    from public.goods_receipt_lines grl
   where grl.id = new.goods_receipt_line_id
   for update;
  if v_match_qty is null or v_receipt_qty is null or v_match_po is distinct from v_receipt_po
     or v_match_org is distinct from new.organization_id then
    raise exception 'INVALID_PURCHASE_RECEIPT_ALLOCATION';
  end if;
  select coalesce(sum(allocated_quantity),0) into v_match_used
    from public.purchase_invoice_receipt_allocations where match_line_id = new.match_line_id;
  select coalesce(sum(allocated_quantity),0) into v_receipt_used
    from public.purchase_invoice_receipt_allocations where goods_receipt_line_id = new.goods_receipt_line_id;
  if v_match_used + new.allocated_quantity > v_match_qty
     or v_receipt_used + new.allocated_quantity > v_receipt_qty then
    raise exception 'PURCHASE_RECEIPT_ALLOCATION_EXCEEDS_AVAILABLE';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_receipt_allocation_v253 on public.purchase_invoice_receipt_allocations;
create trigger trg_guard_purchase_receipt_allocation_v253
before insert on public.purchase_invoice_receipt_allocations
for each row execute function public.verto_guard_purchase_receipt_allocation_v253();

-- A match is auditable: any over-tolerance decision must name reason and approver.
create or replace function public.verto_guard_three_way_match_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_existing public.purchase_invoice_matches%rowtype;
begin
  select * into v_existing from public.purchase_invoice_matches where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  if new.status = 'OVERRIDDEN' and (btrim(coalesce(new.variance_reason,'')) = '' or btrim(coalesce(new.approved_by,'')) = '') then
    raise exception 'THREE_WAY_MATCH_OVERRIDE_REQUIRES_REASON_AND_APPROVER';
  end if;
  if new.status = 'OVERRIDDEN' and not (
    public.is_current_user_org_admin()
    or (public.has_employee_permission('purchases_edit') and public.has_employee_permission('inventory_price'))
  ) then
    raise exception 'THREE_WAY_MATCH_OVERRIDE_PERMISSION_DENIED' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.invoices i where i.id = new.invoice_id and i.organization_id = new.organization_id
      and i.purchase_order_id = new.purchase_order_id and i.category::text = 'PURCHASE'
  ) then raise exception 'INVALID_THREE_WAY_MATCH'; end if;
  return new;
end $$;
drop trigger if exists trg_guard_three_way_match_v253 on public.purchase_invoice_matches;
create trigger trg_guard_three_way_match_v253 before insert on public.purchase_invoice_matches
for each row execute function public.verto_guard_three_way_match_v253();

-- Current payable is dynamic: later GRNs unlock the corresponding previously-invoiced quantity.
-- Invoice allocation is deterministic by (matched_at,id), so two invoices cannot consume the same accepted units.
create or replace function public.verto_purchase_payable_received_v253(p_invoice_id uuid)
returns bigint
language sql
stable
set search_path = public, pg_temp
as $$
  select coalesce(sum(a.allocated_quantity::bigint * ml.invoice_unit_price_minor),0)::bigint
  from public.purchase_invoice_receipt_allocations a
  join public.purchase_invoice_match_lines ml on ml.id = a.match_line_id
  join public.purchase_invoice_matches m on m.id = ml.match_id
  where m.invoice_id = p_invoice_id;
$$;

-- Permission is enforced server-side too; an app-only approval is not a security boundary.
create or replace function public.verto_guard_purchase_payment_override_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_existing public.purchase_payment_overrides%rowtype;
begin
  select * into v_existing from public.purchase_payment_overrides where id = new.id;
  if found and to_jsonb(v_existing) = to_jsonb(new) then return new; end if;
  if new.organization_id is distinct from public.get_my_org_id()
     or not exists (
       select 1 from public.invoices i
       where i.id = new.invoice_id
         and i.organization_id = new.organization_id
         and i.purchase_order_id is not null
     ) then
    raise exception 'INVALID_PURCHASE_PAYMENT_OVERRIDE' using errcode = '42501';
  end if;
  if not (
    public.is_current_user_org_admin()
    or (public.has_employee_permission('purchases_edit') and public.has_employee_permission('suppliers_add_payment'))
  ) then
    raise exception 'PURCHASE_PAYMENT_OVERRIDE_PERMISSION_DENIED' using errcode = '42501';
  end if;
  return new;
end $$;
drop trigger if exists trg_guard_purchase_payment_override_v253 on public.purchase_payment_overrides;
create trigger trg_guard_purchase_payment_override_v253
before insert on public.purchase_payment_overrides
for each row execute function public.verto_guard_purchase_payment_override_v253();

-- Final DB backstop: payment above the current GRN-backed payable requires a documented override.
create or replace function public.verto_guard_unreceived_purchase_payment_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
declare v_po text; v_payable bigint; v_paid bigint;
begin
  if new.reversed_payment_id is not null or coalesce(new.supplier_amount_minor,0) <= 0 then return new; end if;
  select purchase_order_id into v_po from public.invoices where id = new.invoice_id for update;
  if v_po is null then return new; end if;
  if not exists (select 1 from public.purchase_invoice_matches where invoice_id = new.invoice_id) then
    raise exception 'PURCHASE_PAYMENT_REQUIRES_THREE_WAY_MATCH';
  end if;
  v_payable := public.verto_purchase_payable_received_v253(new.invoice_id);
  select coalesce(sum(p.supplier_amount_minor),0) into v_paid
    from public.payments p
   where p.invoice_id = new.invoice_id and p.reversed_payment_id is null
     and not exists (select 1 from public.payments r where r.reversed_payment_id = p.id);
  if v_paid + new.supplier_amount_minor > v_payable and not exists (
      select 1 from public.purchase_payment_overrides o
       where o.invoice_id = new.invoice_id and o.payment_request_id = new.id::text
  ) then raise exception 'PAYMENT_EXCEEDS_RECEIVED_QUANTITY'; end if;
  return new;
end $$;
drop trigger if exists trg_guard_unreceived_purchase_payment_v253 on public.payments;
create trigger trg_guard_unreceived_purchase_payment_v253 before insert on public.payments
for each row execute function public.verto_guard_unreceived_purchase_payment_v253();

-- Idempotent sync RPCs preserve dependency ordering while keeping security-definer writes tenant-bound.
create or replace function public.verto_purchase_cycle_push_pre_v253(
  p_purchase_orders jsonb default '[]'::jsonb,
  p_purchase_order_lines jsonb default '[]'::jsonb,
  p_goods_receipts jsonb default '[]'::jsonb,
  p_goods_receipt_lines jsonb default '[]'::jsonb,
  p_attachments jsonb default '[]'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare v_org uuid := public.get_my_org_id();
begin
  if auth.uid() is null or v_org is null then raise exception 'auth_session_required' using errcode='28000'; end if;
  if exists (
    select 1 from jsonb_array_elements(coalesce(p_purchase_orders,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_purchase_order_lines,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_goods_receipts,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_goods_receipt_lines,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_attachments,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) then raise exception 'purchase_cycle_tenant_mismatch' using errcode='42501'; end if;

  -- Existing ids can never be adopted across organizations.
  if exists (
    select 1 from jsonb_array_elements(coalesce(p_purchase_orders,'[]'::jsonb)) x
    join public.purchase_orders po on po.id=x->>'id'
    where po.organization_id is distinct from v_org
  ) then raise exception 'purchase_order_id_tenant_conflict' using errcode='42501'; end if;

  insert into public.purchase_orders
  select r.* from jsonb_populate_recordset(null::public.purchase_orders, coalesce(p_purchase_orders,'[]'::jsonb)) r
  on conflict(id) do update set
    organization_id=excluded.organization_id, order_number=excluded.order_number,
    supplier_id=excluded.supplier_id, purchase_scope=excluded.purchase_scope,
    currency_code=excluded.currency_code,
    status=case
      when purchase_orders.status in ('CLOSED','CANCELLED') then purchase_orders.status
      when excluded.status in ('CLOSED','CANCELLED') then excluded.status
      when purchase_orders.status='RECEIVED' or excluded.status='RECEIVED' then 'RECEIVED'
      when purchase_orders.status='PARTIALLY_RECEIVED' or excluded.status='PARTIALLY_RECEIVED' then 'PARTIALLY_RECEIVED'
      else 'OPEN'
    end,
    created_at=excluded.created_at, created_by=excluded.created_by, created_by_name=excluded.created_by_name,
    closed_at=case
      when purchase_orders.status in ('CLOSED','CANCELLED') then purchase_orders.closed_at
      when excluded.status in ('CLOSED','CANCELLED') then excluded.closed_at
      else null
    end,
    close_reason=case
      when purchase_orders.status in ('CLOSED','CANCELLED') then purchase_orders.close_reason
      when excluded.status in ('CLOSED','CANCELLED') then excluded.close_reason
      else null
    end,
    note=excluded.note, write_id=excluded.write_id;

  if exists (
    select 1 from jsonb_array_elements(coalesce(p_purchase_order_lines,'[]'::jsonb)) x
    where not exists (select 1 from public.purchase_orders po where po.id=x->>'purchase_order_id' and po.organization_id=v_org)
  ) then raise exception 'purchase_order_line_parent_mismatch' using errcode='42501'; end if;
  insert into public.purchase_order_lines
  select r.* from jsonb_populate_recordset(null::public.purchase_order_lines, coalesce(p_purchase_order_lines,'[]'::jsonb)) r
  on conflict(id) do update set
    purchase_order_id=excluded.purchase_order_id, line_number=excluded.line_number,
    inventory_item_id=excluded.inventory_item_id, item_name_snapshot=excluded.item_name_snapshot,
    ordered_quantity=excluded.ordered_quantity, unit_price_minor=excluded.unit_price_minor;

  if exists (
    select 1 from jsonb_array_elements(coalesce(p_goods_receipts,'[]'::jsonb)) x
    where not exists (select 1 from public.purchase_orders po where po.id=x->>'purchase_order_id' and po.organization_id=v_org)
  ) then raise exception 'goods_receipt_parent_mismatch' using errcode='42501'; end if;
  insert into public.goods_receipts
  select r.* from jsonb_populate_recordset(null::public.goods_receipts, coalesce(p_goods_receipts,'[]'::jsonb)) r
  on conflict(id) do update set
    organization_id=excluded.organization_id, purchase_order_id=excluded.purchase_order_id,
    receipt_number=excluded.receipt_number, received_at=excluded.received_at,
    received_by=excluded.received_by, received_by_name=excluded.received_by_name,
    note=excluded.note, write_id=excluded.write_id;

  if exists (
    select 1 from jsonb_array_elements(coalesce(p_goods_receipt_lines,'[]'::jsonb)) x
    where not exists (
      select 1 from public.goods_receipts gr
      join public.purchase_orders po on po.id=gr.purchase_order_id
      where gr.id=x->>'goods_receipt_id' and po.organization_id=v_org
    )
  ) then raise exception 'goods_receipt_line_parent_mismatch' using errcode='42501'; end if;
  insert into public.goods_receipt_lines
  select r.* from jsonb_populate_recordset(null::public.goods_receipt_lines, coalesce(p_goods_receipt_lines,'[]'::jsonb)) r
  on conflict(id) do update set
    goods_receipt_id=excluded.goods_receipt_id, purchase_order_line_id=excluded.purchase_order_line_id,
    inventory_item_id=excluded.inventory_item_id, received_quantity=excluded.received_quantity,
    accepted_quantity=excluded.accepted_quantity, rejected_quantity=excluded.rejected_quantity,
    unit_cost_minor=excluded.unit_cost_minor;

  insert into public.purchase_cycle_attachments
  select r.* from jsonb_populate_recordset(null::public.purchase_cycle_attachments, coalesce(p_attachments,'[]'::jsonb)) r
  on conflict(id) do update set
    organization_id=excluded.organization_id, owner_type=excluded.owner_type, owner_id=excluded.owner_id,
    uri=excluded.uri, mime_type=excluded.mime_type, display_name=excluded.display_name,
    created_at=excluded.created_at, write_id=excluded.write_id;
end $$;

create or replace function public.verto_purchase_cycle_push_post_v253(
  p_matches jsonb default '[]'::jsonb,
  p_match_lines jsonb default '[]'::jsonb,
  p_allocations jsonb default '[]'::jsonb,
  p_payment_overrides jsonb default '[]'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare v_org uuid := public.get_my_org_id();
begin
  if auth.uid() is null or v_org is null then raise exception 'auth_session_required' using errcode='28000'; end if;
  if exists (
    select 1 from jsonb_array_elements(coalesce(p_matches,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_match_lines,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_allocations,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) or exists (
    select 1 from jsonb_array_elements(coalesce(p_payment_overrides,'[]'::jsonb)) x
    where nullif(x->>'organization_id','')::uuid is distinct from v_org
  ) then raise exception 'purchase_cycle_tenant_mismatch' using errcode='42501'; end if;

  insert into public.purchase_invoice_matches
  select r.* from jsonb_populate_recordset(null::public.purchase_invoice_matches, coalesce(p_matches,'[]'::jsonb)) r
  on conflict(id) do update set
    organization_id=excluded.organization_id, invoice_id=excluded.invoice_id,
    purchase_order_id=excluded.purchase_order_id, status=excluded.status,
    quantity_variance_units=excluded.quantity_variance_units, price_variance_minor=excluded.price_variance_minor,
    quantity_tolerance_units=excluded.quantity_tolerance_units, price_tolerance_minor=excluded.price_tolerance_minor,
    invoice_amount_minor=excluded.invoice_amount_minor, payable_amount_minor=excluded.payable_amount_minor,
    variance_reason=excluded.variance_reason, approved_by=excluded.approved_by,
    approved_by_name=excluded.approved_by_name, matched_at=excluded.matched_at, write_id=excluded.write_id;

  if exists (
    select 1 from jsonb_array_elements(coalesce(p_match_lines,'[]'::jsonb)) x
    where not exists (select 1 from public.purchase_invoice_matches m where m.id=x->>'match_id' and m.organization_id=v_org)
  ) then raise exception 'purchase_match_line_parent_mismatch' using errcode='42501'; end if;
  insert into public.purchase_invoice_match_lines
  select r.* from jsonb_populate_recordset(null::public.purchase_invoice_match_lines, coalesce(p_match_lines,'[]'::jsonb)) r
  on conflict(id) do update set
    match_id=excluded.match_id, invoice_item_id=excluded.invoice_item_id,
    purchase_order_line_id=excluded.purchase_order_line_id, ordered_quantity=excluded.ordered_quantity,
    accepted_quantity=excluded.accepted_quantity, invoiced_quantity=excluded.invoiced_quantity,
    po_unit_price_minor=excluded.po_unit_price_minor, invoice_unit_price_minor=excluded.invoice_unit_price_minor,
    quantity_variance_units=excluded.quantity_variance_units, price_variance_minor=excluded.price_variance_minor,
    payable_amount_minor=excluded.payable_amount_minor;

  insert into public.purchase_invoice_receipt_allocations
  select r.* from jsonb_populate_recordset(null::public.purchase_invoice_receipt_allocations, coalesce(p_allocations,'[]'::jsonb)) r
  on conflict(id) do update set
    organization_id=excluded.organization_id, match_line_id=excluded.match_line_id,
    goods_receipt_line_id=excluded.goods_receipt_line_id, allocated_quantity=excluded.allocated_quantity,
    created_at=excluded.created_at, write_id=excluded.write_id;

  insert into public.purchase_payment_overrides
  select r.* from jsonb_populate_recordset(null::public.purchase_payment_overrides, coalesce(p_payment_overrides,'[]'::jsonb)) r
  on conflict(id) do update set
    organization_id=excluded.organization_id, invoice_id=excluded.invoice_id,
    payment_request_id=excluded.payment_request_id, requested_amount_minor=excluded.requested_amount_minor,
    payable_before_override_minor=excluded.payable_before_override_minor, reason=excluded.reason,
    approved_by=excluded.approved_by, approved_by_name=excluded.approved_by_name, created_at=excluded.created_at;
end $$;

revoke all on function public.verto_purchase_cycle_push_pre_v253(jsonb,jsonb,jsonb,jsonb,jsonb) from public, anon;
grant execute on function public.verto_purchase_cycle_push_pre_v253(jsonb,jsonb,jsonb,jsonb,jsonb) to authenticated;
revoke all on function public.verto_purchase_cycle_push_post_v253(jsonb,jsonb,jsonb,jsonb) from public, anon;
grant execute on function public.verto_purchase_cycle_push_post_v253(jsonb,jsonb,jsonb,jsonb) to authenticated;

-- New purchase-cycle facts are tenant-isolated. Posted GRN/match facts are append-only.
do $$
declare t text; p text;
begin
  foreach t in array array[
    'purchase_orders','goods_receipts','purchase_cycle_attachments','purchase_invoice_matches',
    'purchase_payment_overrides','purchase_order_shipment_sources'
  ] loop
    execute format('alter table public.%I enable row level security',t);
    execute format('revoke all on public.%I from anon',t);
    execute format('revoke all on public.%I from authenticated',t);
    if t = 'purchase_orders' then
      execute format('grant select, insert, update on public.%I to authenticated',t);
    else
      execute format('grant select, insert on public.%I to authenticated',t);
    end if;
    p := t || '_org_select_v253'; execute format('drop policy if exists %I on public.%I',p,t);
    execute format('create policy %I on public.%I for select to authenticated using (organization_id=(select organization_id from public.app_users where id=auth.uid() and coalesce(is_active,true) limit 1))',p,t);
    p := t || '_org_insert_v253'; execute format('drop policy if exists %I on public.%I',p,t);
    execute format('create policy %I on public.%I for insert to authenticated with check (organization_id=(select organization_id from public.app_users where id=auth.uid() and coalesce(is_active,true) limit 1))',p,t);
    if t = 'purchase_orders' then
      p := t || '_org_update_v253'; execute format('drop policy if exists %I on public.%I',p,t);
      execute format('create policy %I on public.%I for update to authenticated using (organization_id=(select organization_id from public.app_users where id=auth.uid() and coalesce(is_active,true) limit 1)) with check (organization_id=(select organization_id from public.app_users where id=auth.uid() and coalesce(is_active,true) limit 1))',p,t);
    end if;
  end loop;
end $$;

-- Child rows inherit tenant visibility from their parent; no unauthenticated direct access.
alter table public.purchase_order_lines enable row level security;
revoke all on public.purchase_order_lines from anon, authenticated;
grant select, insert on public.purchase_order_lines to authenticated;
drop policy if exists purchase_order_lines_org_select_v253 on public.purchase_order_lines;
create policy purchase_order_lines_org_select_v253 on public.purchase_order_lines for select to authenticated using (
  exists (select 1 from public.purchase_orders po where po.id=purchase_order_id and po.organization_id=public.get_my_org_id())
);
drop policy if exists purchase_order_lines_org_insert_v253 on public.purchase_order_lines;
create policy purchase_order_lines_org_insert_v253 on public.purchase_order_lines for insert to authenticated with check (
  exists (select 1 from public.purchase_orders po where po.id=purchase_order_id and po.organization_id=public.get_my_org_id())
);

alter table public.goods_receipt_lines enable row level security;
revoke all on public.goods_receipt_lines from anon, authenticated;
grant select, insert on public.goods_receipt_lines to authenticated;
drop policy if exists goods_receipt_lines_org_select_v253 on public.goods_receipt_lines;
create policy goods_receipt_lines_org_select_v253 on public.goods_receipt_lines for select to authenticated using (
  exists (select 1 from public.goods_receipts gr where gr.id=goods_receipt_id and gr.organization_id=public.get_my_org_id())
);
drop policy if exists goods_receipt_lines_org_insert_v253 on public.goods_receipt_lines;
create policy goods_receipt_lines_org_insert_v253 on public.goods_receipt_lines for insert to authenticated with check (
  exists (select 1 from public.goods_receipts gr where gr.id=goods_receipt_id and gr.organization_id=public.get_my_org_id())
);

alter table public.purchase_invoice_match_lines enable row level security;
revoke all on public.purchase_invoice_match_lines from anon, authenticated;
grant select, insert on public.purchase_invoice_match_lines to authenticated;
drop policy if exists purchase_invoice_match_lines_org_select_v253 on public.purchase_invoice_match_lines;
create policy purchase_invoice_match_lines_org_select_v253 on public.purchase_invoice_match_lines for select to authenticated using (
  exists (select 1 from public.purchase_invoice_matches m where m.id=match_id and m.organization_id=public.get_my_org_id())
);
drop policy if exists purchase_invoice_match_lines_org_insert_v253 on public.purchase_invoice_match_lines;
create policy purchase_invoice_match_lines_org_insert_v253 on public.purchase_invoice_match_lines for insert to authenticated with check (
  exists (select 1 from public.purchase_invoice_matches m where m.id=match_id and m.organization_id=public.get_my_org_id())
);

alter table public.purchase_invoice_receipt_allocations enable row level security;
revoke all on public.purchase_invoice_receipt_allocations from anon, authenticated;
grant select, insert on public.purchase_invoice_receipt_allocations to authenticated;
drop policy if exists purchase_invoice_receipt_allocations_org_select_v253 on public.purchase_invoice_receipt_allocations;
create policy purchase_invoice_receipt_allocations_org_select_v253 on public.purchase_invoice_receipt_allocations for select to authenticated using (
  organization_id=public.get_my_org_id()
  and exists (select 1 from public.purchase_invoice_match_lines ml join public.purchase_invoice_matches m on m.id=ml.match_id where ml.id=match_line_id and m.organization_id=public.get_my_org_id())
);
drop policy if exists purchase_invoice_receipt_allocations_org_insert_v253 on public.purchase_invoice_receipt_allocations;
create policy purchase_invoice_receipt_allocations_org_insert_v253 on public.purchase_invoice_receipt_allocations for insert to authenticated with check (
  organization_id=public.get_my_org_id()
  and exists (select 1 from public.purchase_invoice_match_lines ml join public.purchase_invoice_matches m on m.id=ml.match_id where ml.id=match_line_id and m.organization_id=public.get_my_org_id())
);

-- Immutable facts must never be silently rewritten after posting.
create or replace function public.verto_reject_purchase_fact_mutation_v253()
returns trigger language plpgsql set search_path = public, pg_temp as $$
begin
  if tg_op = 'UPDATE' and to_jsonb(new) = to_jsonb(old) then return old; end if;
  raise exception 'IMMUTABLE_PURCHASE_CYCLE_FACT';
end $$;
do $$
declare t text;
begin
  foreach t in array array['purchase_order_lines','goods_receipts','goods_receipt_lines','purchase_cycle_attachments','purchase_invoice_matches','purchase_invoice_match_lines','purchase_invoice_receipt_allocations','purchase_payment_overrides'] loop
    execute format('drop trigger if exists trg_immutable_purchase_fact_v253 on public.%I', t);
    execute format('create trigger trg_immutable_purchase_fact_v253 before update or delete on public.%I for each row execute function public.verto_reject_purchase_fact_mutation_v253()', t);
  end loop;
end $$;

-- Inventory ledger structure
-- Verto v257 — canonical inventory ledger + cost revision contract.
-- Additive and old-client compatible. Existing rows remain contract_version=1.
-- Apply on Supabase/Postgres before enabling contract v2 writers.

alter table public.inventory_movements add column if not exists movement_kind text;
alter table public.inventory_movements add column if not exists signed_base_quantity bigint;
alter table public.inventory_movements add column if not exists source_type text;
alter table public.inventory_movements add column if not exists source_id text;
alter table public.inventory_movements add column if not exists source_line_id text;
alter table public.inventory_movements add column if not exists command_id text;
alter table public.inventory_movements add column if not exists idempotency_key text;
alter table public.inventory_movements add column if not exists posting_group_id text;
alter table public.inventory_movements add column if not exists reverses_movement_id text;
alter table public.inventory_movements add column if not exists conversion_factor_snapshot text;
alter table public.inventory_movements add column if not exists occurred_at bigint;
alter table public.inventory_movements add column if not exists recorded_at bigint;
alter table public.inventory_movements add column if not exists server_accepted_at timestamptz;
alter table public.inventory_movements add column if not exists server_sequence bigint;
alter table public.inventory_movements add column if not exists created_by uuid;
alter table public.inventory_movements add column if not exists device_id text;
alter table public.inventory_movements add column if not exists contract_version integer not null default 1;

create unique index if not exists inventory_items_org_id_identity
    on public.inventory_items(organization_id, id);
create unique index if not exists inventory_movements_org_idempotency
    on public.inventory_movements(organization_id, idempotency_key)
    where idempotency_key is not null;
create unique index if not exists inventory_movements_org_reversal
    on public.inventory_movements(organization_id, reverses_movement_id)
    where reverses_movement_id is not null;
create index if not exists inventory_movements_org_item_sequence
    on public.inventory_movements(organization_id, item_id, server_sequence);
create index if not exists inventory_movements_org_item_kind_occurred
    on public.inventory_movements(organization_id, item_id, movement_kind, occurred_at);
create index if not exists inventory_movements_org_source
    on public.inventory_movements(organization_id, source_type, source_id, source_line_id);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'inventory_movements_v2_contract_check') then
        alter table public.inventory_movements
            add constraint inventory_movements_v2_contract_check check (
                contract_version < 2 or (
                    organization_id is not null
                    and movement_kind in (
                        'OPENING_BALANCE','PURCHASE','SALE','SALES_RETURN','PURCHASE_RETURN',
                        'MANUAL_ADJUSTMENT','SHIPMENT_RECEIPT','REVERSAL','MIGRATION_RECONCILIATION'
                    )
                    and signed_base_quantity is not null and signed_base_quantity <> 0
                    and nullif(btrim(source_type), '') is not null
                    and nullif(btrim(source_id), '') is not null
                    and nullif(btrim(command_id), '') is not null
                    and nullif(btrim(idempotency_key), '') is not null
                    and occurred_at is not null and recorded_at is not null
                    and created_by is not null
                    and nullif(btrim(device_id), '') is not null
                    and ((movement_kind = 'REVERSAL' and reverses_movement_id is not null)
                         or (movement_kind <> 'REVERSAL' and reverses_movement_id is null))
                    and (movement_kind not in ('PURCHASE','SALES_RETURN','SHIPMENT_RECEIPT') or signed_base_quantity > 0)
                    and (movement_kind not in ('SALE','PURCHASE_RETURN') or signed_base_quantity < 0)
                )
            ) not valid;
    end if;
end $$;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'inventory_movements_org_item_fk') then
        alter table public.inventory_movements
            add constraint inventory_movements_org_item_fk
            foreign key (organization_id, item_id)
            references public.inventory_items(organization_id, id)
            on delete restrict
            not valid;
    end if;
end $$;

create table if not exists public.inventory_cost_revisions (
    cost_revision_id text primary key,
    organization_id uuid not null,
    item_id text not null,
    source_type text not null,
    source_id text not null,
    source_line_id text,
    revision_kind text not null check (revision_kind in (
        'LOCAL_PURCHASE_APPROVED','LANDED_COST_PROVISIONAL','LANDED_COST_APPROVED',
        'PURCHASE_CANCELLATION','COST_CORRECTION','REVERSAL','MIGRATION_BASELINE'
    )),
    direct_purchase_cost_minor bigint not null check (direct_purchase_cost_minor >= 0),
    landed_cost_per_base_unit_minor bigint not null check (landed_cost_per_base_unit_minor >= 0),
    approved_inventory_cost_minor bigint not null check (approved_inventory_cost_minor >= 0),
    currency_code text not null,
    exchange_rate_snapshot numeric not null check (exchange_rate_snapshot > 0),
    allocation_basis text not null default '',
    allocation_residual_minor bigint not null default 0,
    is_provisional boolean not null default false,
    reverses_cost_revision_id text,
    command_id text not null,
    idempotency_key text not null,
    cost_sequence bigint,
    approved_at bigint not null,
    recorded_at bigint not null,
    created_by uuid not null,
    device_id text not null,
    contract_version integer not null default 2 check (contract_version >= 2),
    constraint inventory_cost_revisions_org_item_fk foreign key (organization_id, item_id)
        references public.inventory_items(organization_id, id) on delete restrict,
    constraint inventory_cost_revisions_reversal_shape check (
        (revision_kind = 'REVERSAL' and reverses_cost_revision_id is not null)
        or (revision_kind <> 'REVERSAL' and reverses_cost_revision_id is null)
    )
);

create unique index if not exists inventory_cost_revisions_org_idempotency
    on public.inventory_cost_revisions(organization_id, idempotency_key);
create unique index if not exists inventory_cost_revisions_org_reversal
    on public.inventory_cost_revisions(organization_id, reverses_cost_revision_id)
    where reverses_cost_revision_id is not null;
create index if not exists inventory_cost_revisions_org_item_sequence
    on public.inventory_cost_revisions(organization_id, item_id, cost_sequence);
create index if not exists inventory_cost_revisions_org_source
    on public.inventory_cost_revisions(organization_id, source_type, source_id, source_line_id);

-- Do not validate the legacy movement CHECK/FK or backfill contract v1 rows here.
-- v258 owns canonical reconciliation/backfill and post-backfill validation.

-- Inventory reconciliation structures/RPCs (no reconciliation is started)
-- Verto v258 — centrally owned, resumable inventory reconciliation.
-- Prerequisite: apply v257_inventory_ledger_contract.sql first.
-- IMPORTANT: lock CENTRAL only after verifying the central inventory snapshot is complete.
-- Otherwise lock one OWNER_DEVICE and stage/finalize that owner's snapshot exactly once.

create extension if not exists pgcrypto;
create sequence if not exists public.inventory_reconciliation_server_sequence_seq;

create table if not exists public.inventory_reconciliation_authorities (
    organization_id uuid not null,
    contract_version integer not null,
    authority_kind text not null check (authority_kind in ('CENTRAL','OWNER_DEVICE')),
    source_device_id text,
    locked_by uuid not null,
    locked_at timestamptz not null default now(),
    status text not null check (status in ('CAPTURING','READY','COMPLETE')),
    expected_item_count integer,
    snapshot_item_count integer not null default 0,
    primary key (organization_id, contract_version),
    constraint inventory_reconciliation_owner_device_shape check (
        (authority_kind = 'CENTRAL' and source_device_id is null)
        or (authority_kind = 'OWNER_DEVICE' and nullif(btrim(source_device_id), '') is not null)
    )
);

create table if not exists public.inventory_reconciliation_canonical_snapshots (
    organization_id uuid not null,
    contract_version integer not null,
    item_id text not null,
    quantity bigint not null,
    authority_kind text not null check (authority_kind in ('CENTRAL','OWNER_DEVICE')),
    source_device_id text,
    captured_at timestamptz not null default now(),
    primary key (organization_id, contract_version, item_id),
    foreign key (organization_id, contract_version)
        references public.inventory_reconciliation_authorities(organization_id, contract_version)
        on delete restrict
);

create table if not exists public.inventory_reconciliation_markers (
    organization_id uuid not null,
    contract_version integer not null,
    item_id text not null,
    authority_kind text not null,
    source_device_id text,
    canonical_snapshot bigint not null,
    legacy_ledger_balance bigint not null,
    reconciliation_delta bigint not null,
    reconciliation_movement_id text,
    idempotency_key text not null,
    server_sequence bigint,
    marker_checksum text not null,
    approved_at bigint not null,
    server_accepted_at bigint not null,
    approved_by uuid not null,
    created_at timestamptz not null default now(),
    primary key (organization_id, contract_version, item_id),
    unique (organization_id, idempotency_key),
    unique (marker_checksum),
    foreign key (organization_id, contract_version)
        references public.inventory_reconciliation_authorities(organization_id, contract_version)
        on delete restrict
);

create table if not exists public.inventory_reconciliation_quarantine (
    organization_id uuid not null,
    contract_version integer not null,
    item_id text not null,
    reason text not null,
    canonical_snapshot bigint,
    legacy_ledger_balance bigint,
    detected_at timestamptz not null default now(),
    resolved_at timestamptz,
    primary key (organization_id, contract_version, item_id)
);

alter table public.inventory_reconciliation_authorities enable row level security;
alter table public.inventory_reconciliation_canonical_snapshots enable row level security;
alter table public.inventory_reconciliation_markers enable row level security;
alter table public.inventory_reconciliation_quarantine enable row level security;

drop policy if exists inventory_reconciliation_authorities_select on public.inventory_reconciliation_authorities;
create policy inventory_reconciliation_authorities_select on public.inventory_reconciliation_authorities
for select using (organization_id = public.get_my_org_id());

drop policy if exists inventory_reconciliation_snapshots_select on public.inventory_reconciliation_canonical_snapshots;
create policy inventory_reconciliation_snapshots_select on public.inventory_reconciliation_canonical_snapshots
for select using (organization_id = public.get_my_org_id());

drop policy if exists inventory_reconciliation_markers_select on public.inventory_reconciliation_markers;
create policy inventory_reconciliation_markers_select on public.inventory_reconciliation_markers
for select using (organization_id = public.get_my_org_id());

drop policy if exists inventory_reconciliation_quarantine_select on public.inventory_reconciliation_quarantine;
create policy inventory_reconciliation_quarantine_select on public.inventory_reconciliation_quarantine
for select using (organization_id = public.get_my_org_id());

-- Freeze the legacy baseline while an item's authoritative marker is incomplete.
-- The reconciliation RPC uses a transaction-local bypass only for its own server-owned movement.
create or replace function public.inventory_reconciliation_item_guard_v2()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid;
    v_item text;
begin
    if tg_op = 'DELETE' then
        v_org := old.organization_id;
        v_item := old.id::text;
    else
        v_org := new.organization_id;
        v_item := new.id::text;
    end if;
    if current_setting('verto.inventory_reconciliation_internal', true) = 'on' then
        if tg_op = 'DELETE' then return old; else return new; end if;
    end if;

    if tg_op = 'INSERT' then
        if exists (
            select 1 from public.inventory_reconciliation_authorities a
             where a.organization_id = new.organization_id
               and a.contract_version = 2
               and a.status in ('CAPTURING','READY')
        ) then
            raise exception 'INVENTORY_RECONCILIATION_REQUIRED' using errcode = '55000';
        end if;
        return new;
    end if;

    if tg_op = 'DELETE' or new.quantity is distinct from old.quantity then
        if exists (
            select 1 from public.inventory_reconciliation_authorities a
             where a.organization_id = v_org
               and a.contract_version = 2
               and a.status in ('CAPTURING','READY')
        ) and not exists (
            select 1 from public.inventory_reconciliation_markers m
             where m.organization_id = v_org
               and m.contract_version = 2
               and m.item_id = v_item
        ) then
            raise exception 'INVENTORY_RECONCILIATION_REQUIRED' using errcode = '55000';
        end if;
    end if;
    if tg_op = 'DELETE' then return old; else return new; end if;
end $$;

drop trigger if exists inventory_reconciliation_item_guard_v2 on public.inventory_items;
create trigger inventory_reconciliation_item_guard_v2
before insert or update or delete on public.inventory_items
for each row execute function public.inventory_reconciliation_item_guard_v2();

create or replace function public.inventory_reconciliation_movement_guard_v2()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid;
    v_item text;
begin
    if tg_op = 'DELETE' then
        v_org := old.organization_id;
        v_item := old.item_id::text;
    else
        v_org := new.organization_id;
        v_item := new.item_id::text;
    end if;
    if current_setting('verto.inventory_reconciliation_internal', true) = 'on' or v_item is null then
        if tg_op = 'DELETE' then return old; else return new; end if;
    end if;
    if exists (
        select 1 from public.inventory_reconciliation_authorities a
         where a.organization_id = v_org
           and a.contract_version = 2
           and a.status in ('CAPTURING','READY')
    ) and not exists (
        select 1 from public.inventory_reconciliation_markers m
         where m.organization_id = v_org
           and m.contract_version = 2
           and m.item_id = v_item
    ) then
        raise exception 'INVENTORY_RECONCILIATION_REQUIRED' using errcode = '55000';
    end if;
    if tg_op = 'DELETE' then return old; else return new; end if;
end $$;

drop trigger if exists inventory_reconciliation_movement_guard_v2 on public.inventory_movements;
create trigger inventory_reconciliation_movement_guard_v2
before insert or update or delete on public.inventory_movements
for each row execute function public.inventory_reconciliation_movement_guard_v2();

-- Admin locks the single authority. Repeating with a different authority is forbidden.
create or replace function public.inventory_lock_reconciliation_authority_v2(
    p_contract_version integer,
    p_authority_kind text,
    p_source_device_id text default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid := public.get_my_org_id();
    v_role text := public.get_my_role();
    v_existing public.inventory_reconciliation_authorities%rowtype;
begin
    if v_org is null or auth.uid() is null then
        raise exception 'AUTH_SESSION_REQUIRED' using errcode = '42501';
    end if;
    if coalesce(v_role, '') <> 'admin' then
        raise exception 'ADMIN_REQUIRED' using errcode = '42501';
    end if;
    if p_contract_version <> 2 then
        raise exception 'UNSUPPORTED_RECONCILIATION_CONTRACT';
    end if;
    if p_authority_kind not in ('CENTRAL','OWNER_DEVICE') then
        raise exception 'INVALID_RECONCILIATION_AUTHORITY';
    end if;
    if p_authority_kind = 'OWNER_DEVICE' and nullif(btrim(coalesce(p_source_device_id,'')), '') is null then
        raise exception 'OWNER_DEVICE_ID_REQUIRED';
    end if;

    select * into v_existing
      from public.inventory_reconciliation_authorities
     where organization_id = v_org and contract_version = p_contract_version
     for update;

    if found then
        if v_existing.authority_kind <> p_authority_kind
           or coalesce(v_existing.source_device_id,'') <> coalesce(p_source_device_id,'') then
            raise exception 'RECONCILIATION_AUTHORITY_ALREADY_LOCKED';
        end if;
        return;
    end if;

    insert into public.inventory_reconciliation_authorities(
        organization_id, contract_version, authority_kind, source_device_id,
        locked_by, status, expected_item_count, snapshot_item_count
    ) values (
        v_org, p_contract_version, p_authority_kind,
        case when p_authority_kind = 'OWNER_DEVICE' then p_source_device_id else null end,
        auth.uid(), case when p_authority_kind = 'CENTRAL' then 'READY' else 'CAPTURING' end,
        null, 0
    );

    if p_authority_kind = 'CENTRAL' then
        insert into public.inventory_reconciliation_canonical_snapshots(
            organization_id, contract_version, item_id, quantity, authority_kind, source_device_id
        )
        select organization_id, p_contract_version, id::text, quantity::bigint, 'CENTRAL', null
          from public.inventory_items
         where organization_id = v_org
        on conflict (organization_id, contract_version, item_id) do nothing;

        update public.inventory_reconciliation_authorities a
           set snapshot_item_count = (
               select count(*) from public.inventory_reconciliation_canonical_snapshots s
                where s.organization_id = v_org and s.contract_version = p_contract_version
           )
         where a.organization_id = v_org and a.contract_version = p_contract_version;
    end if;
end $$;

-- OWNER_DEVICE path: locked admin/user stages deterministic pages, then finalizes expected count.
create or replace function public.inventory_stage_owner_reconciliation_snapshot_v2(
    p_contract_version integer,
    p_device_id text,
    p_rows jsonb
)
returns integer
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid := public.get_my_org_id();
    v_authority public.inventory_reconciliation_authorities%rowtype;
    v_count integer;
begin
    select * into v_authority
      from public.inventory_reconciliation_authorities
     where organization_id = v_org and contract_version = p_contract_version
     for update;
    if not found or v_authority.authority_kind <> 'OWNER_DEVICE' or v_authority.status <> 'CAPTURING' then
        raise exception 'OWNER_RECONCILIATION_AUTHORITY_NOT_CAPTURING';
    end if;
    if v_authority.locked_by <> auth.uid() or v_authority.source_device_id <> p_device_id then
        raise exception 'OWNER_RECONCILIATION_AUTHORITY_MISMATCH' using errcode = '42501';
    end if;
    if jsonb_typeof(p_rows) <> 'array' then
        raise exception 'SNAPSHOT_ROWS_MUST_BE_ARRAY';
    end if;

    insert into public.inventory_reconciliation_canonical_snapshots(
        organization_id, contract_version, item_id, quantity, authority_kind, source_device_id
    )
    select v_org, p_contract_version, x.item_id, x.quantity, 'OWNER_DEVICE', p_device_id
      from jsonb_to_recordset(p_rows) as x(item_id text, quantity bigint)
     where nullif(btrim(x.item_id), '') is not null
    on conflict (organization_id, contract_version, item_id) do update
       set quantity = excluded.quantity,
           source_device_id = excluded.source_device_id,
           captured_at = now();

    select count(*) into v_count
      from public.inventory_reconciliation_canonical_snapshots
     where organization_id = v_org and contract_version = p_contract_version;
    update public.inventory_reconciliation_authorities
       set snapshot_item_count = v_count
     where organization_id = v_org and contract_version = p_contract_version;
    return v_count;
end $$;

create or replace function public.inventory_finalize_owner_reconciliation_snapshot_v2(
    p_contract_version integer,
    p_device_id text,
    p_expected_item_count integer
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid := public.get_my_org_id();
    v_authority public.inventory_reconciliation_authorities%rowtype;
begin
    select * into v_authority
      from public.inventory_reconciliation_authorities
     where organization_id = v_org and contract_version = p_contract_version
     for update;
    if not found or v_authority.authority_kind <> 'OWNER_DEVICE' then
        raise exception 'OWNER_RECONCILIATION_AUTHORITY_NOT_FOUND';
    end if;
    if v_authority.locked_by <> auth.uid() or v_authority.source_device_id <> p_device_id then
        raise exception 'OWNER_RECONCILIATION_AUTHORITY_MISMATCH' using errcode = '42501';
    end if;
    if p_expected_item_count < 0 or v_authority.snapshot_item_count <> p_expected_item_count then
        raise exception 'OWNER_RECONCILIATION_SNAPSHOT_INCOMPLETE';
    end if;
    update public.inventory_reconciliation_authorities
       set expected_item_count = p_expected_item_count, status = 'READY'
     where organization_id = v_org and contract_version = p_contract_version;
end $$;

-- Returns one deterministic page. Every item either yields COMPLETE or QUARANTINED.
create or replace function public.inventory_prepare_reconciliation_batch_v2(
    p_contract_version integer,
    p_after_item_id text default '',
    p_limit integer default 200
)
returns table(
    organization_id uuid,
    item_id text,
    contract_version integer,
    status text,
    canonical_snapshot bigint,
    legacy_ledger_balance bigint,
    reconciliation_delta bigint,
    marker_checksum text,
    reconciliation_movement_id text,
    idempotency_key text,
    server_sequence bigint,
    approved_at bigint,
    server_accepted_at bigint,
    approved_by text,
    authority_kind text,
    source_device_id text,
    quarantine_reason text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid := public.get_my_org_id();
    v_authority public.inventory_reconciliation_authorities%rowtype;
    v_snapshot record;
    v_marker public.inventory_reconciliation_markers%rowtype;
    v_quarantine public.inventory_reconciliation_quarantine%rowtype;
    v_balance bigint;
    v_delta bigint;
    v_key text;
    v_movement_id text;
    v_sequence bigint;
    v_now_ms bigint;
    v_checksum text;
begin
    if v_org is null or auth.uid() is null then
        raise exception 'AUTH_SESSION_REQUIRED' using errcode = '42501';
    end if;
    if p_contract_version <> 2 then
        raise exception 'UNSUPPORTED_RECONCILIATION_CONTRACT';
    end if;
    if p_limit < 1 or p_limit > 500 then
        raise exception 'INVALID_RECONCILIATION_BATCH_SIZE';
    end if;

    select * into v_authority
      from public.inventory_reconciliation_authorities
     where organization_id = v_org and contract_version = p_contract_version;
    if not found or v_authority.status not in ('READY','COMPLETE') then
        raise exception 'RECONCILIATION_AUTHORITY_NOT_LOCKED';
    end if;

    for v_snapshot in
        select s.item_id, s.quantity
          from public.inventory_reconciliation_canonical_snapshots s
         where s.organization_id = v_org
           and s.contract_version = p_contract_version
           and s.item_id > coalesce(p_after_item_id, '')
         order by s.item_id
         limit p_limit
    loop
        -- Serialize preparation for the same company/item before checking the immutable marker.
        perform pg_advisory_xact_lock(hashtext(v_org::text), hashtext(v_snapshot.item_id));

        select * into v_marker
          from public.inventory_reconciliation_markers m
         where m.organization_id = v_org
           and m.contract_version = p_contract_version
           and m.item_id = v_snapshot.item_id;
        if found then
            organization_id := v_org; item_id := v_snapshot.item_id; contract_version := p_contract_version;
            status := 'COMPLETE'; canonical_snapshot := v_marker.canonical_snapshot;
            legacy_ledger_balance := v_marker.legacy_ledger_balance; reconciliation_delta := v_marker.reconciliation_delta;
            marker_checksum := v_marker.marker_checksum; reconciliation_movement_id := v_marker.reconciliation_movement_id;
            idempotency_key := v_marker.idempotency_key; server_sequence := v_marker.server_sequence;
            approved_at := v_marker.approved_at; server_accepted_at := v_marker.server_accepted_at;
            approved_by := v_marker.approved_by::text; authority_kind := v_marker.authority_kind;
            source_device_id := v_marker.source_device_id; quarantine_reason := null;
            return next;
            continue;
        end if;

        select * into v_quarantine
          from public.inventory_reconciliation_quarantine q
         where q.organization_id = v_org
           and q.contract_version = p_contract_version
           and q.item_id = v_snapshot.item_id
           and q.resolved_at is null;
        if found then
            organization_id := v_org; item_id := v_snapshot.item_id; contract_version := p_contract_version;
            status := 'QUARANTINED'; canonical_snapshot := v_quarantine.canonical_snapshot;
            legacy_ledger_balance := v_quarantine.legacy_ledger_balance; reconciliation_delta := null;
            marker_checksum := null; reconciliation_movement_id := null; idempotency_key := null;
            server_sequence := null; approved_at := null; server_accepted_at := null; approved_by := null;
            authority_kind := v_authority.authority_kind; source_device_id := v_authority.source_device_id;
            quarantine_reason := v_quarantine.reason;
            return next;
            continue;
        end if;

        if not exists (
            select 1 from public.inventory_items i
             where i.organization_id = v_org and i.id = v_snapshot.item_id::uuid
        ) then
            insert into public.inventory_reconciliation_quarantine(
                organization_id, contract_version, item_id, reason, canonical_snapshot
            ) values (v_org, p_contract_version, v_snapshot.item_id, 'ITEM_NOT_ON_SERVER', v_snapshot.quantity)
            on conflict (organization_id, contract_version, item_id) do nothing;
            organization_id := v_org; item_id := v_snapshot.item_id; contract_version := p_contract_version;
            status := 'QUARANTINED'; canonical_snapshot := v_snapshot.quantity; legacy_ledger_balance := null;
            reconciliation_delta := null; marker_checksum := null; reconciliation_movement_id := null;
            idempotency_key := null; server_sequence := null; approved_at := null; server_accepted_at := null;
            approved_by := null; authority_kind := v_authority.authority_kind;
            source_device_id := v_authority.source_device_id; quarantine_reason := 'ITEM_NOT_ON_SERVER';
            return next;
            continue;
        end if;

        select coalesce(sum(
            case
                when m.movement_kind = 'MIGRATION_RECONCILIATION' then 0::bigint
                when m.signed_base_quantity is not null then m.signed_base_quantity
                else coalesce(m.quantity_after,0)::bigint - coalesce(m.quantity_before,0)::bigint
            end
        ), 0::bigint)
          into v_balance
          from public.inventory_movements m
         where m.organization_id = v_org and m.item_id = v_snapshot.item_id::uuid;

        v_delta := v_snapshot.quantity - v_balance;
        if v_balance < -2147483648 or v_balance > 2147483647
           or v_delta < -2147483647 or v_delta > 2147483647 then
            insert into public.inventory_reconciliation_quarantine(
                organization_id, contract_version, item_id, reason, canonical_snapshot, legacy_ledger_balance
            ) values (v_org, p_contract_version, v_snapshot.item_id, 'LEGACY_BALANCE_OUT_OF_RANGE', v_snapshot.quantity, v_balance)
            on conflict (organization_id, contract_version, item_id) do nothing;
            organization_id := v_org; item_id := v_snapshot.item_id; contract_version := p_contract_version;
            status := 'QUARANTINED'; canonical_snapshot := v_snapshot.quantity; legacy_ledger_balance := v_balance;
            reconciliation_delta := null; marker_checksum := null; reconciliation_movement_id := null;
            idempotency_key := null; server_sequence := null; approved_at := null; server_accepted_at := null;
            approved_by := null; authority_kind := v_authority.authority_kind;
            source_device_id := v_authority.source_device_id; quarantine_reason := 'LEGACY_BALANCE_OUT_OF_RANGE';
            return next;
            continue;
        end if;

        v_key := format('inventory-reconcile:%s:%s:%s', p_contract_version, v_org::text, v_snapshot.item_id);
        v_now_ms := floor(extract(epoch from clock_timestamp()) * 1000)::bigint;
        v_movement_id := null;
        v_sequence := null;

        perform set_config('verto.inventory_reconciliation_internal', 'on', true);

        if v_delta <> 0 then
            v_sequence := nextval('public.inventory_reconciliation_server_sequence_seq');
            v_movement_id := gen_random_uuid()::text;
            insert into public.inventory_movements(
                id, organization_id, item_id, invoice_id, client_id, movement_type,
                quantity, quantity_before, quantity_after, unit_price, note, created_at,
                movement_kind, signed_base_quantity, source_type, source_id, source_line_id,
                command_id, idempotency_key, posting_group_id, reverses_movement_id,
                conversion_factor_snapshot, occurred_at, recorded_at, server_accepted_at,
                server_sequence, created_by, device_id, contract_version
            ) values (
                v_movement_id::uuid, v_org, v_snapshot.item_id::uuid, null, null, 'ADJUST',
                abs(v_delta)::integer, v_balance::integer, v_snapshot.quantity::integer, 0,
                'migration reconciliation v2', now(),
                'MIGRATION_RECONCILIATION', v_delta, 'MIGRATION_RECONCILIATION',
                v_org::text || ':' || v_snapshot.item_id, null,
                v_key, v_key, 'inventory-reconcile:2:' || v_org::text, null,
                null, v_now_ms, v_now_ms, now(), v_sequence, v_authority.locked_by,
                'server-reconciliation', 2
            )
            on conflict (organization_id, idempotency_key) where idempotency_key is not null do nothing;

            select m.id::text, m.server_sequence
              into v_movement_id, v_sequence
              from public.inventory_movements m
             where m.organization_id = v_org and m.idempotency_key = v_key
             limit 1;
            if v_movement_id is null or v_sequence is null then
                raise exception 'RECONCILIATION_MOVEMENT_IDENTITY_FAILURE';
            end if;
        end if;

        v_checksum := encode(digest(concat_ws('|',
            p_contract_version::text, v_org::text, v_snapshot.item_id,
            v_snapshot.quantity::text, v_balance::text, v_delta::text,
            coalesce(v_movement_id,''), v_key, coalesce(v_sequence::text,''),
            v_authority.authority_kind, coalesce(v_authority.source_device_id,'')
        ), 'sha256'), 'hex');

        insert into public.inventory_reconciliation_markers(
            organization_id, contract_version, item_id, authority_kind, source_device_id,
            canonical_snapshot, legacy_ledger_balance, reconciliation_delta,
            reconciliation_movement_id, idempotency_key, server_sequence, marker_checksum,
            approved_at, server_accepted_at, approved_by
        ) values (
            v_org, p_contract_version, v_snapshot.item_id, v_authority.authority_kind,
            v_authority.source_device_id, v_snapshot.quantity, v_balance, v_delta,
            v_movement_id, v_key, v_sequence, v_checksum, v_now_ms, v_now_ms, v_authority.locked_by
        ) on conflict (organization_id, contract_version, item_id) do nothing;

        update public.inventory_items
           set quantity = v_snapshot.quantity::integer,
               updated_at = now()
         where organization_id = v_org
           and id = v_snapshot.item_id::uuid;

        organization_id := v_org; item_id := v_snapshot.item_id; contract_version := p_contract_version;
        status := 'COMPLETE'; canonical_snapshot := v_snapshot.quantity; legacy_ledger_balance := v_balance;
        reconciliation_delta := v_delta; marker_checksum := v_checksum;
        reconciliation_movement_id := v_movement_id; idempotency_key := v_key;
        server_sequence := v_sequence; approved_at := v_now_ms; server_accepted_at := v_now_ms;
        approved_by := v_authority.locked_by::text; authority_kind := v_authority.authority_kind;
        source_device_id := v_authority.source_device_id; quarantine_reason := null;
        return next;
    end loop;

    if not exists (
        select 1
          from public.inventory_reconciliation_canonical_snapshots s
          left join public.inventory_reconciliation_markers m
            on m.organization_id = s.organization_id
           and m.contract_version = s.contract_version
           and m.item_id = s.item_id
         where s.organization_id = v_org
           and s.contract_version = p_contract_version
           and m.item_id is null
    ) then
        update public.inventory_reconciliation_authorities
           set status = 'COMPLETE'
         where organization_id = v_org
           and contract_version = p_contract_version;
    end if;
end $$;

revoke all on function public.inventory_lock_reconciliation_authority_v2(integer,text,text) from public;
revoke all on function public.inventory_stage_owner_reconciliation_snapshot_v2(integer,text,jsonb) from public;
revoke all on function public.inventory_finalize_owner_reconciliation_snapshot_v2(integer,text,integer) from public;
revoke all on function public.inventory_prepare_reconciliation_batch_v2(integer,text,integer) from public;
grant execute on function public.inventory_lock_reconciliation_authority_v2(integer,text,text) to authenticated;
grant execute on function public.inventory_stage_owner_reconciliation_snapshot_v2(integer,text,jsonb) to authenticated;
grant execute on function public.inventory_finalize_owner_reconciliation_snapshot_v2(integer,text,integer) to authenticated;
grant execute on function public.inventory_prepare_reconciliation_batch_v2(integer,text,integer) to authenticated;

-- Do not validate/convert all legacy rows here. Local and server reconciliation remains resumable,
-- marker-driven, and blocked on a locked authority. v259 owns the new write path cutover.

revoke all on function public.inventory_reconciliation_item_guard_v2() from public;
revoke all on function public.inventory_reconciliation_movement_guard_v2() from public;


-- Session 310 inventory RPC prerequisites, deliberately without v262 cutover/revokes.
alter table public.inventory_items add column if not exists is_archived boolean not null default false;
alter table public.inventory_items add column if not exists archived_at timestamptz;
alter table public.inventory_items add column if not exists archived_by uuid;

create table if not exists public.inventory_contract_control (
    organization_id uuid primary key,
    minimum_contract_version integer not null default 1,
    reject_snapshot_writes boolean not null default false,
    writes_enabled boolean not null default true,
    updated_at timestamptz not null default now()
);
create table if not exists public.inventory_server_sequences (
    organization_id uuid primary key,
    last_sequence bigint not null default 0
);
create table if not exists public.inventory_sync_conflicts (
    id text primary key, organization_id uuid not null, conflict_key text not null, item_id text not null,
    conflict_type text not null check (conflict_type in ('OVERSOLD_CONFLICT','COST_ORDER_CONFLICT')),
    server_sequence bigint not null, projected_quantity bigint not null,
    status text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
    details jsonb not null default '{}'::jsonb, detected_at timestamptz not null default now(), resolved_at timestamptz,
    unique (organization_id, conflict_key)
);
create table if not exists public.inventory_command_quarantine (
    id text primary key, organization_id uuid not null, command_id text not null, item_id text,
    reason text not null, payload jsonb not null, detected_at timestamptz not null default now(), resolved_at timestamptz,
    unique (organization_id, command_id)
);

create or replace function public.inventory_apply_commands_v2(p_commands jsonb)
returns table(client_outbox_id text, movement_id text, status text, server_sequence bigint,
              projected_quantity bigint, conflict_type text)
language plpgsql security definer set search_path = public as $$
declare
  v_org uuid := public.get_my_org_id();
  v_user uuid := auth.uid();
  v_command jsonb;
  v_item public.inventory_items%rowtype;
  v_existing public.inventory_movements%rowtype;
  v_sequence bigint;
  v_after bigint;
  v_delta bigint;
  v_kind text;
  v_contract integer;
  v_control public.inventory_contract_control%rowtype;
begin
  if v_org is null or v_user is null then raise exception 'AUTH_SESSION_REQUIRED'; end if;
  if jsonb_typeof(p_commands) <> 'array' or jsonb_array_length(p_commands) > 100 then
    raise exception 'INVALID_INVENTORY_BATCH';
  end if;
  select * into v_control from public.inventory_contract_control where organization_id = v_org;
  if found and not v_control.writes_enabled then raise exception 'INVENTORY_WRITES_DISABLED'; end if;

  for v_command in select value from jsonb_array_elements(p_commands) loop
    client_outbox_id := nullif(v_command->>'client_outbox_id','');
    movement_id := nullif(v_command->>'movement_id','');
    v_contract := coalesce((v_command->>'contract_version')::integer, 0);
    v_delta := coalesce((v_command->>'signed_base_quantity')::bigint, 0);
    v_kind := nullif(v_command->>'movement_kind','');
    conflict_type := null;

    if movement_id is null or client_outbox_id is null or v_delta = 0
       or v_contract < greatest(2, coalesce(v_control.minimum_contract_version, 2)) then
      raise exception 'INVALID_OR_OBSOLETE_INVENTORY_COMMAND';
    end if;

    select * into v_existing from public.inventory_movements
      where organization_id=v_org and idempotency_key=v_command->>'idempotency_key';
    if found then
      status := 'DUPLICATE'; server_sequence := v_existing.server_sequence;
      select quantity::bigint into projected_quantity from public.inventory_items
        where organization_id=v_org and id=v_existing.item_id;
      return next; continue;
    end if;

    select * into v_item from public.inventory_items
      where organization_id=v_org and id=v_command->>'item_id' and not is_archived for update;
    if not found then raise exception 'INVENTORY_ITEM_NOT_FOUND_OR_ARCHIVED'; end if;

    if v_kind = 'REVERSAL' and not exists (
      select 1 from public.inventory_movements where organization_id=v_org
        and id=v_command->>'reverses_movement_id' and movement_kind <> 'REVERSAL'
    ) then
      insert into public.inventory_command_quarantine(id,organization_id,command_id,item_id,reason,payload)
      values ('quarantine:'||(v_command->>'command_id'),v_org,v_command->>'command_id',v_item.id,
              'REVERSAL_ORIGINAL_MISSING',v_command)
      on conflict (organization_id,command_id) do nothing;
      status := 'QUARANTINED'; server_sequence := 0; projected_quantity := v_item.quantity;
      return next; continue;
    end if;

    insert into public.inventory_server_sequences(organization_id,last_sequence) values(v_org,1)
    on conflict (organization_id) do update set last_sequence=public.inventory_server_sequences.last_sequence+1
    returning last_sequence into v_sequence;
    v_after := v_item.quantity::bigint + v_delta;
    if v_after < -2147483648 or v_after > 2147483647 then raise exception 'INVENTORY_QUANTITY_OVERFLOW'; end if;
    perform set_config('verto.inventory_rpc','on',true);
    update public.inventory_items set quantity=v_after::integer, updated_at=now()
      where organization_id=v_org and id=v_item.id;

    insert into public.inventory_movements(
      id,organization_id,item_id,invoice_id,client_id,movement_type,quantity,quantity_before,quantity_after,
      unit_price,note,source_type,source_id,source_line_id,command_id,idempotency_key,posting_group_id,
      reverses_movement_id,conversion_factor_snapshot,movement_kind,signed_base_quantity,occurred_at,recorded_at,
      server_accepted_at,server_sequence,created_by,device_id,contract_version,created_at)
    values(
      movement_id,v_org,v_item.id,coalesce(v_command->>'invoice_id',''),nullif(v_command->>'client_id','')::uuid,
      case when v_delta>0 then 'IN' else 'OUT' end,abs(v_delta)::integer,v_item.quantity,v_after::integer,
      coalesce((v_command->>'unit_price_minor')::numeric,0)/100,coalesce(v_command->>'note',''),
      v_command->>'source_type',v_command->>'source_id',v_command->>'source_line_id',v_command->>'command_id',
      v_command->>'idempotency_key',v_command->>'posting_group_id',v_command->>'reverses_movement_id',
      coalesce(v_command->>'conversion_factor_snapshot','1'),v_kind,v_delta,
      (v_command->>'occurred_at')::bigint,(extract(epoch from now())*1000)::bigint,now(),v_sequence,v_user,
      coalesce(v_command->>'device_id','unknown'),v_contract,now());

    if v_after < 0 then
      conflict_type := 'OVERSOLD_CONFLICT';
      insert into public.inventory_sync_conflicts(id,organization_id,conflict_key,item_id,conflict_type,
                                                   server_sequence,projected_quantity,details)
      values ('oversold:'||v_item.id||':'||v_sequence,v_org,'oversold:'||v_item.id||':'||v_sequence,
              v_item.id,conflict_type,v_sequence,v_after,jsonb_build_object('movement_id',movement_id))
      on conflict (organization_id,conflict_key) do nothing;
    end if;
    status := 'APPLIED'; server_sequence := v_sequence; projected_quantity := v_after; return next;
  end loop;
end $$;

revoke all on function public.inventory_apply_commands_v2(jsonb) from public;
grant execute on function public.inventory_apply_commands_v2(jsonb) to authenticated;

create or replace function public.inventory_apply_cost_revisions_v2(p_revisions jsonb)
returns table(client_outbox_id text, cost_revision_id text, status text, cost_sequence bigint)
language plpgsql security definer set search_path=public as $$
declare
  v_org uuid := public.get_my_org_id(); v_user uuid := auth.uid(); v jsonb;
  v_existing public.inventory_cost_revisions%rowtype; v_sequence bigint; v_kind text; v_item text;
begin
  if v_org is null or v_user is null then raise exception 'AUTH_SESSION_REQUIRED'; end if;
  if jsonb_typeof(p_revisions)<>'array' or jsonb_array_length(p_revisions)>100 then raise exception 'INVALID_COST_BATCH'; end if;
  for v in select value from jsonb_array_elements(p_revisions) loop
    client_outbox_id := v->>'client_outbox_id'; cost_revision_id := v->>'cost_revision_id';
    v_kind := v->>'revision_kind'; v_item := v->>'item_id';
    if client_outbox_id is null or cost_revision_id is null or coalesce((v->>'contract_version')::integer,0)<2
       or coalesce((v->>'approved_inventory_cost_minor')::bigint,-1)<0 then
      raise exception 'INVALID_COST_REVISION';
    end if;
    select * into v_existing from public.inventory_cost_revisions
      where organization_id=v_org and idempotency_key=v->>'idempotency_key';
    if found then status:='DUPLICATE'; cost_sequence:=v_existing.cost_sequence; return next; continue; end if;
    if not exists(select 1 from public.inventory_items where organization_id=v_org and id=v_item and not is_archived) then
      raise exception 'INVENTORY_ITEM_NOT_FOUND_OR_ARCHIVED';
    end if;
    if v_kind='REVERSAL' and not exists(select 1 from public.inventory_cost_revisions
      where organization_id=v_org and cost_revision_id=v->>'reverses_cost_revision_id' and revision_kind<>'REVERSAL') then
      status:='QUARANTINED'; cost_sequence:=0; return next; continue;
    end if;
    insert into public.inventory_server_sequences(organization_id,last_sequence) values(v_org,1)
    on conflict (organization_id) do update set last_sequence=public.inventory_server_sequences.last_sequence+1
    returning last_sequence into v_sequence;
    insert into public.inventory_cost_revisions(
      cost_revision_id,organization_id,item_id,source_type,source_id,source_line_id,revision_kind,
      direct_purchase_cost_minor,landed_cost_per_base_unit_minor,approved_inventory_cost_minor,
      currency_code,exchange_rate_snapshot,allocation_basis,allocation_residual_minor,is_provisional,
      reverses_cost_revision_id,command_id,idempotency_key,cost_sequence,approved_at,recorded_at,
      created_by,device_id,contract_version)
    values(cost_revision_id,v_org,v_item,v->>'source_type',v->>'source_id',v->>'source_line_id',v_kind,
      (v->>'direct_purchase_cost_minor')::bigint,(v->>'landed_cost_per_base_unit_minor')::bigint,
      (v->>'approved_inventory_cost_minor')::bigint,v->>'currency_code',(v->>'exchange_rate_snapshot')::numeric,
      coalesce(v->>'allocation_basis',''),coalesce((v->>'allocation_residual_minor')::bigint,0),
      coalesce((v->>'is_provisional')::boolean,false),v->>'reverses_cost_revision_id',v->>'command_id',
      v->>'idempotency_key',v_sequence,(v->>'approved_at')::bigint,(extract(epoch from now())*1000)::bigint,
      v_user,coalesce(v->>'device_id','unknown'),(v->>'contract_version')::integer);
    update public.inventory_items set buy_price=((v->>'approved_inventory_cost_minor')::numeric/100),updated_at=now()
      where organization_id=v_org and id=v_item;
    status:='APPLIED'; cost_sequence:=v_sequence; return next;
  end loop;
end $$;
revoke all on function public.inventory_apply_cost_revisions_v2(jsonb) from public;
grant execute on function public.inventory_apply_cost_revisions_v2(jsonb) to authenticated;

create or replace function public.inventory_pull_cost_revisions_v2(p_after_sequence bigint, p_limit integer default 500)
returns setof public.inventory_cost_revisions language sql security definer set search_path=public as $$
  select r.* from public.inventory_cost_revisions r
  where r.organization_id=public.get_my_org_id() and r.contract_version>=2
    and r.cost_sequence > greatest(coalesce(p_after_sequence,0),0)
  order by r.cost_sequence limit least(greatest(coalesce(p_limit,500),1),1000)
$$;
revoke all on function public.inventory_pull_cost_revisions_v2(bigint,integer) from public;
grant execute on function public.inventory_pull_cost_revisions_v2(bigint,integer) to authenticated;

create or replace function public.inventory_pull_movements_v2(p_after_sequence bigint, p_limit integer default 500)
returns setof public.inventory_movements language sql security definer set search_path=public as $$
  select m.* from public.inventory_movements m
  where m.organization_id=public.get_my_org_id() and m.contract_version>=2
    and m.server_sequence > greatest(coalesce(p_after_sequence,0),0)
  order by m.server_sequence limit least(greatest(coalesce(p_limit,500),1),1000)
$$;
revoke all on function public.inventory_pull_movements_v2(bigint,integer) from public;
grant execute on function public.inventory_pull_movements_v2(bigint,integer) to authenticated;


-- =============================================================================
-- Session 310 unified stronger-stream bridge. Runtime V2 remains disabled.
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.verto_sync_stronger_adapter_registry_v310 (
    aggregate_type text PRIMARY KEY,
    conflict_policy text NOT NULL,
    delete_policy text NOT NULL,
    local_authority text NOT NULL,
    business_identity text NOT NULL,
    stronger_server_authority text NOT NULL,
    permission_authority text NOT NULL,
    snapshot_policy text NOT NULL,
    client_push_mode text NOT NULL,
    CONSTRAINT verto_sync_stronger_adapter_push_mode_v310 CHECK (client_push_mode IN (
        'STRONGER_BRIDGE_READY_SHADOW','SERVER_AUTHORITATIVE_NO_CLIENT_PUSH','PRESERVED_EXISTING_STRONGER_RUNTIME'
    ))
);
REVOKE ALL ON TABLE public.verto_sync_stronger_adapter_registry_v310 FROM PUBLIC, anon, authenticated;

INSERT INTO public.verto_sync_stronger_adapter_registry_v310(
 aggregate_type, conflict_policy, delete_policy, local_authority, business_identity,
 stronger_server_authority, permission_authority, snapshot_policy, client_push_mode
) VALUES
('INVOICE','SEMANTIC_COMMAND','VOID_OR_REVERSE','financial_outbox','event_id/write_id','financial_sync_apply_event_v1','financial RPC auth + Verto membership','NO_SNAPSHOT_LEDGER_FACT','STRONGER_BRIDGE_READY_SHADOW'),
('PAYMENT','APPEND_ONLY_IDEMPOTENT','VOID_OR_REVERSE','financial_outbox','event_id/write_id','financial_sync_apply_event_v1','financial RPC auth + Verto membership','NO_SNAPSHOT_LEDGER_FACT','STRONGER_BRIDGE_READY_SHADOW'),
('CLIENT_CREDIT','APPEND_ONLY_IDEMPOTENT','NO_CLIENT_DELETE','client_credits immutable row','credit_id','verto_apply_client_credit_v310','trusted Verto membership','IMMUTABLE_EVENT_SNAPSHOT','STRONGER_BRIDGE_READY_SHADOW'),
('GOODS_RECEIPT','SEMANTIC_COMMAND','NO_CLIENT_DELETE','purchase-cycle immutable fact','write_id/receipt_id','verto_purchase_cycle_push_pre_v253','purchase RPC auth + tenant validation','IMMUTABLE_EVENT_SNAPSHOT','STRONGER_BRIDGE_READY_SHADOW'),
('PURCHASE_MATCH','SEMANTIC_COMMAND','NO_CLIENT_DELETE','purchase-cycle immutable fact','write_id/match_id','verto_purchase_cycle_push_post_v253','purchase RPC auth + tenant validation','IMMUTABLE_EVENT_SNAPSHOT','STRONGER_BRIDGE_READY_SHADOW'),
('PURCHASE_PAYMENT_OVERRIDE','SEMANTIC_COMMAND','NO_CLIENT_DELETE','purchase-cycle immutable fact','request_id/write_id','verto_purchase_cycle_push_post_v253','purchase RPC auth + tenant validation','IMMUTABLE_EVENT_SNAPSHOT','STRONGER_BRIDGE_READY_SHADOW'),
('INVENTORY_MOVEMENT','APPEND_ONLY_IDEMPOTENT','NO_CLIENT_DELETE','inventory_stock_outbox','movement_id/command_id/idempotency_key','inventory_apply_commands_v2','inventory RPC auth + tenant authority','NO_SNAPSHOT_LEDGER_FACT','STRONGER_BRIDGE_READY_SHADOW'),
('INVENTORY_COST_REVISION','IMMUTABLE_REVISION','NO_CLIENT_DELETE','inventory_cost_outbox','cost_revision_id/command_id/idempotency_key','inventory_apply_cost_revisions_v2','inventory RPC auth + tenant authority','NO_SNAPSHOT_LEDGER_FACT','STRONGER_BRIDGE_READY_SHADOW'),
('COST_ALLOCATION','IMMUTABLE_REVISION','NO_CLIENT_DELETE','cost_allocations immutable row','allocation_id','verto_apply_cost_allocation_v310','trusted Verto membership','IMMUTABLE_EVENT_SNAPSHOT','STRONGER_BRIDGE_READY_SHADOW'),
('EXPENSE','SEMANTIC_COMMAND','VOID_OR_REVERSE','expenses lifecycle row','expense_id/reversal_write_id','verto_apply_expense_command_v310','trusted Verto membership','DOMAIN_MATERIALIZATION_ONLY','STRONGER_BRIDGE_READY_SHADOW'),
('CASH_REGISTER','SERVER_AUTHORITATIVE','NO_CLIENT_DELETE','server read model','organization_id/register_id','cash_register','trusted Verto membership','SERVER_AUTH_CURRENT_STATE','SERVER_AUTHORITATIVE_NO_CLIENT_PUSH'),
('CASH_MOVEMENT','APPEND_ONLY_IDEMPOTENT','NO_CLIENT_DELETE','cash movement immutable row','write_id/movement_id','verto_apply_cash_movement_v310','trusted Verto membership','NO_SNAPSHOT_LEDGER_FACT','STRONGER_BRIDGE_READY_SHADOW'),
('CASH_RECONCILIATION','SEMANTIC_COMMAND','NO_CLIENT_DELETE','cash reconciliation state machine','reconciliation_id/command','verto_apply_cash_reconciliation_v310','trusted Verto membership','DOMAIN_MATERIALIZATION_ONLY','STRONGER_BRIDGE_READY_SHADOW'),
('COMMISSION_PAYMENT','APPEND_ONLY_IDEMPOTENT','NO_CLIENT_DELETE','server read model','client_request_id/payment_id','commission_payments','server-owned commission authority','IMMUTABLE_EVENT_SNAPSHOT','SERVER_AUTHORITATIVE_NO_CLIENT_PUSH'),
('OPTIMAL_VEHICLE','BRIDGE_EXISTING_STRONGER_CONTRACT','SERVER_OWNED','optimal_outbox','event_id/idempotency_key','optimal_apply_sync_operation_v2','active Optimal member + active Verto link','DOMAIN_MATERIALIZATION_ONLY','PRESERVED_EXISTING_STRONGER_RUNTIME'),
('OPTIMAL_MAINTENANCE','BRIDGE_EXISTING_STRONGER_CONTRACT','NO_CLIENT_DELETE','optimal_outbox','event_id/idempotency_key','optimal_apply_sync_operation_v2','active Optimal member + active Verto link','DOMAIN_MATERIALIZATION_ONLY','PRESERVED_EXISTING_STRONGER_RUNTIME'),
('OPTIMAL_FOLLOW_UP','BRIDGE_EXISTING_STRONGER_CONTRACT','NO_CLIENT_DELETE','optimal_outbox','event_id/idempotency_key','optimal_apply_sync_operation_v2','active Optimal member + active Verto link','DOMAIN_MATERIALIZATION_ONLY','PRESERVED_EXISTING_STRONGER_RUNTIME')
ON CONFLICT (aggregate_type) DO UPDATE SET
 conflict_policy=excluded.conflict_policy, delete_policy=excluded.delete_policy, local_authority=excluded.local_authority,
 business_identity=excluded.business_identity, stronger_server_authority=excluded.stronger_server_authority,
 permission_authority=excluded.permission_authority, snapshot_policy=excluded.snapshot_policy, client_push_mode=excluded.client_push_mode;

-- Lifecycle support is additive; existing rows are not bulk rewritten.
ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS lifecycle_state text NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS voided_at bigint;
ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS void_reason text;
ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS reversal_write_id text;
ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS sync_version bigint NOT NULL DEFAULT 1;
CREATE UNIQUE INDEX IF NOT EXISTS expenses_org_reversal_write_v310
  ON public.expenses(organization_id,reversal_write_id) WHERE reversal_write_id IS NOT NULL AND btrim(reversal_write_id)<>'';

ALTER TABLE public.cash_register ADD COLUMN IF NOT EXISTS balance_minor bigint NOT NULL DEFAULT 0;
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS amount_minor bigint NOT NULL DEFAULT 0;
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS balance_before_minor bigint NOT NULL DEFAULT 0;
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS balance_after_minor bigint NOT NULL DEFAULT 0;
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS source_type text NOT NULL DEFAULT '';
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS source_id text NOT NULL DEFAULT '';
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS source_version integer NOT NULL DEFAULT 1;
ALTER TABLE public.cash_register_movements ADD COLUMN IF NOT EXISTS write_id text NOT NULL DEFAULT '';
CREATE UNIQUE INDEX IF NOT EXISTS cash_movements_org_write_v310
  ON public.cash_register_movements(organization_id,write_id) WHERE btrim(write_id)<>'';
ALTER TABLE public.cash_reconciliation_sessions ADD COLUMN IF NOT EXISTS sync_version bigint NOT NULL DEFAULT 1;
ALTER TABLE public.cash_reconciliation_sessions ADD COLUMN IF NOT EXISTS last_command_id text;

CREATE OR REPLACE FUNCTION public.verto_financial_prepare_legacy_invoice_v310(p_org uuid,p_invoice_id text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
  UPDATE public.invoices i
     SET lifecycle_status = CASE WHEN coalesce(i.voided,false) THEN 'VOID' ELSE 'POSTED' END,
         lifecycle_version = greatest(coalesce(i.lifecycle_version,1),1)
   WHERE i.organization_id=p_org AND i.id::text=p_invoice_id
     AND ((coalesce(i.voided,false) AND i.lifecycle_status<>'VOID') OR i.lifecycle_version<1);
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_client_credit_v310(p_org uuid,p_id text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_client uuid; v_amount numeric; v_existing public.client_credits%rowtype;
BEGIN
  v_client := nullif(p_payload->>'clientId','')::uuid;
  v_amount := coalesce((p_payload->>'amountMinor')::numeric,0)/100;
  IF v_client IS NULL OR coalesce((p_payload->>'amountMinor')::bigint,0)=0 THEN
    RETURN jsonb_build_object('applied',false,'validation_code','INVALID_CLIENT_CREDIT');
  END IF;
  SELECT * INTO v_existing FROM public.client_credits WHERE id=p_id::uuid AND organization_id=p_org FOR UPDATE;
  IF FOUND THEN
    IF v_existing.client_id=v_client AND v_existing.amount=v_amount AND coalesce(v_existing.note,'')=coalesce(p_payload->>'note','') THEN
      RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',1,'authoritative_payload',jsonb_build_object('materialization',p_payload));
    END IF;
    RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','IMMUTABLE_CLIENT_CREDIT_CONFLICT','server_version',1);
  END IF;
  INSERT INTO public.client_credits(id,organization_id,client_id,amount,note,source_payment_id,employee_id,employee_name,created_at,updated_at)
  VALUES(p_id::uuid,p_org,v_client,v_amount,coalesce(p_payload->>'note',''),nullif(p_payload->>'sourcePaymentId','')::uuid,
         nullif(p_payload->>'employeeId','')::uuid,coalesce(p_payload->>'employeeName',''),
         to_timestamp(coalesce((p_payload->>'createdAt')::bigint,0)/1000.0),now());
  RETURN jsonb_build_object('applied',true,'server_version',1,'authoritative_payload',jsonb_build_object('materialization',p_payload));
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_cost_allocation_v310(p_org uuid,p_id text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_existing public.cost_allocations%rowtype; v_item text:=p_payload->>'itemId';
BEGIN
  IF nullif(v_item,'') IS NULL THEN RETURN jsonb_build_object('applied',false,'validation_code','INVALID_COST_ALLOCATION'); END IF;
  SELECT * INTO v_existing FROM public.cost_allocations WHERE id=p_id::uuid AND organization_id=p_org FOR UPDATE;
  IF FOUND THEN
    IF v_existing.item_id::text=v_item AND coalesce(v_existing.source_id::text,'')=coalesce(p_payload->>'sourceId','')
       AND v_existing.allocated_amount=coalesce((p_payload->>'allocatedAmount')::numeric,0) THEN
      RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',1,'authoritative_payload',jsonb_build_object('materialization',p_payload));
    END IF;
    RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','IMMUTABLE_COST_ALLOCATION_CONFLICT','server_version',1);
  END IF;
  INSERT INTO public.cost_allocations(id,organization_id,item_id,source_type,source_id,allocated_amount,per_unit_cost,quantity_affected,method,note,created_at)
  VALUES(p_id::uuid,p_org,v_item,p_payload->>'sourceType',coalesce(p_payload->>'sourceId',''),
         coalesce((p_payload->>'allocatedAmount')::numeric,0),coalesce((p_payload->>'perUnitCost')::numeric,0),
         coalesce((p_payload->>'quantityAffected')::integer,0),coalesce(p_payload->>'method','BY_QUANTITY'),
         coalesce(p_payload->>'note',''),to_timestamp(coalesce((p_payload->>'createdAt')::bigint,0)/1000.0));
  RETURN jsonb_build_object('applied',true,'server_version',1,'authoritative_payload',jsonb_build_object('materialization',p_payload));
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_expense_command_v310(p_org uuid,p_id text,p_operation text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v public.expenses%rowtype; v_write text:=nullif(p_payload->>'reversalWriteId','');
BEGIN
  SELECT * INTO v FROM public.expenses WHERE id=p_id::uuid AND organization_id=p_org FOR UPDATE;
  IF p_operation='COMMAND' THEN
    IF FOUND THEN
      IF v.lifecycle_state='ACTIVE' AND v.category=coalesce(p_payload->>'category','') AND v.amount=coalesce((p_payload->>'amount')::numeric,0) THEN
        RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',v.sync_version,'authoritative_payload',jsonb_build_object('materialization',p_payload));
      END IF;
      RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','EXPENSE_COMMAND_CONFLICT','server_version',v.sync_version);
    END IF;
    INSERT INTO public.expenses(id,organization_id,category,item,amount,note,date,created_at,updated_at,lifecycle_state,sync_version)
    VALUES(p_id::uuid,p_org,p_payload->>'category',coalesce(p_payload->>'item',''),(p_payload->>'amount')::numeric,
           coalesce(p_payload->>'note',''),to_timestamp(coalesce((p_payload->>'date')::bigint,0)/1000.0),now(),now(),'ACTIVE',1);
    RETURN jsonb_build_object('applied',true,'server_version',1,'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','ACTIVE')));
  ELSIF p_operation IN ('VOID','REVERSE') THEN
    IF NOT FOUND THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','EXPENSE_NOT_FOUND'); END IF;
    IF v.lifecycle_state='VOID' THEN
      IF v.reversal_write_id IS NOT DISTINCT FROM v_write THEN
        RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',v.sync_version,
          'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','VOID')));
      END IF;
      RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','EXPENSE_ALREADY_VOID','server_version',v.sync_version);
    END IF;
    IF v_write IS NULL THEN RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_REVERSAL_WRITE_ID_REQUIRED'); END IF;
    UPDATE public.expenses SET lifecycle_state='VOID',voided_at=coalesce((p_payload->>'voidedAt')::bigint,(extract(epoch from now())*1000)::bigint),
      void_reason=coalesce(p_payload->>'voidReason',''),reversal_write_id=v_write,sync_version=sync_version+1,updated_at=now()
      WHERE id=p_id::uuid AND organization_id=p_org RETURNING * INTO v;
    RETURN jsonb_build_object('applied',true,'server_version',v.sync_version,
      'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','VOID')));
  END IF;
  RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_OPERATION_UNSUPPORTED');
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_cash_movement_v310(p_org uuid,p_id text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_write text:=nullif(p_payload->>'writeId',''); v_existing public.cash_register_movements%rowtype;
        v_reg public.cash_register%rowtype; v_delta bigint; v_before bigint; v_after bigint; v_version bigint;
BEGIN
  IF v_write IS NULL THEN RETURN jsonb_build_object('applied',false,'validation_code','CASH_WRITE_ID_REQUIRED'); END IF;
  SELECT * INTO v_existing FROM public.cash_register_movements WHERE organization_id=p_org AND write_id=v_write LIMIT 1;
  IF FOUND THEN
    IF v_existing.id::text=p_id THEN
      RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',greatest(v_existing.source_version,1),
        'authoritative_payload',jsonb_build_object('materialization',p_payload));
    END IF;
    RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','CASH_WRITE_ID_CONFLICT');
  END IF;
  SELECT * INTO v_reg FROM public.cash_register WHERE organization_id=p_org ORDER BY updated_at DESC LIMIT 1 FOR UPDATE;
  IF NOT FOUND THEN
    INSERT INTO public.cash_register(id,organization_id,balance,balance_minor,updated_at) VALUES(gen_random_uuid(),p_org,0,0,now()) RETURNING * INTO v_reg;
  ELSIF v_reg.balance_minor=0 AND coalesce(v_reg.balance,0)<>0 THEN
    UPDATE public.cash_register SET balance_minor=round(balance*100)::bigint WHERE id=v_reg.id RETURNING * INTO v_reg;
  END IF;
  v_delta:=coalesce((p_payload->>'amountMinor')::bigint,0); v_before:=v_reg.balance_minor; v_after:=v_before+v_delta;
  v_version:=greatest(coalesce((p_payload->>'sourceVersion')::bigint,1),1);
  INSERT INTO public.cash_register_movements(id,organization_id,movement_type,amount,amount_minor,balance_before,balance_before_minor,
      balance_after,balance_after_minor,reference_id,note,source_type,source_id,source_version,write_id,created_at,updated_at)
  VALUES(p_id::uuid,p_org,p_payload->>'movementType',v_delta::numeric/100,v_delta,v_before::numeric/100,v_before,
      v_after::numeric/100,v_after,coalesce(p_payload->>'referenceId',''),coalesce(p_payload->>'note',''),coalesce(p_payload->>'sourceType',''),
      coalesce(p_payload->>'sourceId',''),v_version::integer,v_write,to_timestamp(coalesce((p_payload->>'createdAt')::bigint,0)/1000.0),now());
  UPDATE public.cash_register SET balance=v_after::numeric/100,balance_minor=v_after,updated_at=now() WHERE id=v_reg.id;
  RETURN jsonb_build_object('applied',true,'server_version',v_version,
    'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('balanceBeforeMinor',v_before,'balanceAfterMinor',v_after,
      'balanceBefore',v_before::numeric/100,'balanceAfter',v_after::numeric/100)),
    'secondary_changes',jsonb_build_array(jsonb_build_object('aggregate_type','CASH_REGISTER','aggregate_id','main','operation_type','COMMAND','entity_version',v_version,
      'payload',jsonb_build_object('materialization',jsonb_build_object('balance',v_after::numeric/100,'balanceMinor',v_after,'updatedAt',(extract(epoch from now())*1000)::bigint)))));
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_cash_reconciliation_v310(p_org uuid,p_id text,p_operation text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v public.cash_reconciliation_sessions%rowtype; v_cmd text:=coalesce(nullif(p_payload->>'commandId',''),p_id||':'||coalesce(p_payload->>'status','OPEN'));
BEGIN
  SELECT * INTO v FROM public.cash_reconciliation_sessions WHERE id=p_id::uuid AND organization_id=p_org FOR UPDATE;
  IF FOUND AND v.last_command_id=v_cmd THEN
    RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',v.sync_version,'authoritative_payload',jsonb_build_object('materialization',p_payload));
  END IF;
  IF NOT FOUND THEN
    IF coalesce(p_payload->>'status','OPEN')<>'OPEN' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','RECONCILIATION_OPEN_REQUIRED'); END IF;
    INSERT INTO public.cash_reconciliation_sessions(id,organization_id,employee_id,employee_name,opening_balance,total_sales,total_refunds,total_cash_in,total_cash_out,
      expected_balance,actual_counted_balance,variance,variance_reason,status,started_at,ended_at,notes,sync_version,last_command_id,created_at,updated_at)
    VALUES(p_id::uuid,p_org,nullif(p_payload->>'employeeId','')::uuid,coalesce(p_payload->>'employeeName',''),coalesce((p_payload->>'openingBalance')::numeric,0),
      coalesce((p_payload->>'totalSales')::numeric,0),coalesce((p_payload->>'totalRefunds')::numeric,0),coalesce((p_payload->>'totalCashIn')::numeric,0),
      coalesce((p_payload->>'totalCashOut')::numeric,0),coalesce((p_payload->>'expectedBalance')::numeric,0),coalesce((p_payload->>'actualCountedBalance')::numeric,0),
      coalesce((p_payload->>'variance')::numeric,0),coalesce(p_payload->>'varianceReason',''),'OPEN',
      to_timestamp(coalesce((p_payload->>'startedAt')::bigint,0)/1000.0),NULL,coalesce(p_payload->>'notes',''),1,v_cmd,now(),now()) RETURNING * INTO v;
  ELSE
    IF v.status<>'OPEN' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','RECONCILIATION_TERMINAL','server_version',v.sync_version); END IF;
    UPDATE public.cash_reconciliation_sessions SET status=coalesce(p_payload->>'status',status),
      actual_counted_balance=coalesce((p_payload->>'actualCountedBalance')::numeric,actual_counted_balance),
      variance=coalesce((p_payload->>'variance')::numeric,variance),variance_reason=coalesce(p_payload->>'varianceReason',variance_reason),
      ended_at=CASE WHEN coalesce(p_payload->>'status',status)<>'OPEN' THEN to_timestamp(coalesce((p_payload->>'endedAt')::bigint,(extract(epoch from now())*1000)::bigint)/1000.0) ELSE ended_at END,
      notes=coalesce(p_payload->>'notes',notes),sync_version=sync_version+1,last_command_id=v_cmd,updated_at=now()
      WHERE id=p_id::uuid AND organization_id=p_org RETURNING * INTO v;
  END IF;
  RETURN jsonb_build_object('applied',true,'server_version',v.sync_version,'authoritative_payload',jsonb_build_object('materialization',p_payload));
END $$;

CREATE OR REPLACE FUNCTION public.verto_validate_push_policy_v310(p_aggregate_type text,p_operation_type text,p_payload_version integer)
RETURNS text LANGUAGE plpgsql IMMUTABLE SET search_path=public AS $$
DECLARE v_expected integer;
BEGIN
  v_expected:=public.verto_expected_payload_version(p_aggregate_type);
  IF v_expected IS NULL OR v_expected<>p_payload_version THEN RETURN 'CONTRACT_UNSUPPORTED'; END IF;
  IF p_aggregate_type NOT IN (
    'INVOICE','PAYMENT','CLIENT_CREDIT','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE','INVENTORY_MOVEMENT',
    'INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE','CASH_REGISTER','CASH_MOVEMENT','CASH_RECONCILIATION','COMMISSION_PAYMENT',
    'OPTIMAL_VEHICLE','OPTIMAL_MAINTENANCE','OPTIMAL_FOLLOW_UP') THEN
    RETURN public.verto_validate_push_policy_v309(p_aggregate_type,p_operation_type,p_payload_version);
  END IF;
  IF p_aggregate_type IN ('CASH_REGISTER','COMMISSION_PAYMENT') THEN RETURN 'SERVER_AUTHORITATIVE_NO_CLIENT_PUSH'; END IF;
  IF p_operation_type IN ('UPSERT','DELETE') THEN RETURN 'FAIL_OWNER310_GENERIC_MUTATION'; END IF;
  IF p_operation_type='VOID' AND p_aggregate_type NOT IN ('INVOICE','EXPENSE') THEN RETURN 'VALIDATION_DELETE_POLICY'; END IF;
  IF p_operation_type='REVERSE' AND p_aggregate_type NOT IN ('INVOICE','PAYMENT','INVENTORY_MOVEMENT','INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE') THEN RETURN 'VALIDATION_DELETE_POLICY'; END IF;
  IF p_operation_type NOT IN ('COMMAND','VOID','REVERSE') THEN RETURN 'VALIDATION_DELETE_POLICY'; END IF;
  RETURN 'OK';
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_stronger_sync_adapter_v310(
  p_organization_id uuid,p_aggregate_type text,p_aggregate_id text,p_operation_type text,p_payload_version integer,p_payload jsonb,p_base_version bigint
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_fin jsonb; v_result jsonb; v_req jsonb; v_row jsonb; v_status text; v_seq bigint; v_company uuid; v_opt_type text;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.verto_sync_stronger_adapter_registry_v310 r WHERE r.aggregate_type=p_aggregate_type) THEN
    RETURN jsonb_build_object('applied',false,'validation_code','CONTRACT_UNSUPPORTED');
  END IF;

  IF p_aggregate_type IN ('INVOICE','PAYMENT') THEN
    v_fin:=p_payload->'financialEvent';
    IF jsonb_typeof(v_fin)<>'object' THEN RETURN jsonb_build_object('applied',false,'validation_code','FINANCIAL_EVENT_REQUIRED'); END IF;
    IF (p_aggregate_type='PAYMENT') <> (upper(coalesce(v_fin->>'domainOperation','')) LIKE 'PAYMENT_%') THEN
      RETURN jsonb_build_object('applied',false,'validation_code','FINANCIAL_AGGREGATE_OPERATION_MISMATCH');
    END IF;
    PERFORM public.verto_financial_prepare_legacy_invoice_v310(p_organization_id,p_aggregate_id);
    SELECT to_jsonb(x) INTO v_result FROM public.financial_sync_apply_event_v1(
      v_fin->>'eventId',v_fin->>'writeId',p_aggregate_id,(v_fin->>'aggregateVersion')::integer,(v_fin->>'aggregateSequence')::bigint,
      v_fin->>'domainOperation',(v_fin->>'domainPayloadVersion')::integer,(v_fin->>'schemaVersion')::integer,
      coalesce((v_fin->'domainPayload')::text,'{}'),(v_fin->>'occurredAt')::bigint,(v_fin->>'recordedAt')::bigint) x;
    v_status:=upper(coalesce(v_result->>'status',''));
    IF v_status='WAITING_DEPENDENCY' THEN RETURN jsonb_build_object('applied',false,'retryable',true,'validation_code','WAITING_DEPENDENCY'); END IF;
    IF v_status IN ('CONFLICT','REQUIRES_REVIEW') THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','FINANCIAL_DOMAIN_CONFLICT','server_version',greatest((v_fin->>'aggregateVersion')::bigint,1)); END IF;
    IF v_status NOT IN ('APPLIED','REPLAYED') THEN RETURN jsonb_build_object('applied',false,'validation_code','FINANCIAL_RESULT_UNSUPPORTED'); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='REPLAYED'),'server_version',greatest((v_fin->>'aggregateVersion')::bigint,1),
      'authoritative_payload',jsonb_build_object('financialEvent',v_fin||jsonb_build_object('domainServerRevision',(v_result->>'server_revision')::bigint,'domainServerRecordedAt',(v_result->>'server_recorded_at')::bigint)));
  END IF;

  IF p_aggregate_type='INVENTORY_MOVEMENT' THEN
    v_req:=p_payload->'inventoryCommand';
    IF jsonb_typeof(v_req)<>'object' THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_COMMAND_REQUIRED'); END IF;
    SELECT to_jsonb(x) INTO v_result FROM public.inventory_apply_commands_v2(jsonb_build_array(v_req)) x;
    v_status:=upper(coalesce(v_result->>'status','')); v_seq:=coalesce((v_result->>'server_sequence')::bigint,0);
    IF v_status='QUARANTINED' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','INVENTORY_QUARANTINED','server_version',greatest(v_seq,1),'authoritative_payload',jsonb_build_object('movement',v_req,'domainResult',v_result)); END IF;
    IF v_status NOT IN ('APPLIED','DUPLICATE') THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_RESULT_UNSUPPORTED'); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='DUPLICATE'),'server_version',greatest(v_seq,1),
      'authoritative_payload',jsonb_build_object('movement',v_req||jsonb_build_object('serverSequence',v_seq),'domainResult',v_result));
  END IF;

  IF p_aggregate_type='INVENTORY_COST_REVISION' THEN
    v_req:=p_payload->'inventoryCostCommand';
    IF jsonb_typeof(v_req)<>'object' THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_COST_COMMAND_REQUIRED'); END IF;
    SELECT to_jsonb(x) INTO v_result FROM public.inventory_apply_cost_revisions_v2(jsonb_build_array(v_req)) x;
    v_status:=upper(coalesce(v_result->>'status','')); v_seq:=coalesce((v_result->>'cost_sequence')::bigint,0);
    IF v_status='QUARANTINED' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','INVENTORY_COST_QUARANTINED','server_version',greatest(v_seq,1)); END IF;
    IF v_status NOT IN ('APPLIED','DUPLICATE') THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_COST_RESULT_UNSUPPORTED'); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='DUPLICATE'),'server_version',greatest(v_seq,1),
      'authoritative_payload',jsonb_build_object('costRevision',v_req||jsonb_build_object('costSequence',v_seq),'domainResult',v_result));
  END IF;

  IF p_aggregate_type='GOODS_RECEIPT' THEN
    v_req:=p_payload->'purchaseRequest';
    PERFORM public.verto_purchase_cycle_push_pre_v253(coalesce(v_req->'purchaseOrders','[]'::jsonb),coalesce(v_req->'purchaseOrderLines','[]'::jsonb),
      coalesce(v_req->'goodsReceipts','[]'::jsonb),coalesce(v_req->'goodsReceiptLines','[]'::jsonb),coalesce(v_req->'attachments','[]'::jsonb));
    RETURN jsonb_build_object('applied',true,'server_version',greatest(coalesce(p_base_version,1),1),'authoritative_payload',p_payload);
  END IF;
  IF p_aggregate_type IN ('PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE') THEN
    v_req:=p_payload->'purchaseRequest';
    PERFORM public.verto_purchase_cycle_push_post_v253(coalesce(v_req->'matches','[]'::jsonb),coalesce(v_req->'matchLines','[]'::jsonb),
      coalesce(v_req->'allocations','[]'::jsonb),coalesce(v_req->'paymentOverrides','[]'::jsonb));
    RETURN jsonb_build_object('applied',true,'server_version',greatest(coalesce(p_base_version,1),1),'authoritative_payload',p_payload);
  END IF;
  IF p_aggregate_type='CLIENT_CREDIT' THEN RETURN public.verto_apply_client_credit_v310(p_organization_id,p_aggregate_id,p_payload->'materialization'); END IF;
  IF p_aggregate_type='COST_ALLOCATION' THEN RETURN public.verto_apply_cost_allocation_v310(p_organization_id,p_aggregate_id,p_payload->'materialization'); END IF;
  IF p_aggregate_type='EXPENSE' THEN RETURN public.verto_apply_expense_command_v310(p_organization_id,p_aggregate_id,p_operation_type,p_payload->'materialization'); END IF;
  IF p_aggregate_type='CASH_MOVEMENT' THEN RETURN public.verto_apply_cash_movement_v310(p_organization_id,p_aggregate_id,p_payload->'materialization'); END IF;
  IF p_aggregate_type='CASH_RECONCILIATION' THEN RETURN public.verto_apply_cash_reconciliation_v310(p_organization_id,p_aggregate_id,p_operation_type,p_payload->'materialization'); END IF;

  IF p_aggregate_type LIKE 'OPTIMAL_%' THEN
    v_req:=p_payload->'optimalRequest';
    BEGIN v_company:=nullif(v_req->>'companyId','')::uuid; EXCEPTION WHEN others THEN v_company:=NULL; END;
    IF v_company IS NULL OR NOT EXISTS(SELECT 1 FROM public.optimal_verto_links l WHERE l.optimal_company_id=v_company AND l.verto_organization_id=p_organization_id AND l.is_active=true) THEN
      RETURN jsonb_build_object('applied',false,'validation_code','OPTIMAL_VERTO_LINK_REQUIRED');
    END IF;
    v_opt_type:=CASE p_aggregate_type WHEN 'OPTIMAL_VEHICLE' THEN 'VEHICLE' WHEN 'OPTIMAL_MAINTENANCE' THEN 'MAINTENANCE' ELSE 'FOLLOW_UP' END;
    IF upper(coalesce(v_req->>'aggregateType',''))<>v_opt_type THEN RETURN jsonb_build_object('applied',false,'validation_code','OPTIMAL_AGGREGATE_MISMATCH'); END IF;
    SELECT to_jsonb(x) INTO v_result FROM public.optimal_apply_sync_operation_v2(v_company,v_opt_type,p_aggregate_id,
      v_req->>'operationType',coalesce(v_req->'payload','{}'::jsonb),v_req->>'idempotencyKey',coalesce((v_req->>'localVersion')::bigint,1)) x;
    v_status:=upper(coalesce(v_result->>'status',''));
    IF v_status='CONFLICT' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code',coalesce(v_result->>'error_code','OPTIMAL_CONFLICT'),'server_version',greatest(coalesce((v_result->>'remote_version')::bigint,1),1),'authoritative_payload',jsonb_build_object('materialization',coalesce(v_result->'remote_payload',v_req->'payload'))); END IF;
    IF v_status NOT IN ('SUCCESS','APPLIED','ALREADY_APPLIED') THEN RETURN jsonb_build_object('applied',false,'retryable',true,'validation_code',coalesce(v_result->>'error_code','OPTIMAL_RETRYABLE')); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='ALREADY_APPLIED'),'server_version',greatest(coalesce((v_result->>'remote_version')::bigint,1),1),
      'authoritative_payload',jsonb_build_object('materialization',coalesce(v_result->'remote_payload',v_req->'payload'),'optimalResult',v_result));
  END IF;

  RETURN jsonb_build_object('applied',false,'validation_code','CONTRACT_UNSUPPORTED');
END $$;

-- Replace only the public boundary; v309 helpers remain byte-preserved and owner307 behavior is delegated unchanged.
CREATE OR REPLACE FUNCTION public.verto_apply_sync_mutation(p_mutation jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  v_org uuid; v_client_org uuid; v_mutation_id text; v_aggregate_type text; v_aggregate_id text; v_operation_type text;
  v_base_version bigint; v_payload_version integer; v_payload jsonb; v_request_hash text; v_policy text;
  v_receipt public.verto_sync_receipts%rowtype; v_snapshot public.verto_sync_snapshot_state%rowtype; v_adapter jsonb;
  v_new_version bigint; v_new_payload jsonb; v_revision bigint; v_tx text; v_secondary jsonb;
  v_owner310 boolean;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE='28000'; END IF;
  IF p_mutation IS NULL OR jsonb_typeof(p_mutation)<>'object' THEN RAISE EXCEPTION 'VALIDATION: mutation object required' USING ERRCODE='22023'; END IF;
  SELECT s.organization_id INTO v_org FROM public.verto_resolve_sync_scope() s LIMIT 1;
  BEGIN v_client_org:=nullif(p_mutation->>'organization_id','')::uuid; EXCEPTION WHEN others THEN RAISE EXCEPTION 'SCOPE_MISMATCH: invalid organization_id' USING ERRCODE='22023'; END;
  IF v_client_org IS NULL OR v_client_org<>v_org THEN RAISE EXCEPTION 'SCOPE_MISMATCH: mutation organization differs from trusted membership' USING ERRCODE='22023'; END IF;
  v_mutation_id:=btrim(coalesce(p_mutation->>'mutation_id','')); v_aggregate_type:=btrim(coalesce(p_mutation->>'aggregate_type',''));
  v_aggregate_id:=btrim(coalesce(p_mutation->>'aggregate_id','')); v_operation_type:=btrim(coalesce(p_mutation->>'operation_type',''));
  v_payload:=coalesce(p_mutation->'payload','{}'::jsonb);
  BEGIN v_payload_version:=(p_mutation->>'payload_version')::integer; v_base_version:=nullif(p_mutation->>'base_version','')::bigint;
  EXCEPTION WHEN others THEN RAISE EXCEPTION 'VALIDATION: invalid numeric mutation field' USING ERRCODE='22023'; END;
  IF v_mutation_id='' OR v_aggregate_type='' OR v_aggregate_id='' OR v_operation_type='' OR v_payload_version IS NULL THEN RAISE EXCEPTION 'VALIDATION: mutation identity fields required' USING ERRCODE='22023'; END IF;
  IF jsonb_typeof(v_payload)<>'object' OR pg_column_size(v_payload)>524288 THEN RAISE EXCEPTION 'VALIDATION: payload must be object <= 512 KiB' USING ERRCODE='22023'; END IF;

  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('verto-sync-mutation:'||v_org::text||':'||v_mutation_id,0));
  v_request_hash:=public.verto_sync_request_hash_v309(v_org,p_mutation);
  SELECT * INTO v_receipt FROM public.verto_sync_receipts r WHERE r.organization_id=v_org AND r.mutation_id=v_mutation_id;
  IF FOUND THEN
    IF v_receipt.request_hash=v_request_hash THEN RETURN public.verto_sync_receipt_json_v309(v_receipt); END IF;
    RETURN jsonb_build_object('contract_family','verto-unified-sync','contract_version',1,'status','REJECTED','mutation_id',v_mutation_id,
      'aggregate_id',v_aggregate_id,'server_version',NULL,'server_revision',NULL,'authoritative_payload',NULL,'conflict_code',NULL,
      'validation_code','IDEMPOTENCY_CONFLICT','transaction_id',NULL,'retry_after_epoch_millis',NULL,'request_hash',v_request_hash,'resolution_requirement',NULL);
  END IF;

  v_owner310:=EXISTS(SELECT 1 FROM public.verto_sync_stronger_adapter_registry_v310 r WHERE r.aggregate_type=v_aggregate_type);
  v_policy:=CASE WHEN v_owner310 THEN public.verto_validate_push_policy_v310(v_aggregate_type,v_operation_type,v_payload_version)
                 ELSE public.verto_validate_push_policy_v309(v_aggregate_type,v_operation_type,v_payload_version) END;
  IF v_policy<>'OK' THEN
    INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,validation_code)
    VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'REJECTED',v_policy) RETURNING * INTO v_receipt;
    RETURN public.verto_sync_receipt_json_v309(v_receipt);
  END IF;

  -- v309 optimistic-version behavior is preserved byte-for-byte in meaning for owner307 optimistic aggregates.
  IF NOT v_owner310 AND v_aggregate_type IN ('NOTE','REMINDER','INVENTORY_ITEM','INVENTORY_UNIT','CATEGORY','ITEM_CATEGORY','BUDGET','PRICE_LIST','ORGANIZATION_SETTINGS','EDUCATIONAL_CONTENT') THEN
    SELECT * INTO v_snapshot FROM public.verto_sync_snapshot_state s WHERE s.organization_id=v_org AND s.aggregate_type=v_aggregate_type AND s.aggregate_id=v_aggregate_id FOR UPDATE;
    IF FOUND THEN
      IF v_snapshot.entity_version IS NULL OR v_snapshot.entity_version<=0 THEN
        INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,validation_code,authoritative_payload)
        VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'REJECTED','FAIL_VERSION_AUTHORITY_GAP',v_snapshot.payload) RETURNING * INTO v_receipt;
        RETURN public.verto_sync_receipt_json_v309(v_receipt);
      END IF;
      IF v_base_version IS NULL OR v_base_version<>v_snapshot.entity_version THEN
        INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,server_version,authoritative_payload,conflict_code,resolution_requirement)
        VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'CONFLICT',v_snapshot.entity_version,v_snapshot.payload,'STALE_BASE_VERSION','REQUIRES_REVIEW') RETURNING * INTO v_receipt;
        RETURN public.verto_sync_receipt_json_v309(v_receipt);
      END IF;
    ELSIF v_base_version IS NOT NULL THEN
      INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,validation_code)
      VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'REJECTED','BASE_VERSION_WITHOUT_SERVER_ENTITY') RETURNING * INTO v_receipt;
      RETURN public.verto_sync_receipt_json_v309(v_receipt);
    END IF;
  END IF;

  v_adapter:=CASE WHEN v_owner310 THEN public.verto_apply_stronger_sync_adapter_v310(v_org,v_aggregate_type,v_aggregate_id,v_operation_type,v_payload_version,v_payload,v_base_version)
                  ELSE public.verto_apply_sync_adapter_v309(v_org,v_aggregate_type,v_aggregate_id,v_operation_type,v_payload_version,v_payload,v_base_version) END;

  IF coalesce((v_adapter->>'retryable')::boolean,false) THEN
    -- Retryable outcomes are deliberately non-terminal: no immutable receipt is persisted yet.
    RETURN jsonb_build_object('contract_family','verto-unified-sync','contract_version',1,'status','RETRYABLE','mutation_id',v_mutation_id,
      'aggregate_id',v_aggregate_id,'server_version',nullif(v_adapter->>'server_version','')::bigint,'server_revision',NULL,
      'authoritative_payload',v_adapter->'authoritative_payload','conflict_code',NULL,'validation_code',v_adapter->>'validation_code',
      'transaction_id',NULL,'retry_after_epoch_millis',NULL,'request_hash',v_request_hash,'resolution_requirement',NULL);
  END IF;
  IF coalesce((v_adapter->>'conflict')::boolean,false) THEN
    INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,server_version,authoritative_payload,conflict_code,resolution_requirement)
    VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'CONFLICT',nullif(v_adapter->>'server_version','')::bigint,
      v_adapter->'authoritative_payload',coalesce(nullif(v_adapter->>'conflict_code',''),'DOMAIN_CONFLICT'),'REQUIRES_REVIEW') RETURNING * INTO v_receipt;
    RETURN public.verto_sync_receipt_json_v309(v_receipt);
  END IF;
  IF coalesce((v_adapter->>'applied')::boolean,false) IS NOT TRUE THEN
    INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,validation_code)
    VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'REJECTED',coalesce(nullif(v_adapter->>'validation_code',''),'SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE')) RETURNING * INTO v_receipt;
    RETURN public.verto_sync_receipt_json_v309(v_receipt);
  END IF;

  v_new_payload:=coalesce(v_adapter->'authoritative_payload',v_payload); v_new_version:=nullif(v_adapter->>'server_version','')::bigint;
  IF v_new_version IS NULL OR v_new_version<=0 THEN RAISE EXCEPTION 'VALIDATION: APPLIED adapter must return positive server_version' USING ERRCODE='22023'; END IF;
  IF coalesce((v_adapter->>'no_op')::boolean,false) THEN
    INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,server_version,authoritative_payload)
    VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'NO_OP',v_new_version,v_new_payload) RETURNING * INTO v_receipt;
    RETURN public.verto_sync_receipt_json_v309(v_receipt);
  END IF;

  -- Secondary read-model facts and the primary stronger fact are in this same PostgreSQL transaction.
  FOR v_secondary IN SELECT value FROM jsonb_array_elements(coalesce(v_adapter->'secondary_changes','[]'::jsonb)) LOOP
    PERFORM public.verto_append_sync_change(v_org,v_secondary->>'aggregate_type',v_secondary->>'aggregate_id',v_secondary->>'operation_type',
      coalesce((v_secondary->>'entity_version')::bigint,1),public.verto_expected_payload_version(v_secondary->>'aggregate_type'),v_secondary->'payload',v_mutation_id,'BRIDGE');
  END LOOP;
  v_revision:=public.verto_append_sync_change(v_org,v_aggregate_type,v_aggregate_id,v_operation_type,v_new_version,v_payload_version,v_new_payload,v_mutation_id,'BRIDGE');
  SELECT c.transaction_id INTO v_tx FROM public.verto_sync_change_log c WHERE c.revision=v_revision;
  INSERT INTO public.verto_sync_receipts(organization_id,mutation_id,request_hash,aggregate_type,aggregate_id,operation_type,status,server_revision,server_version,transaction_id,authoritative_payload)
  VALUES(v_org,v_mutation_id,v_request_hash,v_aggregate_type,v_aggregate_id,v_operation_type,'APPLIED',v_revision,v_new_version,v_tx,v_new_payload) RETURNING * INTO v_receipt;
  RETURN public.verto_sync_receipt_json_v309(v_receipt);
END $$;

COMMENT ON FUNCTION public.verto_apply_sync_mutation(jsonb) IS
'v310 stronger-domain bridge: one stronger business effect + unified change + immutable receipt in one transaction; owner307 v309 behavior preserved; runtime V2 remains disabled.';

REVOKE ALL ON FUNCTION public.verto_financial_prepare_legacy_invoice_v310(uuid,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_client_credit_v310(uuid,text,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_cost_allocation_v310(uuid,text,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_expense_command_v310(uuid,text,text,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_cash_movement_v310(uuid,text,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_cash_reconciliation_v310(uuid,text,text,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_validate_push_policy_v310(text,text,integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_stronger_sync_adapter_v310(uuid,text,text,text,integer,jsonb,bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_sync_mutation(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verto_apply_sync_mutation(jsonb) TO authenticated;
