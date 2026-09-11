-- Verto v387 — Party V2 cutover for Optimal + AutoDrive.
-- Runtime eligibility is derived from party_roles + customer_profiles.
-- Legacy clients.client_types remains only for older installed builds; v387 no longer reads it here.

alter table public.customer_profiles
    add column if not exists age_years integer,
    add column if not exists purchase_contact_name text not null default '',
    add column if not exists business_activity text not null default '',
    add column if not exists workplace_name text not null default '',
    add column if not exists shop_name text not null default '',
    add column if not exists workshop_name text not null default '',
    add column if not exists vehicle_models text not null default '';

-- One-time compatibility backfill. This is migration-only; runtime functions below never read client_types.
update public.customer_profiles cp
set
    age_years = case
        when cp.segment='INDIVIDUAL'
         and trim(coalesce(c.specialty,'')) ~ '^[0-9]{1,3}$'
        then trim(c.specialty)::integer else cp.age_years end,
    purchase_contact_name = case
        when cp.segment in ('COMPANY','INSTITUTION') and cp.purchase_contact_name=''
        then coalesce(c.specialty,'') else cp.purchase_contact_name end,
    business_activity = case
        when cp.business_activity<>'' then cp.business_activity
        when cp.segment in ('COMPANY','INSTITUTION','DISTRIBUTOR') then coalesce(c.workplace,'')
        when cp.segment in ('WORKSHOP_OWNER','TRADER','COMPETITOR') then coalesce(c.specialty,'')
        else '' end,
    workplace_name = case
        when cp.segment='INDIVIDUAL' and cp.workplace_name='' then coalesce(c.workplace,'') else cp.workplace_name end,
    shop_name = case
        when cp.segment in ('TRADER','COMPETITOR') and cp.shop_name='' then coalesce(c.workplace,'') else cp.shop_name end,
    workshop_name = case
        when cp.segment='WORKSHOP_OWNER' and cp.workshop_name='' then coalesce(c.workplace,'') else cp.workshop_name end,
    vehicle_models = case
        when cp.vehicle_models<>'' then cp.vehicle_models
        when cp.segment='INDIVIDUAL' then concat_ws('||', nullif(cp.vehicle_information,''), nullif(c.secondary_phones,''))
        when cp.segment in ('COMPANY','INSTITUTION') then replace(coalesce(cp.vehicle_information,''), ',', '||')
        else coalesce(cp.vehicle_information,'') end
from public.clients c
where c.id = cp.party_id;

alter table public.customer_profiles drop constraint if exists customer_profiles_age_years_check;
alter table public.customer_profiles add constraint customer_profiles_age_years_check
    check (age_years is null or age_years between 1 and 120);
alter table public.customer_profiles drop constraint if exists customer_profiles_worker_count_check;
alter table public.customer_profiles add constraint customer_profiles_worker_count_check
    check (workshop_worker_count is null or workshop_worker_count >= 0);

create or replace function public.verto_optimal_company_parties_v2()
returns table(
    organization_id uuid,
    party_id uuid,
    name text,
    phone text,
    address text,
    general_note text,
    segment text,
    age_years integer,
    purchase_contact_name text,
    business_activity text,
    workplace_name text,
    shop_name text,
    workshop_name text,
    vehicle_models text,
    workshop_worker_count integer,
    created_at timestamptz,
    created_by uuid
)
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth'
as $function$
declare
    v_actor uuid := auth.uid();
    v_org uuid := public.get_my_org_id();
begin
    if v_actor is null then
        raise exception 'auth_session_required' using errcode='28000';
    end if;
    if v_org is null then
        raise exception 'organization_session_required' using errcode='42501';
    end if;
    if not (public.has_perm('clients_view') or public.has_perm('clients_edit')) then
        raise exception 'clients_view_required' using errcode='42501';
    end if;

    return query
    select
        v_org,
        c.id,
        c.name,
        coalesce(c.phone,''),
        coalesce(c.address,''),
        coalesce(c.general_note,''),
        cp.segment,
        cp.age_years,
        cp.purchase_contact_name,
        cp.business_activity,
        cp.workplace_name,
        cp.shop_name,
        cp.workshop_name,
        cp.vehicle_models,
        cp.workshop_worker_count,
        c.created_at,
        c.created_by
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id
     and pr.organization_id=v_org
     and pr.role='CUSTOMER'
     and pr.status='ACTIVE'
     and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id
     and cp.organization_id=v_org
     and cp.segment='COMPANY'
     and cp.deleted_at is null
    where c.organization_id=v_org
    order by c.name, c.id;
