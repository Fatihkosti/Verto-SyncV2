DO $do$
DECLARE v_def text; v_old text;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_def
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.proname='verto_apply_sync_batch_v2'
    AND pg_get_function_identity_arguments(p.oid)='p_wire_json text, p_wire_sha256 text';
  IF v_def IS NULL THEN RAISE EXCEPTION 'verto_apply_sync_batch_v2 not found'; END IF;
  v_old:=v_def;
  v_def:=replace(v_def, 'IF v_batch_id='''' OR v_count<1 OR jsonb_typeof', 'IF v_batch_id='''' OR v_count<1 OR v_count>1000 OR jsonb_typeof');
  IF v_def=v_old THEN RAISE EXCEPTION 'expected B07 member-count expression not found'; END IF;
  EXECUTE v_def;
END $do$;

DO $do$
DECLARE v_def text; v_old text;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO v_def
  FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.proname='verto_pull_sync_changes_v2'
    AND pg_get_function_identity_arguments(p.oid)='"scopeId" uuid, "cursorToken" text, "softLimit" integer, "maxGroupBytes" bigint';
  IF v_def IS NULL THEN RAISE EXCEPTION 'verto_pull_sync_changes_v2 not found'; END IF;
  v_old:=v_def;
  v_def:=replace(v_def, 'v_page_bytes+v_group_bytes>69206016', 'v_page_bytes+v_group_bytes>2097152');
  IF v_def=v_old THEN RAISE EXCEPTION 'expected B08 page-byte expression not found'; END IF;
  EXECUTE v_def;
END $do$;

COMMENT ON FUNCTION public.verto_apply_sync_batch_v2(text,text) IS 'B07 V2 atomic batch: exact raw-wire SHA-256, max 1000 members / 2MiB, Auth-derived tenant, stable receipts.';
COMMENT ON FUNCTION public.verto_pull_sync_changes_v2(uuid,text,integer,bigint) IS 'B08 V2 delta pull: full transaction groups, soft 1000 rows, hard 2MiB group/page boundary, opaque cursor and scoped manifests.';