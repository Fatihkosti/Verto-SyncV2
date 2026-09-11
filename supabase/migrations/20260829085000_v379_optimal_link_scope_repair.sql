-- v379: Secure Verto -> Optimal company-link read path.
-- optimal_verto_links intentionally remains non-readable through the Data API.

create or replace function public.verto_list_optimal_links()
returns table(
    verto_organization_id uuid,
    verto_client_id uuid,
    optimal_company_id uuid,
    linked_at timestamptz,
    is_active boolean
)
language plpgsql
security definer
set search_path = pg_catalog, public, auth
as $$
declare
    v_user_id uuid := auth.uid();
    v_org_id uuid;
begin
    if v_user_id is null then
        raise exception 'auth_session_required' using errcode = '28000';
    end if;

    v_org_id := public.get_my_org_id();
    if v_org_id is null then
        raise exception 'organization_not_found' using errcode = 'P0002';
    end if;

    if not (public.has_perm('clients_view') or public.has_perm('clients_edit')) then
        raise exception 'clients_view_required' using errcode = '42501';
    end if;

    return query
    select
        l.verto_organization_id,
        l.verto_client_id,
        l.optimal_company_id,
        l.linked_at,
        l.is_active
    from public.optimal_verto_links l
    where l.verto_organization_id = v_org_id
      and l.is_active = true
    order by l.linked_at desc, l.verto_client_id;
end;
$$;

revoke all on function public.verto_list_optimal_links() from public;
revoke all on function public.verto_list_optimal_links() from anon;
grant execute on function public.verto_list_optimal_links() to authenticated;