end;
$function$;
revoke all on function public.verto_optimal_company_parties_v2() from public, anon, service_role;
grant execute on function public.verto_optimal_company_parties_v2() to authenticated;

create or replace function public.verto_autodrive_join_code_candidates_v1()
returns table(client_id uuid, name text, phone text, account_type text, client_types text)
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth'
as $function$
declare
    v_actor uuid := auth.uid();
    v_org uuid := public.get_my_org_id();
begin
    if v_actor is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if v_org is null then raise exception 'organization_session_required' using errcode='42501'; end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('commission_manage')) then
        raise exception 'commission_manage_required' using errcode='42501';
    end if;

    return query
    select c.id, c.name, coalesce(c.phone,''), cp.segment, cp.segment
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_org
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_org
     and cp.segment in ('MARKETER','WORKSHOP_OWNER') and cp.deleted_at is null
    where c.organization_id=v_org
      and not exists (
          select 1 from public.autodrive_users au
          where au.client_id=c.id and au.org_id=v_org
      )
    order by c.name, c.id;
end;
$function$;

create or replace function public.verto_issue_autodrive_join_code(
    p_org_id uuid,
    p_client_id uuid,
    p_account_type text,
    p_expires_in_minutes integer default 1440
)
returns table(organization_id uuid, client_id uuid, account_type text, code text, expires_at timestamptz)
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth','extensions'
as $function$
declare
    v_actor uuid := auth.uid();
    v_session_org uuid := public.get_my_org_id();
    v_account_type text := pg_catalog.upper(pg_catalog.btrim(coalesce(p_account_type,'')));
    v_client_id uuid;
    v_code text;
    v_expires_at timestamptz;
    v_attempt integer := 0;
begin
    if v_actor is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if p_expires_in_minutes is distinct from 1440 then raise exception 'join_code_expiry_must_be_24_hours' using errcode='22023'; end if;
    if p_org_id is null or v_session_org is null or p_org_id is distinct from v_session_org then
        raise exception 'organization_session_mismatch' using errcode='42501';
    end if;
    if v_account_type not in ('MARKETER','WORKSHOP_OWNER') then raise exception 'invalid_join_account_type' using errcode='22023'; end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('commission_manage')) then
        raise exception 'commission_manage_required' using errcode='42501';
    end if;

    select c.id into v_client_id
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_session_org
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_session_org
     and cp.segment=v_account_type and cp.deleted_at is null
    where c.id=p_client_id and c.organization_id=v_session_org
    for share of c;
    if not found then raise exception 'client_account_type_mismatch' using errcode='22023'; end if;

    if exists(select 1 from public.autodrive_users au where au.client_id=v_client_id and au.org_id=v_session_org) then
        raise exception 'client_already_linked' using errcode='23505';
    end if;

    update public.invite_codes ic
       set status='used', used=true, used_at=pg_catalog.now()
     where ic.organization_id=v_session_org
       and ic.marketer_client_id=v_client_id::text
       and coalesce(ic.used,false)=false
       and (ic.expires_at is null or ic.expires_at>pg_catalog.now());

    v_expires_at := pg_catalog.now() + pg_catalog.make_interval(mins=>1440);
    loop
        v_attempt := v_attempt + 1;
        if v_attempt > 20 then raise exception 'code_generation_failed' using errcode='P0001'; end if;
        v_code := public.autodrive_generate_numeric_code();
        begin
            insert into public.invite_codes(
                organization_id, code, status, created_by, created_at, used, expires_at,
                marketer_client_id, permissions, autodrive_account_type
            ) values (
                v_session_org, v_code, 'pending', v_actor, pg_catalog.now(), false, v_expires_at,
                v_client_id::text, '{}', v_account_type
            );
            exit;
        exception when unique_violation then
            if v_attempt=20 then raise; end if;
        end;
    end loop;

    return query select v_session_org, v_client_id, v_account_type, v_code, v_expires_at;
