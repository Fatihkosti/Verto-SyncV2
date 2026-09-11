-- Verto v145 — authenticated, tenant-bound client upsert.
-- Apply once to the same Supabase project used by Verto before installing v145.

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
)
returns uuid
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'auth', 'extensions'
as $function$
declare
    v_actor uuid := auth.uid();
    v_org uuid := public.get_my_org_id();
begin
    if v_actor is null or v_org is null then
        raise exception 'auth_session_required' using errcode = '28000';
    end if;

    if not (public.is_current_user_org_admin() or public.has_employee_permission('clients_edit')) then
        raise exception 'clients_edit_required' using errcode = '42501';
    end if;

    if p_client_id is null then
        raise exception 'client_id_required' using errcode = '22023';
    end if;
    if pg_catalog.btrim(coalesce(p_name, '')) = '' then
        raise exception 'client_name_required' using errcode = '22023';
    end if;

    -- Never permit an existing id to be adopted by another organization.
    if exists (
        select 1 from public.clients c
        where c.id = p_client_id and c.organization_id is distinct from v_org
    ) then
        raise exception 'client_tenant_mismatch' using errcode = '42501';
    end if;

    insert into public.clients as c (
        id, organization_id, created_by, name, phone, address, workplace,
        general_note, client_types, car_type, bank_account, specialty,
        secondary_phones, created_at, updated_at
    ) values (
        p_client_id, v_org, v_actor, pg_catalog.btrim(p_name), coalesce(p_phone, ''),
        coalesce(p_address, ''), coalesce(p_workplace, ''), coalesce(p_general_note, ''),
        coalesce(nullif(pg_catalog.btrim(p_client_types), ''), 'INDIVIDUAL'),
        coalesce(p_car_type, ''), coalesce(p_bank_account, ''), coalesce(p_specialty, ''),
        coalesce(p_secondary_phones, ''), coalesce(p_created_at, pg_catalog.now()), pg_catalog.now()
    )
    on conflict (id) do update set
        name = excluded.name,
        phone = excluded.phone,
        address = excluded.address,
        workplace = excluded.workplace,
        general_note = excluded.general_note,
        client_types = excluded.client_types,
        car_type = excluded.car_type,
        bank_account = excluded.bank_account,
        specialty = excluded.specialty,
        secondary_phones = excluded.secondary_phones,
        updated_at = pg_catalog.now()
    where c.organization_id = v_org;

    if not found then
        raise exception 'client_upsert_rejected' using errcode = '42501';
    end if;

    return p_client_id;
end;
$function$;

revoke all on function public.verto_upsert_client_v1(uuid,text,text,text,text,text,text,text,text,text,text,timestamptz) from public, anon;
grant execute on function public.verto_upsert_client_v1(uuid,text,text,text,text,text,text,text,text,text,text,timestamptz) to authenticated;
