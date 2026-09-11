-- Verto v275 — expand-only Party/Roles/Profiles contract. Apply before Android v2.
begin;
create table if not exists public.party_roles (
 id uuid primary key default gen_random_uuid(), organization_id uuid not null references public.organizations(id),
 party_id uuid not null references public.clients(id), role text not null check(role in ('CUSTOMER','SUPPLIER')),
 status text not null default 'ACTIVE' check(status in ('ACTIVE','ARCHIVED')), server_revision bigint not null default 1,
 server_updated_at timestamptz not null default now(), last_operation_id uuid, archived_at timestamptz,
 archived_by uuid, archive_reason text, deleted_at timestamptz, unique(organization_id,party_id,role));
create index if not exists party_roles_org_role_status_idx on public.party_roles(organization_id,role,status);
create table if not exists public.customer_profiles (
 party_id uuid primary key references public.clients(id), organization_id uuid not null references public.organizations(id),
 segment text not null, vehicle_information text not null default '', workshop_worker_count integer,
 server_revision bigint not null default 1, server_updated_at timestamptz not null default now(), last_operation_id uuid, deleted_at timestamptz);
create table if not exists public.supplier_profiles (
 party_id uuid primary key references public.clients(id), organization_id uuid not null references public.organizations(id),
 scope text not null check(scope in ('LOCAL','INTERNATIONAL','UNKNOWN')), country text not null default '',
 currency_code text not null default '', specialty text not null default '', server_revision bigint not null default 1,
 server_updated_at timestamptz not null default now(), last_operation_id uuid, deleted_at timestamptz);
create table if not exists public.party_sync_operations (
 organization_id uuid not null references public.organizations(id), operation_id uuid not null, aggregate_type text not null,
 aggregate_id uuid not null, response jsonb not null, created_at timestamptz not null default now(), primary key(organization_id,operation_id));
create table if not exists public.party_migration_issues (
 id uuid primary key default gen_random_uuid(), organization_id uuid not null references public.organizations(id),
 party_id uuid not null references public.clients(id), field_name text not null, raw_value text not null, reason text not null,
 resolved_at timestamptz, created_at timestamptz not null default now(), unique(organization_id,party_id,field_name,raw_value,reason));

alter table public.party_roles enable row level security;
alter table public.customer_profiles enable row level security;
alter table public.supplier_profiles enable row level security;
alter table public.party_sync_operations enable row level security;
alter table public.party_migration_issues enable row level security;
do $$ declare t text; begin
 foreach t in array array['party_roles','customer_profiles','supplier_profiles','party_sync_operations','party_migration_issues'] loop
  execute format('drop policy if exists %I_tenant_select on public.%I',t,t);
  execute format('create policy %I_tenant_select on public.%I for select to authenticated using (organization_id=public.get_my_org_id())',t,t);
  execute format('drop policy if exists %I_tenant_insert on public.%I',t,t);
  execute format('create policy %I_tenant_insert on public.%I for insert to authenticated with check (organization_id=public.get_my_org_id())',t,t);
  execute format('drop policy if exists %I_tenant_update on public.%I',t,t);
  execute format('create policy %I_tenant_update on public.%I for update to authenticated using (organization_id=public.get_my_org_id()) with check (organization_id=public.get_my_org_id())',t,t);
  execute format('drop policy if exists %I_tenant_delete on public.%I',t,t);
  execute format('create policy %I_tenant_delete on public.%I for delete to authenticated using (organization_id=public.get_my_org_id())',t,t);
 end loop;
end $$;

