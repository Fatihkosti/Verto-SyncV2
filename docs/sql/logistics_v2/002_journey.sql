begin;

create table if not exists public.logistics_partners (
    organization_id uuid not null,
    id text not null,
    name text not null,
    role text not null,
    phone text,
    notes text not null default '',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id)
);

create table if not exists public.logistics_transport_details (
    organization_id uuid not null,
    id text generated always as (shipment_id) stored,
    shipment_id text not null,
    incoterm_code text,
    container_number text,
    bill_or_airway_number text,
    vessel_or_flight_reference text,
    weight_kg text,
    volume_m3 text,
    package_count bigint,
    pallet_count bigint,
    insurance_reference text,
    updated_at timestamptz not null default now(),
    primary key (organization_id, shipment_id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade
);

create table if not exists public.logistics_shipment_partner_links (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    partner_id text not null,
    role text not null,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, partner_id)
        references public.logistics_partners (organization_id, id)
        on update cascade on delete restrict,
    unique (organization_id, shipment_id, partner_id, role)
);

create table if not exists public.logistics_documents (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    milestone_id text,
    type text not null,
    display_name text not null,
    mime_type text not null,
    size_bytes bigint not null check (size_bytes >= 0),
    private_uri text not null,
    sha256 text not null,
    created_at bigint not null,
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, milestone_id)
        references public.logistics_milestones (organization_id, id)
        on update cascade on delete restrict
);

create table if not exists public.logistics_costs (
    organization_id uuid not null,
    id text not null,
    shipment_id text not null,
    type text not null,
    amount text not null,
    currency text not null,
    exchange_rate_snapshot text not null,
    base_currency_amount text not null,
    status text not null,
    service_partner_id text,
    reference text,
    note text not null default '',
    updated_at timestamptz not null default now(),
    primary key (organization_id, id),
    foreign key (organization_id, shipment_id)
        references public.logistics_shipments (organization_id, id)
        on update cascade on delete cascade,
    foreign key (organization_id, service_partner_id)
        references public.logistics_partners (organization_id, id)
        on update cascade on delete restrict
);

commit;
