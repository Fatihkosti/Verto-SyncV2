-- Verto marketer/workshop join-code issuer.
-- The client only calls this RPC; invite_codes is written inside the
-- SECURITY DEFINER transaction after tenant and permission checks.
create or replace function public.verto_issue_autodrive_join_code(
    p_org_id uuid,
    p_client_id uuid,
    p_account_type text,
    p_expires_in_minutes integer default 1440
)
returns table(
    organization_id uuid,
    client_id uuid,
    account_type text,
    code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'auth', 'extensions'
as $function$
declare
    v_actor uuid := auth.uid();
    v_session_org uuid := public.get_my_org_id();
    v_account_type text := pg_catalog.upper(pg_catalog.btrim(coalesce(p_account_type, '')));
    v_client public.clients%rowtype;
    v_code text;
    v_expires_at timestamptz;
    v_attempt integer := 0;
begin
    if v_actor is null then
        raise exception 'auth_session_required' using errcode = '28000';
    end if;
    if p_expires_in_minutes is distinct from 1440 then
        raise exception 'join_code_expiry_must_be_24_hours' using errcode = '22023';
    end if;
    if p_org_id is null or v_session_org is null or p_org_id is distinct from v_session_org then
        raise exception 'organization_session_mismatch' using errcode = '42501';
    end if;
    if v_account_type not in ('MARKETER', 'WORKSHOP_OWNER') then
        raise exception 'invalid_join_account_type' using errcode = '22023';
    end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('commission_manage')) then
        raise exception 'commission_manage_required' using errcode = '42501';
    end if;

    select c.* into v_client
    from public.clients c
    where c.id = p_client_id and c.organization_id = v_session_org
    for share;
    if not found then
        raise exception 'client_not_found' using errcode = 'P0002';
    end if;
    if not (v_account_type = any (pg_catalog.string_to_array(
        pg_catalog.upper(pg_catalog.replace(coalesce(v_client.client_types, ''), ' ', '')), ','))) then
        raise exception 'client_account_type_mismatch' using errcode = '22023';
    end if;
    if exists (
        select 1 from public.autodrive_users au
        where au.client_id = v_client.id
          and au.org_id = v_session_org
          and au.user_id is not null
    ) then
        raise exception 'client_already_linked' using errcode = '23505';
    end if;

    update public.invite_codes as ic
    set status = 'used', used = true, used_at = pg_catalog.now()
    where ic.organization_id = v_session_org
      and ic.marketer_client_id = v_client.id::text
      and coalesce(ic.used, false) = false
      and (ic.expires_at is null or ic.expires_at > pg_catalog.now());

    v_expires_at := pg_catalog.now() + pg_catalog.make_interval(mins => 1440);
    loop
        v_attempt := v_attempt + 1;
        if v_attempt > 20 then
            raise exception 'code_generation_failed' using errcode = 'P0001';
        end if;
        v_code := public.optimal_generate_numeric_code();
        begin
            insert into public.invite_codes (
                organization_id, code, status, created_by, created_at,
                used, expires_at, marketer_client_id, permissions
            ) values (
                v_session_org, v_code, 'pending', v_actor, pg_catalog.now(),
                false, v_expires_at, v_client.id::text, '{}'
            );
            exit;
        exception when unique_violation then
            if v_attempt = 20 then raise; end if;
        end;
    end loop;

    return query select v_session_org, v_client.id, v_account_type, v_code, v_expires_at;
end;
$function$;

revoke all on function public.verto_issue_autodrive_join_code(uuid, uuid, text, integer) from public, anon, service_role;
grant execute on function public.verto_issue_autodrive_join_code(uuid, uuid, text, integer) to authenticated;
