-- Verto v388 — customer feature closeout: six segments + role-derived competitor.
UPDATE public.customer_profiles
SET segment = CASE
    WHEN segment IN ('INDIVIDUAL','CAR_OWNER','OTHER') THEN 'INDIVIDUAL'
    WHEN segment IN ('COMPANY','INSTITUTION') THEN 'COMPANY'
    WHEN segment IN ('WORKSHOP_OWNER','MECHANIC') THEN 'WORKSHOP_OWNER'
    WHEN segment = 'MARKETER' THEN 'MARKETER'
    WHEN segment IN ('TRADER','SHOP_OWNER','COMPETITOR') THEN 'TRADER'
    WHEN segment IN ('DISTRIBUTOR','WHOLESALE_TRADER') THEN 'DISTRIBUTOR'
    ELSE 'INDIVIDUAL'
END
WHERE segment NOT IN ('INDIVIDUAL','COMPANY','WORKSHOP_OWNER','MARKETER','TRADER','DISTRIBUTOR');

ALTER TABLE public.customer_profiles
    DROP CONSTRAINT IF EXISTS customer_profiles_segment_v388_check;
ALTER TABLE public.customer_profiles
    ADD CONSTRAINT customer_profiles_segment_v388_check
    CHECK (segment IN ('INDIVIDUAL','COMPANY','WORKSHOP_OWNER','MARKETER','TRADER','DISTRIBUTOR'));

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
        else pg_catalog.upper(coalesce(nullif(pg_catalog.btrim(p_customer_segment),''),'INDIVIDUAL'))
    end;
    if v_customer_segment is not null and v_customer_segment not in (
        'INDIVIDUAL','COMPANY','WORKSHOP_OWNER','MARKETER','TRADER','DISTRIBUTOR'
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


-- Verto v387 — compatibility bridge for older installed builds.
-- The legacy argument is accepted only at the API edge and immediately normalized into Party V2.
create or replace function public.verto_upsert_client_v1(
    p_client_id uuid,
    p_name text,
    p_phone text,
    p_address text default '',
    p_workplace text default '',
    p_general_note text default '',
    p_client_types text default 'INDIVIDUAL',
    p_car_type text default '',
    p_bank_account text default '',
    p_specialty text default '',
    p_secondary_phones text default '',
    p_created_at timestamptz default null
) returns uuid
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth','extensions'
as $function$
declare
    v_tokens text := ',' || pg_catalog.upper(pg_catalog.replace(coalesce(nullif(pg_catalog.btrim(p_client_types),''),'INDIVIDUAL'),' ','')) || ',';
    v_segment text;
    v_supplier_scope text;
    v_age integer;
begin
    v_segment := case
        when pg_catalog.strpos(v_tokens,',COMPETITOR,')>0 then 'TRADER'
        when pg_catalog.strpos(v_tokens,',COMPANY,')>0 then 'COMPANY'
        when pg_catalog.strpos(v_tokens,',INSTITUTION,')>0 then 'COMPANY'
        when pg_catalog.strpos(v_tokens,',WORKSHOP_OWNER,')>0 then 'WORKSHOP_OWNER'
        when pg_catalog.strpos(v_tokens,',MARKETER,')>0 then 'MARKETER'
        when pg_catalog.strpos(v_tokens,',TRADER,')>0 then 'TRADER'
        when pg_catalog.strpos(v_tokens,',DISTRIBUTOR,')>0 then 'DISTRIBUTOR'
        when pg_catalog.strpos(v_tokens,',WHOLESALE_TRADER,')>0 then 'DISTRIBUTOR'
        when pg_catalog.strpos(v_tokens,',CAR_OWNER,')>0 then 'INDIVIDUAL'
        when pg_catalog.strpos(v_tokens,',MECHANIC,')>0 then 'WORKSHOP_OWNER'
        when pg_catalog.strpos(v_tokens,',SHOP_OWNER,')>0 then 'TRADER'
        when pg_catalog.strpos(v_tokens,',OTHER,')>0 then 'INDIVIDUAL'
        when pg_catalog.strpos(v_tokens,',SUPPLIER,')=0 and pg_catalog.strpos(v_tokens,',GLOBAL_SUPPLIER,')=0 then 'INDIVIDUAL'
        else null
    end;
    v_supplier_scope := case
        when pg_catalog.strpos(v_tokens,',GLOBAL_SUPPLIER,')>0 then 'INTERNATIONAL'
        when pg_catalog.strpos(v_tokens,',SUPPLIER,')>0 then 'LOCAL'
        when pg_catalog.strpos(v_tokens,',COMPETITOR,')>0 then 'UNKNOWN'
        else null
    end;
    if v_segment='INDIVIDUAL' and pg_catalog.btrim(coalesce(p_specialty,'')) ~ '^[0-9]{1,3}$' then
        v_age := pg_catalog.btrim(p_specialty)::integer;
        if v_age not between 1 and 120 then v_age := null; end if;
    end if;

    return public.verto_upsert_party_v2(
        p_party_id => p_client_id,
        p_name => p_name,
        p_phone => p_phone,
        p_address => p_address,
        p_workplace => p_workplace,
        p_general_note => p_general_note,
        p_car_type => p_car_type,
        p_bank_account => p_bank_account,
        p_specialty => p_specialty,
        p_secondary_phones => p_secondary_phones,
        p_created_at => p_created_at,
        p_customer_role_status => case when v_segment is null then null else 'ACTIVE' end,
        p_customer_segment => v_segment,
        p_age_years => v_age,
        p_purchase_contact_name => case when v_segment='COMPANY' then coalesce(p_specialty,'') else '' end,
        p_business_activity => case when v_segment in ('COMPANY','DISTRIBUTOR') then coalesce(p_workplace,'') when v_segment in ('WORKSHOP_OWNER','TRADER') then coalesce(p_specialty,'') else '' end,
        p_workplace_name => case when v_segment='INDIVIDUAL' then coalesce(p_workplace,'') else '' end,
        p_shop_name => case when v_segment='TRADER' then coalesce(p_workplace,'') else '' end,
        p_workshop_name => case when v_segment='WORKSHOP_OWNER' then coalesce(p_workplace,'') else '' end,
        p_vehicle_models => coalesce(p_car_type,''),
        p_workshop_worker_count => case when v_segment='WORKSHOP_OWNER' and pg_catalog.btrim(coalesce(p_secondary_phones,'')) ~ '^[0-9]{1,5}$' then pg_catalog.btrim(p_secondary_phones)::integer else null end,
        p_supplier_role_status => case when v_supplier_scope is null then null else 'ACTIVE' end,
        p_supplier_scope => v_supplier_scope,
        p_supplier_country => '',
        p_supplier_currency_code => case when v_supplier_scope='INTERNATIONAL' and pg_catalog.upper(pg_catalog.btrim(coalesce(p_secondary_phones,''))) in ('SDG','USD','EUR','SAR','AED','EGP','CNY') then pg_catalog.upper(pg_catalog.btrim(p_secondary_phones)) else '' end,
        p_supplier_specialty => coalesce(p_specialty,'')
    );
end;
$function$;

revoke all on function public.verto_upsert_client_v1(uuid,text,text,text,text,text,text,text,text,text,text,timestamptz) from public, anon;
grant execute on function public.verto_upsert_client_v1(uuid,text,text,text,text,text,text,text,text,text,text,timestamptz) to authenticated, service_role;


-- v387: finish external Party V2 cutover before clients.client_types is dropped.
-- Wire return labels named client_types are retained only for backwards-compatible clients;
-- their values come from customer_profiles.segment and never from a legacy column.

create or replace function public.verto_max_join_code_candidates_v1()
returns table(competitor_party_id uuid, name text, phone text, client_types text, link_status text)
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'max_auth', 'max_integration'
as $function$
declare v_actor uuid:=auth.uid(); v_org uuid:=public.get_my_org_id();
begin
    if v_actor is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if v_org is null then raise exception 'organization_session_required' using errcode='42501'; end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('clients_edit')) then
        raise exception 'clients_edit_required' using errcode='42501';
    end if;
    return query
    select c.id,c.name,coalesce(c.phone,''),cp.segment,l.status
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_org
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_org and cp.deleted_at is null
    join public.party_roles prs
      on prs.party_id=c.id and prs.organization_id=v_org
     and prs.role='SUPPLIER' and prs.status='ACTIVE' and prs.deleted_at is null
    left join max_integration.verto_store_link l
      on l.organization_id=v_org and l.competitor_party_id=c.id
    where c.organization_id=v_org and coalesce(l.status,'')<>'ACTIVE'
      and not exists(
          select 1 from max_auth.registration_code rc
          where rc.source='VERTO' and rc.organization_id=v_org and rc.verto_party_id=c.id
            and rc.used_at is null and rc.expires_at>now()
      )
    order by c.name;
