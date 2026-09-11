-- v376 price-list cutover.
-- PRICE_LIST now means a reusable template of inventory IDs; item names/prices/stock stay owned by inventory.
-- This migration makes the unified sync adapter authoritative for template payloads and removes the old tables.

CREATE OR REPLACE FUNCTION public.verto_apply_sync_adapter_v309(
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
DECLARE
    v_current_version bigint;
    v_new_version bigint;
BEGIN
    IF p_aggregate_type = 'PRICE_LIST' THEN
        IF p_payload_version <> 1 THEN
            RETURN jsonb_build_object('applied', false, 'validation_code', 'PRICE_LIST_PAYLOAD_VERSION');
        END IF;
        IF COALESCE(p_payload->>'kind','') <> 'TEMPLATE' THEN
            RETURN jsonb_build_object('applied', false, 'validation_code', 'PRICE_LIST_TEMPLATE_PAYLOAD_REQUIRED');
        END IF;
        IF p_operation_type = 'UPSERT' THEN
            IF btrim(COALESCE(p_payload->>'name','')) = '' OR btrim(COALESCE(p_payload->>'itemIds','')) = '' THEN
                RETURN jsonb_build_object('applied', false, 'validation_code', 'PRICE_LIST_TEMPLATE_INVALID');
            END IF;
        ELSIF p_operation_type <> 'DELETE' THEN
            RETURN jsonb_build_object('applied', false, 'validation_code', 'VALIDATION_DELETE_POLICY');
        END IF;

        SELECT s.entity_version INTO v_current_version
          FROM public.verto_sync_snapshot_state s
         WHERE s.organization_id = p_organization_id
           AND s.aggregate_type = 'PRICE_LIST'
           AND s.aggregate_id = p_aggregate_id;
        v_new_version := COALESCE(v_current_version, 0) + 1;

        RETURN jsonb_build_object(
            'applied', true,
            'runtime_safe', true,
            'server_version', v_new_version,
            'authoritative_payload', p_payload
        );
    END IF;

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

-- Bootstrap authority is the unified snapshot/change stream, not the retired copied-price tables.
UPDATE public.verto_sync_bootstrap_coverage_v313
   SET owner_session = 376,
       authoritative_source = 'verto_sync_snapshot_state PRICE_LIST template payload',
       backfill_strategy = 'LATEST_UNIFIED_CHANGE_THEN_DOMAIN_MATERIALIZER',
       future_write_strategy = 'APPEND_CHANGE_AUTO_SNAPSHOT',
       stronger_semantics = false
 WHERE aggregate_type = 'PRICE_LIST';

-- Do not let a historical item-level PRICE_LIST snapshot materialize as a template.
DELETE FROM public.verto_sync_snapshot_state
 WHERE aggregate_type = 'PRICE_LIST'
   AND COALESCE(payload->>'kind','') <> 'TEMPLATE';

-- Hard cutover: these tables were the old copied-item price-list system and have no v376 runtime owner.
-- Deploy v376 as the minimum supported client before applying this migration.
DROP TABLE IF EXISTS public.price_list_items;
DROP TABLE IF EXISTS public.price_list_header;

COMMENT ON FUNCTION public.verto_apply_sync_adapter_v309(uuid,text,text,text,integer,jsonb,bigint) IS
'v376: PRICE_LIST is a reusable inventory-linked template; all other owner307 adapters preserve prior fail-closed behavior.';
