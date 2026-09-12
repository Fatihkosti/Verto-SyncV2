-- B08: legal V2 read, delta group manifests, opaque V2 cursors and sealed bootstrap.

ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS snapshot_digest_sha256 text;
ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS coverage_aggregate_types jsonb;
ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS high_watermark bigint;
ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS delta_token text;
ALTER TABLE public.verto_sync_bootstrap_sessions ADD COLUMN IF NOT EXISTS seal_created_at timestamptz;

CREATE OR REPLACE FUNCTION public.verto_jsonb_canonical_text_v2(p_value jsonb)
RETURNS text LANGUAGE plpgsql IMMUTABLE SET search_path TO 'pg_catalog' AS $fn$
DECLARE v_kind text;
BEGIN
  IF p_value IS NULL THEN RETURN 'null'; END IF;
  v_kind:=jsonb_typeof(p_value);
  IF v_kind='object' THEN
    RETURN '{'||COALESCE((SELECT string_agg(to_jsonb(e.key)::text||':'||public.verto_jsonb_canonical_text_v2(e.value),',' ORDER BY e.key) FROM jsonb_each(p_value)e),'')||'}';
  ELSIF v_kind='array' THEN
    RETURN '['||COALESCE((SELECT string_agg(public.verto_jsonb_canonical_text_v2(a.value),',' ORDER BY a.ordinality) FROM jsonb_array_elements(p_value) WITH ORDINALITY a(value,ordinality)),'')||']';
  ELSE
    RETURN p_value::text;
  END IF;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_sync_v2_coverage()
RETURNS jsonb LANGUAGE sql IMMUTABLE SET search_path TO 'pg_catalog' AS $fn$
  SELECT jsonb_agg(v.id ORDER BY v.id) FROM (VALUES
   ('BUDGET'),('CASH_MOVEMENT'),('CASH_RECONCILIATION'),('CASH_REGISTER'),('CATEGORY'),('CLIENT_CREDIT'),
   ('COMMISSION_PAYMENT'),('COST_ALLOCATION'),('CUSTOMER_PROFILE'),('EDUCATIONAL_CONTENT'),('EXPENSE'),
   ('GOODS_RECEIPT'),('INVENTORY_COST_REVISION'),('INVENTORY_ITEM'),('INVENTORY_MOVEMENT'),('INVENTORY_UNIT'),
   ('INVOICE'),('ITEM_CATEGORY'),('NOTE'),('NOTIFICATION'),('OPTIMAL_FOLLOW_UP'),('OPTIMAL_MAINTENANCE'),
   ('OPTIMAL_VEHICLE'),('ORGANIZATION_SETTINGS'),('PARTY_IDENTITY'),('PARTY_ROLE'),('PAYMENT'),('PRICE_LIST'),
   ('PURCHASE_MATCH'),('PURCHASE_ORDER'),('PURCHASE_PAYMENT_OVERRIDE'),('REMINDER'),('SHIPMENT'),
   ('SUPPLIER_PROFILE'),('TEAM_OBSERVATION')) v(id)
$fn$;

