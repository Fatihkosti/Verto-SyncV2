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
