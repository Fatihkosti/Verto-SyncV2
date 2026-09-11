-- V377 AutoDrive join-code flow alignment.
-- AutoDrive client stays unchanged: phone -> OTP -> existing user dashboard / new user invite code.

alter table public.invite_codes
    add column if not exists autodrive_account_type text;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'invite_codes_autodrive_account_type_check'
          and conrelid = 'public.invite_codes'::regclass
    ) then
        alter table public.invite_codes
            add constraint invite_codes_autodrive_account_type_check
            check (autodrive_account_type is null or autodrive_account_type in ('MARKETER', 'WORKSHOP_OWNER'));
    end if;
end;
$$;

create or replace function public.autodrive_generate_numeric_code()
returns text
language plpgsql
security definer
set search_path to 'pg_catalog', 'extensions'
as $function$
declare
    b bytea;
    n bigint;
begin
    b := extensions.gen_random_bytes(4);
    n := (
        pg_catalog.get_byte(b, 0)::bigint * 16777216 +
        pg_catalog.get_byte(b, 1)::bigint * 65536 +
        pg_catalog.get_byte(b, 2)::bigint * 256 +
        pg_catalog.get_byte(b, 3)::bigint
    ) % 100000000;
    return pg_catalog.lpad(n::text, 8, '0');
end;
$function$;

revoke all on function public.autodrive_generate_numeric_code() from public, anon, authenticated, service_role;

create or replace function public.verto_autodrive_join_code_candidates_v1()
returns table(
    client_id uuid,
    name text,
    phone text,
    account_type text,
    client_types text
)
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'auth'
as $function$
declare
    v_actor uuid := auth.uid();
    v_org uuid := public.get_my_org_id();
begin
    if v_actor is null then
        raise exception 'auth_session_required' using errcode = '28000';
    end if;
    if v_org is null then
        raise exception 'organization_session_required' using errcode = '42501';
    end if;
    if not (public.is_current_user_org_admin() or public.has_employee_permission('commission_manage')) then
        raise exception 'commission_manage_required' using errcode = '42501';
    end if;

    return query
    select
        c.id,
        c.name,
        coalesce(c.phone, ''),
        case
            when pg_catalog.strpos(',' || pg_catalog.upper(pg_catalog.replace(coalesce(c.client_types, ''), ' ', '')) || ',', ',MARKETER,') > 0 then 'MARKETER'
            else 'WORKSHOP_OWNER'
        end,
        coalesce(c.client_types, '')
    from public.clients c
    where c.organization_id = v_org
      and (
          pg_catalog.strpos(',' || pg_catalog.upper(pg_catalog.replace(coalesce(c.client_types, ''), ' ', '')) || ',', ',MARKETER,') > 0
          or pg_catalog.strpos(',' || pg_catalog.upper(pg_catalog.replace(coalesce(c.client_types, ''), ' ', '')) || ',', ',WORKSHOP_OWNER,') > 0
      )
      and not exists (
          select 1
          from public.autodrive_users au
          where au.client_id = c.id
            and au.org_id = v_org
      )
    order by c.name;
end;
$function$;

revoke all on function public.verto_autodrive_join_code_candidates_v1() from public, anon, service_role;
grant execute on function public.verto_autodrive_join_code_candidates_v1() to authenticated;

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
    v_tokens text;
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
    where c.id = p_client_id
      and c.organization_id = v_session_org
    for share;
    if not found then
        raise exception 'client_not_found' using errcode = 'P0002';
    end if;

    v_tokens := ',' || pg_catalog.upper(pg_catalog.replace(coalesce(v_client.client_types, ''), ' ', '')) || ',';
    if pg_catalog.strpos(v_tokens, ',' || v_account_type || ',') = 0 then
        raise exception 'client_account_type_mismatch' using errcode = '22023';
    end if;

    if exists (
        select 1
        from public.autodrive_users au
        where au.client_id = v_client.id
          and au.org_id = v_session_org
    ) then
        raise exception 'client_already_linked' using errcode = '23505';
    end if;

    -- Re-issuing a code invalidates any previous still-active code for the same Verto client.
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

        v_code := public.autodrive_generate_numeric_code();
        begin
            insert into public.invite_codes (
                organization_id,
                code,
                status,
                created_by,
                created_at,
                used,
                expires_at,
                marketer_client_id,
                permissions,
                autodrive_account_type
            ) values (
                v_session_org,
                v_code,
                'pending',
                v_actor,
                pg_catalog.now(),
                false,
                v_expires_at,
                v_client.id::text,
                '{}',
                v_account_type
            );
            exit;
        exception when unique_violation then
            if v_attempt = 20 then raise; end if;
        end;
    end loop;

    return query
    select v_session_org, v_client.id, v_account_type, v_code, v_expires_at;