end;
$function$;

create or replace function public.verto_issue_max_join_code_v1(
    p_org_id uuid,
    p_competitor_party_id uuid,
    p_expires_in_minutes integer default 1440
)
returns table(organization_id uuid, competitor_party_id uuid, store_id text, code text, expires_at timestamptz)
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'max_auth', 'max_integration', 'extensions'
as $function$
declare
    v_actor uuid := auth.uid(); v_org uuid := public.get_my_org_id();
    v_client_id uuid; v_client_name text; v_store_id text; v_code text; v_digest text;
    v_expires timestamptz; v_attempt integer:=0; v_link_status text;
begin
    if v_actor is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if p_org_id is null or v_org is null or p_org_id is distinct from v_org then
        raise exception 'organization_session_mismatch' using errcode='42501';
    end if;
    if p_expires_in_minutes is distinct from 1440 then
        raise exception 'join_code_expiry_must_be_24_hours' using errcode='22023';
    end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('clients_edit')) then
        raise exception 'clients_edit_required' using errcode='42501';
    end if;

    select c.id,c.name into v_client_id,v_client_name
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_org
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_org and cp.deleted_at is null
    join public.party_roles prs
      on prs.party_id=c.id and prs.organization_id=v_org
     and prs.role='SUPPLIER' and prs.status='ACTIVE' and prs.deleted_at is null
    where c.id=p_competitor_party_id and c.organization_id=v_org
    for share of c;
    if not found then raise exception 'party_is_not_competitor' using errcode='22023'; end if;

    v_store_id:=v_client_id::text;
    select l.status into v_link_status from max_integration.verto_store_link l
    where l.organization_id=v_org and l.competitor_party_id=v_client_id for update;
    if v_link_status='ACTIVE' then raise exception 'competitor_already_linked' using errcode='23505'; end if;
    if exists(select 1 from max_auth.app_user u where u.store_id=v_store_id and u.status='ACTIVE') then
        raise exception 'competitor_already_linked' using errcode='23505';
    end if;

    insert into max_auth.store(id,display_name,status) values(v_store_id,v_client_name,'ACTIVE')
    on conflict(id) do update set display_name=excluded.display_name,status='ACTIVE';
    insert into max_integration.verto_store_link(organization_id,competitor_party_id,store_id,status,updated_at)
    values(v_org,v_client_id,v_store_id,'PENDING',now())
    on conflict(organization_id,competitor_party_id) do update
      set store_id=excluded.store_id,status='PENDING',max_user_id=null,phone_e164=null,activated_at=null,updated_at=now();
    update max_auth.registration_code set used_at=coalesce(used_at,now())
    where source='VERTO' and organization_id=v_org and verto_party_id=v_client_id and used_at is null;

    v_expires:=now()+interval '24 hours';
    loop
        v_attempt:=v_attempt+1;
        if v_attempt>20 then raise exception 'code_generation_failed' using errcode='P0001'; end if;
        v_code:=max_integration.generate_numeric_code_v1();
        v_digest:='verto-sha256:'||encode(extensions.digest(v_code,'sha256'),'hex');
        begin
            insert into max_auth.registration_code(id,store_id,secret_digest,expires_at,source,organization_id,verto_party_id)
            values(extensions.gen_random_uuid()::text,v_store_id,v_digest,v_expires,'VERTO',v_org,v_client_id);
            exit;
        exception when unique_violation then
            if v_attempt>=20 then raise; end if;
        end;
    end loop;
    return query select v_org,v_client_id,v_store_id,v_code,v_expires;
end;
$function$;


revoke all on function public.verto_max_join_code_candidates_v1() from public, anon;
grant execute on function public.verto_max_join_code_candidates_v1() to authenticated, service_role;
revoke all on function public.verto_issue_max_join_code_v1(uuid,uuid,integer) from public, anon;
grant execute on function public.verto_issue_max_join_code_v1(uuid,uuid,integer) to authenticated, service_role;
