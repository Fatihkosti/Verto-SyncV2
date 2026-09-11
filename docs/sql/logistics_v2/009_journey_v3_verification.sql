-- READ-ONLY verification for 008_journey_v3.sql. No mutation statements.

-- New Room-55 tables: expected missing_table = null for every row.
select expected.table_name, to_regclass('public.' || expected.table_name) as relation
from (values ('logistics_shortages'),('logistics_recoveries'),('logistics_recovery_lines'),('logistics_recovery_postings')) expected(table_name)
order by expected.table_name;

-- Every Room-55 additive column: expected zero rows.
with expected(table_name,column_name) as (values
 ('logistics_milestones','country_code'),('logistics_milestones','country_name_snapshot'),('logistics_milestones','city'),('logistics_milestones','place_name'),('logistics_milestones','plan_kind'),('logistics_milestones','expected_stay_days'),('logistics_milestones','customs_broker_partner_id'),('logistics_milestones','customs_broker_name_snapshot'),('logistics_milestones','customs_broker_phone_snapshot'),
 ('logistics_shipment_legs','plan_kind'),('logistics_shipment_legs','expected_transit_days'),('logistics_shipment_legs','representative_name_snapshot'),('logistics_shipment_legs','representative_phone_snapshot'),('logistics_shipment_legs','package_count'),('logistics_shipment_legs','weight_kg'),('logistics_shipment_legs','superseded_at'),('logistics_shipment_legs','superseded_by_leg_id'),
 ('logistics_custody_handoffs','handover_package_count'),('logistics_custody_handoffs','received_package_count'),('logistics_custody_handoffs','handover_weight_kg'),('logistics_custody_handoffs','received_weight_kg'),('logistics_custody_handoffs','package_change_reason'),('logistics_custody_handoffs','package_change_note'),
 ('logistics_costs','description'),('logistics_costs','payment_state'),('logistics_costs','cash_reference'),('logistics_costs','cash_posted_base_amount'),('logistics_costs','cash_posted_at'),('logistics_costs','reversal_of_cost_id'),('logistics_costs','recovery_id'),('logistics_costs','request_id'),
 ('logistics_documents','handoff_id'),('logistics_documents','cost_id'),('logistics_documents','recovery_id'),
 ('logistics_shortages','organization_id'),('logistics_shortages','id'),('logistics_shortages','shipment_id'),('logistics_shortages','shipment_line_id'),('logistics_shortages','original_missing_quantity'),('logistics_shortages','remaining_missing_quantity'),('logistics_shortages','base_purchase_unit_price_snapshot'),('logistics_shortages','status'),('logistics_shortages','detected_at'),('logistics_shortages','note'),('logistics_shortages','request_id'),
 ('logistics_recoveries','organization_id'),('logistics_recoveries','id'),('logistics_recoveries','shipment_id'),('logistics_recoveries','recovered_at'),('logistics_recoveries','employee_id'),('logistics_recoveries','employee_name_snapshot'),('logistics_recoveries','note'),('logistics_recoveries','request_id'),
 ('logistics_recovery_lines','organization_id'),('logistics_recovery_lines','id'),('logistics_recovery_lines','recovery_id'),('logistics_recovery_lines','shortage_id'),('logistics_recovery_lines','shipment_line_id'),('logistics_recovery_lines','recovered_quantity'),('logistics_recovery_lines','base_purchase_unit_price_snapshot'),('logistics_recovery_lines','allocated_recovery_cost'),
 ('logistics_recovery_postings','organization_id'),('logistics_recovery_postings','posting_id'),('logistics_recovery_postings','recovery_id'),('logistics_recovery_postings','recovery_line_id'),('logistics_recovery_postings','shipment_id'),('logistics_recovery_postings','shipment_line_id'),('logistics_recovery_postings','quantity')
)
select e.* from expected e left join information_schema.columns c on c.table_schema='public' and c.table_name=e.table_name and c.column_name=e.column_name where c.column_name is null order by e.table_name,e.column_name;

