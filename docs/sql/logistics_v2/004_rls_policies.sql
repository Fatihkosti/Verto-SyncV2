begin;

create or replace function public.logistics_v2_current_organization_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select u.organization_id
    from public.app_users u
    where u.id = auth.uid()
      and coalesce(u.is_active, true)
    limit 1
$$;

create or replace function public.logistics_v2_touch_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    new.updated_at := now();
    return new;
end
$$;

do $$
declare
    table_name text;
    policy_name text;
begin
    foreach table_name in array array[
        'logistics_shipments',
        'logistics_shipment_sources',
        'logistics_shipment_lines',
        'logistics_milestones',
        'logistics_assignments',
        'logistics_events',
        'logistics_partners',
        'logistics_shipment_partner_links',
        'logistics_transport_details',
        'logistics_documents',
        'logistics_costs',
        'logistics_receiving_batches',
        'logistics_receiving_lines',
        'logistics_inventory_postings',
        'logistics_cost_allocations'
    ]
    loop
        execute format('alter table public.%I enable row level security', table_name);
        execute format('revoke all on public.%I from anon', table_name);
        -- Logistics V2 is append/update history; authenticated clients never delete remotely.
        -- Revoke from PUBLIC as well because PUBLIC privileges are inherited by authenticated.
        execute format('revoke delete on public.%I from public, anon, authenticated', table_name);
        execute format('grant select, insert, update on public.%I to authenticated', table_name);

        policy_name := table_name || '_org_select';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format(
            'create policy %I on public.%I for select to authenticated using (organization_id = public.logistics_v2_current_organization_id())',
            policy_name, table_name
        );

        policy_name := table_name || '_org_insert';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format(
            'create policy %I on public.%I for insert to authenticated with check (organization_id = public.logistics_v2_current_organization_id())',
            policy_name, table_name
        );

        policy_name := table_name || '_org_update';
        execute format('drop policy if exists %I on public.%I', policy_name, table_name);
        execute format(
            'create policy %I on public.%I for update to authenticated using (organization_id = public.logistics_v2_current_organization_id()) with check (organization_id = public.logistics_v2_current_organization_id())',
            policy_name, table_name
        );

        execute format('drop trigger if exists %I on public.%I', 'trg_' || table_name || '_updated_at', table_name);
        execute format(
            'create trigger %I before update on public.%I for each row execute function public.logistics_v2_touch_updated_at()',
            'trg_' || table_name || '_updated_at', table_name
        );
    end loop;
end
$$;

-- Operational history is not hard-deleted remotely.
-- No DELETE grant or DELETE policy is created for Logistics V2.

commit;
