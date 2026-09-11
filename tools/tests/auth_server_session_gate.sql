-- Verto authentication server regression checks.
-- Read-only: dynamically selects existing live sessions and does not mutate application data.

-- 1) Session gate hardening/ACL contract.
select p.prosecdef as security_definer,
       p.proconfig as function_config,
       has_function_privilege('anon', 'public.verto_auth_session_status()', 'EXECUTE') as anon_execute,
       has_function_privilege('authenticated', 'public.verto_auth_session_status()', 'EXECUTE') as authenticated_execute,
       has_function_privilege('service_role', 'public.verto_auth_session_status()', 'EXECUTE') as service_execute,
       pg_get_functiondef(p.oid) like '%recovery%' as checks_recovery,
       pg_get_functiondef(p.oid) like '%magiclink%' as checks_magiclink,
       pg_get_functiondef(p.oid) like '%otp%' as checks_otp
from pg_proc p join pg_namespace n on n.oid=p.pronamespace
where n.nspname='public' and p.proname='verto_auth_session_status';

-- 2) Registration/join RPC hardening contract.
with funcs as (
  select p.proname, p.prosecdef, p.proconfig, pg_get_functiondef(p.oid) def
  from pg_proc p join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='public' and p.proname in ('create_organization_with_admin','join_organization_with_code')
)
select count(*)=2 as both_rpcs_exist,
       bool_and(prosecdef) as both_security_definer,
       bool_and(array_to_string(proconfig, ',') ilike '%search_path%') as search_path_locked,
       bool_and(def like '%auth.uid()%') as caller_identity_checked,
       bool_or(proname='join_organization_with_code' and lower(def) like '%for update%') as join_locks_invite,
       bool_or(proname='join_organization_with_code' and lower(def) like '%used%') as join_checks_invite_use
from funcs;

-- 3) RLS contract for auth-adjacent application tables.
with t as (
  select c.oid, c.relname, c.relrowsecurity,
         exists(select 1 from pg_policy p where p.polrelid=c.oid) as has_policy
  from pg_class c join pg_namespace n on n.oid=c.relnamespace
  where n.nspname='public'
    and c.relname in ('app_users','organizations','employee_permissions','invite_codes')
)
select count(*)=4 as all_tables_found,
       bool_and(relrowsecurity) as all_rls_enabled,
       bool_and(has_policy) as every_table_has_policy
from t;

-- Behavioral AMR tests (password/recovery/otp/magiclink, anonymous, invalid role,
-- missing session_id, missing profile) are executed by the session report runner because
-- each statement temporarily sets request.jwt.claims for the duration of that statement.
