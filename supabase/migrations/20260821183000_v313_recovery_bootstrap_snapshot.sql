-- Verto v313 — recovery/bootstrap snapshot completeness.
-- Additive migration. Historical v305/v309/v310/v312 files are immutable.
-- Runtime V2 remains disabled client-side; this migration defines server recovery support only.

CREATE TABLE IF NOT EXISTS public.verto_sync_bootstrap_coverage_v313 (
    aggregate_type text PRIMARY KEY,
    payload_version integer NOT NULL CHECK (payload_version > 0),
    owner_session integer NOT NULL,
    authoritative_source text NOT NULL,
    backfill_strategy text NOT NULL,
    future_write_strategy text NOT NULL,
    stronger_semantics boolean NOT NULL,
    CHECK (aggregate_type <> ''),
    CHECK (authoritative_source <> ''),
    CHECK (backfill_strategy <> ''),
    CHECK (future_write_strategy <> '')
);

INSERT INTO public.verto_sync_bootstrap_coverage_v313
(aggregate_type,payload_version,owner_session,authoritative_source,backfill_strategy,future_write_strategy,stronger_semantics) VALUES
('PARTY_IDENTITY',2,307,'clients','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('PARTY_ROLE',2,307,'clients/party-role contract','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('CUSTOMER_PROFILE',2,307,'clients/customer profile','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('SUPPLIER_PROFILE',2,307,'clients/supplier profile','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('NOTE',1,307,'notes','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('REMINDER',1,307,'client_reminders','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('INVOICE',1,310,'financial event/materialized invoice authority','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('PAYMENT',1,310,'financial event/payment authority','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('CLIENT_CREDIT',1,310,'client_credits','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('PURCHASE_ORDER',1,307,'purchase_orders/purchase_order_lines','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('GOODS_RECEIPT',1,310,'goods_receipts/goods_receipt_lines','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('PURCHASE_MATCH',1,310,'purchase_invoice_matches','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('PURCHASE_PAYMENT_OVERRIDE',1,310,'purchase_payment_overrides','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('INVENTORY_ITEM',1,307,'inventory_items','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('INVENTORY_UNIT',1,307,'inventory_units','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('CATEGORY',1,307,'categories','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('ITEM_CATEGORY',1,307,'item_categories','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('INVENTORY_MOVEMENT',1,310,'inventory_movements','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('INVENTORY_COST_REVISION',1,310,'inventory_cost_revisions','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('COST_ALLOCATION',1,310,'cost_allocations','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('EXPENSE',1,310,'expenses','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('BUDGET',1,307,'budgets','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('CASH_REGISTER',1,310,'cash_register','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('CASH_MOVEMENT',1,310,'cash_register_movements','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('CASH_RECONCILIATION',1,310,'cash_reconciliation_sessions/cash_denominations','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('COMMISSION_PAYMENT',1,310,'commission_payments','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('PRICE_LIST',1,307,'price_list_header/price_list_items','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('NOTIFICATION',1,308,'notification cache authority','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('ORGANIZATION_SETTINGS',1,307,'organization_settings','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('SHIPMENT',1,307,'logistics shipment aggregate','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('EDUCATIONAL_CONTENT',1,307,'educational_topics/targets','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',false),
('OPTIMAL_VEHICLE',1,310,'optimal vehicle authority','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('OPTIMAL_MAINTENANCE',1,310,'optimal maintenance authority','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true),
('OPTIMAL_FOLLOW_UP',1,310,'optimal follow-up authority','LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER','APPEND_CHANGE_AUTO_SNAPSHOT',true)
ON CONFLICT (aggregate_type) DO UPDATE SET
 payload_version=EXCLUDED.payload_version, owner_session=EXCLUDED.owner_session,
 authoritative_source=EXCLUDED.authoritative_source, backfill_strategy=EXCLUDED.backfill_strategy,
 future_write_strategy=EXCLUDED.future_write_strategy, stronger_semantics=EXCLUDED.stronger_semantics;

DO $$
BEGIN
  IF (SELECT count(*) FROM public.verto_sync_bootstrap_coverage_v313) <> 34 THEN
    RAISE EXCEPTION 'FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE: v313 coverage registry must contain exactly 34 aggregates';
  END IF;
  IF (SELECT count(*) FROM public.verto_sync_bootstrap_coverage_v313 WHERE owner_session=310) <> 17 THEN
    RAISE EXCEPTION 'FAIL_OWNER310_BOOTSTRAP_COVERAGE: v313 owner310 coverage must contain exactly 17 aggregates';
  END IF;
END $$;

-- Central continuity repair: every committed unified change now maintains the bootstrap snapshot in
-- the SAME PostgreSQL transaction. This automatically covers v310 primary AND secondary changes,
-- and also restores continuity for owner307 paths after v310 replaced verto_apply_sync_mutation.
CREATE OR REPLACE FUNCTION public.verto_append_sync_change(
    p_organization_id uuid,
    p_aggregate_type text,
    p_aggregate_id text,
    p_operation_type text,
    p_entity_version bigint,
    p_payload_version integer,
    p_payload jsonb,
    p_origin_mutation_id text DEFAULT NULL,
    p_source_kind text DEFAULT 'SERVER_TRIGGER',
    p_visibility_principal_id uuid DEFAULT NULL,
    p_required_permission text DEFAULT NULL,
    p_deleted_at timestamptz DEFAULT NULL
) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $$
DECLARE
    v_revision bigint;
    v_expected integer;
BEGIN
    IF p_organization_id IS NULL OR p_aggregate_id IS NULL OR btrim(p_aggregate_id) = '' OR p_payload IS NULL THEN
        RAISE EXCEPTION 'VALIDATION: organization, aggregate id and payload are required' USING ERRCODE = '22023';
    END IF;
    v_expected := public.verto_expected_payload_version(p_aggregate_type);
    IF v_expected IS NULL OR v_expected <> p_payload_version THEN
        RAISE EXCEPTION 'CONTRACT_UNSUPPORTED: aggregate/payload version' USING ERRCODE = '22023';
    END IF;
    IF pg_column_size(p_payload) > 524288 THEN
        RAISE EXCEPTION 'CONTRACT_PAYLOAD_TOO_LARGE: mutation payload exceeds 512 KiB' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.verto_sync_change_log (
        revision, organization_id, aggregate_type, aggregate_id, operation_type,
        entity_version, payload_version, payload, origin_mutation_id, transaction_id,
        actor_user_id, source_kind, visibility_principal_id, required_permission, deleted_at
    ) VALUES (
        NULL, p_organization_id, p_aggregate_type, p_aggregate_id, p_operation_type,
        p_entity_version, p_payload_version, p_payload, p_origin_mutation_id, NULL,
        auth.uid(), p_source_kind, p_visibility_principal_id, p_required_permission, p_deleted_at
    ) RETURNING revision INTO v_revision;

    IF p_operation_type = 'DELETE' THEN
        PERFORM public.verto_remove_sync_snapshot_state(p_organization_id,p_aggregate_type,p_aggregate_id,v_revision);
    ELSE
        PERFORM public.verto_upsert_sync_snapshot_state(
            p_organization_id,p_aggregate_type,p_aggregate_id,p_entity_version,p_payload_version,p_payload,
            v_revision,'default',p_visibility_principal_id,p_required_permission
        );
    END IF;
    RETURN v_revision;
END;
$$;

COMMENT ON FUNCTION public.verto_append_sync_change(uuid,text,text,text,bigint,integer,jsonb,text,text,uuid,text,timestamptz) IS
'v313: unified change append + bootstrap snapshot maintenance are one transaction; DELETE removes snapshot, state transitions remain materialized rows.';

-- Idempotent pre-existing unified-history materializer. It never replays business commands; it only
-- reconstructs sync metadata from already-committed authoritative changes, assigning no wall-clock ordering.
CREATE OR REPLACE FUNCTION public.verto_backfill_sync_snapshot_v313_from_change_log()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $$
DECLARE r record; v_upserts bigint := 0; v_deletes bigint := 0;
BEGIN
  FOR r IN
    SELECT DISTINCT ON (organization_id,aggregate_type,aggregate_id)
      organization_id,aggregate_type,aggregate_id,operation_type,entity_version,payload_version,payload,
      revision,visibility_principal_id,required_permission
    FROM public.verto_sync_change_log
    ORDER BY organization_id,aggregate_type,aggregate_id,revision DESC
  LOOP
    IF r.operation_type='DELETE' THEN
      DELETE FROM public.verto_sync_snapshot_state s
       WHERE s.organization_id=r.organization_id AND s.aggregate_type=r.aggregate_type AND s.aggregate_id=r.aggregate_id;
      v_deletes:=v_deletes+1;
    ELSE
      INSERT INTO public.verto_sync_snapshot_state(
        organization_id,aggregate_type,aggregate_id,entity_version,payload_version,payload,partition_key,
        visibility_principal_id,required_permission,updated_revision,updated_at
      ) VALUES (
        r.organization_id,r.aggregate_type,r.aggregate_id,r.entity_version,r.payload_version,r.payload,'default',
        r.visibility_principal_id,r.required_permission,r.revision,now()
      ) ON CONFLICT (organization_id,aggregate_type,aggregate_id) DO UPDATE SET
        entity_version=EXCLUDED.entity_version,payload_version=EXCLUDED.payload_version,payload=EXCLUDED.payload,
        visibility_principal_id=EXCLUDED.visibility_principal_id,required_permission=EXCLUDED.required_permission,
        updated_revision=EXCLUDED.updated_revision,updated_at=EXCLUDED.updated_at
      WHERE public.verto_sync_snapshot_state.updated_revision < EXCLUDED.updated_revision;
      v_upserts:=v_upserts+1;
    END IF;
  END LOOP;
  RETURN jsonb_build_object('metadata_only',true,'business_commands_replayed',false,'upserts_seen',v_upserts,'deletes_seen',v_deletes);
END $$;

-- Runtime gate used by 314/staging. v313 defines the complete 34-source contract but deliberately
-- does not claim that legacy rows have been materialized until this assertion passes on real data.
CREATE OR REPLACE FUNCTION public.verto_assert_bootstrap_coverage_v313()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public'
AS $$
DECLARE v_rows integer; v_owner310 integer;
BEGIN
  SELECT count(*),count(*) FILTER (WHERE owner_session=310) INTO v_rows,v_owner310
  FROM public.verto_sync_bootstrap_coverage_v313;
  IF v_rows<>34 OR v_owner310<>17 THEN
    RAISE EXCEPTION 'FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE: contract registry incomplete';
  END IF;
  RETURN jsonb_build_object('aggregate_coverage',v_rows,'owner310_coverage',v_owner310,
    'future_write_continuity','ATOMIC_APPEND_CHANGE','existing_data_materializer','DEFINED_NOT_RUNTIME_VERIFIED');
END $$;

REVOKE ALL ON TABLE public.verto_sync_bootstrap_coverage_v313 FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.verto_backfill_sync_snapshot_v313_from_change_log() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_assert_bootstrap_coverage_v313() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verto_assert_bootstrap_coverage_v313() TO authenticated;
