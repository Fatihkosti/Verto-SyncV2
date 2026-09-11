begin;

create table if not exists public.logistics_shipments (
    organization_id uuid not null,
    id text not null,
    shipment_number text not null,
    source_location text not null,
    destination_location text not null,
    state text not null,
    created_at bigint not null,
    transport_mode text,
    assigned_employee_id text,
    assigned_employee_name_snapshot text,
    started_at bigint,
    expected_departure_at bigint,
    expected_arrival_at bigint,
    notes text not null default '',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    unique (organization_id, shipment_number)
);

create table if not exists public.logistics_shipment_sources (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    invoice_id text not null,
    supplier_id text not null,
    supplier_name_snapshot text not null,
    invoice_number_snapshot text not null,
    original_currency text,
    exchange_rate_snapshot text,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade
);

create table if not exists public.logistics_shipment_lines (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    source_invoice_id text not null,
    source_invoice_item_id text not null,
    inventory_item_id text not null,
    item_name_snapshot text not null,
    expected_quantity bigint not null check (expected_quantity > 0),
    base_purchase_unit_price text not null,
    hs_code text,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade
);

create table if not exists public.logistics_milestones (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    type text not null,
    milestone_order bigint not null,
    location text not null,
    planned_arrival_at bigint,
    arrived_at bigint,
    departed_at bigint,
    note text not null default '',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    check (departed_at is null or arrived_at is not null)
);

create unique index if not exists logistics_destination_one_per_shipment
on public.logistics_milestones (organization_id, shipment_id)
where type = 'DESTINATION';

create table if not exists public.logistics_assignments (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    employee_id text not null,
    employee_name_snapshot text not null,
    assigned_at bigint not null,
    ended_at bigint,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade
);

create unique index if not exists logistics_one_active_assignment
on public.logistics_assignments (organization_id, shipment_id)
where ended_at is null;

create table if not exists public.logistics_events (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    type text not null,
    occurred_at bigint not null,
    employee_id text,
    employee_name_snapshot text,
    request_id text not null,
    payload_json text not null default '{}',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    unique (organization_id, shipment_id, type, request_id)
);

commit;
