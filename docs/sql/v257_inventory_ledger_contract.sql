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
