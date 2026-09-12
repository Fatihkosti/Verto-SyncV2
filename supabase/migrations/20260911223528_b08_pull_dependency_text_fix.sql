DO $do$
DECLARE v_def text; v_old text;
BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='verto_pull_sync_changes_v2'
   AND pg_get_function_identity_arguments(p.oid)='"scopeId" uuid, "cursorToken" text, "softLimit" integer, "maxGroupBytes" bigint';
 IF v_def IS NULL THEN RAISE EXCEPTION 'verto_pull_sync_changes_v2 definition not found'; END IF;
 v_old:=v_def;
 v_def:=replace(v_def,'SELECT dep#>>''{}'' d FROM','SELECT dep d FROM');
 IF v_def=v_old THEN RAISE EXCEPTION 'expected dependency expression not found'; END IF;
 EXECUTE v_def;
END $do$;