end;
$function$;

create or replace function public.redeem_invite_code(
    p_code text,
    p_full_name text,
    p_phone text,
    p_account_type text,
    p_bank_name text default null,
    p_bank_account text default null,
    p_workshop_name text default null,
    p_specialty text default null,
    p_workers_count integer default null,
    p_address text default null
)
returns uuid
language plpgsql
security definer
set search_path to 'public','pg_temp'
as $function$
declare
    v_user_id uuid := auth.uid();
    v_invite public.invite_codes%rowtype;
    v_client_id uuid;
    v_org_id uuid;
    v_existing_id uuid;
    v_existing_uid uuid;
    v_result_id uuid;
    v_account_type text := upper(btrim(coalesce(p_account_type,'')));
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_account_type not in ('MARKETER','WORKSHOP_OWNER') then raise exception 'INVALID_ACCOUNT_TYPE'; end if;

    select * into v_invite from public.invite_codes where code=p_code for update;
    if not found then raise exception 'CODE_NOT_FOUND'; end if;
    if v_invite.expires_at is not null and v_invite.expires_at < now() then raise exception 'CODE_EXPIRED'; end if;
    if v_invite.status='used' or v_invite.used=true then raise exception 'CODE_ALREADY_USED'; end if;
    if v_invite.marketer_client_id is null then raise exception 'NOT_A_MARKETER_CODE'; end if;

    v_client_id := v_invite.marketer_client_id::uuid;
    v_org_id := v_invite.organization_id;

    perform 1
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_org_id
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_org_id
     and cp.segment=v_account_type and cp.deleted_at is null
    where c.id=v_client_id and c.organization_id=v_org_id;
    if not found then raise exception 'CLIENT_ACCOUNT_TYPE_MISMATCH'; end if;
    if v_invite.autodrive_account_type is not null and v_invite.autodrive_account_type<>v_account_type then
        raise exception 'CLIENT_ACCOUNT_TYPE_MISMATCH';
    end if;

    if exists(select 1 from public.autodrive_users au where au.user_id=v_user_id and au.client_id<>v_client_id) then
        raise exception 'USER_ALREADY_LINKED';
    end if;

    select id,user_id into v_existing_id,v_existing_uid from public.autodrive_users where client_id=v_client_id for update;
    if v_existing_id is not null then
        if v_existing_uid is not null and v_existing_uid<>v_user_id then raise exception 'ALREADY_LINKED'; end if;
        update public.autodrive_users set
            user_id=v_user_id, account_type=v_account_type, full_name=p_full_name, phone=p_phone,
            bank_name=p_bank_name, bank_account=p_bank_account, workshop_name=p_workshop_name,
            specialty=p_specialty, workers_count=p_workers_count, address=p_address, updated_at=now()
        where id=v_existing_id;
        v_result_id := v_existing_id;
    else
        insert into public.autodrive_users(
            user_id,client_id,org_id,account_type,full_name,phone,bank_name,bank_account,
            workshop_name,specialty,workers_count,address
        ) values (
            v_user_id,v_client_id,v_org_id,v_account_type,p_full_name,p_phone,p_bank_name,p_bank_account,
            p_workshop_name,p_specialty,p_workers_count,p_address
        ) returning id into v_result_id;
    end if;

    insert into public.marketer_balance(client_id,org_id,balance) values(v_client_id,v_org_id,0)
    on conflict(client_id) do nothing;
    update public.invite_codes set status='used', used=true, used_at=now(), used_by_user_id=v_user_id where code=p_code;
    return v_result_id;
end;
$function$;

