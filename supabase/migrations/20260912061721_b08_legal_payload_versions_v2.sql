CREATE OR REPLACE FUNCTION public.verto_sync_v2_payload_version(p_aggregate_type text,p_stored_version integer)
RETURNS integer LANGUAGE sql IMMUTABLE SET search_path TO 'public' AS $fn$
SELECT CASE WHEN p_aggregate_type IN(
 'PARTY_IDENTITY','PARTY_ROLE','CUSTOMER_PROFILE','SUPPLIER_PROFILE','INVOICE','PAYMENT','CLIENT_CREDIT',
 'GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE','INVENTORY_MOVEMENT','INVENTORY_COST_REVISION',
 'COST_ALLOCATION','EXPENSE','CASH_MOVEMENT','CASH_RECONCILIATION'
) THEN 2 ELSE p_stored_version END;
$fn$;
REVOKE ALL ON FUNCTION public.verto_sync_v2_payload_version(text,integer) FROM PUBLIC,anon,authenticated;

DO $do$ DECLARE v_def text;v_old text; BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='verto_pull_sync_changes_v2';
 v_old:=v_def;
 v_def:=replace(v_def,'''payloadVersion'',q.payload_version','''payloadVersion'',public.verto_sync_v2_payload_version(q.aggregate_type,q.payload_version)');
 IF v_def=v_old THEN RAISE EXCEPTION 'pull payloadVersion expression not found';END IF;
 EXECUTE v_def;
END $do$;

DO $do$ DECLARE v_def text;v_old text; BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='verto_read_sync_aggregate_v2';
 v_old:=v_def;
 v_def:=replace(v_def,'''payloadVersion'',v_row.payload_version','''payloadVersion'',public.verto_sync_v2_payload_version(v_row.aggregate_type,v_row.payload_version)');
 IF v_def=v_old THEN RAISE EXCEPTION 'aggregate payloadVersion expression not found';END IF;
 EXECUTE v_def;
END $do$;

DO $do$ DECLARE v_def text;v_old text; BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='verto_begin_sync_bootstrap_v2';
 v_old:=v_def;
 v_def:=replace(v_def,'s.entity_version,s.payload_version,s.payload,s.partition_key','s.entity_version,public.verto_sync_v2_payload_version(s.aggregate_type,s.payload_version),s.payload,s.partition_key');
 IF v_def=v_old THEN RAISE EXCEPTION 'bootstrap payloadVersion expression not found';END IF;
 EXECUTE v_def;
END $do$;