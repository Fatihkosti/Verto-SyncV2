-- Verto Logistics V2 operational-control delta for Room schema 54.
-- Apply only after 001..005. This file is intentionally idempotent where PostgreSQL permits it.

begin;

-- ---------------------------------------------------------------------------
-- 1) Existing-table operational columns added by Room 54.
-- ---------------------------------------------------------------------------

alter table public.logistics_shipments
    add column if not exists cancelled_at bigint,
    add column if not exists cancel_reason text not null default '';

alter table public.logistics_milestones
    add column if not exists planned_departure_at bigint,
    add column if not exists handling_status text not null default 'PENDING',
    add column if not exists unloaded_at bigint,
    add column if not exists loaded_at bigint;

alter table public.logistics_documents
    add column if not exists source_id text,
    add column if not exists leg_id text;

alter table public.logistics_costs
    add column if not exists leg_id text,
    add column if not exists milestone_id text,
    add column if not exists source_id text;

-- ---------------------------------------------------------------------------
-- 2) Room-54 operational tables.
-- ---------------------------------------------------------------------------

create table if not exists public.logistics_shipment_legs (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    sequence bigint not null,
    from_milestone_id text not null,
    to_milestone_id text not null,
    mode text not null,
    carrier_partner_id text not null,
    status text not null,
    planned_departure_at bigint,
    planned_arrival_at bigint,
    actual_departure_at bigint,
    actual_arrival_at bigint,
    road_vehicle_number text,
    road_driver_name text,
    road_driver_phone text,
    sea_container_number text,
    sea_bill_of_lading text,
    sea_vessel_reference text,
    air_waybill_number text,
    air_flight_reference text,
    note text not null default '',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, from_milestone_id)
        references public.logistics_milestones (organization_id, id)
        on update cascade on delete restrict,
    foreign key (organization_id, to_milestone_id)
        references public.logistics_milestones (organization_id, id)
        on update cascade on delete restrict,
    foreign key (organization_id, carrier_partner_id)
        references public.logistics_partners (organization_id, id)
        on update cascade on delete restrict,
    check (
        road_driver_phone is null
        or btrim(road_driver_phone) = ''
        or road_driver_phone ~ '^[0-9]+$'
    )
);

create table if not exists public.logistics_custody_handoffs (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    source_id text,
    milestone_id text,
    from_holder_type text not null,
    from_holder_id text,
    from_holder_name_snapshot text not null,
    to_holder_type text not null,
    to_holder_id text,
    to_holder_name_snapshot text not null,
    transferred_at bigint not null,
    received_at bigint not null,
    request_id text not null,
    note text not null default '',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, source_id)
        references public.logistics_shipment_sources (organization_id, id)
        on update cascade on delete restrict,
    foreign key (organization_id, milestone_id)
        references public.logistics_milestones (organization_id, id)
        on update cascade on delete restrict
);

-- ---------------------------------------------------------------------------
-- 3) Required indexes and idempotency keys.
-- ---------------------------------------------------------------------------

create unique index if not exists logistics_shipment_legs_org_shipment_sequence_uq
    on public.logistics_shipment_legs (organization_id, shipment_id, sequence);
create index if not exists logistics_shipment_legs_org_shipment_idx
    on public.logistics_shipment_legs (organization_id, shipment_id);
create index if not exists logistics_shipment_legs_org_carrier_idx
    on public.logistics_shipment_legs (organization_id, carrier_partner_id);
create index if not exists logistics_shipment_legs_org_from_milestone_idx
    on public.logistics_shipment_legs (organization_id, from_milestone_id);
create index if not exists logistics_shipment_legs_org_to_milestone_idx
    on public.logistics_shipment_legs (organization_id, to_milestone_id);

create index if not exists logistics_custody_org_shipment_received_idx
    on public.logistics_custody_handoffs (organization_id, shipment_id, received_at);
create index if not exists logistics_custody_org_source_received_idx
    on public.logistics_custody_handoffs (organization_id, source_id, received_at);
create unique index if not exists logistics_custody_org_shipment_request_uq
    on public.logistics_custody_handoffs (organization_id, shipment_id, request_id);

create index if not exists logistics_documents_org_source_idx
    on public.logistics_documents (organization_id, source_id);
create index if not exists logistics_documents_org_leg_idx
    on public.logistics_documents (organization_id, leg_id);
create index if not exists logistics_costs_org_source_idx
    on public.logistics_costs (organization_id, source_id);
create index if not exists logistics_costs_org_milestone_idx
    on public.logistics_costs (organization_id, milestone_id);
create index if not exists logistics_costs_org_leg_idx
    on public.logistics_costs (organization_id, leg_id);

-- ---------------------------------------------------------------------------
-- 4) New scope foreign keys and single-scope guards.
--    Constraint creation is catalog-guarded because PostgreSQL has no
--    ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS syntax.
-- ---------------------------------------------------------------------------

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'logistics_documents_source_fk') then
        alter table public.logistics_documents
            add constraint logistics_documents_source_fk
            foreign key (organization_id, source_id)
            references public.logistics_shipment_sources (organization_id, id)
            on update cascade on delete restrict;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'logistics_documents_leg_fk') then
        alter table public.logistics_documents
            add constraint logistics_documents_leg_fk
            foreign key (organization_id, leg_id)
            references public.logistics_shipment_legs (organization_id, id)
            on update cascade on delete restrict;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'logistics_documents_single_scope_ck') then
        alter table public.logistics_documents
            add constraint logistics_documents_single_scope_ck
            check (num_nonnulls(source_id, milestone_id, leg_id) <= 1) not valid;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'logistics_costs_leg_fk') then
        alter table public.logistics_costs
            add constraint logistics_costs_leg_fk
            foreign key (organization_id, leg_id)
            references public.logistics_shipment_legs (organization_id, id)
            on update cascade on delete restrict;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'logistics_costs_milestone_fk') then
        alter table public.logistics_costs
            add constraint logistics_costs_milestone_fk
            foreign key (organization_id, milestone_id)
            references public.logistics_milestones (organization_id, id)
            on update cascade on delete restrict;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'logistics_costs_source_fk') then
        alter table public.logistics_costs
            add constraint logistics_costs_source_fk
            foreign key (organization_id, source_id)
            references public.logistics_shipment_sources (organization_id, id)
            on update cascade on delete restrict;
    end if;

    if not exists (select 1 from pg_constraint where conname = 'logistics_costs_single_scope_ck') then
        alter table public.logistics_costs
            add constraint logistics_costs_single_scope_ck
            check (num_nonnulls(source_id, milestone_id, leg_id) <= 1) not valid;
    end if;
