-- Verto v229 foundation contract. Additive/idempotent; Logistics V2 remains runtime-disabled.
-- Requires Logistics V2 SQL 001..009. Applying this file does NOT enable SyncLogisticsV2.
begin;

-- Core invoices: explicit business truth replaces shipment_id inference.
alter table if exists public.invoices
  add column if not exists purchase_scope text not null default 'LOCAL';
alter table if exists public.invoices
  drop constraint if exists invoices_purchase_scope_check;
alter table if exists public.invoices
  add constraint invoices_purchase_scope_check check (purchase_scope in ('LOCAL','INTERNATIONAL'));

update public.invoices i
set purchase_scope = 'INTERNATIONAL'
where i.category = 'PURCHASE'
  and i.purchase_scope = 'LOCAL'
  and (
    i.shipment_id is not null
    or exists (
      select 1 from public.clients c
      where c.id = i.client_id and coalesce(c.client_types, '') like '%GLOBAL_SUPPLIER%'
    )
  );
create index if not exists invoices_purchase_scope_idx on public.invoices(purchase_scope);

-- Logistics state/time/planning metadata.
alter table public.logistics_shipments
  add column if not exists customs_milestone_id text,
  add column if not exists customs_calendar_policy_id text not null default 'FRIDAY_OFF',
  add column if not exists event_timezone_id text not null default 'UTC';
update public.logistics_shipments set state = 'AT_STATION' where state = 'ARRIVED';
update public.logistics_shipments set state = 'RECEIVING' where state in ('PARTIAL','RECEIVED');

alter table public.logistics_events
  add column if not exists recorded_at bigint not null default 0;
update public.logistics_events set recorded_at = occurred_at where recorded_at = 0;

alter table public.logistics_partners
  add column if not exists representative_name text,
  add column if not exists representative_phone text;

alter table public.logistics_custody_handoffs
  add column if not exists opened_package_count bigint not null default 0,
  add column if not exists damaged_package_count bigint not null default 0;

alter table public.logistics_costs
  add column if not exists exchange_rate_date bigint;

-- Cost and payment are separate records from v229 onward.
create table if not exists public.logistics_payments (
  organization_id uuid not null,
  id text not null,
  shipment_id text not null,
  cost_id text not null,
  state text not null,
  amount text not null,
  currency text not null default 'SDG',
  account_id text,
  paid_at bigint,
  reference text,
  proof_document_id text,
  cash_reference text,
  request_id text not null,
  created_at bigint not null,
  server_updated_at timestamptz not null default now(),
  primary key (organization_id,id),
  foreign key (organization_id,shipment_id) references public.logistics_shipments(organization_id,id) on update cascade on delete cascade,
  foreign key (organization_id,cost_id) references public.logistics_costs(organization_id,id) on update cascade on delete cascade
);
create index if not exists logistics_payments_org_shipment_idx on public.logistics_payments(organization_id,shipment_id);
create index if not exists logistics_payments_org_cost_idx on public.logistics_payments(organization_id,cost_id);
create unique index if not exists logistics_payments_org_request_uq on public.logistics_payments(organization_id,request_id);
create unique index if not exists logistics_payments_org_cash_reference_uq on public.logistics_payments(organization_id,cash_reference);

-- Preserve historical paid facts. New writes use logistics_payments as the payment truth.
insert into public.logistics_payments(
  organization_id,id,shipment_id,cost_id,state,amount,currency,paid_at,reference,cash_reference,request_id,created_at
)
select organization_id,
       id || ':legacy-payment',
       shipment_id,
       id,
       case when payment_state = 'REVERSED' then 'REVERSED' else 'PAID' end,
       coalesce(cash_posted_base_amount,base_currency_amount),
       'SDG',
       cash_posted_at,
       reference,
       cash_reference,
       id || ':legacy-payment',
       coalesce(cash_posted_at,0)
from public.logistics_costs
where payment_state in ('PAID','REVERSED') and cash_reference is not null
on conflict (organization_id,id) do nothing;

-- Route template foundation; no supplier/invoice/carrier/cost runtime facts are stored here.
create table if not exists public.logistics_route_templates (
  organization_id uuid not null,
  id text not null,
  name text not null,
  origin_country_code text not null,
  origin_city text not null,
  destination_country_code text not null,
  destination_city text not null,
  transport_plan_kind text not null,
  unified_transport_mode text,
  customs_stop_order bigint,
  expected_customs_minutes bigint,
  created_at bigint not null,
  updated_at bigint not null,
  server_updated_at timestamptz not null default now(),
  primary key (organization_id,id)
);
create unique index if not exists logistics_route_templates_org_name_uq
  on public.logistics_route_templates(organization_id,name);

create table if not exists public.logistics_route_template_stops (
  organization_id uuid not null,
  id text not null,
  template_id text not null,
  stop_order bigint not null,
  country_code text not null,
  city text not null,
  place_name text not null default '',
  expected_transit_minutes_to_next bigint,
  server_updated_at timestamptz not null default now(),
  primary key (organization_id,id),
  foreign key (organization_id,template_id) references public.logistics_route_templates(organization_id,id) on update cascade on delete cascade
);
create index if not exists logistics_route_template_stops_org_template_idx
  on public.logistics_route_template_stops(organization_id,template_id);
create unique index if not exists logistics_route_template_stops_org_order_uq
  on public.logistics_route_template_stops(organization_id,template_id,stop_order);

-- New synchronized tables use the same organization boundary as Logistics V2.
do $$
declare table_name text; policy_name text;
begin
  foreach table_name in array array['logistics_payments','logistics_route_templates','logistics_route_template_stops'] loop
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
end $$;

revoke delete on table public.logistics_payments, public.logistics_route_templates, public.logistics_route_template_stops
  from public, anon, authenticated;

commit;

-- Runtime gate remains false in SyncLogisticsV2. Server activation requires an independent live RLS verification.
