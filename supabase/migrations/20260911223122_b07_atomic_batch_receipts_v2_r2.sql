-- B07 additive V2 overlay; V1 remains valid.
ALTER TABLE public.verto_sync_contract DROP CONSTRAINT IF EXISTS verto_sync_contract_version_v1;
ALTER TABLE public.verto_sync_contract ADD CONSTRAINT verto_sync_contract_version_supported CHECK (contract_version IN (1,2));

CREATE TABLE IF NOT EXISTS public.verto_sync_batch_receipts_v2 (
    organization_id uuid NOT NULL,
    batch_id text NOT NULL,
    request_hash text NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    member_count integer NOT NULL CHECK (member_count > 0),
    response_json jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, batch_id)
);
CREATE TABLE IF NOT EXISTS public.verto_sync_member_receipts_v2 (
    organization_id uuid NOT NULL,
    mutation_id text NOT NULL,
    request_hash text NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    batch_id text NOT NULL,
    member_order integer NOT NULL CHECK (member_order >= 0),
    business_identity text,
    response_json jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, mutation_id),
    UNIQUE (organization_id, batch_id, member_order)
);
CREATE INDEX IF NOT EXISTS verto_sync_member_receipts_v2_batch_idx ON public.verto_sync_member_receipts_v2(organization_id,batch_id,member_order);
CREATE INDEX IF NOT EXISTS verto_sync_member_receipts_v2_business_idx ON public.verto_sync_member_receipts_v2(organization_id,business_identity) WHERE business_identity IS NOT NULL;
ALTER TABLE public.verto_sync_batch_receipts_v2 ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_member_receipts_v2 ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.verto_sync_batch_receipts_v2 FROM anon,authenticated;
REVOKE ALL ON public.verto_sync_member_receipts_v2 FROM anon,authenticated;

INSERT INTO public.verto_sync_contract (
 contract_family,contract_version,schema_version,scope_definition_version,status,min_available_revision,
 default_pull_page_changes,pull_limit_max,max_pull_page_bytes,max_transaction_group_bytes,max_mutation_payload_bytes,
 max_push_batch_mutations,max_push_batch_bytes,max_worker_operations_per_run,max_worker_runtime_millis,
 max_backlog_before_diagnostic_warning,bootstrap_session_ttl_seconds,bootstrap_page_max_rows,bootstrap_page_max_bytes,
 supported_offline_window_days,supported_old_client_window_days,retention_safety_margin_days,change_log_retention_days,
 tombstone_retention_days,receipt_retention_days,device_inactive_after_days,local_acked_outbox_diagnostic_retention_days,
 local_inbox_diagnostic_retention_days,production_pruning_enabled,created_at,updated_at)
SELECT contract_family,2,GREATEST(schema_version,2),scope_definition_version,'EXPAND_ONLY',min_available_revision,
 1000,1000,2097152,2097152,max_mutation_payload_bytes,GREATEST(max_push_batch_mutations,1000),
 GREATEST(max_push_batch_bytes,2097152),max_worker_operations_per_run,max_worker_runtime_millis,
 max_backlog_before_diagnostic_warning,bootstrap_session_ttl_seconds,1000,2097152,supported_offline_window_days,
 supported_old_client_window_days,retention_safety_margin_days,change_log_retention_days,tombstone_retention_days,
 receipt_retention_days,device_inactive_after_days,local_acked_outbox_diagnostic_retention_days,
 local_inbox_diagnostic_retention_days,false,now(),now()
FROM public.verto_sync_contract WHERE contract_family='verto-unified-sync' AND contract_version=1
ON CONFLICT(contract_family,contract_version) DO UPDATE SET status='EXPAND_ONLY',default_pull_page_changes=1000,
 pull_limit_max=1000,max_pull_page_bytes=2097152,max_transaction_group_bytes=2097152,
 max_push_batch_bytes=GREATEST(public.verto_sync_contract.max_push_batch_bytes,2097152),bootstrap_page_max_rows=1000,
 bootstrap_page_max_bytes=2097152,updated_at=now();

CREATE OR REPLACE FUNCTION public.verto_validate_sync_org_v2(p_organization_id uuid) RETURNS uuid
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_uid uuid:=auth.uid();v_org uuid;v_active boolean;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE='28000'; END IF;
 SELECT au.organization_id,COALESCE(au.is_active,true) INTO v_org,v_active FROM public.app_users au WHERE au.id=v_uid;
 IF v_org IS NULL OR NOT v_active THEN RAISE EXCEPTION 'AUTH: active Verto membership required' USING ERRCODE='28000'; END IF;
 IF p_organization_id IS NULL OR p_organization_id<>v_org THEN RAISE EXCEPTION 'SCOPE_MISMATCH: organization is not current membership' USING ERRCODE='22023'; END IF;
 RETURN v_uid;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_resolve_sync_scope_v2(p_organization_id uuid)
