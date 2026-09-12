DO $do$
DECLARE v_def text;v_old text;
BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='inventory_apply_commands_v2';
 IF v_def IS NULL THEN RAISE EXCEPTION 'inventory_apply_commands_v2 missing'; END IF;
 v_old:=v_def;
 v_def:=replace(v_def,'movement_id,v_org,v_item.id,coalesce(v_command->>''invoice_id'',''''),nullif(v_command->>''client_id'','''')::uuid,','public.try_uuid(movement_id),v_org,v_item.id,coalesce(v_command->>''invoice_id'',''''),nullif(v_command->>''client_id'',''''),');
 IF v_def=v_old THEN RAISE EXCEPTION 'expected inventory insert expression not found'; END IF;
 EXECUTE v_def;
END $do$;