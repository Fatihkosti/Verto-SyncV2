-- Run only after 001..004 are applied in the target Supabase project.
-- This file performs verification; it does not create production data permanently.

-- 1) Contract tables exist.
select expected.table_name,
       to_regclass('public.' || expected.table_name) is not null as exists
from (
    values
      ('logistics_shipments'),
      ('logistics_shipment_sources'),
      ('logistics_shipment_lines'),
      ('logistics_milestones'),
      ('logistics_assignments'),
      ('logistics_events'),
      ('logistics_partners'),
      ('logistics_shipment_partner_links'),
      ('logistics_transport_details'),
      ('logistics_documents'),
      ('logistics_costs'),
      ('logistics_receiving_batches'),
      ('logistics_receiving_lines'),
      ('logistics_inventory_postings'),
      ('logistics_cost_allocations')
) as expected(table_name)
order by expected.table_name;

-- 2) RLS is enabled on every Logistics V2 table.
select c.relname as table_name, c.relrowsecurity as rls_enabled
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname like 'logistics_%'
order by c.relname;

-- 3) The authenticated session must resolve exactly one organization.
select public.logistics_v2_current_organization_id() as current_organization_id;

-- 4) Own-org visibility check. Every count below must only include the session organization.
select
    (select count(*) from public.logistics_shipments) as shipments,
    (select count(*) from public.logistics_receiving_lines) as receiving_lines,
    (select count(*) from public.logistics_cost_allocations) as cost_allocations;

-- 5) Cross-org denial must be tested using two authenticated users from different organizations:
--    A inserts a fixture under A. B must SELECT 0 rows for A and INSERT/UPDATE using A.organization_id must fail RLS.
--    Do not use service_role for this test because it bypasses RLS.

-- 6) Round-trip matrix to execute from Android/server verification harness before activation:
--    shipment/source/line, customs milestone, assignment/event, partner/link/transport/document/cost,
--    receiving batch/lines, inventory posting, cost allocation.
--    Verify values survive push -> pull and all rows retain organization_id.

-- 7) Idempotency checks.
select organization_id, shipment_id, type, request_id, count(*)
from public.logistics_events
group by organization_id, shipment_id, type, request_id
having count(*) > 1;

select organization_id, request_id, count(*)
from public.logistics_receiving_batches
group by organization_id, request_id
having count(*) > 1;

select organization_id, posting_id, count(*)
from public.logistics_inventory_postings
group by organization_id, posting_id
having count(*) > 1;

-- All three queries above must return zero rows.
