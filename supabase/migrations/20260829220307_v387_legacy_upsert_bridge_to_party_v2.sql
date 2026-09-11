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
        when pg_catalog.strpos(v_tokens,',COMPETITOR,')>0 then 'COMPETITOR'
        when pg_catalog.strpos(v_tokens,',COMPANY,')>0 then 'COMPANY'
        when pg_catalog.strpos(v_tokens,',INSTITUTION,')>0 then 'INSTITUTION'
        when pg_catalog.strpos(v_tokens,',WORKSHOP_OWNER,')>0 then 'WORKSHOP_OWNER'
        when pg_catalog.strpos(v_tokens,',MARKETER,')>0 then 'MARKETER'
        when pg_catalog.strpos(v_tokens,',TRADER,')>0 then 'TRADER'
        when pg_catalog.strpos(v_tokens,',DISTRIBUTOR,')>0 then 'DISTRIBUTOR'
        when pg_catalog.strpos(v_tokens,',WHOLESALE_TRADER,')>0 then 'WHOLESALE_TRADER'
        when pg_catalog.strpos(v_tokens,',CAR_OWNER,')>0 then 'CAR_OWNER'
        when pg_catalog.strpos(v_tokens,',MECHANIC,')>0 then 'MECHANIC'
        when pg_catalog.strpos(v_tokens,',SHOP_OWNER,')>0 then 'SHOP_OWNER'
        when pg_catalog.strpos(v_tokens,',OTHER,')>0 then 'OTHER'
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
        p_purchase_contact_name => case when v_segment in ('COMPANY','INSTITUTION') then coalesce(p_specialty,'') else '' end,
        p_business_activity => case when v_segment in ('COMPANY','INSTITUTION','DISTRIBUTOR') then coalesce(p_workplace,'') when v_segment in ('WORKSHOP_OWNER','TRADER','COMPETITOR') then coalesce(p_specialty,'') else '' end,
        p_workplace_name => case when v_segment='INDIVIDUAL' then coalesce(p_workplace,'') else '' end,
        p_shop_name => case when v_segment in ('TRADER','COMPETITOR') then coalesce(p_workplace,'') else '' end,
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
