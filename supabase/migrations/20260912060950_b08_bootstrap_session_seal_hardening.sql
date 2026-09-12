ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS coverage_digest_sha256 text;
ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS session_seal_sha256 text;

CREATE OR REPLACE FUNCTION public.verto_begin_sync_bootstrap_v2(p_scope_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_org uuid; v_uid uuid; v_session uuid:=gen_random_uuid(); v_baseline bigint; v_cursor text;
  v_count bigint; v_token uuid; v_ttl integer; v_digest text; v_coverage jsonb; v_digest_input text;
  v_coverage_digest text; v_seal text; v_scope_version integer;
BEGIN
  SELECT v.organization_id,v.principal_id INTO v_org,v_uid FROM public.verto_validate_sync_scope_v2(p_scope_id)v;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('verto-sync-revision:'||v_org::text,0));
  SELECT COALESCE(max(cl.revision),0) INTO v_baseline
  FROM public.verto_sync_change_log cl
  WHERE cl.organization_id=v_org
    AND NOT EXISTS(SELECT 1 FROM public.verto_sync_change_suppressions q WHERE q.revision=cl.revision)
    AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission);
  v_cursor:=public.verto_encode_sync_cursor_v2(p_scope_id,v_baseline);
  v_coverage:=public.verto_sync_v2_coverage();
  v_coverage_digest:=encode(extensions.digest(convert_to(public.verto_jsonb_canonical_text_v2(v_coverage),'UTF8'),'sha256'),'hex');
  SELECT bootstrap_session_ttl_seconds,scope_definition_version INTO v_ttl,v_scope_version
  FROM public.verto_sync_contract WHERE contract_family='verto-unified-sync' AND contract_version=2;
  INSERT INTO public.verto_sync_bootstrap_sessions(
    bootstrap_session_id,scope_id,organization_id,principal_id,contract_family,contract_version,
    scope_definition_version,baseline_revision,baseline_cursor,snapshot_row_count,expires_at,state,
    coverage_aggregate_types,high_watermark,delta_token,coverage_digest_sha256
  ) VALUES(
    v_session,p_scope_id,v_org,v_uid,'verto-unified-sync',2,v_scope_version,v_baseline,v_cursor,0,
    now()+make_interval(secs=>v_ttl),'IN_PROGRESS',v_coverage,v_baseline,v_cursor,v_coverage_digest
  );
  INSERT INTO public.verto_sync_bootstrap_rows(
    bootstrap_session_id,ordinal,aggregate_type,aggregate_id,entity_version,payload_version,payload,partition_key
  )
  SELECT v_session,row_number()OVER(ORDER BY s.aggregate_type,s.partition_key,s.aggregate_id),s.aggregate_type,s.aggregate_id,
         s.entity_version,s.payload_version,s.payload,s.partition_key
  FROM public.verto_sync_snapshot_state s
  WHERE s.organization_id=v_org AND s.updated_revision<=v_baseline
    AND public.verto_sync_row_visible_to_current_principal(s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission)
  ORDER BY s.aggregate_type,s.partition_key,s.aggregate_id;
  GET DIAGNOSTICS v_count=ROW_COUNT;
  SELECT string_agg(
    r.ordinal::text||'|'||encode(extensions.digest(convert_to(
      r.aggregate_type||'|'||r.aggregate_id||'|'||COALESCE(r.entity_version::text,'∅')||'|'||
      r.payload_version::text||'|'||r.partition_key||'|0|'||public.verto_jsonb_canonical_text_v2(r.payload),
      'UTF8'),'sha256'),'hex')||'|0',E'\\n' ORDER BY r.ordinal
  ) INTO v_digest_input
  FROM public.verto_sync_bootstrap_rows r WHERE r.bootstrap_session_id=v_session;
  v_digest:=encode(extensions.digest(convert_to(COALESCE(v_digest_input,''),'UTF8'),'sha256'),'hex');
  v_seal:=encode(extensions.digest(convert_to(
    p_scope_id::text||'|'||v_org::text||'|'||v_uid::text||'|'||v_scope_version::text||'|'||v_baseline::text||'|'||
    v_cursor||'|'||v_count::text||'|'||v_digest||'|'||v_coverage_digest,
    'UTF8'),'sha256'),'hex');
  UPDATE public.verto_sync_bootstrap_sessions SET
    snapshot_row_count=v_count,snapshot_digest_sha256=v_digest,coverage_digest_sha256=v_coverage_digest,
    session_seal_sha256=v_seal,state='READY',seal_created_at=now()
  WHERE bootstrap_session_id=v_session;
  INSERT INTO public.verto_sync_bootstrap_page_tokens(bootstrap_session_id,after_ordinal)
  VALUES(v_session,0) RETURNING page_token INTO v_token;
  RETURN jsonb_build_object(
    'contract_family','verto-unified-sync','contract_version',2,'scope_id',p_scope_id,
    'bootstrap_session_id',v_session,'baseline_revision',v_baseline,'baseline_cursor',v_cursor,
    'snapshot_row_count',v_count,'snapshot_digest_sha256',v_digest,'coverage_aggregate_types',v_coverage,
    'coverage_digest_sha256',v_coverage_digest,'high_watermark',v_baseline,'delta_token',v_cursor,
    'session_seal_sha256',v_seal,'first_page_token',v_token::text,
    'expires_at_epoch_millis',floor(extract(epoch from(now()+make_interval(secs=>v_ttl)))*1000)::bigint
  );
