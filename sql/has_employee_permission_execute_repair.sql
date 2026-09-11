-- NOT EXECUTED by the application or this workspace.
-- Purpose: repair the Supabase error:
--   permission denied for function public.has_employee_permission
-- Review the function definitions and current grants before running manually.

BEGIN;

DO $$
DECLARE
    routine_signature regprocedure;
BEGIN
    FOR routine_signature IN
        SELECT p.oid::regprocedure
        FROM pg_proc AS p
        JOIN pg_namespace AS n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.proname = 'has_employee_permission'
    LOOP
        EXECUTE format(
            'GRANT EXECUTE ON FUNCTION %s TO authenticated',
            routine_signature
        );
    END LOOP;
END $$;

-- Verify manually before committing:
-- SELECT p.oid::regprocedure AS routine,
--        has_function_privilege('authenticated', p.oid, 'EXECUTE') AS can_execute
-- FROM pg_proc AS p
-- JOIN pg_namespace AS n ON n.oid = p.pronamespace
-- WHERE n.nspname = 'public'
--   AND p.proname = 'has_employee_permission';

COMMIT;

-- Rollback (run manually only if the grant was not previously required):
-- BEGIN;
-- DO $$
-- DECLARE
--     routine_signature regprocedure;
-- BEGIN
--     FOR routine_signature IN
--         SELECT p.oid::regprocedure
--         FROM pg_proc AS p
--         JOIN pg_namespace AS n ON n.oid = p.pronamespace
--         WHERE n.nspname = 'public'
--           AND p.proname = 'has_employee_permission'
--     LOOP
--         EXECUTE format(
--             'REVOKE EXECUTE ON FUNCTION %s FROM authenticated',
--             routine_signature
--         );
--     END LOOP;
-- END $$;
-- COMMIT;
