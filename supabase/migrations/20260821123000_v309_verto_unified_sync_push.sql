-- Verto Session 309 — idempotent unified Push EXPAND only.
-- Runtime V2 remains disabled. Historical v305 migration is immutable.
-- This migration establishes request identity, receipts, optimistic conflict semantics and a
-- fail-closed adapter boundary. Business writers without verified unified version capture remain
-- SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE and are never silently treated as APPLIED.

ALTER TABLE public.verto_sync_receipts
    ADD COLUMN IF NOT EXISTS resolution_requirement text;

ALTER TABLE public.verto_sync_receipts
    ADD CONSTRAINT verto_sync_receipt_resolution_requirement_v309 CHECK (
        resolution_requirement IS NULL OR resolution_requirement IN (
            'AUTO_REBASE_SAFE','SERVER_WINS_SAFE','LOCAL_RETRY_WITH_NEW_BASE','REQUIRES_REVIEW','REJECTED'
        )
    ) NOT VALID;

CREATE INDEX IF NOT EXISTS verto_sync_receipts_org_created_v309_idx
    ON public.verto_sync_receipts (organization_id, created_at);

-- Canonical semantic request hash. jsonb::text is whitespace-insensitive and object-key canonical;
-- array order and JSON scalar types remain significant. Operational client metadata is excluded.
CREATE FUNCTION public.verto_sync_request_hash_v309(
    p_organization_id uuid,
    p_mutation jsonb
) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public'
AS $$
DECLARE
    v_semantic jsonb;
BEGIN
    IF p_organization_id IS NULL OR p_mutation IS NULL OR jsonb_typeof(p_mutation) <> 'object' THEN
        RAISE EXCEPTION 'VALIDATION: semantic mutation object required' USING ERRCODE = '22023';
    END IF;
    v_semantic := jsonb_build_object(
        'organization_id', p_organization_id::text,
        'mutation_id', p_mutation->'mutation_id',
        'aggregate_type', p_mutation->'aggregate_type',
        'aggregate_id', p_mutation->'aggregate_id',
        'operation_type', p_mutation->'operation_type',
        'base_version', COALESCE(p_mutation->'base_version', 'null'::jsonb),
        'payload_version', p_mutation->'payload_version',
        'payload', COALESCE(p_mutation->'payload', '{}'::jsonb),
        'command_batch_id', COALESCE(p_mutation->'command_batch_id', 'null'::jsonb),
        'command_order', COALESCE(p_mutation->'command_order', 'null'::jsonb),
        'depends_on_mutation_id', COALESCE(p_mutation->'depends_on_mutation_id', 'null'::jsonb)
    );
    RETURN encode(extensions.digest(convert_to(v_semantic::text, 'UTF8'), 'sha256'), 'hex');
END;
$$;

CREATE FUNCTION public.verto_sync_receipt_json_v309(
    p_receipt public.verto_sync_receipts
) RETURNS jsonb
    LANGUAGE sql STABLE
    SET search_path TO 'public'
AS $$
    SELECT jsonb_build_object(
        'contract_family', 'verto-unified-sync',
        'contract_version', 1,
        'status', p_receipt.status,
        'mutation_id', p_receipt.mutation_id,
        'aggregate_id', p_receipt.aggregate_id,
        'server_version', p_receipt.server_version,
        'server_revision', p_receipt.server_revision,
        'authoritative_payload', p_receipt.authoritative_payload,
        'conflict_code', p_receipt.conflict_code,
        'validation_code', p_receipt.validation_code,
        'transaction_id', p_receipt.transaction_id,
        'retry_after_epoch_millis', NULL,
        'request_hash', p_receipt.request_hash,
        'resolution_requirement', p_receipt.resolution_requirement
    )
$$;

-- Explicit owner/deletion-policy checks. This is server-side defense in depth; the Android registry
-- is not trusted for correctness.
CREATE FUNCTION public.verto_validate_push_policy_v309(
    p_aggregate_type text,
    p_operation_type text,
    p_payload_version integer
) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public'
AS $$
DECLARE
    v_expected integer;
