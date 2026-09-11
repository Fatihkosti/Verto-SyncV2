-- Verto Logistics V2 v180 verification-only matrix.
-- Run manually only after 001..006 are applied. This file changes no production data.

-- ---------------------------------------------------------------------------
-- 1) Required Room-54 tables exist.
-- Expected: exists = true for both rows.
-- ---------------------------------------------------------------------------
select expected.table_name,
       to_regclass('public.' || expected.table_name) is not null as exists
from (
    values
      ('logistics_shipment_legs'),
      ('logistics_custody_handoffs')
) as expected(table_name)
order by expected.table_name;

-- ---------------------------------------------------------------------------
-- 2) Operational columns exist on pre-v54 tables.
-- Expected: missing columns query returns zero rows.
-- ---------------------------------------------------------------------------
with expected(table_name, column_name) as (
    values
      ('logistics_shipments', 'cancelled_at'),
      ('logistics_shipments', 'cancel_reason'),
      ('logistics_milestones', 'planned_departure_at'),
      ('logistics_milestones', 'handling_status'),
      ('logistics_milestones', 'unloaded_at'),
      ('logistics_milestones', 'loaded_at'),
      ('logistics_documents', 'source_id'),
      ('logistics_documents', 'leg_id'),
      ('logistics_costs', 'leg_id'),
      ('logistics_costs', 'milestone_id'),
      ('logistics_costs', 'source_id')
)
select expected.table_name, expected.column_name
from expected
left join information_schema.columns c
  on c.table_schema = 'public'
 and c.table_name = expected.table_name
 and c.column_name = expected.column_name
where c.column_name is null
order by expected.table_name, expected.column_name;

-- ---------------------------------------------------------------------------
-- 3) Every synchronized column for the two new tables exists.
-- Expected: missing columns query returns zero rows.
-- ---------------------------------------------------------------------------
with expected(table_name, column_name) as (
    values
      ('logistics_shipment_legs', 'organization_id'),
      ('logistics_shipment_legs', 'id'),
      ('logistics_shipment_legs', 'shipment_id'),
      ('logistics_shipment_legs', 'sequence'),
      ('logistics_shipment_legs', 'from_milestone_id'),
      ('logistics_shipment_legs', 'to_milestone_id'),
      ('logistics_shipment_legs', 'mode'),
      ('logistics_shipment_legs', 'carrier_partner_id'),
      ('logistics_shipment_legs', 'status'),
      ('logistics_shipment_legs', 'planned_departure_at'),
      ('logistics_shipment_legs', 'planned_arrival_at'),
      ('logistics_shipment_legs', 'actual_departure_at'),
      ('logistics_shipment_legs', 'actual_arrival_at'),
      ('logistics_shipment_legs', 'road_vehicle_number'),
      ('logistics_shipment_legs', 'road_driver_name'),
      ('logistics_shipment_legs', 'road_driver_phone'),
      ('logistics_shipment_legs', 'sea_container_number'),
      ('logistics_shipment_legs', 'sea_bill_of_lading'),
      ('logistics_shipment_legs', 'sea_vessel_reference'),
      ('logistics_shipment_legs', 'air_waybill_number'),
      ('logistics_shipment_legs', 'air_flight_reference'),
      ('logistics_shipment_legs', 'note'),
      ('logistics_custody_handoffs', 'organization_id'),
      ('logistics_custody_handoffs', 'id'),
      ('logistics_custody_handoffs', 'shipment_id'),
      ('logistics_custody_handoffs', 'source_id'),
      ('logistics_custody_handoffs', 'milestone_id'),
      ('logistics_custody_handoffs', 'from_holder_type'),
      ('logistics_custody_handoffs', 'from_holder_id'),
      ('logistics_custody_handoffs', 'from_holder_name_snapshot'),
      ('logistics_custody_handoffs', 'to_holder_type'),
      ('logistics_custody_handoffs', 'to_holder_id'),
      ('logistics_custody_handoffs', 'to_holder_name_snapshot'),
      ('logistics_custody_handoffs', 'transferred_at'),
      ('logistics_custody_handoffs', 'received_at'),
      ('logistics_custody_handoffs', 'request_id'),
      ('logistics_custody_handoffs', 'note')
)
select expected.table_name, expected.column_name
from expected
left join information_schema.columns c
  on c.table_schema = 'public'
 and c.table_name = expected.table_name
 and c.column_name = expected.column_name
where c.column_name is null
order by expected.table_name, expected.column_name;