-- Required indexes/uniqueness: expected zero rows.
with expected(index_name) as (values
 ('logistics_shortages_org_shipment_idx'),('logistics_shortages_org_status_idx'),('logistics_shortages_org_shipment_line_uq'),('logistics_shortages_org_shipment_request_uq'),
 ('logistics_recoveries_org_shipment_idx'),('logistics_recoveries_org_shipment_request_uq'),
 ('logistics_recovery_lines_org_recovery_idx'),('logistics_recovery_lines_org_shortage_idx'),('logistics_recovery_lines_org_shipment_line_idx'),('logistics_recovery_lines_org_recovery_shortage_uq'),
 ('logistics_recovery_postings_org_shipment_idx'),('logistics_recovery_postings_org_recovery_idx'),('logistics_recovery_postings_org_recovery_line_uq'),('logistics_recovery_postings_org_shipment_line_idx'),
 ('logistics_costs_org_shipment_request_uq'),('logistics_costs_org_recovery_idx'),('logistics_documents_org_handoff_idx'),('logistics_documents_org_cost_idx'),('logistics_documents_org_recovery_idx')
)
select e.index_name from expected e left join pg_indexes i on i.schemaname='public' and i.indexname=e.index_name where i.indexname is null order by e.index_name;

-- RLS must be enabled on all new synchronized tables.
select c.relname as table_name,c.relrowsecurity as rls_enabled from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname in ('logistics_shortages','logistics_recoveries','logistics_recovery_lines','logistics_recovery_postings') order by c.relname;

-- Expected exactly SELECT/INSERT/UPDATE org policies per new table.
select tablename,policyname,cmd from pg_policies where schemaname='public' and tablename in ('logistics_shortages','logistics_recoveries','logistics_recovery_lines','logistics_recovery_postings') order by tablename,cmd,policyname;

-- No authenticated DELETE on any synchronized V3 table.
select table_name,has_table_privilege('authenticated',format('public.%I',table_name),'DELETE') as authenticated_delete_granted from (values ('logistics_shortages'),('logistics_recoveries'),('logistics_recovery_lines'),('logistics_recovery_postings')) v(table_name) order by table_name;

-- Idempotency/uniqueness checks: every query must return zero rows.
select organization_id,shipment_id,request_id,count(*) from public.logistics_shortages group by organization_id,shipment_id,request_id having count(*)>1;
select organization_id,shipment_id,request_id,count(*) from public.logistics_recoveries group by organization_id,shipment_id,request_id having count(*)>1;
select organization_id,recovery_id,shortage_id,count(*) from public.logistics_recovery_lines group by organization_id,recovery_id,shortage_id having count(*)>1;
select organization_id,recovery_line_id,count(*) from public.logistics_recovery_postings group by organization_id,recovery_line_id having count(*)>1;
select organization_id,shipment_id,request_id,count(*) from public.logistics_costs where request_id is not null group by organization_id,shipment_id,request_id having count(*)>1;

-- Legacy Logistics V2 tables must all still exist: expected zero rows.
with expected(table_name) as (values ('logistics_shipments'),('logistics_shipment_sources'),('logistics_shipment_lines'),('logistics_milestones'),('logistics_shipment_legs'),('logistics_custody_handoffs'),('logistics_assignments'),('logistics_events'),('logistics_partners'),('logistics_shipment_partner_links'),('logistics_transport_details'),('logistics_documents'),('logistics_costs'),('logistics_receiving_batches'),('logistics_receiving_lines'),('logistics_inventory_postings'),('logistics_cost_allocations'))
select e.table_name from expected e where to_regclass('public.'||e.table_name) is null order by e.table_name;

-- Organization isolation assumptions: all new tables have organization_id uuid NOT NULL.
select table_name,data_type,is_nullable from information_schema.columns where table_schema='public' and table_name in ('logistics_shortages','logistics_recoveries','logistics_recovery_lines','logistics_recovery_postings') and column_name='organization_id' order by table_name;
-- Live RLS verification still requires two real authenticated organizations; service_role is invalid because it bypasses RLS.