create or replace function public.verto_mutate_party_role_v2(p_operation_id uuid,p_party_id uuid,p_role text,p_action text,p_base_revision bigint,p_reason text default '')
returns jsonb language plpgsql security definer set search_path to 'pg_catalog','public','auth','extensions' as $$
declare v_org uuid:=public.get_my_org_id(); v_actor uuid:=auth.uid(); v_row public.party_roles; v_response jsonb;
begin
 if v_org is null or v_actor is null then raise exception 'auth_session_required' using errcode='28000'; end if;
 if not (public.is_current_user_org_admin() or public.has_employee_permission('clients_edit')) then raise exception 'clients_edit_required' using errcode='42501'; end if;
 if p_operation_id is null then raise exception 'operation_id_required' using errcode='22023'; end if;
 if p_role not in ('CUSTOMER','SUPPLIER') or p_action not in ('ATTACH','ARCHIVE','RESTORE') then raise exception 'invalid_role_operation' using errcode='22023'; end if;
 select response into v_response from public.party_sync_operations where organization_id=v_org and operation_id=p_operation_id;
 if found then return v_response; end if;
 if not exists(select 1 from public.clients where id=p_party_id and organization_id=v_org) then raise exception 'party_tenant_mismatch' using errcode='42501'; end if;
 select * into v_row from public.party_roles where organization_id=v_org and party_id=p_party_id and role=p_role for update;
 if found and v_row.server_revision<>p_base_revision then raise exception 'stale_party_revision:%',v_row.server_revision using errcode='40001'; end if;
 insert into public.party_roles(organization_id,party_id,role,status,last_operation_id,archived_at,archived_by,archive_reason)
 values(v_org,p_party_id,p_role,case when p_action='ARCHIVE' then 'ARCHIVED' else 'ACTIVE' end,p_operation_id,
 case when p_action='ARCHIVE' then now() end,case when p_action='ARCHIVE' then v_actor end,nullif(p_reason,''))
 on conflict(organization_id,party_id,role) do update set status=excluded.status,server_revision=party_roles.server_revision+1,
 server_updated_at=now(),last_operation_id=p_operation_id,archived_at=excluded.archived_at,archived_by=excluded.archived_by,
 archive_reason=excluded.archive_reason,deleted_at=null returning * into v_row;
 v_response:=jsonb_build_object('party_id',v_row.party_id,'role',v_row.role,'status',v_row.status,'server_revision',v_row.server_revision,'server_updated_at',v_row.server_updated_at);
 insert into public.party_sync_operations values(v_org,p_operation_id,'PARTY_ROLE',p_party_id,v_response,now()); return v_response;
end $$;
revoke all on function public.verto_mutate_party_role_v2(uuid,uuid,text,text,bigint,text) from public,anon;
grant execute on function public.verto_mutate_party_role_v2(uuid,uuid,text,text,bigint,text) to authenticated;

-- Idempotent backfill; exact comma-delimited tokens prevent substring classification.
insert into public.party_roles(organization_id,party_id,role,status)
select organization_id,id,'CUSTOMER','ACTIVE' from public.clients c where (','||upper(replace(coalesce(c.client_types,''),' ',''))||',') ~ ',(INDIVIDUAL|COMPANY|INSTITUTION|CAR_OWNER|MECHANIC|SHOP_OWNER|WORKSHOP_OWNER|MARKETER|TRADER|DISTRIBUTOR|WHOLESALE_TRADER|COMPETITOR|OTHER),'
on conflict(organization_id,party_id,role) do nothing;
insert into public.party_roles(organization_id,party_id,role,status)
select organization_id,id,'SUPPLIER','ACTIVE' from public.clients c where (','||upper(replace(coalesce(c.client_types,''),' ',''))||',') ~ ',(SUPPLIER|GLOBAL_SUPPLIER|COMPETITOR),'
on conflict(organization_id,party_id,role) do nothing;

insert into public.customer_profiles(party_id,organization_id,segment,vehicle_information,workshop_worker_count)
select c.id,c.organization_id,
 case
  when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',COMPETITOR,')>0 then 'COMPETITOR'
  when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',DISTRIBUTOR,')>0 then 'DISTRIBUTOR'
  when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',WORKSHOP_OWNER,')>0 then 'WORKSHOP_OWNER'
  when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',COMPANY,')>0 then 'COMPANY'
  else 'OTHER' end,
 case when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',SUPPLIER,')=0 and strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',GLOBAL_SUPPLIER,')=0 then coalesce(c.car_type,'') else '' end,
 case when trim(coalesce(c.secondary_phones,'')) ~ '^[0-9]{1,5}$' and strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',WORKSHOP_OWNER,')>0 then trim(c.secondary_phones)::integer end
from public.clients c join public.party_roles r on r.party_id=c.id and r.organization_id=c.organization_id and r.role='CUSTOMER'
on conflict(party_id) do nothing;

insert into public.supplier_profiles(party_id,organization_id,scope,country,currency_code,specialty)
select c.id,c.organization_id,
 case when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',GLOBAL_SUPPLIER,')>0 then 'INTERNATIONAL'
      when strpos(','||upper(replace(coalesce(c.client_types,''),' ',''))||',',',SUPPLIER,')>0 then 'LOCAL' else 'UNKNOWN' end,
 '',case when upper(trim(coalesce(c.secondary_phones,''))) in ('SDG','USD','EUR','SAR','AED','EGP','CNY') then upper(trim(c.secondary_phones)) else '' end,
 coalesce(c.specialty,'')
from public.clients c join public.party_roles r on r.party_id=c.id and r.organization_id=c.organization_id and r.role='SUPPLIER'
on conflict(party_id) do nothing;

insert into public.party_migration_issues(organization_id,party_id,field_name,raw_value,reason)
select c.organization_id,c.id,'car_type',c.car_type,'AMBIGUOUS_SUPPLIER_COUNTRY'
from public.clients c join public.party_roles r on r.party_id=c.id and r.organization_id=c.organization_id and r.role='SUPPLIER'
where trim(coalesce(c.car_type,''))<>'' on conflict do nothing;
commit;
