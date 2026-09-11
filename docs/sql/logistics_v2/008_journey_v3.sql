-- Verto Logistics Journey V3 server contract for Android Room schema 55.
-- Apply only after 001..007. Additive/idempotent where PostgreSQL permits it.
begin;

-- Room 55 additive columns.
alter table public.logistics_milestones
  add column if not exists country_code text not null default '',
  add column if not exists country_name_snapshot text not null default '',
  add column if not exists city text not null default '',
  add column if not exists place_name text not null default '',
  add column if not exists plan_kind text not null default 'PLANNED',
  add column if not exists expected_stay_days bigint,
  add column if not exists customs_broker_partner_id text,
  add column if not exists customs_broker_name_snapshot text,
  add column if not exists customs_broker_phone_snapshot text;

update public.logistics_milestones set place_name = location where place_name = '';

alter table public.logistics_shipment_legs
  add column if not exists plan_kind text not null default 'PLANNED',
  add column if not exists expected_transit_days bigint,
  add column if not exists representative_name_snapshot text,
  add column if not exists representative_phone_snapshot text,
  add column if not exists package_count bigint,
  add column if not exists weight_kg text,
  add column if not exists superseded_at bigint,
  add column if not exists superseded_by_leg_id text;

alter table public.logistics_custody_handoffs
  add column if not exists handover_package_count bigint,
  add column if not exists received_package_count bigint,
  add column if not exists handover_weight_kg text,
  add column if not exists received_weight_kg text,
  add column if not exists package_change_reason text,
  add column if not exists package_change_note text;

alter table public.logistics_costs
  add column if not exists description text not null default '',
  add column if not exists payment_state text not null default 'UNPAID',
  add column if not exists cash_reference text,
  add column if not exists cash_posted_base_amount text,
  add column if not exists cash_posted_at bigint,
  add column if not exists reversal_of_cost_id text,
  add column if not exists recovery_id text,
  add column if not exists request_id text;

alter table public.logistics_documents
  add column if not exists handoff_id text,
  add column if not exists cost_id text,
  add column if not exists recovery_id text;

-- Room 55 shortage/recovery tables.
create table if not exists public.logistics_shortages (
  organization_id uuid not null,
  id text not null,
  shipment_id text not null,
  shipment_line_id text not null,
  original_missing_quantity bigint not null,
  remaining_missing_quantity bigint not null,
  base_purchase_unit_price_snapshot text not null,
  status text not null,
  detected_at bigint not null,
  note text not null default '',
  request_id text not null,
  updated_at timestamptz not null default now(),
  primary key (organization_id, id),
  foreign key (organization_id, shipment_id) references public.logistics_shipments(organization_id,id) on update cascade on delete cascade,
  foreign key (organization_id, shipment_line_id) references public.logistics_shipment_lines(organization_id,id) on update cascade on delete restrict
);

create table if not exists public.logistics_recoveries (
  organization_id uuid not null,
  id text not null,
  shipment_id text not null,
  recovered_at bigint not null,
  employee_id text not null,
  employee_name_snapshot text not null,
  note text not null default '',
  request_id text not null,
  updated_at timestamptz not null default now(),
  primary key (organization_id, id),
  foreign key (organization_id, shipment_id) references public.logistics_shipments(organization_id,id) on update cascade on delete cascade
);

create table if not exists public.logistics_recovery_lines (
  organization_id uuid not null,
  id text not null,
  recovery_id text not null,
  shortage_id text not null,
  shipment_line_id text not null,
  recovered_quantity bigint not null,
  base_purchase_unit_price_snapshot text not null,
  allocated_recovery_cost text not null,
  updated_at timestamptz not null default now(),
  primary key (organization_id, id),
  foreign key (organization_id, recovery_id) references public.logistics_recoveries(organization_id,id) on update cascade on delete cascade,
  foreign key (organization_id, shortage_id) references public.logistics_shortages(organization_id,id) on update cascade on delete restrict,
  foreign key (organization_id, shipment_line_id) references public.logistics_shipment_lines(organization_id,id) on update cascade on delete restrict
);

