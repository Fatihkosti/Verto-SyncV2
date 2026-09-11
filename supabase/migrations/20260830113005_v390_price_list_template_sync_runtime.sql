create or replace function public.verto_apply_sync_adapter_v390(
  p_organization_id uuid,
  p_aggregate_type text,
  p_aggregate_id text,
  p_operation_type text,
  p_payload_version integer,
  p_payload jsonb,
  p_base_version bigint
)
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_payload jsonb := coalesce(p_payload, '{}'::jsonb);
  v_kind text := upper(btrim(coalesce(v_payload->>'kind','')));
  v_op text := upper(btrim(coalesce(p_operation_type,'')));
  v_payload_id text := btrim(coalesce(v_payload->>'id',''));
  v_payload_org uuid;
  v_name text := btrim(coalesce(v_payload->>'name',''));
  v_item_ids text := btrim(coalesce(v_payload->>'itemIds',''));
  v_new_version bigint := greatest(coalesce(p_base_version,0) + 1, 1);
begin
  if upper(btrim(coalesce(p_aggregate_type,''))) <> 'PRICE_LIST' then
    return public.verto_apply_sync_adapter_v309(
      p_organization_id,p_aggregate_type,p_aggregate_id,p_operation_type,
      p_payload_version,p_payload,p_base_version
    );
  end if;

  if v_op not in ('UPSERT','DELETE') then
    return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_OPERATION_UNSUPPORTED');
  end if;

  if v_op = 'UPSERT' then
    if v_kind <> 'TEMPLATE' then
      return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_KIND_REQUIRED');
    end if;
    if v_payload_id = '' or v_payload_id <> p_aggregate_id then
      return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_ID_MISMATCH');
    end if;
    if v_name = '' then
      return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_NAME_REQUIRED');
    end if;
    if v_item_ids = '' then
      return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_ITEMS_REQUIRED');
    end if;
    if nullif(btrim(coalesce(v_payload->>'organizationId','')),'') is not null then
      begin
        v_payload_org := (v_payload->>'organizationId')::uuid;
      exception when others then
        return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_ORG_INVALID');
      end;
      if v_payload_org is distinct from p_organization_id then
        return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_ORG_MISMATCH');
      end if;
    end if;
  else
    if v_payload_id <> '' and v_payload_id <> p_aggregate_id then
      return jsonb_build_object('applied',false,'validation_code','PRICE_LIST_TEMPLATE_ID_MISMATCH');
    end if;
  end if;

  return jsonb_build_object(
    'applied',true,
    'no_op',false,
    'server_version',v_new_version,
    'authoritative_payload',v_payload
  );
end
$function$;

-- Route only legacy-owner traffic through the v390 wrapper. All non-PRICE_LIST
-- aggregates delegate byte-for-byte to the existing v309 adapter.
do $patch$
declare
  v_oid oid;
  v_def text;
  v_new_def text;
begin
  select p.oid into v_oid
  from pg_proc p join pg_namespace n on n.oid=p.pronamespace
  where n.nspname='public' and p.proname='verto_apply_sync_mutation'
    and pg_get_function_identity_arguments(p.oid)='p_mutation jsonb';
  if v_oid is null then
    raise exception 'verto_apply_sync_mutation(jsonb) not found';
  end if;
  v_def := pg_get_functiondef(v_oid);
  if (length(v_def)-length(replace(v_def,'public.verto_apply_sync_adapter_v309(',''))) / length('public.verto_apply_sync_adapter_v309(') <> 1 then
    raise exception 'unexpected v309 adapter call count in verto_apply_sync_mutation';
  end if;
  v_new_def := replace(v_def,'public.verto_apply_sync_adapter_v309(','public.verto_apply_sync_adapter_v390(');
  execute v_new_def;
end
$patch$;

-- Recover only legacy lists whose every row maps uniquely to one current
-- inventory item. Existing PRICE_LIST snapshots win and are never overwritten.
do $backfill$
declare
  r record;
  v_now_ms bigint := floor(extract(epoch from clock_timestamp()) * 1000)::bigint;
  v_payload jsonb;
  v_revision bigint;
begin
  for r in
    with mapped as (
      select h.id::text as template_id,
             h.organization_id,
             coalesce(nullif(btrim(h.shop_name),''),'قالب مستعاد') as template_name,
             string_agg(inv.id::text, ',' order by i.sort_order, i.id::text) as item_ids,
             count(*) as joined_count,
             count(distinct i.id) as legacy_item_count,
             count(distinct inv.id) as inventory_item_count
      from public.price_list_header h
      join public.price_list_items i
        on i.header_id=h.id and i.organization_id=h.organization_id
      join public.inventory_items inv
        on inv.organization_id=i.organization_id
       and lower(btrim(inv.name))=lower(btrim(i.name))
      group by h.id,h.organization_id,h.shop_name
    )
    select * from mapped m
    where m.joined_count=m.legacy_item_count
      and m.legacy_item_count=m.inventory_item_count
      and m.legacy_item_count>0
      and not exists (
        select 1 from public.verto_sync_snapshot_state s
        where s.organization_id=m.organization_id
          and s.aggregate_type='PRICE_LIST'
          and s.aggregate_id=m.template_id
      )
  loop
    v_payload := jsonb_build_object(
      'kind','TEMPLATE',
      'id',r.template_id,
      'organizationId',r.organization_id::text,
      'name',r.template_name,
      'sortOrder',0,
      'itemIds',r.item_ids,
      'createdAt',v_now_ms,
      'updatedAt',v_now_ms,
      'serverVersion',1
    );
    v_revision := public.verto_append_sync_change(
      r.organization_id,'PRICE_LIST',r.template_id,'UPSERT',1,
      public.verto_expected_payload_version('PRICE_LIST'),v_payload,
      'v390-legacy-price-list-backfill:'||r.template_id,'MIGRATION'
    );
  end loop;
end
$backfill$;
