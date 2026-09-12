CREATE OR REPLACE FUNCTION public.verto_v2_member_to_v1_mutation_v2(p_member jsonb)
RETURNS jsonb
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_type text:=upper(coalesce(p_member->>'aggregateType',''));
  v_payload jsonb:=coalesce(p_member->'payload','{}'::jsonb);
  v_pv integer:=coalesce((p_member->>'payloadVersion')::integer,0);
  v_dto jsonb;
  v_legacy_payload jsonb;
BEGIN
  IF p_member IS NULL OR jsonb_typeof(p_member)<>'object' THEN
    RAISE EXCEPTION 'VALIDATION: V2 member object required' USING ERRCODE='22023';
  END IF;
  IF v_pv=2 AND v_type='INVENTORY_MOVEMENT' THEN
    v_dto:=v_payload->'inventoryMovement';
    IF jsonb_typeof(v_dto)<>'object' THEN
      RAISE EXCEPTION 'CONTRACT_FIELD_MISSING: inventoryMovement' USING ERRCODE='22023';
    END IF;
    v_legacy_payload:=jsonb_build_object('inventoryCommand',jsonb_build_object(
      'client_outbox_id',p_member->>'mutationId',
      'movement_id',p_member->>'aggregateId',
      'item_id',v_dto->>'itemId',
      'invoice_id',coalesce(v_dto->>'invoiceId',''),
      'client_id',nullif(v_dto->>'clientId',''),
      'movement_kind',v_dto->>'movementKind',
      'signed_base_quantity',v_dto->'signedBaseQuantity',
      'unit_price_minor',v_dto->'unitPriceMinor',
      'note',coalesce(v_dto->>'note',''),
      'source_type',coalesce(v_dto->>'sourceType',''),
      'source_id',coalesce(v_dto->>'sourceId',''),
      'source_line_id',v_dto->>'sourceLineId',
      'command_id',v_dto->>'commandId',
      'idempotency_key',v_dto->>'idempotencyKey',
      'posting_group_id',v_dto->>'postingGroupId',
      'reverses_movement_id',v_dto->>'reversesMovementId',
      'conversion_factor_snapshot',coalesce(v_dto->>'conversionFactorSnapshot','1'),
      'occurred_at',v_dto->'occurredAt',
      'device_id',coalesce(v_dto->>'deviceId','v2'),
      'contract_version',greatest(coalesce((v_dto->>'contractVersion')::integer,2),2)
    ));
    v_payload:=v_legacy_payload; v_pv:=1;
  ELSIF v_pv=2 AND v_type='INVENTORY_COST_REVISION' THEN
    v_dto:=v_payload->'inventoryCostRevision';
    IF jsonb_typeof(v_dto)<>'object' THEN
      RAISE EXCEPTION 'CONTRACT_FIELD_MISSING: inventoryCostRevision' USING ERRCODE='22023';
    END IF;
    v_legacy_payload:=jsonb_build_object('inventoryCostCommand',jsonb_build_object(
      'client_outbox_id',p_member->>'mutationId',
      'cost_revision_id',p_member->>'aggregateId',
      'item_id',v_dto->>'itemId','source_type',v_dto->>'sourceType','source_id',v_dto->>'sourceId',
      'source_line_id',v_dto->>'sourceLineId','revision_kind',v_dto->>'revisionKind',
      'direct_purchase_cost_minor',v_dto->'directPurchaseCostMinor',
      'landed_cost_per_base_unit_minor',v_dto->'landedCostPerBaseUnitMinor',
      'approved_inventory_cost_minor',v_dto->'approvedInventoryCostMinor',
      'currency_code',v_dto->>'currencyCode','exchange_rate_snapshot',v_dto->>'exchangeRateSnapshot',
      'allocation_basis',coalesce(v_dto->>'allocationBasis',''),
      'allocation_residual_minor',coalesce(v_dto->'allocationResidualMinor','0'::jsonb),
      'is_provisional',coalesce(v_dto->'isProvisional','false'::jsonb),
      'reverses_cost_revision_id',v_dto->>'reversesCostRevisionId',
      'command_id',v_dto->>'commandId','idempotency_key',v_dto->>'idempotencyKey',
      'approved_at',v_dto->'approvedAt','device_id',coalesce(v_dto->>'deviceId','v2'),
      'contract_version',greatest(coalesce((v_dto->>'contractVersion')::integer,2),2)
    ));
    v_payload:=v_legacy_payload; v_pv:=1;
  ELSIF v_pv=2 AND v_type IN(
    'CLIENT_CREDIT','COST_ALLOCATION','EXPENSE','CASH_MOVEMENT','CASH_RECONCILIATION',
    'GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE'
  ) THEN
    -- B06 freezes the same materialization/purchaseRequest semantics under payloadVersion 2.
    -- Existing domain adapters remain authoritative; only the transport envelope is upgraded.
    v_pv:=1;
  END IF;
  RETURN jsonb_build_object(
    'organization_id',p_member->>'organizationId','mutation_id',p_member->>'mutationId',
    'aggregate_type',v_type,'aggregate_id',p_member->>'aggregateId','operation_type',p_member->>'operationType',
    'base_version',p_member->'baseVersion','payload_version',v_pv,'payload',v_payload,
    'command_batch_id',p_member->'commandBatchId','command_order',p_member->'commandOrder',
    'depends_on_mutation_id',p_member->'dependsOnMutationId'
  );
END $function$;
REVOKE ALL ON FUNCTION public.verto_v2_member_to_v1_mutation_v2(jsonb) FROM PUBLIC,anon,authenticated;

DO $do$
DECLARE v_def text; v_old text;
BEGIN
 SELECT pg_get_functiondef(p.oid) INTO v_def FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname='verto_apply_sync_batch_v2'
   AND pg_get_function_identity_arguments(p.oid)='p_wire_json text, p_wire_sha256 text';
 IF v_def IS NULL THEN RAISE EXCEPTION 'B07 batch RPC not found'; END IF;
 v_old:=v_def;
 v_def:=replace(v_def,'v_receipt:=public.verto_apply_sync_mutation(v_mutation);','v_receipt:=public.verto_apply_sync_mutation(public.verto_v2_member_to_v1_mutation_v2(v_member_json));');
 IF v_def=v_old THEN RAISE EXCEPTION 'B07 batch apply call not found'; END IF;
 EXECUTE v_def;
END $do$;