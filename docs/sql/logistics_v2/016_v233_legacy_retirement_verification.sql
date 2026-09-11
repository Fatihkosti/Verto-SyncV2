-- v233 read-only verification. Logistics V2 remains remote-runtime OFF.
-- This file does not mutate the remote database.

-- All authoritative Logistics V2 tables must exist.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'logistics_shipments', 'logistics_shipment_sources', 'logistics_shipment_lines',
    'logistics_milestones', 'logistics_shipment_legs', 'logistics_custody_handoffs',
    'logistics_assignments', 'logistics_events', 'logistics_documents', 'logistics_costs',
    'logistics_payments', 'logistics_receiving_batches', 'logistics_receiving_lines',
    'logistics_shortages', 'logistics_shortage_settlements'
  )
order by table_name;

-- Legacy remote names are reported only; v233 Android does not read/write them.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in ('shipments','shipment_stops','shipment_documents','shipment_costs','shipment_receipts')
order by table_name;