BEGIN
    v_expected := public.verto_expected_payload_version(p_aggregate_type);
    IF v_expected IS NULL OR v_expected <> p_payload_version THEN
        RETURN 'CONTRACT_UNSUPPORTED';
    END IF;

    IF p_aggregate_type IN (
        'INVOICE','PAYMENT','CLIENT_CREDIT','GOODS_RECEIPT','PURCHASE_MATCH','PURCHASE_PAYMENT_OVERRIDE',
        'INVENTORY_MOVEMENT','INVENTORY_COST_REVISION','COST_ALLOCATION','EXPENSE','CASH_REGISTER',
        'CASH_MOVEMENT','CASH_RECONCILIATION','COMMISSION_PAYMENT','OPTIMAL_VEHICLE',
        'OPTIMAL_MAINTENANCE','OPTIMAL_FOLLOW_UP'
    ) THEN
        RETURN 'DEFERRED_STRONGER_STREAM_310';
    END IF;
    IF p_aggregate_type = 'NOTIFICATION' THEN
        RETURN 'SERVER_OWNED_NO_CLIENT_PUSH';
    END IF;

    IF p_aggregate_type = 'INVENTORY_ITEM' AND p_operation_type NOT IN ('UPSERT','ARCHIVE') THEN
        RETURN 'VALIDATION_DELETE_POLICY';
    ELSIF p_aggregate_type = 'ORGANIZATION_SETTINGS' AND p_operation_type <> 'UPSERT' THEN
        RETURN 'VALIDATION_DELETE_POLICY';
    ELSIF p_aggregate_type IN ('PURCHASE_ORDER','SHIPMENT') AND p_operation_type NOT IN ('UPSERT','COMMAND','CANCEL') THEN
        RETURN 'VALIDATION_DELETE_POLICY';
    ELSIF p_aggregate_type IN (
        'NOTE','REMINDER','INVENTORY_UNIT','CATEGORY','ITEM_CATEGORY','BUDGET','PRICE_LIST','EDUCATIONAL_CONTENT',
        'PARTY_IDENTITY','CUSTOMER_PROFILE','SUPPLIER_PROFILE'
    ) AND p_operation_type NOT IN ('UPSERT','DELETE') THEN
        RETURN 'VALIDATION_DELETE_POLICY';
    ELSIF p_aggregate_type = 'PARTY_ROLE' AND p_operation_type <> 'COMMAND' THEN
        RETURN 'VALIDATION_DELETE_POLICY';
    END IF;

    IF p_aggregate_type NOT IN (
        'PARTY_IDENTITY','PARTY_ROLE','CUSTOMER_PROFILE','SUPPLIER_PROFILE','NOTE','REMINDER',
        'PURCHASE_ORDER','INVENTORY_ITEM','INVENTORY_UNIT','CATEGORY','ITEM_CATEGORY','BUDGET',
        'PRICE_LIST','ORGANIZATION_SETTINGS','SHIPMENT','EDUCATIONAL_CONTENT'
    ) THEN
        RETURN 'CONTRACT_UNSUPPORTED';
    END IF;
    RETURN 'OK';
END;
$$;

-- Adapter boundary. v309 deliberately refuses to invent business-table mappings from an incomplete
-- server dump. Later owner sessions may replace/extend this helper only after proving that legacy
-- server writers update the same version/change authority. Returning applied=false is fail-closed.
CREATE FUNCTION public.verto_apply_sync_adapter_v309(
    p_organization_id uuid,
    p_aggregate_type text,
    p_aggregate_id text,
    p_operation_type text,
    p_payload_version integer,
    p_payload jsonb,
    p_base_version bigint
) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
BEGIN
    IF p_aggregate_type = 'PARTY_ROLE' THEN
        RETURN jsonb_build_object(
            'applied', false,
            'validation_code', 'PARTY_STRONGER_SERVER_ADAPTER_NOT_VERIFIED',
            'runtime_safe', false
        );
    END IF;
    RETURN jsonb_build_object(
        'applied', false,
        'validation_code', 'SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE',
        'runtime_safe', false
    );
END;
$$;

