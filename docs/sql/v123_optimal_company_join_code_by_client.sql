-- Verto v123: issue an Optimal company join code for an explicitly selected Verto client.
-- Apply with the Supabase migration role after the v69 Optimal registration migration.
-- The RPC never accepts organization/company names from the client; both are resolved server-side.

begin;

create extension if not exists pgcrypto;

create table if not exists public.optimal_company_join_codes (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null,
    client_id uuid,
    company_name text,
    code_hash text,
    state text not null default 'ACTIVE',
    created_by uuid,
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours'),
    used_at timestamptz,
    cancelled_at timestamptz
);

-- Upgrade older organization-only code tables without destroying existing rows.
alter table public.optimal_company_join_codes
    add column if not exists client_id uuid,
    add column if not exists company_name text,
    add column if not exists code_hash text,
    add column if not exists state text not null default 'ACTIVE',
    add column if not exists created_by uuid,
    add column if not exists issued_at timestamptz not null default now(),
    add column if not exists expires_at timestamptz,
    add column if not exists used_at timestamptz,
    add column if not exists cancelled_at timestamptz;

create index if not exists optimal_company_join_codes_org_client_idx
    on public.optimal_company_join_codes (organization_id, client_id, issued_at desc);

create unique index if not exists optimal_company_join_codes_active_company_uq
    on public.optimal_company_join_codes (organization_id, client_id)
    where state = 'ACTIVE' and used_at is null and client_id is not null;

create unique index if not exists optimal_company_join_codes_hash_uq
    on public.optimal_company_join_codes (code_hash)
    where code_hash is not null;

alter table public.optimal_company_join_codes enable row level security;
revoke all on public.optimal_company_join_codes from anon, authenticated;

create or replace function public.verto_can_issue_optimal_company_code(p_organization_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.app_users u
        where u.id = auth.uid()
          and u.organization_id = p_organization_id
          and coalesce(u.is_active, true)
          and (
              lower(coalesce(u.role, '')) = 'admin'
              or exists (
                  select 1
                  from public.employee_permissions ep
                  where ep.user_id = auth.uid()
                    and ep.org_id = p_organization_id
                    and lower(coalesce(ep.permissions ->> 'ISSUE_OPTIMAL_CODE', 'false')) = 'true'
              )
          )
    )
$$;

revoke all on function public.verto_can_issue_optimal_company_code(uuid) from public, anon, authenticated;

