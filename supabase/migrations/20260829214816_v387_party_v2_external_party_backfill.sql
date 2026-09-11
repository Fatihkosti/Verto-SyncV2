-- V387 safety backfill before external integrations rely exclusively on Party V2.
-- Reads legacy role tokens once, creates only missing normalized CUSTOMER role/profile rows.

with candidates as (
    select
        c.id as party_id,
        c.organization_id,
        case
            when upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,COMPANY,%' then 'COMPANY'
            when upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,MARKETER,%' then 'MARKETER'
            when upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,WORKSHOP_OWNER,%' then 'WORKSHOP_OWNER'
            else null
        end as segment
    from public.clients c
    where
        upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,COMPANY,%'
        or upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,MARKETER,%'
        or upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,WORKSHOP_OWNER,%'
)
insert into public.party_roles(id,organization_id,party_id,role,status,server_revision,server_updated_at)
select extensions.gen_random_uuid(), x.organization_id, x.party_id, 'CUSTOMER', 'ACTIVE', 1, pg_catalog.now()
from candidates x
where x.segment is not null
on conflict(organization_id,party_id,role) do update
set status='ACTIVE', deleted_at=null, server_revision=party_roles.server_revision+1, server_updated_at=pg_catalog.now();

with candidates as (
    select
        c.id as party_id,
        c.organization_id,
        case
            when upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,COMPANY,%' then 'COMPANY'
            when upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,MARKETER,%' then 'MARKETER'
            when upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,WORKSHOP_OWNER,%' then 'WORKSHOP_OWNER'
            else null
        end as segment,
        c.workplace,
        c.specialty,
        c.car_type,
        c.secondary_phones
    from public.clients c
    where
        upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,COMPANY,%'
        or upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,MARKETER,%'
        or upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,WORKSHOP_OWNER,%'
)
insert into public.customer_profiles(
    party_id, organization_id, segment, vehicle_information, workshop_worker_count,
    server_revision, server_updated_at, age_years, purchase_contact_name, business_activity,
    workplace_name, shop_name, workshop_name, vehicle_models
)
select
    x.party_id,
    x.organization_id,
    x.segment,
    case when x.segment='COMPANY' then coalesce(x.car_type,'') else '' end,
    case when x.segment='WORKSHOP_OWNER' and trim(coalesce(x.secondary_phones,'')) ~ '^[0-9]{1,5}$'
         then trim(x.secondary_phones)::integer else null end,
    1,
    pg_catalog.now(),
    null,
    case when x.segment='COMPANY' then coalesce(x.specialty,'') else '' end,
    case when x.segment='COMPANY' then coalesce(x.workplace,'')
         when x.segment='WORKSHOP_OWNER' then coalesce(x.specialty,'') else '' end,
    '',
    '',
    case when x.segment='WORKSHOP_OWNER' then coalesce(x.workplace,'') else '' end,
    case when x.segment='COMPANY' then replace(coalesce(x.car_type,''),',','||') else '' end
from candidates x
where x.segment is not null
on conflict(party_id) do nothing;
