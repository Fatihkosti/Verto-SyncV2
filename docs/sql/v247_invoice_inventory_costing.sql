-- Verto v247 — immutable sale-cost snapshots + inventory costing audit contract.
-- Additive/idempotent. Apply before clients start syncing v247 invoice lines.

alter table public.invoice_items add column if not exists unit_sell_price numeric not null default 0;
alter table public.invoice_items add column if not exists unit_sell_price_minor bigint not null default 0;
alter table public.invoice_items add column if not exists unit_cost_at_sale numeric not null default 0;
alter table public.invoice_items add column if not exists unit_cost_at_sale_minor bigint not null default 0;
alter table public.invoice_items add column if not exists line_revenue_snapshot numeric not null default 0;
alter table public.invoice_items add column if not exists line_revenue_snapshot_minor bigint not null default 0;
alter table public.invoice_items add column if not exists line_cost_snapshot numeric not null default 0;
alter table public.invoice_items add column if not exists line_cost_snapshot_minor bigint not null default 0;
alter table public.invoice_items add column if not exists gross_profit_snapshot numeric not null default 0;
alter table public.invoice_items add column if not exists gross_profit_snapshot_minor bigint not null default 0;
alter table public.invoice_items add column if not exists cost_snapshot_status text not null default 'LEGACY_UNKNOWN';

-- Safe legacy backfill: historical revenue/sell price are known; historical cost is deliberately left unknown.
update public.invoice_items
set unit_sell_price = sell_price,
    unit_sell_price_minor = round(sell_price * 100)::bigint,
    line_revenue_snapshot = total_price,
    line_revenue_snapshot_minor = round(total_price * 100)::bigint
where cost_snapshot_status = 'LEGACY_UNKNOWN';

create table if not exists public.inventory_cost_revaluation_events (
    id text primary key,
    organization_id uuid,
    item_id text not null,
    quantity_before integer not null,
    old_unit_cost_minor bigint not null,
    new_unit_cost_minor bigint not null,
    revaluation_difference_minor bigint not null,
    source_type text not null,
    source_id text not null,
    source_version integer not null default 1,
    actor_id text not null default '',
    actor_name text not null default '',
    occurred_at bigint not null,
    write_id text not null
);
create index if not exists index_inventory_cost_revaluation_events_item_id
    on public.inventory_cost_revaluation_events(item_id);
create unique index if not exists index_inventory_cost_revaluation_identity
    on public.inventory_cost_revaluation_events(source_type, source_id, write_id, item_id, new_unit_cost_minor);

create table if not exists public.landed_cost_adjustment_events (
    id text primary key,
    organization_id uuid,
    posting_id text not null,
    shipment_id text not null,
    item_id text not null,
    quantity_at_adjustment integer not null,
    previous_posting_unit_cost_minor bigint not null,
    new_posting_unit_cost_minor bigint not null,
    previous_inventory_unit_cost_minor bigint not null,
    new_inventory_unit_cost_minor bigint not null,
    revaluation_difference_minor bigint not null,
    occurred_at bigint not null,
    write_id text not null
);
create index if not exists index_landed_cost_adjustment_events_shipment_id
    on public.landed_cost_adjustment_events(shipment_id);
create unique index if not exists index_landed_cost_adjustment_identity
    on public.landed_cost_adjustment_events(posting_id, new_posting_unit_cost_minor);
