-- V387 follow-up: finish AutoDrive Party V2 cutover and repair MARKETER profiles.
update public.customer_profiles cp
set segment='MARKETER', server_revision=server_revision+1, server_updated_at=pg_catalog.now()
from public.clients c
where cp.party_id=c.id
  and cp.organization_id=c.organization_id
  and cp.segment='OTHER'
  and upper(','||replace(coalesce(c.client_types,''),' ','')||',') like '%,MARKETER,%';

create or replace function public.verto_autodrive_join_code_candidates_v2()
returns table(client_id uuid, name text, phone text, account_type text)
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
    select c.id,c.name,coalesce(c.phone,''),cp.segment
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=v_org
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=v_org
     and cp.segment in ('MARKETER','WORKSHOP_OWNER') and cp.deleted_at is null
    where c.organization_id=v_org
      and not exists(select 1 from public.autodrive_users au where au.client_id=c.id and au.org_id=v_org)
    order by c.name,c.id;
end;
$function$;
revoke all on function public.verto_autodrive_join_code_candidates_v2() from public,anon,service_role;
grant execute on function public.verto_autodrive_join_code_candidates_v2() to authenticated;

create or replace function public.notify_admins_commission_needed()
returns trigger
language plpgsql
security definer
set search_path to 'pg_catalog','public','pg_temp'
as $function$
declare
    v_client_name text;
    v_segment text;
    v_creator_role text;
    v_route text;
begin
    if NEW.category <> 'SALE' then return NEW; end if;
    select c.name,cp.segment into v_client_name,v_segment
    from public.clients c
    join public.party_roles pr
      on pr.party_id=c.id and pr.organization_id=NEW.organization_id
     and pr.role='CUSTOMER' and pr.status='ACTIVE' and pr.deleted_at is null
    join public.customer_profiles cp
      on cp.party_id=c.id and cp.organization_id=NEW.organization_id and cp.deleted_at is null
    where c.id=NEW.client_id and c.organization_id=NEW.organization_id;
    if v_segment not in ('MARKETER','WORKSHOP_OWNER') then return NEW; end if;
    select u.role into v_creator_role from public.app_users u where u.id=NEW.created_by;
    if v_creator_role='admin' then return NEW; end if;
    v_route := 'invoice/'||NEW.id::text||'?openCommission=true';
    insert into public.notifications(user_id,client_id,org_id,type,title,body,related_entity_id,related_entity_type,data)
    select u.id,NEW.client_id,NEW.organization_id,'ADMIN_COMMISSION_NEEDED','فاتورة بحاجة لعمولة',
           format('تم إضافة فاتورة لـ %s — اضغط لإضافة عمولة',coalesce(v_client_name,'عميل')),
           NEW.id::text,'INVOICE',
           jsonb_build_object('invoice_id',NEW.id,'client_name',coalesce(v_client_name,''),'navigation_route',v_route,'route',v_route)
    from public.app_users u
    where u.organization_id=NEW.organization_id and u.role='admin' and u.is_active=true;
    return NEW;
exception when others then
    raise warning 'notify_admins_commission_needed failed: %',SQLERRM;
    return NEW;
end;
$function$;
