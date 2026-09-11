-- B12: mutable/versioned expense history and cash-delta contract. Append-only migration.

CREATE TABLE IF NOT EXISTS public.expense_revision_history (
  organization_id uuid NOT NULL,
  expense_id uuid NOT NULL,
  server_version bigint NOT NULL,
  previous_version bigint,
  write_id text NOT NULL,
  before_content_hash text,
  after_content_hash text NOT NULL,
  actor_id uuid NOT NULL,
  changed_at bigint NOT NULL,
  cash_delta_minor bigint NOT NULL,
  cash_movement_id uuid,
  PRIMARY KEY (organization_id, expense_id, server_version),
  UNIQUE (organization_id, write_id),
  CHECK (server_version > 0),
  CHECK (previous_version IS NULL OR previous_version >= 0),
  CHECK (before_content_hash IS NULL OR before_content_hash ~ '^[0-9a-f]{64}$'),
  CHECK (after_content_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX IF NOT EXISTS index_expense_revision_history_org_expense_b12
  ON public.expense_revision_history(organization_id, expense_id, server_version DESC);
ALTER TABLE public.expense_revision_history ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.expense_revision_history FROM PUBLIC, anon, authenticated;

-- Publish the B12 authority name without changing ownership or push-mode metadata.
UPDATE public.verto_sync_stronger_adapter_registry_v310
   SET stronger_server_authority='verto_apply_expense_command_b12',
       conflict_policy='VERSIONED_MUTABLE_EXPENSE'
 WHERE aggregate_type='EXPENSE';

CREATE OR REPLACE FUNCTION public.verto_reject_expense_revision_mutation_b12()
RETURNS trigger LANGUAGE plpgsql SET search_path=public AS $$
BEGIN
  RAISE EXCEPTION 'FAIL_EXPENSE_REVISION_HISTORY_IMMUTABLE';
END $$;
DROP TRIGGER IF EXISTS expense_revision_history_immutable_b12 ON public.expense_revision_history;
CREATE TRIGGER expense_revision_history_immutable_b12
BEFORE UPDATE OR DELETE ON public.expense_revision_history
FOR EACH ROW EXECUTE FUNCTION public.verto_reject_expense_revision_mutation_b12();

CREATE OR REPLACE FUNCTION public.verto_b12_len_token(p_value text)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE SET search_path=public AS $$
  SELECT CASE WHEN p_value IS NULL THEN '-1:'
    ELSE octet_length(convert_to(p_value, 'UTF8'))::text || ':' || p_value END
$$;

CREATE OR REPLACE FUNCTION public.verto_expense_content_hash_b12(
  p_id text, p_category text, p_item text, p_amount_minor bigint, p_note text, p_date_ms bigint,
  p_lifecycle text, p_voided_at bigint, p_void_reason text, p_reversal_write_id text
) RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE SET search_path=public AS $$
  SELECT encode(extensions.digest(convert_to(concat_ws('|',
    'B12',
    public.verto_b12_len_token(p_id),
    public.verto_b12_len_token(p_category),
    public.verto_b12_len_token(p_item),
    public.verto_b12_len_token(p_amount_minor::text),
    public.verto_b12_len_token(p_note),
    public.verto_b12_len_token(p_date_ms::text),
    public.verto_b12_len_token(p_lifecycle),
    public.verto_b12_len_token(p_voided_at::text),
    public.verto_b12_len_token(p_void_reason),
    public.verto_b12_len_token(p_reversal_write_id)
  ), 'UTF8'), 'sha256'), 'hex')
$$;

/** Validate the two owner facts before B07's atomic batch endpoint is allowed to call the expense writer. */
CREATE OR REPLACE FUNCTION public.verto_validate_expense_group_b12(
  p_expense_payload jsonb,
  p_cash_payload jsonb
) RETURNS text LANGUAGE plpgsql IMMUTABLE SET search_path=public AS $$
DECLARE
  v_intent jsonb:=p_expense_payload->'expenseRevisionIntent';
  v_mat jsonb:=p_expense_payload->'materialization';
  v_cash jsonb:=CASE WHEN p_cash_payload ? 'materialization' THEN p_cash_payload->'materialization' ELSE p_cash_payload END;
  v_delta bigint;
  v_cash_id text;
BEGIN
  IF jsonb_typeof(v_intent)<>'object' OR jsonb_typeof(v_mat)<>'object' THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  v_delta:=coalesce((v_intent->>'cashDeltaMinor')::bigint,0);
  v_cash_id:=nullif(v_intent->>'cashMovementId','');
  IF v_delta=0 THEN
    IF v_cash_id IS NOT NULL OR nullif(v_intent->>'cashMutationId','') IS NOT NULL THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
    RETURN 'OK';
  END IF;
  IF jsonb_typeof(v_cash)<>'object' THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  IF v_cash_id IS NULL OR v_cash->>'id'<>v_cash_id THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  IF coalesce((v_cash->>'amountMinor')::bigint,0)<>v_delta THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  IF v_cash->>'writeId'<>v_intent->>'writeId' THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  IF v_cash->>'referenceId'<>v_intent->>'expenseId' THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  IF coalesce(v_cash->>'sourceType','') NOT LIKE 'EXPENSE%' THEN RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT'; END IF;
  RETURN 'OK';
EXCEPTION WHEN others THEN
  RETURN 'BLOCKED_EXPENSE_DOMAIN_DRIFT';
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_expense_command_b12(
  p_org uuid,
  p_id text,
  p_operation text,
  p_payload jsonb,
  p_base_version bigint
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  v public.expenses%rowtype;
  v_mat jsonb:=p_payload->'materialization';
  v_intent jsonb:=p_payload->'expenseRevisionIntent';
  v_before jsonb;
  v_after jsonb;
  v_write text;
  v_actor uuid:=auth.uid();
  v_amount_minor bigint;
  v_old_effective bigint:=0;
  v_new_effective bigint:=0;
  v_cash_delta bigint;
  v_before_hash text;
  v_after_hash text;
  v_previous bigint;
  v_new_version bigint;
  v_changed_at bigint:=(extract(epoch from clock_timestamp())*1000)::bigint;
  v_date_ms bigint;
  v_lifecycle text;
BEGIN
  IF v_actor IS NULL THEN RETURN jsonb_build_object('applied',false,'validation_code','AUTH_REQUIRED'); END IF;
  IF jsonb_typeof(v_mat)<>'object' OR jsonb_typeof(v_intent)<>'object' THEN
    RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_REVISION_INTENT_REQUIRED');
  END IF;
  IF coalesce((v_intent->>'schemaVersion')::integer,0)<>1 OR v_intent->>'expenseId'<>p_id THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;
  v_write:=nullif(v_intent->>'writeId','');
  IF v_write IS NULL THEN RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_WRITE_ID_REQUIRED'); END IF;
  IF nullif(v_intent->>'actorId','') IS DISTINCT FROM v_actor::text
     OR coalesce((v_intent->>'createdAt')::bigint,0)<=0 THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;
  IF (p_operation='COMMAND' AND v_intent->>'operation'<>'UPSERT')
     OR (p_operation='VOID' AND v_intent->>'operation'<>'VOID')
     OR p_operation='REVERSE' THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;
  IF v_mat->>'id'<>p_id OR v_intent->'after' IS DISTINCT FROM v_mat THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;
  v_amount_minor:=coalesce((v_mat->>'amountMinor')::bigint,0);
  IF v_amount_minor<=0 THEN RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT'); END IF;
  v_lifecycle:=coalesce(v_mat->>'lifecycleState','ACTIVE');
  IF v_lifecycle NOT IN ('ACTIVE','VOID') THEN RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT'); END IF;
  IF p_operation='COMMAND' AND v_lifecycle<>'ACTIVE' THEN RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT'); END IF;
  IF p_operation IN ('VOID','REVERSE') AND v_lifecycle<>'VOID' THEN RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT'); END IF;
  IF p_operation NOT IN ('COMMAND','VOID','REVERSE') THEN RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_OPERATION_UNSUPPORTED'); END IF;
  v_date_ms:=coalesce((v_mat->>'date')::bigint,0);
  IF v_date_ms<=0 THEN RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT'); END IF;

  SELECT * INTO v FROM public.expenses WHERE id=p_id::uuid AND organization_id=p_org FOR UPDATE;
  IF FOUND THEN
    v_previous:=v.sync_version;
    IF p_base_version IS NULL OR p_base_version<>v_previous THEN
      RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','STALE_BASE_VERSION','server_version',v_previous);
    END IF;
    v_before:=jsonb_build_object(
      'id',v.id::text,'category',v.category,'item',coalesce(v.item,''),'amountMinor',round(v.amount*100)::bigint,
      'note',coalesce(v.note,''),'date',round(extract(epoch from v.date)*1000)::bigint,'lifecycleState',v.lifecycle_state,
      'voidedAt',v.voided_at,'voidReason',v.void_reason,'reversalWriteId',v.reversal_write_id);
    v_before_hash:=public.verto_expense_content_hash_b12(
      v.id::text,v.category,coalesce(v.item,''),round(v.amount*100)::bigint,coalesce(v.note,''),
      round(extract(epoch from v.date)*1000)::bigint,v.lifecycle_state,v.voided_at,v.void_reason,v.reversal_write_id);
    v_old_effective:=CASE WHEN v.lifecycle_state='ACTIVE' THEN round(v.amount*100)::bigint ELSE 0 END;
    IF v_intent->'before' IS DISTINCT FROM v_before OR nullif(v_intent->>'beforeContentHash','') IS DISTINCT FROM v_before_hash THEN
      RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
    END IF;
  ELSE
    v_previous:=NULL;
    IF coalesce(p_base_version,0)<>0 OR v_intent->'before' IS DISTINCT FROM 'null'::jsonb OR nullif(v_intent->>'beforeContentHash','') IS NOT NULL THEN
      RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','STALE_BASE_VERSION','server_version',0);
    END IF;
  END IF;

  v_after:=v_mat;
  v_after_hash:=public.verto_expense_content_hash_b12(
    p_id,coalesce(v_mat->>'category',''),coalesce(v_mat->>'item',''),v_amount_minor,coalesce(v_mat->>'note',''),v_date_ms,
    v_lifecycle,(v_mat->>'voidedAt')::bigint,nullif(v_mat->>'voidReason',''),nullif(v_mat->>'reversalWriteId',''));
  IF nullif(v_intent->>'afterContentHash','') IS DISTINCT FROM v_after_hash THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;
  v_new_effective:=CASE WHEN v_lifecycle='ACTIVE' THEN v_amount_minor ELSE 0 END;
  v_cash_delta:=-(v_new_effective-v_old_effective);
  IF coalesce((v_intent->>'cashDeltaMinor')::bigint,9223372036854775807)<>v_cash_delta THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;
  IF v_cash_delta<>0 THEN
    IF nullif(v_intent->>'cashMovementId','') IS NULL OR nullif(v_intent->>'cashMutationId','') IS NULL THEN
      RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
    END IF;
    -- B07 owns the future atomic group endpoint. Never apply one side independently before that gate exists.
    RETURN jsonb_build_object('applied',false,'retryable',true,'validation_code','B12_EXPENSE_BATCH_REQUIRED');
  END IF;
  IF nullif(v_intent->>'cashMovementId','') IS NOT NULL OR nullif(v_intent->>'cashMutationId','') IS NOT NULL THEN
    RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
  END IF;

  IF v_previous IS NULL THEN
    INSERT INTO public.expenses(id,organization_id,category,item,amount,note,date,created_at,updated_at,lifecycle_state,voided_at,void_reason,reversal_write_id,sync_version)
    VALUES(p_id::uuid,p_org,v_mat->>'category',coalesce(v_mat->>'item',''),v_amount_minor::numeric/100,coalesce(v_mat->>'note',''),
      to_timestamp(v_date_ms/1000.0),now(),now(),v_lifecycle,(v_mat->>'voidedAt')::bigint,nullif(v_mat->>'voidReason',''),nullif(v_mat->>'reversalWriteId',''),1)
    RETURNING sync_version INTO v_new_version;
  ELSE
    UPDATE public.expenses SET category=v_mat->>'category',item=coalesce(v_mat->>'item',''),amount=v_amount_minor::numeric/100,
      note=coalesce(v_mat->>'note',''),date=to_timestamp(v_date_ms/1000.0),lifecycle_state=v_lifecycle,
      voided_at=(v_mat->>'voidedAt')::bigint,void_reason=nullif(v_mat->>'voidReason',''),reversal_write_id=nullif(v_mat->>'reversalWriteId',''),
      sync_version=sync_version+1,updated_at=now()
    WHERE id=p_id::uuid AND organization_id=p_org RETURNING sync_version INTO v_new_version;
  END IF;

  INSERT INTO public.expense_revision_history(organization_id,expense_id,server_version,previous_version,write_id,before_content_hash,
      after_content_hash,actor_id,changed_at,cash_delta_minor,cash_movement_id)
  VALUES(p_org,p_id::uuid,v_new_version,v_previous,v_write,v_before_hash,v_after_hash,v_actor,v_changed_at,v_cash_delta,NULL)
  ON CONFLICT (organization_id,write_id) DO NOTHING;
  IF NOT EXISTS(SELECT 1 FROM public.expense_revision_history h WHERE h.organization_id=p_org AND h.write_id=v_write
    AND h.expense_id=p_id::uuid AND h.server_version=v_new_version AND h.after_content_hash=v_after_hash) THEN
    RAISE EXCEPTION 'BLOCKED_EXPENSE_DOMAIN_DRIFT: expense revision replay mismatch';
  END IF;

  RETURN jsonb_build_object('applied',true,'server_version',v_new_version,
    'authoritative_payload',jsonb_build_object(
      'materialization',v_after,
      'expenseRevision',jsonb_build_object(
        'expenseId',p_id,'serverVersion',v_new_version,'previousVersion',v_previous,'writeId',v_write,
        'beforeContentHash',v_before_hash,'afterContentHash',v_after_hash,'cashDeltaMinor',v_cash_delta,
        'cashMovementId',NULL,'actorId',v_actor::text,'changedAt',v_changed_at)));
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
  RETURN jsonb_build_object('applied',false,'validation_code','BLOCKED_EXPENSE_DOMAIN_DRIFT');
END $$;

CREATE OR REPLACE FUNCTION public.verto_apply_stronger_sync_adapter_v310(
  p_organization_id uuid,p_aggregate_type text,p_aggregate_id text,p_operation_type text,p_payload_version integer,p_payload jsonb,p_base_version bigint
) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE v_fin jsonb; v_result jsonb; v_req jsonb; v_row jsonb; v_status text; v_seq bigint; v_company uuid; v_opt_type text;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.verto_sync_stronger_adapter_registry_v310 r WHERE r.aggregate_type=p_aggregate_type) THEN
    RETURN jsonb_build_object('applied',false,'validation_code','CONTRACT_UNSUPPORTED');
  END IF;

  IF p_aggregate_type IN ('INVOICE','PAYMENT') THEN
    v_fin:=p_payload->'financialEvent';
    IF jsonb_typeof(v_fin)<>'object' THEN RETURN jsonb_build_object('applied',false,'validation_code','FINANCIAL_EVENT_REQUIRED'); END IF;
    IF (p_aggregate_type='PAYMENT') <> (upper(coalesce(v_fin->>'domainOperation','')) LIKE 'PAYMENT_%') THEN
      RETURN jsonb_build_object('applied',false,'validation_code','FINANCIAL_AGGREGATE_OPERATION_MISMATCH');
    END IF;
    PERFORM public.verto_financial_prepare_legacy_invoice_v310(p_organization_id,p_aggregate_id);
    SELECT to_jsonb(x) INTO v_result FROM public.financial_sync_apply_event_v1(
      v_fin->>'eventId',v_fin->>'writeId',p_aggregate_id,(v_fin->>'aggregateVersion')::integer,(v_fin->>'aggregateSequence')::bigint,
      v_fin->>'domainOperation',(v_fin->>'domainPayloadVersion')::integer,(v_fin->>'schemaVersion')::integer,
      coalesce((v_fin->'domainPayload')::text,'{}'),(v_fin->>'occurredAt')::bigint,(v_fin->>'recordedAt')::bigint) x;
    v_status:=upper(coalesce(v_result->>'status',''));
    IF v_status='WAITING_DEPENDENCY' THEN RETURN jsonb_build_object('applied',false,'retryable',true,'validation_code','WAITING_DEPENDENCY'); END IF;
    IF v_status IN ('CONFLICT','REQUIRES_REVIEW') THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','FINANCIAL_DOMAIN_CONFLICT','server_version',greatest((v_fin->>'aggregateVersion')::bigint,1)); END IF;
    IF v_status NOT IN ('APPLIED','REPLAYED') THEN RETURN jsonb_build_object('applied',false,'validation_code','FINANCIAL_RESULT_UNSUPPORTED'); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='REPLAYED'),'server_version',greatest((v_fin->>'aggregateVersion')::bigint,1),
      'authoritative_payload',jsonb_build_object('financialEvent',v_fin||jsonb_build_object('domainServerRevision',(v_result->>'server_revision')::bigint,'domainServerRecordedAt',(v_result->>'server_recorded_at')::bigint)));
  END IF;

  IF p_aggregate_type='INVENTORY_MOVEMENT' THEN
    v_req:=p_payload->'inventoryCommand';
    IF jsonb_typeof(v_req)<>'object' THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_COMMAND_REQUIRED'); END IF;
    SELECT to_jsonb(x) INTO v_result FROM public.inventory_apply_commands_v2(jsonb_build_array(v_req)) x;
    v_status:=upper(coalesce(v_result->>'status','')); v_seq:=coalesce((v_result->>'server_sequence')::bigint,0);
    IF v_status='QUARANTINED' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','INVENTORY_QUARANTINED','server_version',greatest(v_seq,1),'authoritative_payload',jsonb_build_object('movement',v_req,'domainResult',v_result)); END IF;
    IF v_status NOT IN ('APPLIED','DUPLICATE') THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_RESULT_UNSUPPORTED'); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='DUPLICATE'),'server_version',greatest(v_seq,1),
      'authoritative_payload',jsonb_build_object('movement',v_req||jsonb_build_object('serverSequence',v_seq),'domainResult',v_result));
  END IF;

  IF p_aggregate_type='INVENTORY_COST_REVISION' THEN
    v_req:=p_payload->'inventoryCostCommand';
    IF jsonb_typeof(v_req)<>'object' THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_COST_COMMAND_REQUIRED'); END IF;
    SELECT to_jsonb(x) INTO v_result FROM public.inventory_apply_cost_revisions_v2(jsonb_build_array(v_req)) x;
    v_status:=upper(coalesce(v_result->>'status','')); v_seq:=coalesce((v_result->>'cost_sequence')::bigint,0);
    IF v_status='QUARANTINED' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','INVENTORY_COST_QUARANTINED','server_version',greatest(v_seq,1)); END IF;
    IF v_status NOT IN ('APPLIED','DUPLICATE') THEN RETURN jsonb_build_object('applied',false,'validation_code','INVENTORY_COST_RESULT_UNSUPPORTED'); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='DUPLICATE'),'server_version',greatest(v_seq,1),
      'authoritative_payload',jsonb_build_object('costRevision',v_req||jsonb_build_object('costSequence',v_seq),'domainResult',v_result));
  END IF;

  IF p_aggregate_type='GOODS_RECEIPT' THEN
    v_req:=p_payload->'purchaseRequest';
    PERFORM public.verto_purchase_cycle_push_pre_v253(coalesce(v_req->'purchaseOrders','[]'::jsonb),coalesce(v_req->'purchaseOrderLines','[]'::jsonb),
      coalesce(v_req->'goodsReceipts','[]'::jsonb),coalesce(v_req->'goodsReceiptLines','[]'::jsonb),coalesce(v_req->'attachments','[]'::jsonb));
    RETURN jsonb_build_object('applied',true,'server_version',greatest(coalesce(p_base_version,1),1),'authoritative_payload',p_payload);
  END IF;
  IF p_aggregate_type IN ('PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE') THEN
    v_req:=p_payload->'purchaseRequest';
    PERFORM public.verto_purchase_cycle_push_post_v253(coalesce(v_req->'matches','[]'::jsonb),coalesce(v_req->'matchLines','[]'::jsonb),
      coalesce(v_req->'allocations','[]'::jsonb),coalesce(v_req->'paymentOverrides','[]'::jsonb));
    RETURN jsonb_build_object('applied',true,'server_version',greatest(coalesce(p_base_version,1),1),'authoritative_payload',p_payload);
  END IF;
  IF p_aggregate_type='CLIENT_CREDIT' THEN RETURN public.verto_apply_client_credit_v310(p_organization_id,p_aggregate_id,p_payload->'materialization'); END IF;
  IF p_aggregate_type='COST_ALLOCATION' THEN RETURN public.verto_apply_cost_allocation_v310(p_organization_id,p_aggregate_id,p_payload->'materialization'); END IF;
  IF p_aggregate_type='EXPENSE' THEN RETURN public.verto_apply_expense_command_b12(p_organization_id,p_aggregate_id,p_operation_type,p_payload,p_base_version); END IF;
  IF p_aggregate_type='CASH_MOVEMENT' THEN RETURN public.verto_apply_cash_movement_v310(p_organization_id,p_aggregate_id,p_payload->'materialization'); END IF;
  IF p_aggregate_type='CASH_RECONCILIATION' THEN RETURN public.verto_apply_cash_reconciliation_v310(p_organization_id,p_aggregate_id,p_operation_type,p_payload->'materialization'); END IF;

  IF p_aggregate_type LIKE 'OPTIMAL_%' THEN
    v_req:=p_payload->'optimalRequest';
    BEGIN v_company:=nullif(v_req->>'companyId','')::uuid; EXCEPTION WHEN others THEN v_company:=NULL; END;
    IF v_company IS NULL OR NOT EXISTS(SELECT 1 FROM public.optimal_verto_links l WHERE l.optimal_company_id=v_company AND l.verto_organization_id=p_organization_id AND l.is_active=true) THEN
      RETURN jsonb_build_object('applied',false,'validation_code','OPTIMAL_VERTO_LINK_REQUIRED');
    END IF;
    v_opt_type:=CASE p_aggregate_type WHEN 'OPTIMAL_VEHICLE' THEN 'VEHICLE' WHEN 'OPTIMAL_MAINTENANCE' THEN 'MAINTENANCE' ELSE 'FOLLOW_UP' END;
    IF upper(coalesce(v_req->>'aggregateType',''))<>v_opt_type THEN RETURN jsonb_build_object('applied',false,'validation_code','OPTIMAL_AGGREGATE_MISMATCH'); END IF;
    SELECT to_jsonb(x) INTO v_result FROM public.optimal_apply_sync_operation_v2(v_company,v_opt_type,p_aggregate_id,
      v_req->>'operationType',coalesce(v_req->'payload','{}'::jsonb),v_req->>'idempotencyKey',coalesce((v_req->>'localVersion')::bigint,1)) x;
    v_status:=upper(coalesce(v_result->>'status',''));
    IF v_status='CONFLICT' THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code',coalesce(v_result->>'error_code','OPTIMAL_CONFLICT'),'server_version',greatest(coalesce((v_result->>'remote_version')::bigint,1),1),'authoritative_payload',jsonb_build_object('materialization',coalesce(v_result->'remote_payload',v_req->'payload'))); END IF;
    IF v_status NOT IN ('SUCCESS','APPLIED','ALREADY_APPLIED') THEN RETURN jsonb_build_object('applied',false,'retryable',true,'validation_code',coalesce(v_result->>'error_code','OPTIMAL_RETRYABLE')); END IF;
    RETURN jsonb_build_object('applied',true,'no_op',(v_status='ALREADY_APPLIED'),'server_version',greatest(coalesce((v_result->>'remote_version')::bigint,1),1),
      'authoritative_payload',jsonb_build_object('materialization',coalesce(v_result->'remote_payload',v_req->'payload'),'optimalResult',v_result));
  END IF;

  RETURN jsonb_build_object('applied',false,'validation_code','CONTRACT_UNSUPPORTED');
END $$;

REVOKE ALL ON FUNCTION public.verto_reject_expense_revision_mutation_b12() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_b12_len_token(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_expense_content_hash_b12(text,text,text,bigint,text,bigint,text,bigint,text,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_validate_expense_group_b12(jsonb,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_expense_command_b12(uuid,text,text,jsonb,bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_stronger_sync_adapter_v310(uuid,text,text,text,integer,jsonb,bigint) FROM PUBLIC, anon, authenticated;