create table if not exists public.logistics_recovery_postings (
  organization_id uuid not null,
  posting_id text not null,
  recovery_id text not null,
  recovery_line_id text not null,
  shipment_id text not null,
  shipment_line_id text not null,
  quantity bigint not null,
  updated_at timestamptz not null default now(),
  primary key (organization_id, posting_id),
  foreign key (organization_id, recovery_id) references public.logistics_recoveries(organization_id,id) on update cascade on delete cascade,
  foreign key (organization_id, recovery_line_id) references public.logistics_recovery_lines(organization_id,id) on update cascade on delete cascade,
  foreign key (organization_id, shipment_line_id) references public.logistics_shipment_lines(organization_id,id) on update cascade on delete restrict
);

-- Room 55 indexes/idempotency keys.
create index if not exists logistics_shortages_org_shipment_idx on public.logistics_shortages(organization_id,shipment_id);
create index if not exists logistics_shortages_org_status_idx on public.logistics_shortages(organization_id,status);
create unique index if not exists logistics_shortages_org_shipment_line_uq on public.logistics_shortages(organization_id,shipment_id,shipment_line_id);
create unique index if not exists logistics_shortages_org_shipment_request_uq on public.logistics_shortages(organization_id,shipment_id,request_id);
create index if not exists logistics_recoveries_org_shipment_idx on public.logistics_recoveries(organization_id,shipment_id);
create unique index if not exists logistics_recoveries_org_shipment_request_uq on public.logistics_recoveries(organization_id,shipment_id,request_id);
create index if not exists logistics_recovery_lines_org_recovery_idx on public.logistics_recovery_lines(organization_id,recovery_id);
create index if not exists logistics_recovery_lines_org_shortage_idx on public.logistics_recovery_lines(organization_id,shortage_id);
create index if not exists logistics_recovery_lines_org_shipment_line_idx on public.logistics_recovery_lines(organization_id,shipment_line_id);
create unique index if not exists logistics_recovery_lines_org_recovery_shortage_uq on public.logistics_recovery_lines(organization_id,recovery_id,shortage_id);
create index if not exists logistics_recovery_postings_org_shipment_idx on public.logistics_recovery_postings(organization_id,shipment_id);
create index if not exists logistics_recovery_postings_org_recovery_idx on public.logistics_recovery_postings(organization_id,recovery_id);
create unique index if not exists logistics_recovery_postings_org_recovery_line_uq on public.logistics_recovery_postings(organization_id,recovery_line_id);
create index if not exists logistics_recovery_postings_org_shipment_line_idx on public.logistics_recovery_postings(organization_id,shipment_line_id);
create unique index if not exists logistics_costs_org_shipment_request_uq on public.logistics_costs(organization_id,shipment_id,request_id);
create index if not exists logistics_costs_org_recovery_idx on public.logistics_costs(organization_id,recovery_id);
create index if not exists logistics_documents_org_handoff_idx on public.logistics_documents(organization_id,handoff_id);
create index if not exists logistics_documents_org_cost_idx on public.logistics_documents(organization_id,cost_id);
create index if not exists logistics_documents_org_recovery_idx on public.logistics_documents(organization_id,recovery_id);

-- Organization-scoped RLS for every new synchronized table. No public/anon writes and no client DELETE.
do $$
declare table_name text; policy_name text;
begin
  foreach table_name in array array['logistics_shortages','logistics_recoveries','logistics_recovery_lines','logistics_recovery_postings'] loop
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
    execute format('drop trigger if exists %I on public.%I', 'trg_' || table_name || '_updated_at', table_name);
    execute format('create trigger %I before update on public.%I for each row execute function public.logistics_v2_touch_updated_at()', 'trg_' || table_name || '_updated_at', table_name);
  end loop;
end $$;

revoke delete on table public.logistics_shortages, public.logistics_recoveries, public.logistics_recovery_lines, public.logistics_recovery_postings from public, anon, authenticated;
commit;
