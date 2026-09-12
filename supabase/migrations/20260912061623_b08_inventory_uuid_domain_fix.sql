DO $do$
DECLARE v_def text;v_old text;
BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='inventory_apply_commands_v2';
 IF v_def IS NULL THEN RAISE EXCEPTION 'inventory_apply_commands_v2 missing'; END IF;
 v_old:=v_def;
 v_def:=replace(v_def,'id=v_command->>''item_id''','id=public.try_uuid(v_command->>''item_id'')');
 v_def:=replace(v_def,'id=v_command->>''reverses_movement_id''','id=public.try_uuid(v_command->>''reverses_movement_id'')');
 IF v_def=v_old THEN RAISE EXCEPTION 'expected inventory UUID comparisons not found'; END IF;
 EXECUTE v_def;
END $do$;