end
$$;

-- Existing partner rows may contain legacy formatting. NOT VALID preserves those
-- rows while enforcing digits-only for subsequent nonblank writes.
do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'logistics_partners_phone_digits_ck') then
        alter table public.logistics_partners
            add constraint logistics_partners_phone_digits_ck
            check (phone is null or btrim(phone) = '' or phone ~ '^[0-9]+$') not valid;
    end if;
end
$$;

-- ---------------------------------------------------------------------------
-- 5) RLS and grants for the two new synchronized tables.
-- ---------------------------------------------------------------------------

do $$
declare
    table_name text;
    policy_name text;
begin
    foreach table_name in array array[
        'logistics_shipment_legs',
        'logistics_custody_handoffs'
    ]
    loop
        execute format('alter table public.%I enable row level security', table_name);
        execute format('revoke all on public.%I from anon', table_name);
        execute format('revoke delete on public.%I from public, anon, authenticated', table_name);
        execute format('grant select, insert, update on public.%I to authenticated', table_name);

        policy_name := table_name || '_org_select';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format(
            'create policy %I on public.%I for select to authenticated using (organization_id = public.logistics_v2_current_organization_id())',
            policy_name, table_name
        );

        policy_name := table_name || '_org_insert';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format(
            'create policy %I on public.%I for insert to authenticated with check (organization_id = public.logistics_v2_current_organization_id())',
            policy_name, table_name
        );

        policy_name := table_name || '_org_update';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format(
            'create policy %I on public.%I for update to authenticated using (organization_id = public.logistics_v2_current_organization_id()) with check (organization_id = public.logistics_v2_current_organization_id())',
            policy_name, table_name
        );

        execute format('drop trigger if exists %I on public.%I', 'trg_' || table_name || '_updated_at', table_name);
        execute format(
            'create trigger %I before update on public.%I for each row execute function public.logistics_v2_touch_updated_at()',
            'trg_' || table_name || '_updated_at', table_name
        );
    end loop;
end
$$;

-- Operational history remains append/update only from authenticated clients.
revoke delete on table
    public.logistics_shipments,
    public.logistics_shipment_sources,
    public.logistics_shipment_lines,
    public.logistics_milestones,
    public.logistics_shipment_legs,
    public.logistics_custody_handoffs,
    public.logistics_assignments,
    public.logistics_events,
    public.logistics_partners,
    public.logistics_shipment_partner_links,
    public.logistics_transport_details,
    public.logistics_documents,
    public.logistics_costs,
    public.logistics_receiving_batches,
    public.logistics_receiving_lines,
    public.logistics_inventory_postings,
    public.logistics_cost_allocations
from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 6) Atomic shipment-number allocator.
--    The counter is server-internal and is never part of Android sync.
-- ---------------------------------------------------------------------------

create table if not exists public.logistics_shipment_number_counters (
    organization_id uuid primary key,
    last_number bigint not null default 0 check (last_number between 0 and 2147483647),
    updated_at timestamptz not null default now()
);

alter table public.logistics_shipment_number_counters enable row level security;
revoke all on public.logistics_shipment_number_counters from public, anon, authenticated;

create or replace function public.allocate_logistics_shipment_number(p_org uuid)
returns bigint
language plpgsql
security definer
set search_path = public
as $$
declare
    current_org uuid;
    observed_max bigint;
    next_number bigint;
begin
    if p_org is null then
        raise exception 'organization is required' using errcode = '22023';
    end if;

    current_org := public.logistics_v2_current_organization_id();
    if current_org is null or current_org <> p_org then
        raise exception 'cross-organization shipment number allocation denied' using errcode = '42501';
    end if;

    select least(
        coalesce(
            max(
                case
                    when shipment_number ~ '^[0-9]+$'
                        then shipment_number::numeric
                    else null
                end
            ),
            0
        ),
        2147483647
    )::bigint
    into observed_max
    from public.logistics_shipments
    where organization_id = p_org;

    if observed_max >= 2147483647 then
        raise exception 'shipment number range exhausted' using errcode = '22003';
    end if;

    insert into public.logistics_shipment_number_counters (organization_id, last_number, updated_at)
    values (p_org, observed_max + 1, now())
    on conflict (organization_id) do update
       set last_number = greatest(
               public.logistics_shipment_number_counters.last_number,
               observed_max
           ) + 1,
           updated_at = now()
    returning last_number into next_number;

    if next_number > 2147483647 then
        raise exception 'shipment number range exhausted' using errcode = '22003';
    end if;

    return next_number;
end
$$;

revoke all on function public.allocate_logistics_shipment_number(uuid) from public, anon;
grant execute on function public.allocate_logistics_shipment_number(uuid) to authenticated;

commit;
