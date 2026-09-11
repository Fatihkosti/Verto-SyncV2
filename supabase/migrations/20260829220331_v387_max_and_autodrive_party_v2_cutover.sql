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
      on cp.party_id=c.id and cp.organization_id=v_org
     and cp.segment='COMPETITOR' and cp.deleted_at is null
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
      on cp.party_id=c.id and cp.organization_id=v_org
     and cp.segment='COMPETITOR' and cp.deleted_at is null
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

create or replace function public.verto_approve_autodrive_join_request_v1(
    p_request_id uuid,
    p_client_id uuid default null,
    p_create_client boolean default false
)
returns table(request_id uuid, status text, client_id uuid, organization_id uuid)
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'auth', 'extensions'
as $function$
declare
    v_actor uuid:=auth.uid(); v_org uuid:=public.get_my_org_id(); v_req public.autodrive_join_requests%rowtype;
    v_client_id uuid; v_cfg public.autodrive_org_config%rowtype;
begin
    if v_actor is null or v_org is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
    if not public.autodrive_can_manage_v1() then raise exception 'AUTODRIVE_MANAGE_REQUIRED' using errcode='42501'; end if;
    if p_create_client and p_client_id is not null then raise exception 'CHOOSE_LINK_OR_CREATE' using errcode='22023'; end if;
    if not p_create_client and p_client_id is null then raise exception 'CLIENT_DECISION_REQUIRED' using errcode='22023'; end if;
    if upper(coalesce((select account_type from public.autodrive_join_requests where id=p_request_id),'')) not in ('MARKETER','WORKSHOP_OWNER') then
        raise exception 'INVALID_ACCOUNT_TYPE' using errcode='22023';
    end if;

    select r.* into v_req from public.autodrive_join_requests r
    where r.id=p_request_id and r.organization_id=v_org for update;
    if not found then raise exception 'REQUEST_NOT_FOUND' using errcode='P0002'; end if;
    if v_req.status<>'PENDING' then raise exception 'REQUEST_NOT_PENDING' using errcode='55000'; end if;
    if v_req.expires_at<=now() then raise exception 'REQUEST_EXPIRED' using errcode='55000'; end if;
    select c.* into v_cfg from public.autodrive_org_config c where c.organization_id=v_org;
    if not found or not v_cfg.enabled then raise exception 'AUTODRIVE_NOT_ENABLED' using errcode='42501'; end if;

    if p_create_client then
        insert into public.clients(organization_id,created_by,name,phone,address)
        values(v_org,v_actor,v_req.full_name,v_req.phone_normalized,'') returning id into v_client_id;
        insert into public.party_roles(id,organization_id,party_id,role,status,server_revision,server_updated_at,last_operation_id)
        values(extensions.gen_random_uuid(),v_org,v_client_id,'CUSTOMER','ACTIVE',1,now(),extensions.gen_random_uuid());
        insert into public.customer_profiles(
            party_id,organization_id,segment,vehicle_information,workshop_worker_count,server_revision,server_updated_at,last_operation_id,
            age_years,purchase_contact_name,business_activity,workplace_name,shop_name,workshop_name,vehicle_models
        ) values(v_client_id,v_org,upper(v_req.account_type),'',null,1,now(),extensions.gen_random_uuid(),null,'','','','','','');
    else
        perform 1
        from public.clients c
        join public.party_roles pr
          on pr.party_id=c.id and pr.organization_id=v_org
         and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
        join public.customer_profiles cp
          on cp.party_id=c.id and cp.organization_id=v_org
         and cp.segment=upper(v_req.account_type) and cp.deleted_at is null
        where c.id=p_client_id and c.organization_id=v_org;
        if not found then raise exception 'CLIENT_ACCOUNT_TYPE_MISMATCH' using errcode='22023'; end if;
        v_client_id:=p_client_id;
    end if;

    if exists(select 1 from public.autodrive_users au where au.client_id=v_client_id and au.user_id is not null) then
        raise exception 'CLIENT_ALREADY_LINKED' using errcode='23505';
    end if;
    update public.autodrive_join_requests
       set status='APPROVED',client_id=v_client_id,reviewed_by=v_actor,reviewed_at=now(),approved_at=now(),
           expires_at=now()+make_interval(hours=>v_cfg.approval_ttl_hours),version=version+1
     where id=v_req.id;
    insert into public.autodrive_join_request_events(request_id,event_type,actor_user_id,from_status,to_status,details)
    values(v_req.id,'REQUEST_APPROVED',v_actor,'PENDING','APPROVED',jsonb_build_object('client_id',v_client_id,'created_client',p_create_client));
    insert into public.autodrive_notification_outbox(request_id,event_type,payload)
    values(v_req.id,'JOIN_REQUEST_APPROVED',jsonb_build_object('request_id',v_req.id,'title','تم قبول طلبك','body','افتح AutoDrive وأدخل رقم هاتفك لإكمال التسجيل.'));
    return query select v_req.id,'APPROVED'::text,v_client_id,v_org;
end;
$function$;

revoke all on function public.verto_max_join_code_candidates_v1() from public, anon;
grant execute on function public.verto_max_join_code_candidates_v1() to authenticated, service_role;
revoke all on function public.verto_issue_max_join_code_v1(uuid,uuid,integer) from public, anon;
grant execute on function public.verto_issue_max_join_code_v1(uuid,uuid,integer) to authenticated, service_role;
revoke all on function public.verto_approve_autodrive_join_request_v1(uuid,uuid,boolean) from public, anon, authenticated;
grant execute on function public.verto_approve_autodrive_join_request_v1(uuid,uuid,boolean) to service_role;
