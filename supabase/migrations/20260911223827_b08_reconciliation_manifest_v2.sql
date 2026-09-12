DO $do$
DECLARE v_def text; v_old text;
BEGIN
 SELECT pg_get_functiondef('public.verto_get_reconciliation_manifest(uuid,text)'::regprocedure) INTO v_def;
 v_old:=v_def;
 v_def:=replace(v_def,'FUNCTION public.verto_get_reconciliation_manifest(','FUNCTION public.verto_get_reconciliation_manifest_v2(');
 v_def:=replace(v_def,'public.verto_validate_sync_scope(p_scope_id)','public.verto_validate_sync_scope_v2(p_scope_id)');
 IF v_def=v_old THEN RAISE EXCEPTION 'could not derive V2 reconciliation manifest'; END IF;
 EXECUTE v_def;
END $do$;
REVOKE ALL ON FUNCTION public.verto_get_reconciliation_manifest_v2(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.verto_get_reconciliation_manifest_v2(uuid,text) TO authenticated;