END $function$;

CREATE OR REPLACE FUNCTION public.verto_pull_sync_bootstrap_page_v2(
  p_bootstrap_session_id uuid, p_page_token text, p_limit integer DEFAULT 1000
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_session public.verto_sync_bootstrap_sessions%ROWTYPE; v_token uuid; v_after bigint;
  v_limit integer:=COALESCE(p_limit,1000); v_rows jsonb:='[]'::jsonb; v_last bigint;
  v_has_more boolean; v_next_token uuid; v_first_bytes bigint;
BEGIN
  IF v_limit<1 OR v_limit>1000 THEN RAISE EXCEPTION 'VALIDATION: bootstrap p_limit must be 1..1000' USING ERRCODE='22023';END IF;
  BEGIN v_token:=p_page_token::uuid; EXCEPTION WHEN invalid_text_representation THEN
    RAISE EXCEPTION 'VALIDATION: malformed bootstrap page token' USING ERRCODE='22023'; END;
  SELECT * INTO v_session FROM public.verto_sync_bootstrap_sessions s WHERE s.bootstrap_session_id=p_bootstrap_session_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'BOOTSTRAP_RESTART_REQUIRED: session not found' USING ERRCODE='22023';END IF;
  PERFORM 1 FROM public.verto_validate_sync_scope_v2(v_session.scope_id);
  IF v_session.contract_version<>2 OR v_session.principal_id<>auth.uid() OR v_session.expires_at<=now()
     OR v_session.state NOT IN('READY','IN_PROGRESS','COMPLETED')
     OR v_session.snapshot_digest_sha256 IS NULL OR v_session.coverage_aggregate_types IS NULL
     OR v_session.coverage_digest_sha256 IS NULL OR v_session.delta_token IS NULL OR v_session.session_seal_sha256 IS NULL
  THEN RAISE EXCEPTION 'BOOTSTRAP_RESTART_REQUIRED: expired, invalid or unsealed session' USING ERRCODE='22023';END IF;
  SELECT t.after_ordinal INTO v_after FROM public.verto_sync_bootstrap_page_tokens t
   WHERE t.page_token=v_token AND t.bootstrap_session_id=p_bootstrap_session_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'SCOPE_MISMATCH: bootstrap token does not belong to session' USING ERRCODE='22023';END IF;
  SELECT octet_length(convert_to(public.verto_jsonb_canonical_text_v2(r.payload),'UTF8'))::bigint INTO v_first_bytes
  FROM public.verto_sync_bootstrap_rows r WHERE r.bootstrap_session_id=p_bootstrap_session_id AND r.ordinal>v_after
  ORDER BY r.ordinal LIMIT 1;
  IF COALESCE(v_first_bytes,0)>1048576 THEN RAISE EXCEPTION 'CONTRACT_PAYLOAD_TOO_LARGE: bootstrap row exceeds 1 MiB' USING ERRCODE='22023';END IF;
  WITH candidates AS(
    SELECT r.*,octet_length(convert_to(public.verto_jsonb_canonical_text_v2(jsonb_build_object(
      'ordinal',r.ordinal,'aggregateType',r.aggregate_type,'aggregateId',r.aggregate_id,
      'entityVersion',r.entity_version,'payloadVersion',r.payload_version,'payload',r.payload,
      'partitionKey',r.partition_key,'is_tombstone',false)),'UTF8'))::bigint row_bytes
    FROM public.verto_sync_bootstrap_rows r
    WHERE r.bootstrap_session_id=p_bootstrap_session_id AND r.ordinal>v_after ORDER BY r.ordinal
  ), ranked AS(
    SELECT c.*,row_number()OVER(ORDER BY ordinal) rn,
      sum(row_bytes)OVER(ORDER BY ordinal ROWS UNBOUNDED PRECEDING) cumulative_bytes FROM candidates c
  ), selected AS(
    SELECT * FROM ranked WHERE rn<=v_limit AND cumulative_bytes<=2097152
  )
  SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'ordinal',ordinal,'aggregateType',aggregate_type,'aggregateId',aggregate_id,'entityVersion',entity_version,
    'payloadVersion',payload_version,'payload',payload,'partitionKey',partition_key,'is_tombstone',false
  ) ORDER BY ordinal),'[]'::jsonb),max(ordinal) INTO v_rows,v_last FROM selected;
  v_last:=COALESCE(v_last,v_after);
  SELECT EXISTS(SELECT 1 FROM public.verto_sync_bootstrap_rows r
    WHERE r.bootstrap_session_id=p_bootstrap_session_id AND r.ordinal>v_last) INTO v_has_more;
  IF v_has_more THEN
    INSERT INTO public.verto_sync_bootstrap_page_tokens(bootstrap_session_id,after_ordinal)
    VALUES(p_bootstrap_session_id,v_last)
    ON CONFLICT(bootstrap_session_id,after_ordinal) DO UPDATE SET after_ordinal=EXCLUDED.after_ordinal
    RETURNING page_token INTO v_next_token;
  ELSE
    UPDATE public.verto_sync_bootstrap_sessions SET state='COMPLETED',completed_at=COALESCE(completed_at,now())
    WHERE bootstrap_session_id=p_bootstrap_session_id;
  END IF;
  RETURN jsonb_build_object(
    'contract_family','verto-unified-sync','contract_version',2,'scope_id',v_session.scope_id,
    'bootstrap_session_id',p_bootstrap_session_id,'baseline_cursor',v_session.baseline_cursor,
    'rows',v_rows,'has_more',v_has_more,'next_page_token',CASE WHEN v_has_more THEN v_next_token::text ELSE NULL END,
    'snapshot_complete',NOT v_has_more,'snapshot_row_count',v_session.snapshot_row_count,
    'snapshot_digest_sha256',v_session.snapshot_digest_sha256,'coverage_aggregate_types',v_session.coverage_aggregate_types,
    'coverage_digest_sha256',v_session.coverage_digest_sha256,'high_watermark',v_session.high_watermark,
    'delta_token',v_session.delta_token,'session_seal_sha256',v_session.session_seal_sha256
  );
END $function$;

REVOKE ALL ON FUNCTION public.verto_begin_sync_bootstrap_v2(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.verto_begin_sync_bootstrap_v2(uuid) TO authenticated;
REVOKE ALL ON FUNCTION public.verto_pull_sync_bootstrap_page_v2(uuid,text,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.verto_pull_sync_bootstrap_page_v2(uuid,text,integer) TO authenticated;