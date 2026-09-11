-- Read-only verification for v231 remote schema.
select table_name, column_name
from information_schema.columns
where table_schema = 'public'
  and (table_name, column_name) in (
    ('logistics_milestones','customs_started_at'),
    ('logistics_milestones','customs_completed_at'),
    ('logistics_documents','employee_id'),
    ('logistics_documents','employee_name_snapshot')
  )
order by table_name, column_name;