-- ---------------------------------------------------------------------------
-- 4) RLS enabled on the two new synchronized tables.
-- Expected: rls_enabled = true for both.
-- ---------------------------------------------------------------------------
select c.relname as table_name, c.relrowsecurity as rls_enabled
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('logistics_shipment_legs', 'logistics_custody_handoffs')
order by c.relname;

-- ---------------------------------------------------------------------------
-- 5) Same-organization SELECT/INSERT/UPDATE policies exist.
-- Expected: three policies per table, commands SELECT/INSERT/UPDATE.
-- ---------------------------------------------------------------------------
select tablename, policyname, cmd
from pg_policies
where schemaname = 'public'
  and tablename in ('logistics_shipment_legs', 'logistics_custody_handoffs')
order by tablename, cmd, policyname;

-- ---------------------------------------------------------------------------
-- 6) Authenticated DELETE is not granted.
-- Expected: every row has authenticated_delete_granted = false.
-- ---------------------------------------------------------------------------
select table_name,
       has_table_privilege('authenticated', format('public.%I', table_name), 'DELETE')
           as authenticated_delete_granted
from (
    values
      ('logistics_shipments'),
      ('logistics_shipment_sources'),
      ('logistics_shipment_lines'),
      ('logistics_milestones'),
      ('logistics_shipment_legs'),
      ('logistics_custody_handoffs'),
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
order by table_name;

-- ---------------------------------------------------------------------------
-- 7) Shipment-number allocator exists with authenticated EXECUTE only.
-- Expected: allocator_exists = true, authenticated_execute = true.
-- ---------------------------------------------------------------------------
select
    to_regprocedure('public.allocate_logistics_shipment_number(uuid)') is not null
        as allocator_exists,
    has_function_privilege(
        'authenticated',
        'public.allocate_logistics_shipment_number(uuid)',
        'EXECUTE'
    ) as authenticated_execute;

-- ---------------------------------------------------------------------------
-- 8) Idempotency/uniqueness checks.
-- Expected: both duplicate queries return zero rows.
-- ---------------------------------------------------------------------------
select organization_id, shipment_id, request_id, count(*) as duplicate_count
from public.logistics_custody_handoffs
group by organization_id, shipment_id, request_id
having count(*) > 1;

select organization_id, shipment_id, sequence, count(*) as duplicate_count
from public.logistics_shipment_legs
group by organization_id, shipment_id, sequence
having count(*) > 1;

-- ---------------------------------------------------------------------------
-- 9) Scope integrity checks.
-- Expected: both queries return zero rows.
-- ---------------------------------------------------------------------------
select organization_id, id, source_id, milestone_id, leg_id
from public.logistics_documents
where num_nonnulls(source_id, milestone_id, leg_id) > 1;

select organization_id, id, source_id, milestone_id, leg_id
from public.logistics_costs
where num_nonnulls(source_id, milestone_id, leg_id) > 1;

-- ---------------------------------------------------------------------------
-- 10) Cross-organization RLS manual test instructions.
-- ---------------------------------------------------------------------------
-- Use two normal authenticated users A and B from different organizations.
-- A: create/read a Logistics V2 fixture under A.organization_id.
-- B: SELECT must return zero A rows from logistics_shipment_legs and
--    logistics_custody_handoffs.
-- B: INSERT/UPDATE carrying A.organization_id must fail RLS.
-- Repeat in the reverse direction.
-- Do not use service_role: it bypasses RLS and invalidates the test.

-- ---------------------------------------------------------------------------
-- 11) Android <-> server round-trip checklist after live activation is approved.
-- ---------------------------------------------------------------------------
-- [ ] Push/pull shipment cancelled_at + cancel_reason.
-- [ ] Push/pull milestone planned_departure_at + handling_status + unloaded_at + loaded_at.
-- [ ] Push/pull every logistics_shipment_legs field, including mode-specific nullable fields.
-- [ ] Push/pull every logistics_custody_handoffs field, including source-scoped and shipment-wide rows.
-- [ ] Push/pull document source_id/milestone_id/leg_id with at most one scope.
-- [ ] Push/pull cost source_id/milestone_id/leg_id with at most one scope.
-- [ ] Verify organization_id is unchanged for every round-tripped row.
-- [ ] Verify a second pull is idempotent (no duplicate leg sequence or custody request_id).
-- [ ] Verify missing/denied server operations surface as failure; never treat them as sync success.
-- [ ] Only after the full matrix passes may Logistics V2 remote activation be enabled.
