begin;

create table if not exists public.logistics_receiving_batches (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    request_id text not null,
    received_at bigint not null,
    received_by_employee_id text not null,
    received_by_employee_name_snapshot text not null,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    unique (organization_id, request_id)
);

create table if not exists public.logistics_receiving_lines (
    organization_id uuid not null,
    id text not null,
    batch_id text not null,
    shipment_id text not null,
    shipment_line_id text not null,
    expected_quantity_snapshot bigint not null check (expected_quantity_snapshot >= 0),
    received_quantity bigint not null check (received_quantity >= 0),
    accepted_quantity bigint not null check (accepted_quantity >= 0),
    damaged_quantity bigint not null check (damaged_quantity >= 0),
    rejected_quantity bigint not null check (rejected_quantity >= 0),
    quarantined_quantity bigint not null check (quarantined_quantity >= 0),
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, batch_id)
        references public.logistics_receiving_batches (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, shipment_line_id)
        references public.logistics_shipment_lines (organization_id, id)
        on update cascade on delete restrict,
    check (accepted_quantity + damaged_quantity + rejected_quantity + quarantined_quantity = received_quantity)
);

create table if not exists public.logistics_inventory_postings (
    organization_id uuid not null,
    id text generated always as (posting_id) stored,
    posting_id text not null,
    shipment_id text not null,
    receiving_batch_id text not null,
    receiving_line_id text not null,
    quantity bigint not null check (quantity >= 0),
    updated_at timestamptz not null default now(),
    primary key (organization_id, posting_id),
    foreign key (organization_id, receiving_batch_id)
        references public.logistics_receiving_batches (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, receiving_line_id)
        references public.logistics_receiving_lines (organization_id, id)
        on update cascade on delete cascade,
    unique (organization_id, receiving_line_id)
);

create table if not exists public.logistics_cost_allocations (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    shipment_line_id text not null,
    amount text not null,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, shipment_line_id)
        references public.logistics_shipment_lines (organization_id, id)
        on update cascade on delete restrict,
    unique (organization_id, shipment_id, shipment_line_id)
);

commit;
