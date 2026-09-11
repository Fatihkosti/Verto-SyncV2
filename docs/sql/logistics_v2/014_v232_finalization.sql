-- Verto Logistics V2 v232 finalization contract. Remote runtime remains OFF.
-- Additive only: shortage outcomes and post-close landed-cost adjustments.

begin;

create table if not exists public.logistics_shortage_settlements (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    shortage_id text not null,
    type text not null check (type in ('COMPENSATED','FINAL_LOSS')),
    quantity integer not null check (quantity > 0),
    compensation_amount numeric,
    currency text,
    exchange_rate_snapshot numeric,
    base_currency_amount numeric,
    occurred_at bigint not null,
    employee_id text,
    employee_name_snapshot text,
    note text not null default '',
    request_id text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id) references public.logistics_shipments(organization_id, id) on update cascade on delete cascade,
    foreign key (organization_id, shortage_id) references public.logistics_shortages(organization_id, id) on update cascade on delete cascade,
    unique (organization_id, shipment_id, request_id),
    check (
      (type = 'FINAL_LOSS' and compensation_amount is null and currency is null and exchange_rate_snapshot is null and base_currency_amount is null)
      or
      (type = 'COMPENSATED' and compensation_amount > 0 and currency is not null and exchange_rate_snapshot > 0 and base_currency_amount > 0)
    )
);

create index if not exists idx_logistics_shortage_settlements_shipment
    on public.logistics_shortage_settlements (organization_id, shipment_id);
create index if not exists idx_logistics_shortage_settlements_shortage
    on public.logistics_shortage_settlements (organization_id, shortage_id);

create table if not exists public.logistics_late_cost_adjustments (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    cost_id text not null,
    recorded_at bigint not null,
    employee_id text,
    employee_name_snapshot text,
    request_id text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id) references public.logistics_shipments(organization_id, id) on update cascade on delete cascade,
    foreign key (organization_id, cost_id) references public.logistics_costs(organization_id, id) on update cascade on delete cascade,
    unique (organization_id, cost_id),
    unique (organization_id, shipment_id, request_id)
);

create table if not exists public.logistics_late_cost_allocations (
    organization_id uuid not null,
    id text not null,
    adjustment_id text not null,
    shipment_id text not null,
    shipment_line_id text not null,
    amount numeric not null check (amount >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, adjustment_id) references public.logistics_late_cost_adjustments(organization_id, id) on update cascade on delete cascade,
    foreign key (organization_id, shipment_id) references public.logistics_shipments(organization_id, id) on update cascade on delete cascade,
    foreign key (organization_id, shipment_line_id) references public.logistics_shipment_lines(organization_id, id) on update cascade on delete restrict,
    unique (organization_id, adjustment_id, shipment_line_id)
);

create index if not exists idx_logistics_late_cost_adjustments_shipment
    on public.logistics_late_cost_adjustments (organization_id, shipment_id);
create index if not exists idx_logistics_late_cost_allocations_shipment
    on public.logistics_late_cost_allocations (organization_id, shipment_id);

-- Same tenant boundary as the existing Logistics V2 tables. Remote DELETE remains disabled.
do $$
declare
    table_name text;
    policy_name text;
begin
    foreach table_name in array array[
        'logistics_shortage_settlements',
        'logistics_late_cost_adjustments',
        'logistics_late_cost_allocations'
    ]
    loop
        execute format('alter table public.%I enable row level security', table_name);
        execute format('revoke all on public.%I from anon', table_name);
        execute format('revoke delete on public.%I from public, anon, authenticated', table_name);
        execute format('grant select, insert, update on public.%I to authenticated', table_name);

        policy_name := table_name || '_org_select';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format('create policy %I on public.%I for select to authenticated using (organization_id = public.logistics_v2_current_organization_id())', policy_name, table_name);

        policy_name := table_name || '_org_insert';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format('create policy %I on public.%I for insert to authenticated with check (organization_id = public.logistics_v2_current_organization_id())', policy_name, table_name);

        policy_name := table_name || '_org_update';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format('create policy %I on public.%I for update to authenticated using (organization_id = public.logistics_v2_current_organization_id()) with check (organization_id = public.logistics_v2_current_organization_id())', policy_name, table_name);
    end loop;
end
$$;

commit;