RETURNS TABLE(scope_id uuid,organization_id uuid,sync_principal_id uuid,contract_family text,contract_version integer,scope_definition_version integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
#variable_conflict use_column
DECLARE v_uid uuid;v_fp text;v_scope_id uuid;v_scope_version integer;
BEGIN
 v_uid:=public.verto_validate_sync_org_v2(p_organization_id);
 SELECT c.scope_definition_version INTO v_scope_version FROM public.verto_sync_contract c WHERE c.contract_family='verto-unified-sync' AND c.contract_version=2 AND c.status='EXPAND_ONLY' LIMIT 1;
 IF v_scope_version IS NULL THEN RAISE EXCEPTION 'CONTRACT_UNSUPPORTED: V2 contract not active' USING ERRCODE='22023'; END IF;
 v_fp:=public.verto_current_visibility_fingerprint();
 UPDATE public.verto_sync_scopes s SET invalidated_at=now() WHERE s.principal_id=v_uid AND s.organization_id=p_organization_id AND s.contract_family='verto-unified-sync' AND s.contract_version=2 AND s.scope_definition_version=v_scope_version AND s.invalidated_at IS NULL AND s.visibility_fingerprint<>v_fp;
 SELECT s.scope_id INTO v_scope_id FROM public.verto_sync_scopes s WHERE s.principal_id=v_uid AND s.organization_id=p_organization_id AND s.contract_family='verto-unified-sync' AND s.contract_version=2 AND s.scope_definition_version=v_scope_version AND s.visibility_fingerprint=v_fp AND s.invalidated_at IS NULL LIMIT 1;
 IF v_scope_id IS NULL THEN
  INSERT INTO public.verto_sync_scopes(organization_id,principal_id,contract_family,contract_version,scope_definition_version,visibility_fingerprint)
  VALUES(p_organization_id,v_uid,'verto-unified-sync',2,v_scope_version,v_fp)
  ON CONFLICT(organization_id,principal_id,contract_family,contract_version,scope_definition_version,visibility_fingerprint) WHERE invalidated_at IS NULL
  DO UPDATE SET visibility_fingerprint=EXCLUDED.visibility_fingerprint RETURNING verto_sync_scopes.scope_id INTO v_scope_id;
 END IF;
 RETURN QUERY SELECT v_scope_id,p_organization_id,v_uid,'verto-unified-sync'::text,2,v_scope_version;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_resolve_sync_scope_v2()
RETURNS TABLE(scope_id uuid,organization_id uuid,sync_principal_id uuid,contract_family text,contract_version integer,scope_definition_version integer)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_uid uuid:=auth.uid();v_org uuid;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE='28000'; END IF;
 SELECT au.organization_id INTO v_org FROM public.app_users au WHERE au.id=v_uid AND COALESCE(au.is_active,true);
 IF v_org IS NULL THEN RAISE EXCEPTION 'AUTH: active Verto membership required' USING ERRCODE='28000'; END IF;
 RETURN QUERY SELECT * FROM public.verto_resolve_sync_scope_v2(v_org);
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_validate_sync_scope_v2(p_scope_id uuid)
RETURNS TABLE(organization_id uuid,principal_id uuid,visibility_fingerprint text)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_uid uuid:=auth.uid();v_scope public.verto_sync_scopes%ROWTYPE;v_org uuid;v_active boolean;v_fp text;v_def integer;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE='28000'; END IF;
 SELECT * INTO v_scope FROM public.verto_sync_scopes s WHERE s.scope_id=p_scope_id;
 IF NOT FOUND OR v_scope.principal_id<>v_uid THEN RAISE EXCEPTION 'SCOPE_MISMATCH: scope does not belong to current principal' USING ERRCODE='22023'; END IF;
 SELECT au.organization_id,COALESCE(au.is_active,true) INTO v_org,v_active FROM public.app_users au WHERE au.id=v_uid;
 SELECT c.scope_definition_version INTO v_def FROM public.verto_sync_contract c WHERE c.contract_family='verto-unified-sync' AND c.contract_version=2;
 IF v_org IS NULL OR NOT v_active THEN RAISE EXCEPTION 'AUTH: active Verto membership required' USING ERRCODE='28000'; END IF;
 IF v_scope.invalidated_at IS NOT NULL OR v_scope.organization_id<>v_org OR v_scope.contract_family<>'verto-unified-sync' OR v_scope.contract_version<>2 OR v_scope.scope_definition_version<>v_def THEN RAISE EXCEPTION 'SCOPE_MISMATCH: scope inactive or contract-bound elsewhere' USING ERRCODE='22023'; END IF;
 v_fp:=public.verto_current_visibility_fingerprint();
 IF v_fp<>v_scope.visibility_fingerprint THEN RAISE EXCEPTION 'SCOPE_MISMATCH: visibility changed' USING ERRCODE='22023'; END IF;
 RETURN QUERY SELECT v_scope.organization_id,v_scope.principal_id,v_scope.visibility_fingerprint;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_sync_repair_capabilities_v1(p_organization_id uuid) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_uid uuid;v_fp text;v_payloads jsonb;v_receipt_days integer;v_min bigint;
BEGIN
 v_uid:=public.verto_validate_sync_org_v2(p_organization_id);
 SELECT COALESCE(jsonb_object_agg(q.aggregate_type,q.payload_version),'{}'::jsonb) INTO v_payloads FROM(
  SELECT DISTINCT t.aggregate_type,public.verto_expected_payload_version(t.aggregate_type) payload_version FROM(
   SELECT aggregate_type FROM public.verto_sync_stronger_adapter_registry_v310
   UNION SELECT aggregate_type FROM public.verto_sync_snapshot_state WHERE organization_id=p_organization_id
   UNION SELECT aggregate_type FROM public.verto_sync_change_log WHERE organization_id=p_organization_id)t
  WHERE public.verto_expected_payload_version(t.aggregate_type) IS NOT NULL)q;
 SELECT c.receipt_retention_days,c.min_available_revision INTO v_receipt_days,v_min FROM public.verto_sync_contract c WHERE c.contract_family='verto-unified-sync' AND c.contract_version=2;
 SELECT encode(extensions.digest(convert_to(COALESCE(string_agg(pg_get_functiondef(p.oid),E'\n' ORDER BY p.proname),''),'UTF8'),'sha256'),'hex') INTO v_fp
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname IN('verto_resolve_sync_scope_v2','verto_apply_sync_batch_v2','verto_get_sync_receipts_v2','verto_pull_sync_changes_v2','verto_begin_sync_bootstrap_v2','verto_pull_sync_bootstrap_page_v2');
 RETURN jsonb_build_object('contractFamily','verto-unified-sync','contractVersion',2,'payloadVersions',v_payloads,'maxGroupBytes',2097152,'definitionFingerprint',v_fp,'receiptHorizonDays',v_receipt_days,'minAvailableRevision',v_min,'legacyFence',jsonb_build_object('readReceiptsAllowed',true,'v2WriteRequiredForV2Scope',true),'storageCapabilities',jsonb_build_object('privateDocuments',true,'contentAddressedKeys',true));
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_apply_sync_batch_v2(p_wire_json text,p_wire_sha256 text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_uid uuid:=auth.uid();v_org uuid;v_body jsonb;v_actual_hash text;v_batch_id text;v_count integer;
 v_existing public.verto_sync_batch_receipts_v2%ROWTYPE;v_member jsonb;v_member_text text;v_member_hash text;v_member_json jsonb;v_mutation jsonb;v_receipt jsonb;v_receipts jsonb:='[]'::jsonb;v_index integer;
 v_mid text;v_agg_type text;v_agg_id text;v_dep text;v_business text;v_blob jsonb;v_blob_text text;v_blob_hash text;v_root record;v_result jsonb;v_old_exists boolean;v_member_existing public.verto_sync_member_receipts_v2%ROWTYPE;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE='28000'; END IF;
 IF p_wire_json IS NULL OR p_wire_sha256 IS NULL OR length(p_wire_json)=0 THEN RAISE EXCEPTION 'VALIDATION: frozen batch text/hash required' USING ERRCODE='22023'; END IF;
 IF octet_length(convert_to(p_wire_json,'UTF8'))>2097152 THEN RAISE EXCEPTION 'CONTRACT_GROUP_TOO_LARGE' USING ERRCODE='22023'; END IF;
 v_actual_hash:=encode(extensions.digest(convert_to(p_wire_json,'UTF8'),'sha256'),'hex');
 IF v_actual_hash<>lower(p_wire_sha256) THEN RAISE EXCEPTION 'FROZEN_BATCH_HASH_MISMATCH' USING ERRCODE='22023'; END IF;
 BEGIN v_body:=p_wire_json::jsonb;EXCEPTION WHEN others THEN RAISE EXCEPTION 'VALIDATION: batch JSON invalid' USING ERRCODE='22023';END;
 IF v_body->>'contractFamily'<>'verto-unified-sync' OR COALESCE((v_body->>'contractVersion')::integer,0)<>2 THEN RAISE EXCEPTION 'CONTRACT_UNSUPPORTED' USING ERRCODE='22023'; END IF;
 BEGIN v_org:=(v_body->>'organizationId')::uuid;EXCEPTION WHEN others THEN RAISE EXCEPTION 'SCOPE_MISMATCH: invalid organizationId' USING ERRCODE='22023';END;
 PERFORM public.verto_validate_sync_org_v2(v_org);
 v_batch_id:=btrim(COALESCE(v_body->>'batchId',''));v_count:=COALESCE((v_body->>'memberCount')::integer,0);
 IF v_batch_id='' OR v_count<1 OR jsonb_typeof(v_body->'members')<>'array' OR jsonb_array_length(v_body->'members')<>v_count OR jsonb_typeof(COALESCE(v_body->'snapshotBlobs','[]'::jsonb))<>'array' THEN RAISE EXCEPTION 'BATCH_MANIFEST_INVALID' USING ERRCODE='22023';END IF;
 IF(SELECT count(DISTINCT(x.value->>'memberOrder')::integer)FROM jsonb_array_elements(v_body->'members')x)<>v_count OR(SELECT min((x.value->>'memberOrder')::integer)FROM jsonb_array_elements(v_body->'members')x)<>0 OR(SELECT max((x.value->>'memberOrder')::integer)FROM jsonb_array_elements(v_body->'members')x)<>v_count-1 THEN RAISE EXCEPTION 'BATCH_MEMBER_ORDER_INVALID' USING ERRCODE='22023';END IF;
 IF(SELECT count(DISTINCT((x.value->>'memberWireJson')::jsonb->>'mutationId'))FROM jsonb_array_elements(v_body->'members')x)<>v_count THEN RAISE EXCEPTION 'BATCH_MEMBER_DUPLICATE' USING ERRCODE='22023';END IF;
 FOR v_blob IN SELECT value FROM jsonb_array_elements(COALESCE(v_body->'snapshotBlobs','[]'::jsonb)) LOOP
  v_blob_text:=v_blob->>'snapshotJson';v_blob_hash:=lower(COALESCE(v_blob->>'sha256',''));
  IF v_blob_text IS NULL OR v_blob_hash!~'^[0-9a-f]{64}$' OR encode(extensions.digest(convert_to(v_blob_text,'UTF8'),'sha256'),'hex')<>v_blob_hash THEN RAISE EXCEPTION 'BATCH_SNAPSHOT_BLOB_HASH_MISMATCH' USING ERRCODE='22023';END IF;
 END LOOP;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('verto-sync-batch:'||v_org::text||':'||v_batch_id,0));
 SELECT*INTO v_existing FROM public.verto_sync_batch_receipts_v2 r WHERE r.organization_id=v_org AND r.batch_id=v_batch_id;
 IF FOUND THEN IF v_existing.request_hash=v_actual_hash THEN RETURN v_existing.response_json;END IF;RETURN jsonb_build_object('contract_family','verto-unified-sync','contract_version',2,'status','REJECTED','batch_id',v_batch_id,'request_hash',v_actual_hash,'validation_code','IDEMPOTENCY_CONFLICT','member_receipts','[]'::jsonb);END IF;
 FOR v_root IN SELECT DISTINCT((m.value->>'memberWireJson')::jsonb->>'aggregateType') aggregate_type,((m.value->>'memberWireJson')::jsonb->>'aggregateId') aggregate_id FROM jsonb_array_elements(v_body->'members')m ORDER BY 1,2 LOOP
  IF v_root.aggregate_type IS NULL OR v_root.aggregate_id IS NULL THEN RAISE EXCEPTION 'BATCH_MEMBER_IDENTITY_MISSING' USING ERRCODE='22023';END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('verto-sync-root:'||v_org::text||':'||v_root.aggregate_type||':'||v_root.aggregate_id,0));
 END LOOP;
 BEGIN
  FOR v_member,v_index IN SELECT value,(ordinality-1)::integer FROM jsonb_array_elements(v_body->'members')WITH ORDINALITY ORDER BY ordinality LOOP
   IF COALESCE((v_member->>'memberOrder')::integer,-1)<>v_index THEN RAISE EXCEPTION 'BATCH_MEMBER_ORDER_INVALID';END IF;
   v_member_text:=v_member->>'memberWireJson';v_member_hash:=lower(COALESCE(v_member->>'memberWireSha256',''));
   IF v_member_text IS NULL OR v_member_hash!~'^[0-9a-f]{64}$' OR encode(extensions.digest(convert_to(v_member_text,'UTF8'),'sha256'),'hex')<>v_member_hash THEN RAISE EXCEPTION 'BATCH_MEMBER_HASH_MISMATCH';END IF;
   v_member_json:=v_member_text::jsonb;
   IF v_member_json->>'contractFamily'<>'verto-unified-sync' OR COALESCE((v_member_json->>'contractVersion')::integer,0)<>2 OR(v_member_json->>'organizationId')::uuid<>v_org THEN RAISE EXCEPTION 'CONTRACT_OR_SCOPE_MISMATCH';END IF;
   v_mid:=btrim(COALESCE(v_member_json->>'mutationId',''));v_agg_type:=btrim(COALESCE(v_member_json->>'aggregateType',''));v_agg_id:=btrim(COALESCE(v_member_json->>'aggregateId',''));
   IF v_mid='' OR v_agg_type='' OR v_agg_id='' THEN RAISE EXCEPTION 'BATCH_MEMBER_IDENTITY_MISSING';END IF;
   IF NULLIF(v_member_json->>'commandBatchId','')IS NOT NULL AND v_member_json->>'commandBatchId'<>v_batch_id THEN RAISE EXCEPTION 'BATCH_MEMBERSHIP_MISMATCH';END IF;
   v_dep:=NULLIF(v_member_json->>'dependsOnMutationId','');
   IF v_dep IS NOT NULL AND NOT EXISTS(SELECT 1 FROM jsonb_array_elements(v_body->'members')z WHERE((z.value->>'memberWireJson')::jsonb->>'mutationId')=v_dep)AND NOT EXISTS(SELECT 1 FROM public.verto_sync_member_receipts_v2 rr WHERE rr.organization_id=v_org AND rr.mutation_id=v_dep AND rr.response_json->>'status'IN('APPLIED','REPLAYED','NO_OP'))THEN RAISE EXCEPTION 'BATCH_DEPENDENCY_MISSING';END IF;
   IF NULLIF(v_member_json#>>'{payload,snapshotHash}','')IS NOT NULL AND NOT EXISTS(SELECT 1 FROM jsonb_array_elements(COALESCE(v_body->'snapshotBlobs','[]'::jsonb))b WHERE b.value->>'sha256'=v_member_json#>>'{payload,snapshotHash}')THEN RAISE EXCEPTION 'BATCH_SNAPSHOT_BLOB_MISSING';END IF;
   SELECT*INTO v_member_existing FROM public.verto_sync_member_receipts_v2 r WHERE r.organization_id=v_org AND r.mutation_id=v_mid;
   IF FOUND THEN IF v_member_existing.request_hash<>v_member_hash OR v_member_existing.batch_id<>v_batch_id OR v_member_existing.member_order<>v_index THEN RAISE EXCEPTION 'IDEMPOTENCY_CONFLICT';END IF;RAISE EXCEPTION 'OUTCOME_UNKNOWN: member receipt exists without batch receipt';END IF;
   SELECT EXISTS(SELECT 1 FROM public.verto_sync_receipts r WHERE r.organization_id=v_org AND r.mutation_id=v_mid)INTO v_old_exists;
   IF v_old_exists THEN RAISE EXCEPTION 'LEGACY_UNPROVABLE: old receipt lacks exact V2 bytes';END IF;
   v_mutation:=jsonb_build_object('organization_id',v_member_json->>'organizationId','mutation_id',v_mid,'aggregate_type',v_agg_type,'aggregate_id',v_agg_id,'operation_type',v_member_json->>'operationType','base_version',v_member_json->'baseVersion','payload_version',v_member_json->'payloadVersion','payload',COALESCE(v_member_json->'payload','{}'::jsonb),'command_batch_id',v_member_json->'commandBatchId','command_order',v_member_json->'commandOrder','depends_on_mutation_id',v_member_json->'dependsOnMutationId');
   v_receipt:=public.verto_apply_sync_mutation(v_mutation);
   IF COALESCE(v_receipt->>'status','')NOT IN('APPLIED','REPLAYED','NO_OP')THEN RAISE EXCEPTION 'BATCH_MEMBER_REJECTED:%:%',v_mid,COALESCE(v_receipt->>'validation_code',v_receipt->>'conflict_code','UNKNOWN');END IF;
   v_receipt:=v_receipt||jsonb_build_object('contract_family','verto-unified-sync','contract_version',2,'request_hash',v_member_hash);
   v_business:=COALESCE(NULLIF(v_member_json#>>'{payload,businessIdentity}',''),v_agg_id);
   INSERT INTO public.verto_sync_member_receipts_v2(organization_id,mutation_id,request_hash,batch_id,member_order,business_identity,response_json)VALUES(v_org,v_mid,v_member_hash,v_batch_id,v_index,v_business,v_receipt);
   v_receipts:=v_receipts||jsonb_build_array(v_receipt);
  END LOOP;
 EXCEPTION WHEN others THEN RETURN jsonb_build_object('contract_family','verto-unified-sync','contract_version',2,'status','REJECTED','batch_id',v_batch_id,'request_hash',v_actual_hash,'validation_code',split_part(SQLERRM,':',1),'detail',left(SQLERRM,180),'member_receipts','[]'::jsonb);END;
 v_result:=jsonb_build_object('contract_family','verto-unified-sync','contract_version',2,'status','APPLIED','batch_id',v_batch_id,'request_hash',v_actual_hash,'member_receipts',v_receipts);
 INSERT INTO public.verto_sync_batch_receipts_v2(organization_id,batch_id,request_hash,member_count,response_json)VALUES(v_org,v_batch_id,v_actual_hash,v_count,v_result);
 RETURN v_result;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_get_sync_receipts_v2(p_organization_id uuid,p_mutation_ids text[] DEFAULT NULL,p_business_identities text[] DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_uid uuid;v_days integer;v_items jsonb;
BEGIN
 v_uid:=public.verto_validate_sync_org_v2(p_organization_id);
 SELECT receipt_retention_days INTO v_days FROM public.verto_sync_contract WHERE contract_family='verto-unified-sync' AND contract_version=2;
 WITH wanted AS(SELECT DISTINCT x mutation_id FROM unnest(COALESCE(p_mutation_ids,ARRAY[]::text[]))x WHERE x IS NOT NULL AND btrim(x)<>''),rows AS(
  SELECT w.mutation_id,CASE WHEN r.mutation_id IS NOT NULL THEN jsonb_build_object('outcome','FOUND','mutationId',w.mutation_id,'receipt',r.response_json)WHEN old.mutation_id IS NOT NULL THEN jsonb_build_object('outcome','LEGACY_UNPROVABLE','mutationId',w.mutation_id)ELSE jsonb_build_object('outcome','NOT_FOUND','mutationId',w.mutation_id)END item
  FROM wanted w LEFT JOIN public.verto_sync_member_receipts_v2 r ON r.organization_id=p_organization_id AND r.mutation_id=w.mutation_id LEFT JOIN public.verto_sync_receipts old ON old.organization_id=p_organization_id AND old.mutation_id=w.mutation_id)
 SELECT COALESCE(jsonb_agg(item ORDER BY mutation_id),'[]'::jsonb)INTO v_items FROM rows;
 RETURN jsonb_build_object('contractFamily','verto-unified-sync','contractVersion',2,'organizationId',p_organization_id,'receiptHorizonCompleteSinceEpochMillis',floor(extract(epoch from(now()-make_interval(days=>COALESCE(v_days,0))))*1000)::bigint,'items',v_items,'businessIdentityQueries',COALESCE(to_jsonb(p_business_identities),'[]'::jsonb));
END $fn$;

REVOKE ALL ON FUNCTION public.verto_validate_sync_org_v2(uuid) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_validate_sync_scope_v2(uuid) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_resolve_sync_scope_v2(uuid) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_resolve_sync_scope_v2() FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_sync_repair_capabilities_v1(uuid) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_apply_sync_batch_v2(text,text) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_get_sync_receipts_v2(uuid,text[],text[]) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.verto_resolve_sync_scope_v2(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_resolve_sync_scope_v2() TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_sync_repair_capabilities_v1(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_apply_sync_batch_v2(text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_get_sync_receipts_v2(uuid,text[],text[]) TO authenticated;