create or replace function public.verto_issue_optimal_registration_code(
    p_client_id uuid,
    p_expires_in_minutes integer default 1440
)
returns table(organization_id uuid, client_id uuid, code text, company_name text, expires_at timestamptz)
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth','extensions'
as $function$
declare
    v_auth_user uuid := auth.uid();
    v_org_id uuid;
    v_client_id uuid;
    v_client_name text;
    v_code text;
    v_expires_at timestamptz;
    v_attempt integer := 0;
begin
    if v_auth_user is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if p_expires_in_minutes is distinct from 1440 then raise exception 'registration_code_expiry_must_be_24_hours' using errcode='22023'; end if;
    v_org_id := public.get_my_org_id();
    if v_org_id is null or not public.has_perm('clients_edit') then raise exception 'clients_edit_required' using errcode='42501'; end if;

    select c.id,c.name into v_client_id,v_client_name
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_org_id
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_org_id
     and cp.segment='COMPANY' and cp.deleted_at is null
    where c.id=p_client_id and c.organization_id=v_org_id
    for share of c;
    if not found then raise exception 'client_must_be_company' using errcode='22023'; end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('optimal-verto-client:'||v_client_id::text,0));
    if exists(select 1 from public.optimal_verto_links l where l.verto_client_id=v_client_id and l.verto_organization_id=v_org_id and l.is_active=true) then
        raise exception 'company_already_linked' using errcode='23505';
    end if;

    update public.optimal_verto_registration_codes
       set state=case when expires_at<=pg_catalog.now() then 'EXPIRED' else 'REVOKED' end,
           cancelled_at=case when expires_at>pg_catalog.now() then pg_catalog.now() else cancelled_at end,
           cancelled_by_user_id=case when expires_at>pg_catalog.now() then v_auth_user else cancelled_by_user_id end,
           updated_at=pg_catalog.now()
     where verto_client_id=v_client_id and verto_organization_id=v_org_id and state='PENDING';

    v_expires_at := pg_catalog.now()+pg_catalog.make_interval(mins=>1440);
    loop
        v_attempt:=v_attempt+1;
        if v_attempt>20 then raise exception 'code_generation_failed' using errcode='P0001'; end if;
        v_code:=public.optimal_generate_numeric_code();
        begin
            insert into public.optimal_verto_registration_codes(
                verto_client_id,verto_organization_id,code_hash,state,expires_at,created_by_user_id
            ) values(v_client_id,v_org_id,extensions.digest(v_code,'sha256'),'PENDING',v_expires_at,v_auth_user);
            exit;
        exception when unique_violation then if v_attempt=20 then raise; end if;
        end;
    end loop;
    return query select v_org_id,v_client_id,v_code,v_client_name,v_expires_at;
end;
$function$;

create or replace function public.optimal_complete_company_registration(p_code text,p_display_name text)
returns table(company_id uuid, company_name text, member_id uuid, display_name text, phone text, normalized_phone text, is_active boolean)
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth','extensions'
as $function$
declare
    v_auth_user uuid := auth.uid();
    v_auth_phone text;
    v_phone_confirmed_at timestamptz;
    v_normalized_phone text;
    v_code public.optimal_verto_registration_codes%rowtype;
    v_client_id uuid;
    v_client_org uuid;
    v_client_name text;
    v_company public.optimal_companies%rowtype;
    v_member public.optimal_members%rowtype;
