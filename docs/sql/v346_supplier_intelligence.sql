-- Session 346 — Supplier Intelligence
-- Adds an explicit supplier-confirmed PO delivery promise. Nullable by design:
-- no promise => On-Time Delivery is unavailable, never inferred.

alter table if exists public.purchase_orders
  add column if not exists promised_delivery_at bigint;

do $$
begin
  if not exists (
    select 1
      from pg_constraint
     where conname = 'purchase_orders_promised_delivery_at_check'
       and conrelid = 'public.purchase_orders'::regclass
  ) then
    alter table public.purchase_orders
      add constraint purchase_orders_promised_delivery_at_check
      check (promised_delivery_at is null or promised_delivery_at >= created_at);
  end if;
end $$;

create index if not exists purchase_orders_supplier_promised_delivery_idx
  on public.purchase_orders(supplier_id, promised_delivery_at)
  where promised_delivery_at is not null;

-- PO promise is an immutable ordering fact, like supplier/currency/created_at.
create or replace function public.verto_guard_purchase_order_update_v253()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if old.organization_id is distinct from new.organization_id
     or old.order_number is distinct from new.order_number
     or old.supplier_id is distinct from new.supplier_id
     or old.purchase_scope is distinct from new.purchase_scope
     or old.currency_code is distinct from new.currency_code
     or old.created_at is distinct from new.created_at
     or old.promised_delivery_at is distinct from new.promised_delivery_at
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

comment on column public.purchase_orders.promised_delivery_at is
  'Supplier-confirmed delivery promise epoch milliseconds. NULL means no reliable promise captured.';
