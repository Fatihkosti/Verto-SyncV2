-- Session 335: finance multi-device authority hardening. Append-only migration.
CREATE INDEX IF NOT EXISTS index_expenses_org_lifecycle_date_v335
  ON public.expenses(organization_id, lifecycle_state, date DESC);
CREATE INDEX IF NOT EXISTS index_expenses_org_lifecycle_category_date_v335
  ON public.expenses(organization_id, lifecycle_state, category, date DESC);
CREATE INDEX IF NOT EXISTS index_cash_movements_org_created_v335
  ON public.cash_register_movements(organization_id, created_at DESC, id);

-- v310 COMMAND correctly handled create/replay but rejected a legitimate later edit of an ACTIVE
-- expense. The unified receipt ledger still owns mutation idempotency; this function owns the
-- aggregate transition and never revives a VOID expense.
CREATE OR REPLACE FUNCTION public.verto_apply_expense_command_v310(
  p_org uuid,
  p_id text,
  p_operation text,
  p_payload jsonb
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
DECLARE
  v public.expenses%rowtype;
  v_write text:=nullif(p_payload->>'reversalWriteId','');
  v_amount numeric:=coalesce((p_payload->>'amount')::numeric,0);
  v_date timestamptz:=to_timestamp(coalesce((p_payload->>'date')::bigint,0)/1000.0);
BEGIN
  SELECT * INTO v FROM public.expenses WHERE id=p_id::uuid AND organization_id=p_org FOR UPDATE;
  IF p_operation='COMMAND' THEN
    IF FOUND THEN
      IF v.lifecycle_state<>'ACTIVE' THEN
        RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','EXPENSE_NOT_ACTIVE','server_version',v.sync_version);
      END IF;
      IF v.category=coalesce(p_payload->>'category','')
         AND v.item=coalesce(p_payload->>'item','')
         AND v.amount=v_amount
         AND v.note=coalesce(p_payload->>'note','')
         AND v.date=v_date THEN
        RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',v.sync_version,
          'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','ACTIVE')));
      END IF;
      UPDATE public.expenses
         SET category=coalesce(p_payload->>'category',''),
             item=coalesce(p_payload->>'item',''),
             amount=v_amount,
             note=coalesce(p_payload->>'note',''),
             date=v_date,
             sync_version=sync_version+1,
             updated_at=now()
       WHERE id=p_id::uuid AND organization_id=p_org
       RETURNING * INTO v;
      RETURN jsonb_build_object('applied',true,'server_version',v.sync_version,
        'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','ACTIVE')));
    END IF;
    INSERT INTO public.expenses(id,organization_id,category,item,amount,note,date,created_at,updated_at,lifecycle_state,sync_version)
    VALUES(p_id::uuid,p_org,p_payload->>'category',coalesce(p_payload->>'item',''),v_amount,
           coalesce(p_payload->>'note',''),v_date,now(),now(),'ACTIVE',1);
    RETURN jsonb_build_object('applied',true,'server_version',1,
      'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','ACTIVE')));
  ELSIF p_operation IN ('VOID','REVERSE') THEN
    IF NOT FOUND THEN RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','EXPENSE_NOT_FOUND'); END IF;
    IF v.lifecycle_state='VOID' THEN
      IF v.reversal_write_id IS NOT DISTINCT FROM v_write THEN
        RETURN jsonb_build_object('applied',true,'no_op',true,'server_version',v.sync_version,
          'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','VOID')));
      END IF;
      RETURN jsonb_build_object('applied',false,'conflict',true,'conflict_code','EXPENSE_ALREADY_VOID','server_version',v.sync_version);
    END IF;
    IF v_write IS NULL THEN RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_REVERSAL_WRITE_ID_REQUIRED'); END IF;
    UPDATE public.expenses
       SET lifecycle_state='VOID',
           voided_at=coalesce((p_payload->>'voidedAt')::bigint,(extract(epoch from now())*1000)::bigint),
           void_reason=coalesce(p_payload->>'voidReason',''),
           reversal_write_id=v_write,
           sync_version=sync_version+1,
           updated_at=now()
     WHERE id=p_id::uuid AND organization_id=p_org
     RETURNING * INTO v;
    RETURN jsonb_build_object('applied',true,'server_version',v.sync_version,
      'authoritative_payload',jsonb_build_object('materialization',p_payload||jsonb_build_object('lifecycleState','VOID')));
  END IF;
  RETURN jsonb_build_object('applied',false,'validation_code','EXPENSE_OPERATION_UNSUPPORTED');
END $$;

REVOKE ALL ON FUNCTION public.verto_apply_expense_command_v310(uuid,text,text,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verto_apply_expense_command_v310(uuid,text,text,jsonb) TO authenticated;