begin
    if v_auth_user is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if p_code is null or p_code !~ '^[0-9]{8}$' then raise exception 'invalid_registration_code' using errcode='22023'; end if;
    if pg_catalog.char_length(pg_catalog.btrim(coalesce(p_display_name,'')))<2 then raise exception 'invalid_display_name' using errcode='22023'; end if;

    select u.phone,u.phone_confirmed_at into v_auth_phone,v_phone_confirmed_at from auth.users u where u.id=v_auth_user;
    v_normalized_phone:=public.optimal_normalize_phone(v_auth_phone);
    if v_normalized_phone is null or v_normalized_phone !~ '^249[0-9]{9}$' or v_phone_confirmed_at is null then
        raise exception 'verified_phone_required' using errcode='42501';
    end if;

    select r.* into v_code from public.optimal_verto_registration_codes r where r.code_hash=extensions.digest(p_code,'sha256') for update;
    if not found then raise exception 'invalid_registration_code' using errcode='P0002'; end if;
    if v_code.state<>'PENDING' then raise exception 'registration_code_already_used' using errcode='P0002'; end if;
    if v_code.expires_at<=pg_catalog.now() then
        update public.optimal_verto_registration_codes set state='EXPIRED',updated_at=pg_catalog.now() where id=v_code.id and state='PENDING';
        raise exception 'registration_code_expired' using errcode='P0002';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('optimal-auth-user:'||v_auth_user::text,0));
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('optimal-verto-client:'||v_code.verto_client_id::text,0));

    select c.id,c.organization_id,c.name into v_client_id,v_client_org,v_client_name
    from public.clients c
    join public.party_roles pr on pr.party_id=c.id and pr.organization_id=c.organization_id and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp on cp.party_id=c.id and cp.organization_id=c.organization_id and cp.segment='COMPANY' and cp.deleted_at is null
    where c.id=v_code.verto_client_id and c.organization_id=v_code.verto_organization_id
    for share of c;
    if not found then raise exception 'client_must_be_company' using errcode='22023'; end if;

    if exists(select 1 from public.optimal_members m where m.auth_user_id=v_auth_user) then raise exception 'user_already_has_company' using errcode='23505'; end if;
    if exists(select 1 from public.optimal_verto_links l where l.verto_client_id=v_client_id and l.verto_organization_id=v_client_org and l.is_active=true) then raise exception 'company_already_linked' using errcode='23505'; end if;
    if exists(select 1 from public.optimal_members m where m.normalized_phone=v_normalized_phone and m.is_active=true) then raise exception 'phone_already_bound' using errcode='23505'; end if;

    insert into public.optimal_companies(name) values(pg_catalog.btrim(v_client_name)) returning * into v_company;
    insert into public.optimal_members(company_id,auth_user_id,display_name,phone,normalized_phone,role,permissions,is_active)
    values(v_company.id,v_auth_user,pg_catalog.btrim(p_display_name),coalesce(v_auth_phone,'+'||v_normalized_phone),v_normalized_phone,'OWNER','{}'::jsonb,true)
    returning * into v_member;
    insert into public.optimal_verto_links(optimal_company_id,verto_client_id,verto_organization_id,linked_by_user_id,is_active,linked_at,updated_at)
    values(v_company.id,v_client_id,v_client_org,v_auth_user,true,pg_catalog.now(),pg_catalog.now());
    update public.optimal_verto_registration_codes set state='USED',used_by_user_id=v_auth_user,used_at=pg_catalog.now(),updated_at=pg_catalog.now()
    where id=v_code.id and state='PENDING';
    if not found then raise exception 'registration_code_concurrently_consumed' using errcode='40001'; end if;
    return query select v_company.id,v_company.name,v_member.id,v_member.display_name,v_member.phone,v_member.normalized_phone,v_member.is_active;
end;
$function$;

create or replace function public.optimal_complete_company_join(p_code text,p_company_name text,p_display_name text)
returns table(company_id uuid, company_name text, member_id uuid, verto_client_id uuid, verto_organization_id uuid)
language plpgsql
security definer
set search_path to 'pg_catalog','public','auth','extensions'
as $function$
declare
    v_user_id uuid := auth.uid();
    v_auth_phone text;
    v_phone_confirmed_at timestamptz;
    v_phone text;
    v_code public.optimal_company_join_codes%rowtype;
    v_company public.optimal_companies%rowtype;
    v_member public.optimal_members%rowtype;
    v_client_id uuid := extensions.gen_random_uuid();
    v_role_id uuid := extensions.gen_random_uuid();
