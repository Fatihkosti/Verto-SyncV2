-- Read-only verification for v232 remote schema.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_name in (
    'logistics_shortage_settlements',
    'logistics_late_cost_adjustments',
    'logistics_late_cost_allocations'
  )
order by table_name;

select table_name, column_name
from information_schema.columns
where table_schema = 'public'
  and (table_name, column_name) in (
    ('logistics_shortage_settlements','request_id'),
    ('logistics_shortage_settlements','base_currency_amount'),
    ('logistics_late_cost_adjustments','cost_id'),
    ('logistics_late_cost_allocations','amount')
  )
order by table_name, column_name;
