create or replace function public.verto_auth_session_status()
returns text
language plpgsql
stable
security definer
set search_path = 'pg_catalog', 'public', 'auth'
as $function$
declare
    v_uid uuid := auth.uid();
    v_session_claim text := auth.jwt() ->> 'session_id';
    v_session_id uuid;
    v_user auth.users%rowtype;
    v_profile public.app_users%rowtype;
    v_org public.organizations%rowtype;
begin
    if v_uid is null then
        raise exception 'AUTH_SESSION_REQUIRED' using errcode = '28000';
    end if;

    if coalesce(auth.jwt() ->> 'role', '') <> 'authenticated' then
        return 'ACCOUNT_BLOCKED';
    end if;

    if coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then
        return 'ACCOUNT_BLOCKED';
    end if;

    -- Recovery identities prove ownership for a credential change only.
    -- They must never become an operational Verto ERP session.
    if coalesce(jsonb_typeof(auth.jwt() -> 'amr'), '') = 'array'
       and exists (
            select 1
              from jsonb_array_elements(auth.jwt() -> 'amr') as method_entry
             where coalesce(method_entry ->> 'method', '') in ('recovery', 'otp', 'magiclink')
       ) then
        return 'ACCOUNT_BLOCKED';
    end if;

    select * into v_user
      from auth.users
     where id = v_uid;

    if not found
       or v_user.deleted_at is not null
       or (v_user.banned_until is not null and v_user.banned_until > pg_catalog.now()) then
        return 'ACCOUNT_BLOCKED';
    end if;

    if nullif(v_session_claim, '') is null then
        return 'ACCOUNT_BLOCKED';
    end if;

    begin
        v_session_id := v_session_claim::uuid;
    exception when invalid_text_representation then
        return 'ACCOUNT_BLOCKED';
    end;

    if not exists (
        select 1
          from auth.sessions s
         where s.id = v_session_id
           and s.user_id = v_uid
           and (s.not_after is null or s.not_after > pg_catalog.now())
    ) then
        return 'ACCOUNT_BLOCKED';
    end if;

    select * into v_profile
      from public.app_users u
     where u.id = v_uid;

    if not found then
        return 'PROFILE_MISSING';
    end if;

    if v_profile.is_active is not true then
        return 'PROFILE_INACTIVE';
    end if;

    if v_profile.organization_id is null
       or v_profile.role not in ('admin', 'accountant', 'sales', 'warehouse') then
        return 'MEMBERSHIP_INVALID';
    end if;

    select * into v_org
      from public.organizations o
     where o.id = v_profile.organization_id;

    if not found then
        return 'MEMBERSHIP_INVALID';
    end if;

    if nullif(pg_catalog.btrim(v_profile.name), '') is null
       or nullif(pg_catalog.btrim(v_org.name), '') is null then
        return 'PROVISIONING_INCOMPLETE';
    end if;

    return 'AUTHORIZED';
end;
$function$;

revoke all on function public.verto_auth_session_status() from public, anon;
grant execute on function public.verto_auth_session_status() to authenticated, service_role;