begin
    if v_user_id is null then raise exception 'auth_session_required' using errcode='28000'; end if;
    if p_code is null or p_code !~ '^[0-9]{8}$' then raise exception 'invalid_registration_code' using errcode='22023'; end if;
    if pg_catalog.char_length(pg_catalog.btrim(coalesce(p_company_name,'')))<2 then raise exception 'invalid_company_name' using errcode='22023'; end if;
    if pg_catalog.char_length(pg_catalog.btrim(coalesce(p_display_name,'')))<2 then raise exception 'invalid_display_name' using errcode='22023'; end if;

    select u.phone,u.phone_confirmed_at into v_auth_phone,v_phone_confirmed_at from auth.users u where u.id=v_user_id;
    v_phone:=public.optimal_normalize_phone(v_auth_phone);
    if v_phone is null or v_phone !~ '^249[0-9]{9}$' or v_phone_confirmed_at is null then raise exception 'verified_phone_required' using errcode='42501'; end if;

    select c.* into v_code from public.optimal_company_join_codes c where c.code_hash=extensions.digest(p_code,'sha256') for update;
    if not found then raise exception 'invalid_registration_code' using errcode='P0002'; end if;
    if v_code.state<>'PENDING' then raise exception 'registration_code_already_used' using errcode='P0002'; end if;
    if v_code.expires_at<=pg_catalog.now() then
        update public.optimal_company_join_codes set state='EXPIRED',updated_at=pg_catalog.now() where id=v_code.id and state='PENDING';
        raise exception 'registration_code_expired' using errcode='P0002';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('optimal-auth-user:'||v_user_id::text,0));
    if exists(select 1 from public.optimal_members m where m.auth_user_id=v_user_id) then raise exception 'user_already_has_company' using errcode='23505'; end if;
    if exists(select 1 from public.optimal_members m where m.normalized_phone=v_phone and m.is_active=true) then raise exception 'phone_already_bound' using errcode='23505'; end if;

    insert into public.optimal_companies(name) values(pg_catalog.btrim(p_company_name)) returning * into v_company;
    insert into public.optimal_members(company_id,auth_user_id,display_name,phone,normalized_phone,role,permissions,is_active)
    values(v_company.id,v_user_id,pg_catalog.btrim(p_display_name),coalesce(v_auth_phone,'+'||v_phone),v_phone,'OWNER','{}'::jsonb,true)
    returning * into v_member;

    insert into public.clients(id,organization_id,name,phone,created_at,updated_at)
    values(v_client_id,v_code.verto_organization_id,pg_catalog.btrim(p_company_name),coalesce(v_auth_phone,'+'||v_phone),pg_catalog.now(),pg_catalog.now());

    insert into public.party_roles(id,organization_id,party_id,role,status,server_revision,server_updated_at)
    values(v_role_id,v_code.verto_organization_id,v_client_id,'CUSTOMER','ACTIVE',1,pg_catalog.now())
    on conflict(organization_id,party_id,role) do update set status='ACTIVE',deleted_at=null,server_revision=party_roles.server_revision+1,server_updated_at=pg_catalog.now();

    insert into public.customer_profiles(
        party_id,organization_id,segment,vehicle_information,workshop_worker_count,
        server_revision,server_updated_at,age_years,purchase_contact_name,business_activity,
        workplace_name,shop_name,workshop_name,vehicle_models
    ) values(
        v_client_id,v_code.verto_organization_id,'COMPANY','',null,
        1,pg_catalog.now(),null,'','','','','',''
    ) on conflict(party_id) do update set
        organization_id=excluded.organization_id, segment='COMPANY', deleted_at=null,
        server_revision=customer_profiles.server_revision+1,server_updated_at=pg_catalog.now();

    insert into public.optimal_verto_links(optimal_company_id,verto_client_id,verto_organization_id,linked_by_user_id,is_active,linked_at,updated_at)
    values(v_company.id,v_client_id,v_code.verto_organization_id,v_user_id,true,pg_catalog.now(),pg_catalog.now());

    update public.optimal_company_join_codes set state='USED',used_by_user_id=v_user_id,optimal_company_id=v_company.id,verto_client_id=v_client_id,used_at=pg_catalog.now(),updated_at=pg_catalog.now()
    where id=v_code.id and state='PENDING';
    if not found then raise exception 'registration_code_concurrently_consumed' using errcode='40001'; end if;
    return query select v_company.id,v_company.name,v_member.id,v_client_id,v_code.verto_organization_id;
end;
$function$;