CREATE FUNCTION public.verto_apply_sync_mutation(p_mutation jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_org uuid;
    v_client_org uuid;
    v_mutation_id text;
    v_aggregate_type text;
    v_aggregate_id text;
    v_operation_type text;
    v_base_version bigint;
    v_payload_version integer;
    v_payload jsonb;
    v_request_hash text;
    v_policy text;
    v_receipt public.verto_sync_receipts%ROWTYPE;
    v_snapshot public.verto_sync_snapshot_state%ROWTYPE;
    v_adapter jsonb;
    v_new_version bigint;
    v_new_payload jsonb;
    v_revision bigint;
    v_tx text;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE = '28000';
    END IF;
    IF p_mutation IS NULL OR jsonb_typeof(p_mutation) <> 'object' THEN
        RAISE EXCEPTION 'VALIDATION: mutation object required' USING ERRCODE = '22023';
    END IF;

    SELECT s.organization_id INTO v_org
      FROM public.verto_resolve_sync_scope() s
     LIMIT 1;

    BEGIN
        v_client_org := NULLIF(p_mutation->>'organization_id','')::uuid;
    EXCEPTION WHEN others THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: invalid organization_id' USING ERRCODE = '22023';
    END;
    IF v_client_org IS NULL OR v_client_org <> v_org THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: mutation organization differs from trusted membership' USING ERRCODE = '22023';
    END IF;

    v_mutation_id := btrim(COALESCE(p_mutation->>'mutation_id',''));
    v_aggregate_type := btrim(COALESCE(p_mutation->>'aggregate_type',''));
    v_aggregate_id := btrim(COALESCE(p_mutation->>'aggregate_id',''));
    v_operation_type := btrim(COALESCE(p_mutation->>'operation_type',''));
    v_payload := COALESCE(p_mutation->'payload', '{}'::jsonb);
    BEGIN
        v_payload_version := (p_mutation->>'payload_version')::integer;
        v_base_version := NULLIF(p_mutation->>'base_version','')::bigint;
    EXCEPTION WHEN others THEN
        RAISE EXCEPTION 'VALIDATION: invalid numeric mutation field' USING ERRCODE = '22023';
    END;

    IF v_mutation_id = '' OR v_aggregate_type = '' OR v_aggregate_id = '' OR v_operation_type = '' OR v_payload_version IS NULL THEN
        RAISE EXCEPTION 'VALIDATION: mutation identity fields required' USING ERRCODE = '22023';
    END IF;
    IF jsonb_typeof(v_payload) <> 'object' OR pg_column_size(v_payload) > 524288 THEN
        RAISE EXCEPTION 'VALIDATION: payload must be object <= 512 KiB' USING ERRCODE = '22023';
    END IF;

    -- Critical section is tenant+mutation scoped. Duplicate first attempts cannot race past receipt lookup.
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('verto-sync-mutation:' || v_org::text || ':' || v_mutation_id, 0)
    );
    v_request_hash := public.verto_sync_request_hash_v309(v_org, p_mutation);

    SELECT * INTO v_receipt
      FROM public.verto_sync_receipts r
     WHERE r.organization_id = v_org AND r.mutation_id = v_mutation_id;
    IF FOUND THEN
        IF v_receipt.request_hash = v_request_hash THEN
            -- Exact replay: return the immutable persisted terminal outcome. No business/change/snapshot write.
            RETURN public.verto_sync_receipt_json_v309(v_receipt);
        END IF;
        RETURN jsonb_build_object(
            'contract_family','verto-unified-sync','contract_version',1,
            'status','REJECTED','mutation_id',v_mutation_id,'aggregate_id',v_aggregate_id,
            'server_version',NULL,'server_revision',NULL,'authoritative_payload',NULL,
            'conflict_code',NULL,'validation_code','IDEMPOTENCY_CONFLICT','transaction_id',NULL,
            'retry_after_epoch_millis',NULL,'request_hash',v_request_hash,'resolution_requirement',NULL
        );
    END IF;

    v_policy := public.verto_validate_push_policy_v309(v_aggregate_type, v_operation_type, v_payload_version);
    IF v_policy <> 'OK' THEN
        INSERT INTO public.verto_sync_receipts(
            organization_id, mutation_id, request_hash, aggregate_type, aggregate_id, operation_type,
            status, validation_code
        ) VALUES (
            v_org, v_mutation_id, v_request_hash, v_aggregate_type, v_aggregate_id, v_operation_type,
            'REJECTED', v_policy
        ) RETURNING * INTO v_receipt;
        RETURN public.verto_sync_receipt_json_v309(v_receipt);
    END IF;

    -- Optimistic owner-307 aggregates compare only the server-owned snapshot entity version.
    IF v_aggregate_type IN (
        'NOTE','REMINDER','INVENTORY_ITEM','INVENTORY_UNIT','CATEGORY','ITEM_CATEGORY',
        'BUDGET','PRICE_LIST','ORGANIZATION_SETTINGS','EDUCATIONAL_CONTENT'
    ) THEN
        SELECT * INTO v_snapshot
          FROM public.verto_sync_snapshot_state s
         WHERE s.organization_id = v_org
           AND s.aggregate_type = v_aggregate_type
           AND s.aggregate_id = v_aggregate_id
         FOR UPDATE;

        IF FOUND THEN
            IF v_snapshot.entity_version IS NULL OR v_snapshot.entity_version <= 0 THEN
                INSERT INTO public.verto_sync_receipts(
                    organization_id, mutation_id, request_hash, aggregate_type, aggregate_id, operation_type,
                    status, validation_code, authoritative_payload
                ) VALUES (
                    v_org, v_mutation_id, v_request_hash, v_aggregate_type, v_aggregate_id, v_operation_type,
                    'REJECTED', 'FAIL_VERSION_AUTHORITY_GAP', v_snapshot.payload
                ) RETURNING * INTO v_receipt;
                RETURN public.verto_sync_receipt_json_v309(v_receipt);
            END IF;
            IF v_base_version IS NULL OR v_base_version <> v_snapshot.entity_version THEN
                INSERT INTO public.verto_sync_receipts(
                    organization_id, mutation_id, request_hash, aggregate_type, aggregate_id, operation_type,
                    status, server_version, authoritative_payload, conflict_code, resolution_requirement
                ) VALUES (
                    v_org, v_mutation_id, v_request_hash, v_aggregate_type, v_aggregate_id, v_operation_type,
                    'CONFLICT', v_snapshot.entity_version, v_snapshot.payload,
                    'STALE_BASE_VERSION', 'REQUIRES_REVIEW'
                ) RETURNING * INTO v_receipt;
                RETURN public.verto_sync_receipt_json_v309(v_receipt);
            END IF;
        ELSIF v_base_version IS NOT NULL THEN
            INSERT INTO public.verto_sync_receipts(
                organization_id, mutation_id, request_hash, aggregate_type, aggregate_id, operation_type,
                status, validation_code
            ) VALUES (
                v_org, v_mutation_id, v_request_hash, v_aggregate_type, v_aggregate_id, v_operation_type,
                'REJECTED', 'BASE_VERSION_WITHOUT_SERVER_ENTITY'
            ) RETURNING * INTO v_receipt;
            RETURN public.verto_sync_receipt_json_v309(v_receipt);
        END IF;
    END IF;

    -- Domain business write boundary. In v309 all unproven adapters fail closed; therefore no APPLIED
    -- receipt can be emitted until an adapter explicitly returns applied=true.
    v_adapter := public.verto_apply_sync_adapter_v309(
        v_org, v_aggregate_type, v_aggregate_id, v_operation_type,
        v_payload_version, v_payload, v_base_version
    );
    IF COALESCE((v_adapter->>'applied')::boolean, false) IS NOT TRUE THEN
        INSERT INTO public.verto_sync_receipts(
            organization_id, mutation_id, request_hash, aggregate_type, aggregate_id, operation_type,
            status, validation_code
        ) VALUES (
            v_org, v_mutation_id, v_request_hash, v_aggregate_type, v_aggregate_id, v_operation_type,
            'REJECTED', COALESCE(NULLIF(v_adapter->>'validation_code',''),'SHADOW_PUSH_ONLY_NOT_RUNTIME_SAFE')
        ) RETURNING * INTO v_receipt;
        RETURN public.verto_sync_receipt_json_v309(v_receipt);
    END IF;

    -- The following APPLIED path is one PostgreSQL transaction with the adapter call above.
    -- It is intentionally unreachable for v309's fail-closed adapters, but defines the cutover-safe boundary.
    v_new_payload := COALESCE(v_adapter->'authoritative_payload', v_payload);
    v_new_version := NULLIF(v_adapter->>'server_version','')::bigint;
    IF v_new_version IS NULL OR v_new_version <= 0 THEN
        RAISE EXCEPTION 'VALIDATION: APPLIED adapter must return positive server_version' USING ERRCODE = '22023';
    END IF;

    v_revision := public.verto_append_sync_change(
        v_org, v_aggregate_type, v_aggregate_id, v_operation_type,
        v_new_version, v_payload_version, v_new_payload, v_mutation_id, 'CLIENT_MUTATION'
    );
    IF v_operation_type = 'DELETE' THEN
        PERFORM public.verto_remove_sync_snapshot_state(v_org, v_aggregate_type, v_aggregate_id, v_revision);
    ELSE
        PERFORM public.verto_upsert_sync_snapshot_state(
            v_org, v_aggregate_type, v_aggregate_id, v_new_version,
            v_payload_version, v_new_payload, v_revision
        );
    END IF;
    SELECT c.transaction_id INTO v_tx FROM public.verto_sync_change_log c WHERE c.revision = v_revision;

    INSERT INTO public.verto_sync_receipts(
        organization_id, mutation_id, request_hash, aggregate_type, aggregate_id, operation_type,
        status, server_revision, server_version, transaction_id, authoritative_payload
    ) VALUES (
        v_org, v_mutation_id, v_request_hash, v_aggregate_type, v_aggregate_id, v_operation_type,
        'APPLIED', v_revision, v_new_version, v_tx, v_new_payload
    ) RETURNING * INTO v_receipt;
    RETURN public.verto_sync_receipt_json_v309(v_receipt);
END;
$$;

COMMENT ON FUNCTION public.verto_apply_sync_mutation(jsonb) IS
'v309 authenticated idempotent push boundary: tenant+mutation receipt identity, deterministic request hash, optimistic conflicts, fail-closed adapters; runtime V2 remains disabled.';

REVOKE ALL ON FUNCTION public.verto_sync_request_hash_v309(uuid,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_sync_receipt_json_v309(public.verto_sync_receipts) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_validate_push_policy_v309(text,text,integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_sync_adapter_v309(uuid,text,text,text,integer,jsonb,bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_apply_sync_mutation(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verto_apply_sync_mutation(jsonb) TO authenticated;