end;
$function$;

revoke all on function public.verto_issue_autodrive_join_code(uuid, uuid, text, integer) from public, anon, service_role;
grant execute on function public.verto_issue_autodrive_join_code(uuid, uuid, text, integer) to authenticated;

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
set search_path to 'public', 'pg_temp'
as $function$
declare
    v_user_id uuid := auth.uid();
    v_invite public.invite_codes%rowtype;
    v_client public.clients%rowtype;
    v_client_id uuid;
    v_org_id uuid;
    v_existing_id uuid;
    v_existing_uid uuid;
    v_result_id uuid;
    v_account_type text := upper(btrim(coalesce(p_account_type, '')));
    v_tokens text;
begin
    if v_user_id is null then raise exception 'AUTH_REQUIRED'; end if;
    if v_account_type not in ('MARKETER', 'WORKSHOP_OWNER') then
        raise exception 'INVALID_ACCOUNT_TYPE';
    end if;

    select * into v_invite
    from public.invite_codes
    where code = p_code
    for update;

    if not found then raise exception 'CODE_NOT_FOUND'; end if;
    if v_invite.expires_at is not null and v_invite.expires_at < now() then
        raise exception 'CODE_EXPIRED';
    end if;
    if v_invite.status = 'used' or v_invite.used = true then
        raise exception 'CODE_ALREADY_USED';
    end if;
    if v_invite.marketer_client_id is null then
        raise exception 'NOT_A_MARKETER_CODE';
    end if;

    v_client_id := v_invite.marketer_client_id::uuid;
    v_org_id := v_invite.organization_id;

    select c.* into v_client
    from public.clients c
    where c.id = v_client_id
      and c.organization_id = v_org_id
    for share;
    if not found then
        raise exception 'CLIENT_NOT_IN_INVITE_ORG';
    end if;

    v_tokens := ',' || upper(replace(coalesce(v_client.client_types, ''), ' ', '')) || ',';
    if strpos(v_tokens, ',' || v_account_type || ',') = 0 then
        raise exception 'CLIENT_ACCOUNT_TYPE_MISMATCH';
    end if;
    if v_invite.autodrive_account_type is not null
       and v_invite.autodrive_account_type <> v_account_type then
        raise exception 'CLIENT_ACCOUNT_TYPE_MISMATCH';
    end if;

    if exists (
        select 1
        from public.autodrive_users au
        where au.user_id = v_user_id
          and au.client_id <> v_client_id
    ) then
        raise exception 'USER_ALREADY_LINKED';
    end if;

    select id, user_id into v_existing_id, v_existing_uid
    from public.autodrive_users
    where client_id = v_client_id
    for update;

    if v_existing_id is not null then
        if v_existing_uid is not null and v_existing_uid <> v_user_id then
            raise exception 'ALREADY_LINKED';
        end if;
        update public.autodrive_users
        set user_id = v_user_id,
            account_type = v_account_type,
            full_name = p_full_name,
            phone = p_phone,
            bank_name = p_bank_name,
            bank_account = p_bank_account,
            workshop_name = p_workshop_name,
            specialty = p_specialty,
            workers_count = p_workers_count,
            address = p_address,
            updated_at = now()
        where id = v_existing_id;
        v_result_id := v_existing_id;
    else
        insert into public.autodrive_users (
            user_id,
            client_id,
            org_id,
            account_type,
            full_name,
            phone,
            bank_name,
            bank_account,
            workshop_name,
            specialty,
            workers_count,
            address
        ) values (
            v_user_id,
            v_client_id,
            v_org_id,
            v_account_type,
            p_full_name,
            p_phone,
            p_bank_name,
            p_bank_account,
            p_workshop_name,
            p_specialty,
            p_workers_count,
            p_address
        )
        returning id into v_result_id;
    end if;

    insert into public.marketer_balance (client_id, org_id, balance)
    values (v_client_id, v_org_id, 0)
    on conflict (client_id) do nothing;

    update public.invite_codes
    set status = 'used',
        used = true,
        used_at = now(),
        used_by_user_id = v_user_id
    where code = p_code;

    return v_result_id;
end;
$function$;

revoke all on function public.redeem_invite_code(text, text, text, text, text, text, text, text, integer, text) from public, anon, service_role;
grant execute on function public.redeem_invite_code(text, text, text, text, text, text, text, text, integer, text) to authenticated;
