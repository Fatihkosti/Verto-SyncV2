-- Verto F253 — controlled purchase cycle: PO -> GRN -> supplier invoice -> payment.
-- Additive/idempotent. Deploy only after F252 and before enabling F253 purchase-cycle UI/sync.
begin;

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

commit;
