DO $do$
DECLARE v_def text;v_old text;
BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='inventory_apply_cost_revisions_v2';
 IF v_def IS NULL THEN RAISE EXCEPTION 'inventory_apply_cost_revisions_v2 missing';END IF;
 v_old:=v_def;
 v_def:=replace(v_def,'where organization_id=v_org and id=v_item and not is_archived','where organization_id=v_org and id=public.try_uuid(v_item) and not is_archived');
 v_def:=replace(v_def,'values(cost_revision_id,v_org,v_item,','values(cost_revision_id,v_org,public.try_uuid(v_item),');
 v_def:=replace(v_def,'where organization_id=v_org and id=v_item;','where organization_id=v_org and id=public.try_uuid(v_item);');
 IF v_def=v_old THEN RAISE EXCEPTION 'expected inventory cost UUID expressions not found';END IF;
 EXECUTE v_def;
END $do$;