create or replace function public.verto_issue_optimal_company_join_code(
    p_client_id uuid,
    p_expires_in_minutes integer default 1440
)
returns table (
    organization_id uuid,
    client_id uuid,
    company_name text,
    code text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_organization_id uuid;
    v_company_name text;
    v_code text;
    v_code_hash text;
    v_expires_at timestamptz;
    v_is_linked boolean := false;
begin
    if auth.uid() is null then
        raise exception 'auth_session_required';
    end if;
    if p_client_id is null then
        raise exception 'company_client_required';
    end if;
    if p_expires_in_minutes is null or p_expires_in_minutes < 5 or p_expires_in_minutes > 1440 then
        raise exception 'invalid_expiry_window';
    end if;

    select u.organization_id
      into v_organization_id
      from public.app_users u
     where u.id = auth.uid()
       and coalesce(u.is_active, true)
     limit 1;

    if v_organization_id is null then
        raise exception 'auth_session_required';
    end if;
    if not public.verto_can_issue_optimal_company_code(v_organization_id) then
        raise exception 'clients_edit_required';
    end if;

    select c.name
      into v_company_name
      from public.clients c
     where c.id = p_client_id
       and c.organization_id = v_organization_id
       and position(
           ',COMPANY,' in ',' || replace(upper(coalesce(c.client_types, '')), ' ', '') || ','
       ) > 0
     limit 1;

    if v_company_name is null or btrim(v_company_name) = '' then
        raise exception 'client_not_found_or_not_company';
    end if;

    -- The historical link table changed column names between server revisions.
    -- JSON projection keeps this migration compatible with both supported layouts.
    if to_regclass('public.optimal_verto_links') is not null then
        execute $linked$
            select exists (
                select 1
                from public.optimal_verto_links l
                where coalesce(
                    to_jsonb(l) ->> 'organization_id',
                    to_jsonb(l) ->> 'verto_organization_id'
                ) = $1::text
                  and coalesce(
                    to_jsonb(l) ->> 'client_id',
                    to_jsonb(l) ->> 'verto_client_id'
                  ) = $2::text
            )
        $linked$
        into v_is_linked
        using v_organization_id, p_client_id;
    end if;

    if v_is_linked then
        raise exception 'company_already_linked';
    end if;

    -- Invalidate only previous active codes for this exact company.
    update public.optimal_company_join_codes as join_code
       set state = 'CANCELLED',
           cancelled_at = now()
     where join_code.organization_id = v_organization_id
       and join_code.client_id = p_client_id
       and join_code.state = 'ACTIVE'
       and join_code.used_at is null;

    v_expires_at := now() + make_interval(mins => p_expires_in_minutes);

    loop
        v_code := upper(substr(encode(gen_random_bytes(8), 'hex'), 1, 10));
        v_code_hash := encode(digest(v_code, 'sha256'), 'hex');
        exit when not exists (
            select 1
            from public.optimal_company_join_codes c
            where c.code_hash = v_code_hash
        );
    end loop;

    insert into public.optimal_company_join_codes (
        organization_id,
        client_id,
        company_name,
        code_hash,
        state,
        created_by,
        issued_at,
        expires_at,
        used_at,
        cancelled_at
    ) values (
        v_organization_id,
        p_client_id,
        btrim(v_company_name),
        v_code_hash,
        'ACTIVE',
        auth.uid(),
        now(),
        v_expires_at,
        null,
        null
    );

    return query
    select
        v_organization_id,
        p_client_id,
        btrim(v_company_name),
        v_code,
        v_expires_at;
end
$$;

-- Atomic one-time consumer for the Optimal completion RPC.
-- The completion flow must use the returned client_id/company_name and must not accept replacements.
create or replace function public.optimal_consume_verto_company_join_code(p_code text)
returns table (
    organization_id uuid,
    client_id uuid,
    company_name text
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_id uuid;
    v_hash text;
begin
    if p_code is null or btrim(p_code) = '' then
        raise exception 'join_code_required';
    end if;

    v_hash := encode(digest(upper(btrim(p_code)), 'sha256'), 'hex');

    select c.id, c.organization_id, c.client_id, c.company_name
      into v_id, organization_id, client_id, company_name
      from public.optimal_company_join_codes c
     where c.code_hash = v_hash
       and c.state = 'ACTIVE'
       and c.used_at is null
       and c.cancelled_at is null
       and c.expires_at > now()
       and c.client_id is not null
     for update skip locked
     limit 1;

    if v_id is null then
        raise exception 'join_code_invalid_or_expired';
    end if;

    update public.optimal_company_join_codes
       set state = 'USED',
           used_at = now()
     where id = v_id
       and state = 'ACTIVE'
       and used_at is null;

    if not found then
        raise exception 'join_code_already_used';
    end if;

    return next;
end
$$;

revoke all on function public.verto_issue_optimal_company_join_code(uuid, integer) from public, anon;
grant execute on function public.verto_issue_optimal_company_join_code(uuid, integer) to authenticated;

-- Internal helper only: the SECURITY DEFINER completion RPC calls it in the same transaction.
revoke all on function public.optimal_consume_verto_company_join_code(text) from public, anon, authenticated;

-- Keep the backend readiness flag enabled and advance the contract marker when the table exists.
do $$
begin
    if to_regclass('public.optimal_backend_contracts') is not null then
        execute $contract$
            update public.optimal_backend_contracts
               set contract_version = greatest(contract_version, 123),
                   verto_registration_ready = true
        $contract$;
    end if;
end
$$;

commit;
