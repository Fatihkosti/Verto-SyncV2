select version,name
from supabase_migrations.schema_migrations
where version >= '20260830113005'
order by version;