CREATE OR REPLACE FUNCTION public.verto_touched_keys_v2(p_aggregate_type text,p_aggregate_id text,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql IMMUTABLE SET search_path TO 'pg_catalog' AS $fn$
DECLARE v_s jsonb:=p_payload->'financialSnapshot'; v_p jsonb:=COALESCE(p_payload->'materialization',p_payload); v_result jsonb;
BEGIN
 WITH raw(type,id) AS (
   SELECT CASE WHEN p_aggregate_type='PAYMENT' THEN 'INVOICE' ELSE p_aggregate_type END,p_aggregate_id
   UNION ALL SELECT 'INVOICE',v_s->>'invoiceId' WHERE v_s IS NOT NULL
   UNION ALL SELECT 'INVOICE_ITEM',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'items','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'INVOICE_DUE_INSTALLMENT',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'dueInstallments','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'PAYMENT',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'payments','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'PAYMENT_ALLOCATION',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'paymentAllocations','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'REALIZED_FX_EVENT',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'realizedFxEvents','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'INVOICE_RETURN',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'returnDocuments','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'INVOICE_RETURN_LINE',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'returnLines','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'INVOICE_RETURN_PAYMENT_ALLOCATION',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'returnPaymentAllocations','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT x->>'entityType',x->>'id' FROM jsonb_array_elements(COALESCE(v_s->'explicitTombstones','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT x->>'factType',x->>'factId' FROM jsonb_array_elements(COALESCE(v_s->'effectReferences','[]'::jsonb))x WHERE v_s IS NOT NULL
   UNION ALL SELECT 'CASH_DENOMINATION',x->>'id' FROM jsonb_array_elements(COALESCE(v_p->'denominations','[]'::jsonb))x WHERE p_aggregate_type='CASH_RECONCILIATION'
   UNION ALL SELECT 'PURCHASE_ORDER_LINE',x->>'id' FROM jsonb_array_elements(COALESCE(v_p->'lines','[]'::jsonb))x WHERE p_aggregate_type='PURCHASE_ORDER'
   UNION ALL SELECT 'GOODS_RECEIPT_LINE',x->>'id' FROM jsonb_array_elements(COALESCE(v_p->'lines','[]'::jsonb))x WHERE p_aggregate_type='GOODS_RECEIPT'
   UNION ALL SELECT 'PURCHASE_MATCH_LINE',x->>'id' FROM jsonb_array_elements(COALESCE(v_p->'lines','[]'::jsonb))x WHERE p_aggregate_type='PURCHASE_MATCH'
   UNION ALL SELECT 'PRICE_LIST_ITEM',p_aggregate_id||':'||btrim(x) FROM unnest(string_to_array(COALESCE(v_p->>'itemIds',''),','))x WHERE p_aggregate_type='PRICE_LIST' AND btrim(x)<>''
   UNION ALL SELECT p_aggregate_type,COALESCE(NULLIF(v_p->>'partyId',''),p_aggregate_id) WHERE p_aggregate_type IN('CUSTOMER_PROFILE','SUPPLIER_PROFILE')
   UNION ALL SELECT 'PARTY_IDENTITY',COALESCE(NULLIF(v_p->>'partyId',''),p_aggregate_id) WHERE p_aggregate_type IN('CUSTOMER_PROFILE','SUPPLIER_PROFILE','PARTY_ROLE')
   UNION ALL SELECT 'CASH_REGISTER','main' WHERE p_aggregate_type='CASH_REGISTER'
 ), dedup AS (SELECT DISTINCT type,id FROM raw WHERE type IS NOT NULL AND btrim(type)<>'' AND id IS NOT NULL AND btrim(id)<>'')
 SELECT COALESCE(jsonb_agg(jsonb_build_object('type',type,'id',id) ORDER BY type,id),'[]'::jsonb) INTO v_result FROM dedup;
 RETURN v_result;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_encode_sync_cursor_v2(p_scope_id uuid,p_revision bigint)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_token uuid;
BEGIN
 PERFORM 1 FROM public.verto_validate_sync_scope_v2(p_scope_id);
 IF p_revision IS NULL OR p_revision<0 THEN RAISE EXCEPTION 'VALIDATION: cursor revision must be non-negative' USING ERRCODE='22023';END IF;
 INSERT INTO public.verto_sync_cursor_tokens(scope_id,revision)VALUES(p_scope_id,p_revision)
 ON CONFLICT(scope_id,revision)DO UPDATE SET revision=EXCLUDED.revision RETURNING cursor_token INTO v_token;
 RETURN v_token::text;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_decode_sync_cursor_v2(p_scope_id uuid,p_cursor text)
RETURNS bigint LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_token uuid;v_scope uuid;v_revision bigint;
BEGIN
 PERFORM 1 FROM public.verto_validate_sync_scope_v2(p_scope_id);
 IF p_cursor IS NULL OR btrim(p_cursor)='' THEN RAISE EXCEPTION 'VALIDATION: cursor must be nonblank' USING ERRCODE='22023';END IF;
 BEGIN v_token:=p_cursor::uuid;EXCEPTION WHEN invalid_text_representation THEN RAISE EXCEPTION 'VALIDATION: malformed cursor' USING ERRCODE='22023';END;
 SELECT t.scope_id,t.revision INTO v_scope,v_revision FROM public.verto_sync_cursor_tokens t WHERE t.cursor_token=v_token;
 IF NOT FOUND THEN RAISE EXCEPTION 'VALIDATION: unknown or tampered cursor' USING ERRCODE='22023';END IF;
 IF v_scope<>p_scope_id THEN RAISE EXCEPTION 'SCOPE_MISMATCH: cursor belongs to another scope' USING ERRCODE='22023';END IF;
 RETURN v_revision;
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_read_sync_aggregate_v2(p_scope_id uuid,p_aggregate_type text,p_aggregate_id text)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_org uuid;v_row public.verto_sync_snapshot_state%ROWTYPE;
BEGIN
 SELECT v.organization_id INTO v_org FROM public.verto_validate_sync_scope_v2(p_scope_id)v LIMIT 1;
 SELECT * INTO v_row FROM public.verto_sync_snapshot_state s WHERE s.organization_id=v_org AND s.aggregate_type=p_aggregate_type AND s.aggregate_id=p_aggregate_id AND public.verto_sync_row_visible_to_current_principal(s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission);
 IF NOT FOUND THEN RETURN jsonb_build_object('contractFamily','verto-unified-sync','contractVersion',2,'scopeId',p_scope_id,'status','NOT_FOUND','aggregateType',p_aggregate_type,'aggregateId',p_aggregate_id);END IF;
 RETURN jsonb_build_object('contractFamily','verto-unified-sync','contractVersion',2,'scopeId',p_scope_id,'status','FOUND','aggregateType',v_row.aggregate_type,'aggregateId',v_row.aggregate_id,'entityVersion',v_row.entity_version,'payloadVersion',v_row.payload_version,'payload',v_row.payload,'partitionKey',v_row.partition_key,'serverRevision',v_row.updated_revision,'recordedAtEpochMillis',floor(extract(epoch from v_row.updated_at)*1000)::bigint);
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_pull_sync_changes_v2("scopeId" uuid,"cursorToken" text,"softLimit" integer DEFAULT 1000,"maxGroupBytes" bigint DEFAULT 2097152)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_org uuid;v_principal uuid;v_after bigint;v_min bigint;v_high bigint;v_tx record;v_group_changes jsonb;v_keys jsonb;v_deps jsonb;v_body jsonb;v_body_text text;v_group_hash text;v_group_bytes bigint;v_group_rows integer;v_total_rows integer:=0;v_page_bytes bigint:=0;v_changes jsonb:='[]'::jsonb;v_groups jsonb:='[]'::jsonb;v_last bigint;v_has_more boolean;v_next text;v_first boolean:=true;v_scope jsonb;
BEGIN
 SELECT v.organization_id,v.principal_id INTO v_org,v_principal FROM public.verto_validate_sync_scope_v2("scopeId")v LIMIT 1;
 IF "softLimit"<1 OR "softLimit">1000 THEN RAISE EXCEPTION 'VALIDATION: softLimit must be 1..1000' USING ERRCODE='22023';END IF;
 IF "maxGroupBytes"<>2097152 THEN RAISE EXCEPTION 'CONTRACT_UNSUPPORTED: maxGroupBytes must equal 2097152' USING ERRCODE='22023';END IF;
 v_after:=public.verto_decode_sync_cursor_v2("scopeId","cursorToken");v_last:=v_after;
 SELECT c.min_available_revision INTO v_min FROM public.verto_sync_contract c WHERE c.contract_family='verto-unified-sync' AND c.contract_version=2;
 IF v_after<v_min THEN RAISE EXCEPTION 'CURSOR_EXPIRED: bootstrap required' USING ERRCODE='22023';END IF;
 SELECT COALESCE(max(cl.revision),v_after) INTO v_high FROM public.verto_sync_change_log cl WHERE cl.organization_id=v_org AND NOT EXISTS(SELECT 1 FROM public.verto_sync_change_suppressions q WHERE q.revision=cl.revision) AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission);
 FOR v_tx IN
  SELECT cl.transaction_id,min(cl.revision) first_revision,max(cl.revision) last_revision,count(*)::integer member_count
  FROM public.verto_sync_change_log cl WHERE cl.organization_id=v_org AND cl.revision>v_after AND NOT EXISTS(SELECT 1 FROM public.verto_sync_change_suppressions q WHERE q.revision=cl.revision) AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission)
  GROUP BY cl.transaction_id ORDER BY min(cl.revision)
 LOOP
  SELECT jsonb_agg(jsonb_build_object('revision',q.revision,'organizationId',v_org::text,'syncScopeId',"scopeId"::text,'aggregateType',q.aggregate_type,'aggregateId',q.aggregate_id,'operationType',q.operation_type,'entityVersion',q.entity_version,'payloadVersion',q.payload_version,'payload',q.payload,'originMutationId',q.origin_mutation_id,'transactionId',q.transaction_id,'transactionOrder',q.tx_order,'transactionSize',v_tx.member_count,'deletedAtEpochMillis',CASE WHEN q.deleted_at IS NULL THEN NULL ELSE floor(extract(epoch from q.deleted_at)*1000)::bigint END,'changedAtEpochMillis',floor(extract(epoch from q.changed_at)*1000)::bigint) ORDER BY q.revision)
  INTO v_group_changes FROM(
   SELECT cl.*,row_number()OVER(ORDER BY cl.revision)::integer-1 tx_order FROM public.verto_sync_change_log cl
   WHERE cl.organization_id=v_org AND cl.transaction_id=v_tx.transaction_id AND cl.revision>v_after AND NOT EXISTS(SELECT 1 FROM public.verto_sync_change_suppressions z WHERE z.revision=cl.revision) AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission)
  )q;
  SELECT COALESCE(jsonb_agg(k.key ORDER BY k.key->>'type',k.key->>'id'),'[]'::jsonb) INTO v_keys FROM(
   SELECT DISTINCT key FROM public.verto_sync_change_log cl CROSS JOIN LATERAL jsonb_array_elements(public.verto_touched_keys_v2(cl.aggregate_type,cl.aggregate_id,cl.payload)) key
   WHERE cl.organization_id=v_org AND cl.transaction_id=v_tx.transaction_id AND cl.revision>v_after AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission)
  )k;
  SELECT COALESCE(jsonb_agg(DISTINCT d ORDER BY d),'[]'::jsonb) INTO v_deps FROM(
   SELECT dep#>>'{}' d FROM public.verto_sync_change_log cl CROSS JOIN LATERAL jsonb_array_elements_text(CASE WHEN jsonb_typeof(cl.payload->'dependsOnTransactionIds')='array' THEN cl.payload->'dependsOnTransactionIds' ELSE '[]'::jsonb END) dep
   WHERE cl.organization_id=v_org AND cl.transaction_id=v_tx.transaction_id AND cl.revision>v_after
  )x WHERE d IS NOT NULL AND btrim(d)<>'' AND d<>v_tx.transaction_id;
  v_body:=jsonb_build_object('transactionId',v_tx.transaction_id,'changes',v_group_changes,'touchedKeys',v_keys,'dependsOnTransactionIds',v_deps);
  v_body_text:=public.verto_jsonb_canonical_text_v2(v_body);v_group_bytes:=octet_length(convert_to(v_body_text,'UTF8'));v_group_hash:=encode(extensions.digest(convert_to(v_body_text,'UTF8'),'sha256'),'hex');v_group_rows:=v_tx.member_count;
  IF v_group_bytes>"maxGroupBytes" THEN RAISE EXCEPTION 'CONTRACT_GROUP_TOO_LARGE: transaction group exceeds 2 MiB' USING ERRCODE='22023';END IF;
  IF NOT v_first AND(v_total_rows+v_group_rows>"softLimit" OR v_page_bytes+v_group_bytes>69206016)THEN EXIT;END IF;
  v_changes:=v_changes||v_group_changes;
  v_groups:=v_groups||jsonb_build_array(jsonb_build_object('transactionId',v_tx.transaction_id,'memberCount',v_group_rows,'firstRevision',v_tx.first_revision,'lastRevision',v_tx.last_revision,'contentSha256',v_group_hash,'serializedBytes',v_group_bytes,'touchedKeys',v_keys,'dependsOnTransactionIds',v_deps));
  v_total_rows:=v_total_rows+v_group_rows;v_page_bytes:=v_page_bytes+v_group_bytes;v_last:=v_tx.last_revision;v_first:=false;
 END LOOP;
 SELECT EXISTS(SELECT 1 FROM public.verto_sync_change_log cl WHERE cl.organization_id=v_org AND cl.revision>v_last AND NOT EXISTS(SELECT 1 FROM public.verto_sync_change_suppressions q WHERE q.revision=cl.revision) AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission))INTO v_has_more;
 IF v_last=v_after THEN v_next:="cursorToken";v_has_more:=false;ELSE v_next:=public.verto_encode_sync_cursor_v2("scopeId",v_last);END IF;
 v_scope:=jsonb_build_object('organizationId',v_org::text,'syncPrincipalId',v_principal::text,'scopeId',"scopeId"::text,'contractFamily','verto-unified-sync','contractVersion',2,'scopeDefinitionVersion',(SELECT scope_definition_version FROM public.verto_sync_contract WHERE contract_family='verto-unified-sync' AND contract_version=2));
 RETURN jsonb_build_object('changes',v_changes,'nextCursor',v_next,'hasMore',v_has_more,'minAvailableRevision',v_min,'contractFamily','verto-unified-sync','contractVersion',2,'pageHighWatermark',v_high,'endsAtTransactionBoundary',true,'scopeIdentity',v_scope,'coverage','GLOBAL_SCOPE','advancesGlobalCursor',true,'fromCursor',"cursorToken",'coveredThroughRevision',v_last,'groups',v_groups);
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_begin_sync_bootstrap_v2(p_scope_id uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_org uuid;v_uid uuid;v_session uuid:=gen_random_uuid();v_baseline bigint;v_cursor text;v_count bigint;v_token uuid;v_ttl integer;v_digest text;v_coverage jsonb;v_digest_input text;
BEGIN
 SELECT v.organization_id,v.principal_id INTO v_org,v_uid FROM public.verto_validate_sync_scope_v2(p_scope_id)v;
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('verto-sync-revision:'||v_org::text,0));
 SELECT COALESCE(max(cl.revision),0)INTO v_baseline FROM public.verto_sync_change_log cl WHERE cl.organization_id=v_org AND NOT EXISTS(SELECT 1 FROM public.verto_sync_change_suppressions q WHERE q.revision=cl.revision) AND public.verto_sync_row_visible_to_current_principal(cl.aggregate_type,cl.payload,cl.visibility_principal_id,cl.required_permission);
 v_cursor:=public.verto_encode_sync_cursor_v2(p_scope_id,v_baseline);v_coverage:=public.verto_sync_v2_coverage();
 SELECT bootstrap_session_ttl_seconds INTO v_ttl FROM public.verto_sync_contract WHERE contract_family='verto-unified-sync' AND contract_version=2;
 INSERT INTO public.verto_sync_bootstrap_sessions(bootstrap_session_id,scope_id,organization_id,principal_id,contract_family,contract_version,scope_definition_version,baseline_revision,baseline_cursor,snapshot_row_count,expires_at,state,coverage_aggregate_types,high_watermark,delta_token)
 VALUES(v_session,p_scope_id,v_org,v_uid,'verto-unified-sync',2,(SELECT scope_definition_version FROM public.verto_sync_contract WHERE contract_family='verto-unified-sync' AND contract_version=2),v_baseline,v_cursor,0,now()+make_interval(secs=>v_ttl),'IN_PROGRESS',v_coverage,v_baseline,v_cursor);
 INSERT INTO public.verto_sync_bootstrap_rows(bootstrap_session_id,ordinal,aggregate_type,aggregate_id,entity_version,payload_version,payload,partition_key)
 SELECT v_session,row_number()OVER(ORDER BY s.aggregate_type,s.partition_key,s.aggregate_id),s.aggregate_type,s.aggregate_id,s.entity_version,s.payload_version,s.payload,s.partition_key
 FROM public.verto_sync_snapshot_state s WHERE s.organization_id=v_org AND s.updated_revision<=v_baseline AND public.verto_sync_row_visible_to_current_principal(s.aggregate_type,s.payload,s.visibility_principal_id,s.required_permission)
 ORDER BY s.aggregate_type,s.partition_key,s.aggregate_id;
 GET DIAGNOSTICS v_count=ROW_COUNT;
 SELECT string_agg(r.ordinal::text||'|'||encode(extensions.digest(convert_to(r.aggregate_type||'|'||r.aggregate_id||'|'||COALESCE(r.entity_version::text,'∅')||'|'||r.payload_version::text||'|'||r.partition_key||'|0|'||public.verto_jsonb_canonical_text_v2(r.payload),'UTF8'),'sha256'),'hex')||'|0',E'\n' ORDER BY r.ordinal) INTO v_digest_input FROM public.verto_sync_bootstrap_rows r WHERE r.bootstrap_session_id=v_session;
 v_digest:=encode(extensions.digest(convert_to(COALESCE(v_digest_input,''),'UTF8'),'sha256'),'hex');
 UPDATE public.verto_sync_bootstrap_sessions SET snapshot_row_count=v_count,snapshot_digest_sha256=v_digest,state='READY',seal_created_at=now() WHERE bootstrap_session_id=v_session;
 INSERT INTO public.verto_sync_bootstrap_page_tokens(bootstrap_session_id,after_ordinal)VALUES(v_session,0)RETURNING page_token INTO v_token;
 RETURN jsonb_build_object('contract_family','verto-unified-sync','contract_version',2,'scope_id',p_scope_id,'bootstrap_session_id',v_session,'baseline_revision',v_baseline,'baseline_cursor',v_cursor,'snapshot_row_count',v_count,'snapshot_digest_sha256',v_digest,'coverage_aggregate_types',v_coverage,'high_watermark',v_baseline,'delta_token',v_cursor,'first_page_token',v_token::text,'expires_at_epoch_millis',floor(extract(epoch from(now()+make_interval(secs=>v_ttl)))*1000)::bigint);
