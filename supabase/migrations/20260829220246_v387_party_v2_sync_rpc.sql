-- Verto v387 — canonical Party V2 write contract.
create or replace function public.verto_upsert_party_v2(
    p_party_id uuid,
    p_name text,
    p_phone text,
    p_address text,
    p_workplace text,
    p_general_note text,
    p_car_type text,
    p_bank_account text,
    p_specialty text,
    p_secondary_phones text,
    p_created_at timestamptz,
    p_customer_role_status text,
    p_customer_segment text,
    p_age_years integer,
    p_purchase_contact_name text,
    p_business_activity text,
    p_workplace_name text,
    p_shop_name text,
    p_workshop_name text,
    p_vehicle_models text,
    p_workshop_worker_count integer,
    p_supplier_role_status text,
    p_supplier_scope text,
    p_supplier_country text,
    p_supplier_currency_code text,
    p_supplier_specialty text
) returns uuid
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth','extensions'
as $function$
declare
    v_actor uuid := auth.uid();
    v_org uuid := public.get_my_org_id();
    v_now timestamptz := pg_catalog.now();
    v_customer_segment text;
    v_supplier_scope text;
begin
    if v_actor is null or v_org is null then
        raise exception 'auth_session_required' using errcode='28000';
    end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('clients_edit')) then
        raise exception 'clients_edit_required' using errcode='42501';
    end if;
    if p_party_id is null then raise exception 'party_id_required' using errcode='22023'; end if;
    if pg_catalog.btrim(coalesce(p_name,''))='' then raise exception 'party_name_required' using errcode='22023'; end if;
    if p_customer_role_status is not null and pg_catalog.upper(p_customer_role_status) not in ('ACTIVE','ARCHIVED') then
        raise exception 'invalid_customer_role_status' using errcode='22023';
    end if;
    if p_supplier_role_status is not null and pg_catalog.upper(p_supplier_role_status) not in ('ACTIVE','ARCHIVED') then
        raise exception 'invalid_supplier_role_status' using errcode='22023';
    end if;

    v_customer_segment := case
        when p_customer_role_status is null then null
        else pg_catalog.upper(coalesce(nullif(pg_catalog.btrim(p_customer_segment),''),'OTHER'))
    end;
    if v_customer_segment is not null and v_customer_segment not in (
        'INDIVIDUAL','COMPANY','INSTITUTION','CAR_OWNER','MECHANIC','SHOP_OWNER',
        'WORKSHOP_OWNER','MARKETER','TRADER','DISTRIBUTOR','WHOLESALE_TRADER','COMPETITOR','OTHER'
    ) then raise exception 'invalid_customer_segment' using errcode='22023'; end if;

    v_supplier_scope := case
        when p_supplier_role_status is null then null
        else pg_catalog.upper(coalesce(nullif(pg_catalog.btrim(p_supplier_scope),''),'UNKNOWN'))
    end;
    if v_supplier_scope is not null and v_supplier_scope not in ('LOCAL','INTERNATIONAL','UNKNOWN') then
        raise exception 'invalid_supplier_scope' using errcode='22023';
    end if;
    if p_age_years is not null and (p_age_years < 1 or p_age_years > 120) then
        raise exception 'invalid_age_years' using errcode='22023';
    end if;
    if p_workshop_worker_count is not null and p_workshop_worker_count < 0 then
        raise exception 'invalid_workshop_worker_count' using errcode='22023';
    end if;

    if exists(select 1 from public.clients c where c.id=p_party_id and c.organization_id is distinct from v_org) then
        raise exception 'party_tenant_mismatch' using errcode='42501';
    end if;

    insert into public.clients as c(
        id,organization_id,created_by,name,phone,address,workplace,general_note,
        car_type,bank_account,specialty,secondary_phones,created_at,updated_at
    ) values(
        p_party_id,v_org,v_actor,pg_catalog.btrim(p_name),coalesce(p_phone,''),coalesce(p_address,''),
        coalesce(p_workplace,''),coalesce(p_general_note,''),coalesce(p_car_type,''),coalesce(p_bank_account,''),
        coalesce(p_specialty,''),coalesce(p_secondary_phones,''),coalesce(p_created_at,v_now),v_now
    )
    on conflict(id) do update set
        name=excluded.name, phone=excluded.phone, address=excluded.address,
        workplace=excluded.workplace, general_note=excluded.general_note,
        car_type=excluded.car_type, bank_account=excluded.bank_account,
        specialty=excluded.specialty, secondary_phones=excluded.secondary_phones,
        updated_at=v_now
    where c.organization_id=v_org;
    if not found then raise exception 'party_upsert_rejected' using errcode='42501'; end if;

    if p_customer_role_status is not null then
        insert into public.party_roles as pr(
            id,organization_id,party_id,role,status,server_revision,server_updated_at,last_operation_id,
            archived_at,archived_by,archive_reason,deleted_at
        ) values(
            extensions.gen_random_uuid(),v_org,p_party_id,'CUSTOMER',pg_catalog.upper(p_customer_role_status),1,v_now,extensions.gen_random_uuid(),
            case when pg_catalog.upper(p_customer_role_status)='ARCHIVED' then v_now else null end,
            case when pg_catalog.upper(p_customer_role_status)='ARCHIVED' then v_actor else null end,
            case when pg_catalog.upper(p_customer_role_status)='ARCHIVED' then 'SYNC' else null end,null
        )
        on conflict(organization_id,party_id,role) do update set
            status=excluded.status,
            server_revision=pr.server_revision+1,
            server_updated_at=v_now,
            last_operation_id=excluded.last_operation_id,
            archived_at=excluded.archived_at,
            archived_by=excluded.archived_by,
            archive_reason=excluded.archive_reason,
            deleted_at=null;

        insert into public.customer_profiles as cp(
            party_id,organization_id,segment,vehicle_information,workshop_worker_count,
            server_revision,server_updated_at,last_operation_id,deleted_at,
            age_years,purchase_contact_name,business_activity,workplace_name,shop_name,workshop_name,vehicle_models
        ) values(
            p_party_id,v_org,v_customer_segment,coalesce(p_vehicle_models,''),p_workshop_worker_count,
            1,v_now,extensions.gen_random_uuid(),null,
            p_age_years,coalesce(p_purchase_contact_name,''),coalesce(p_business_activity,''),coalesce(p_workplace_name,''),
            coalesce(p_shop_name,''),coalesce(p_workshop_name,''),coalesce(p_vehicle_models,'')
        )
        on conflict(party_id) do update set
            organization_id=excluded.organization_id,
            segment=excluded.segment,
            vehicle_information=excluded.vehicle_information,
            workshop_worker_count=excluded.workshop_worker_count,
            server_revision=cp.server_revision+1,
            server_updated_at=v_now,
            last_operation_id=excluded.last_operation_id,
            deleted_at=null,
            age_years=excluded.age_years,
            purchase_contact_name=excluded.purchase_contact_name,
            business_activity=excluded.business_activity,
            workplace_name=excluded.workplace_name,
            shop_name=excluded.shop_name,
            workshop_name=excluded.workshop_name,
            vehicle_models=excluded.vehicle_models;
    end if;

    if p_supplier_role_status is not null then
        insert into public.party_roles as pr(
            id,organization_id,party_id,role,status,server_revision,server_updated_at,last_operation_id,
            archived_at,archived_by,archive_reason,deleted_at
        ) values(
            extensions.gen_random_uuid(),v_org,p_party_id,'SUPPLIER',pg_catalog.upper(p_supplier_role_status),1,v_now,extensions.gen_random_uuid(),
            case when pg_catalog.upper(p_supplier_role_status)='ARCHIVED' then v_now else null end,
            case when pg_catalog.upper(p_supplier_role_status)='ARCHIVED' then v_actor else null end,
            case when pg_catalog.upper(p_supplier_role_status)='ARCHIVED' then 'SYNC' else null end,null
        )
        on conflict(organization_id,party_id,role) do update set
            status=excluded.status,
            server_revision=pr.server_revision+1,
            server_updated_at=v_now,
            last_operation_id=excluded.last_operation_id,
            archived_at=excluded.archived_at,
            archived_by=excluded.archived_by,
            archive_reason=excluded.archive_reason,
            deleted_at=null;

        insert into public.supplier_profiles as sp(
            party_id,organization_id,scope,country,currency_code,specialty,
            server_revision,server_updated_at,last_operation_id,deleted_at
        ) values(
            p_party_id,v_org,v_supplier_scope,coalesce(p_supplier_country,''),coalesce(p_supplier_currency_code,''),
            coalesce(p_supplier_specialty,''),1,v_now,extensions.gen_random_uuid(),null
        )
        on conflict(party_id) do update set
            organization_id=excluded.organization_id,
            scope=excluded.scope,
            country=excluded.country,
            currency_code=excluded.currency_code,
            specialty=excluded.specialty,
            server_revision=sp.server_revision+1,
            server_updated_at=v_now,
            last_operation_id=excluded.last_operation_id,
            deleted_at=null;
    end if;

    return p_party_id;
end;
$function$;

revoke all on function public.verto_upsert_party_v2(uuid,text,text,text,text,text,text,text,text,text,timestamptz,text,text,integer,text,text,text,text,text,text,integer,text,text,text,text,text) from public, anon, service_role;
grant execute on function public.verto_upsert_party_v2(uuid,text,text,text,text,text,text,text,text,text,timestamptz,text,text,integer,text,text,text,text,text,text,integer,text,text,text,text,text) to authenticated;