END $fn$;

CREATE OR REPLACE FUNCTION public.verto_pull_sync_bootstrap_page_v2(p_bootstrap_session_id uuid,p_page_token text,p_limit integer DEFAULT 1000)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public' AS $fn$
DECLARE v_session public.verto_sync_bootstrap_sessions%ROWTYPE;v_token uuid;v_after bigint;v_limit integer:=COALESCE(p_limit,1000);v_rows jsonb:='[]'::jsonb;v_last bigint;v_has_more boolean;v_next_token uuid;v_first_bytes bigint;
BEGIN
 IF v_limit<1 OR v_limit>1000 THEN RAISE EXCEPTION 'VALIDATION: bootstrap p_limit must be 1..1000' USING ERRCODE='22023';END IF;
 BEGIN v_token:=p_page_token::uuid;EXCEPTION WHEN invalid_text_representation THEN RAISE EXCEPTION 'VALIDATION: malformed bootstrap page token' USING ERRCODE='22023';END;
 SELECT*INTO v_session FROM public.verto_sync_bootstrap_sessions s WHERE s.bootstrap_session_id=p_bootstrap_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'BOOTSTRAP_RESTART_REQUIRED: session not found' USING ERRCODE='22023';END IF;
 PERFORM 1 FROM public.verto_validate_sync_scope_v2(v_session.scope_id);
 IF v_session.contract_version<>2 OR v_session.principal_id<>auth.uid() OR v_session.expires_at<=now() OR v_session.state NOT IN('READY','IN_PROGRESS') OR v_session.snapshot_digest_sha256 IS NULL OR v_session.coverage_aggregate_types IS NULL OR v_session.delta_token IS NULL THEN RAISE EXCEPTION 'BOOTSTRAP_RESTART_REQUIRED: expired, invalid or unsealed session' USING ERRCODE='22023';END IF;
 SELECT t.after_ordinal INTO v_after FROM public.verto_sync_bootstrap_page_tokens t WHERE t.page_token=v_token AND t.bootstrap_session_id=p_bootstrap_session_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'SCOPE_MISMATCH: bootstrap token does not belong to session' USING ERRCODE='22023';END IF;
 SELECT octet_length(convert_to(public.verto_jsonb_canonical_text_v2(r.payload),'UTF8'))::bigint INTO v_first_bytes FROM public.verto_sync_bootstrap_rows r WHERE r.bootstrap_session_id=p_bootstrap_session_id AND r.ordinal>v_after ORDER BY r.ordinal LIMIT 1;
 IF COALESCE(v_first_bytes,0)>1048576 THEN RAISE EXCEPTION 'CONTRACT_PAYLOAD_TOO_LARGE: bootstrap row exceeds 1 MiB' USING ERRCODE='22023';END IF;
 WITH candidates AS(
  SELECT r.*,octet_length(convert_to(public.verto_jsonb_canonical_text_v2(jsonb_build_object('ordinal',r.ordinal,'aggregateType',r.aggregate_type,'aggregateId',r.aggregate_id,'entityVersion',r.entity_version,'payloadVersion',r.payload_version,'payload',r.payload,'partitionKey',r.partition_key,'is_tombstone',false)),'UTF8'))::bigint row_bytes
  FROM public.verto_sync_bootstrap_rows r WHERE r.bootstrap_session_id=p_bootstrap_session_id AND r.ordinal>v_after ORDER BY r.ordinal),ranked AS(
  SELECT c.*,row_number()OVER(ORDER BY ordinal)rn,sum(row_bytes)OVER(ORDER BY ordinal ROWS UNBOUNDED PRECEDING)cumulative_bytes FROM candidates c),selected AS(
  SELECT*FROM ranked WHERE rn<=v_limit AND cumulative_bytes<=2097152)
 SELECT COALESCE(jsonb_agg(jsonb_build_object('ordinal',ordinal,'aggregateType',aggregate_type,'aggregateId',aggregate_id,'entityVersion',entity_version,'payloadVersion',payload_version,'payload',payload,'partitionKey',partition_key,'is_tombstone',false)ORDER BY ordinal),'[]'::jsonb),max(ordinal)INTO v_rows,v_last FROM selected;
 v_last:=COALESCE(v_last,v_after);
 SELECT EXISTS(SELECT 1 FROM public.verto_sync_bootstrap_rows r WHERE r.bootstrap_session_id=p_bootstrap_session_id AND r.ordinal>v_last)INTO v_has_more;
 IF v_has_more THEN INSERT INTO public.verto_sync_bootstrap_page_tokens(bootstrap_session_id,after_ordinal)VALUES(p_bootstrap_session_id,v_last)ON CONFLICT(bootstrap_session_id,after_ordinal)DO UPDATE SET after_ordinal=EXCLUDED.after_ordinal RETURNING page_token INTO v_next_token;ELSE UPDATE public.verto_sync_bootstrap_sessions SET state='COMPLETED',completed_at=COALESCE(completed_at,now())WHERE bootstrap_session_id=p_bootstrap_session_id;END IF;
 RETURN jsonb_build_object('bootstrap_session_id',p_bootstrap_session_id,'baseline_cursor',v_session.baseline_cursor,'rows',v_rows,'has_more',v_has_more,'next_page_token',CASE WHEN v_has_more THEN v_next_token::text ELSE NULL END,'snapshot_complete',NOT v_has_more);
END $fn$;

REVOKE ALL ON FUNCTION public.verto_jsonb_canonical_text_v2(jsonb) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.verto_sync_v2_coverage() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.verto_touched_keys_v2(text,text,jsonb) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.verto_encode_sync_cursor_v2(uuid,bigint) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_decode_sync_cursor_v2(uuid,text) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_read_sync_aggregate_v2(uuid,text,text) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_pull_sync_changes_v2(uuid,text,integer,bigint) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_begin_sync_bootstrap_v2(uuid) FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.verto_pull_sync_bootstrap_page_v2(uuid,text,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.verto_read_sync_aggregate_v2(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_pull_sync_changes_v2(uuid,text,integer,bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_begin_sync_bootstrap_v2(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_pull_sync_bootstrap_page_v2(uuid,text,integer) TO authenticated;
