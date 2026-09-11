-- Verto Session 305 — unified sync server EXPAND only.
-- Authority: SESSION_305_FINAL_REVISED_V2.md / verto-unified-sync contract v1.
-- PostgreSQL 17.6 baseline: schema(2).sql sha256 fb83bf253fefe7f7b684a41dc5df6e06b0aa933a2c13e26cb835842e1c7f9ea8
-- No business writer is cut over by this migration.

CREATE SEQUENCE public.verto_sync_change_revision_seq
    AS bigint START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE public.verto_sync_contract (
    contract_family text NOT NULL,
    contract_version integer NOT NULL,
    schema_version integer NOT NULL,
    scope_definition_version integer NOT NULL,
    status text NOT NULL,
    min_available_revision bigint NOT NULL DEFAULT 0,
    default_pull_page_changes integer NOT NULL,
    pull_limit_max integer NOT NULL,
    max_pull_page_bytes integer NOT NULL,
    max_transaction_group_bytes integer NOT NULL,
    max_mutation_payload_bytes integer NOT NULL,
    max_push_batch_mutations integer NOT NULL,
    max_push_batch_bytes integer NOT NULL,
    max_worker_operations_per_run integer NOT NULL,
    max_worker_runtime_millis integer NOT NULL,
    max_backlog_before_diagnostic_warning integer NOT NULL,
    bootstrap_session_ttl_seconds integer NOT NULL,
    bootstrap_page_max_rows integer NOT NULL,
    bootstrap_page_max_bytes integer NOT NULL,
    supported_offline_window_days integer NOT NULL,
    supported_old_client_window_days integer NOT NULL,
    retention_safety_margin_days integer NOT NULL,
    change_log_retention_days integer NOT NULL,
    tombstone_retention_days integer NOT NULL,
    receipt_retention_days integer NOT NULL,
    device_inactive_after_days integer NOT NULL,
    local_acked_outbox_diagnostic_retention_days integer NOT NULL,
    local_inbox_diagnostic_retention_days integer NOT NULL,
    production_pruning_enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (contract_family, contract_version),
    CONSTRAINT verto_sync_contract_family_v1 CHECK (contract_family = 'verto-unified-sync'),
    CONSTRAINT verto_sync_contract_version_v1 CHECK (contract_version = 1),
    CONSTRAINT verto_sync_contract_status_expand CHECK (status = 'EXPAND_ONLY'),
    CONSTRAINT verto_sync_contract_limits CHECK (
        default_pull_page_changes > 0 AND
        default_pull_page_changes <= pull_limit_max AND
        max_mutation_payload_bytes <= max_pull_page_bytes AND
        max_pull_page_bytes <= max_transaction_group_bytes AND
        bootstrap_page_max_rows <= pull_limit_max AND
        bootstrap_page_max_bytes <= max_pull_page_bytes
    ),
    CONSTRAINT verto_sync_contract_retention CHECK (
        tombstone_retention_days >= GREATEST(supported_offline_window_days, supported_old_client_window_days) + retention_safety_margin_days AND
        change_log_retention_days >= supported_offline_window_days + retention_safety_margin_days
    )
);

INSERT INTO public.verto_sync_contract (
    contract_family, contract_version, schema_version, scope_definition_version, status,
    min_available_revision, default_pull_page_changes, pull_limit_max,
    max_pull_page_bytes, max_transaction_group_bytes, max_mutation_payload_bytes,
    max_push_batch_mutations, max_push_batch_bytes,
    max_worker_operations_per_run, max_worker_runtime_millis,
    max_backlog_before_diagnostic_warning,
    bootstrap_session_ttl_seconds, bootstrap_page_max_rows, bootstrap_page_max_bytes,
    supported_offline_window_days, supported_old_client_window_days, retention_safety_margin_days,
    change_log_retention_days, tombstone_retention_days, receipt_retention_days,
    device_inactive_after_days, local_acked_outbox_diagnostic_retention_days,
    local_inbox_diagnostic_retention_days, production_pruning_enabled
) VALUES (
    'verto-unified-sync', 1, 1, 1, 'EXPAND_ONLY',
    0, 100, 200,
    1048576, 2097152, 524288,
    100, 2097152,
    500, 30000,
    5000,
    1800, 200, 1048576,
    90, 30, 30,
    120, 120, 180,
    120, 14, 14, false
);

CREATE TABLE public.verto_sync_scopes (
    scope_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    principal_id uuid NOT NULL,
    contract_family text NOT NULL DEFAULT 'verto-unified-sync',
    contract_version integer NOT NULL DEFAULT 1,
    scope_definition_version integer NOT NULL DEFAULT 1,
    visibility_fingerprint text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    invalidated_at timestamptz,
    CONSTRAINT verto_sync_scope_contract CHECK (contract_family = 'verto-unified-sync' AND contract_version = 1 AND scope_definition_version = 1)
);

CREATE UNIQUE INDEX verto_sync_scopes_active_identity_uq
    ON public.verto_sync_scopes (
        organization_id, principal_id, contract_family, contract_version,
        scope_definition_version, visibility_fingerprint
    ) WHERE invalidated_at IS NULL;
CREATE INDEX verto_sync_scopes_principal_idx
    ON public.verto_sync_scopes (principal_id, organization_id, invalidated_at);

CREATE TABLE public.verto_sync_change_log (
    revision bigint PRIMARY KEY,
    organization_id uuid NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id text NOT NULL,
    operation_type text NOT NULL,
    entity_version bigint,
    payload_version integer NOT NULL,
    payload jsonb NOT NULL,
    origin_mutation_id text,
    transaction_id text NOT NULL,
    actor_user_id uuid,
    source_kind text NOT NULL,
    contract_family text NOT NULL DEFAULT 'verto-unified-sync',
    contract_version integer NOT NULL DEFAULT 1,
    scope_definition_version integer NOT NULL DEFAULT 1,
    visibility_principal_id uuid,
    required_permission text,
    deleted_at timestamptz,
    changed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verto_sync_change_revision_positive CHECK (revision > 0),
    CONSTRAINT verto_sync_change_payload_version_positive CHECK (payload_version > 0),
    CONSTRAINT verto_sync_change_contract CHECK (contract_family = 'verto-unified-sync' AND contract_version = 1 AND scope_definition_version = 1),
    CONSTRAINT verto_sync_change_operation CHECK (operation_type IN ('UPSERT','DELETE','COMMAND','ARCHIVE','VOID','REVERSE','CANCEL')),
    CONSTRAINT verto_sync_change_source CHECK (source_kind IN ('USER_RPC','LEGACY_TABLE_WRITE','SERVER_TRIGGER','SYSTEM_JOB','SERVICE_COMMAND','BRIDGE','MIGRATION')),
    CONSTRAINT verto_sync_change_aggregate CHECK (aggregate_type IN (
        'BUDGET','CASH_MOVEMENT','CASH_RECONCILIATION','CASH_REGISTER','CATEGORY','CLIENT_CREDIT',
        'COMMISSION_PAYMENT','COST_ALLOCATION','CUSTOMER_PROFILE','EDUCATIONAL_CONTENT','EXPENSE',
        'GOODS_RECEIPT','INVENTORY_COST_REVISION','INVENTORY_ITEM','INVENTORY_MOVEMENT','INVENTORY_UNIT',
        'INVOICE','ITEM_CATEGORY','NOTE','NOTIFICATION','OPTIMAL_FOLLOW_UP','OPTIMAL_MAINTENANCE',
        'OPTIMAL_VEHICLE','ORGANIZATION_SETTINGS','PARTY_IDENTITY','PARTY_ROLE','PAYMENT','PRICE_LIST',
        'PURCHASE_MATCH','PURCHASE_ORDER','PURCHASE_PAYMENT_OVERRIDE','REMINDER','SHIPMENT','SUPPLIER_PROFILE'
    ))
);

CREATE INDEX verto_sync_change_log_org_revision_idx
    ON public.verto_sync_change_log (organization_id, revision);
CREATE INDEX verto_sync_change_log_org_tx_revision_idx
    ON public.verto_sync_change_log (organization_id, transaction_id, revision);
CREATE INDEX verto_sync_change_log_entity_revision_idx
    ON public.verto_sync_change_log (organization_id, aggregate_type, aggregate_id, revision DESC);
CREATE INDEX verto_sync_change_log_origin_mutation_idx
    ON public.verto_sync_change_log (origin_mutation_id) WHERE origin_mutation_id IS NOT NULL;

CREATE TABLE public.verto_sync_receipts (
    organization_id uuid NOT NULL,
    mutation_id text NOT NULL,
    request_hash text NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id text NOT NULL,
    operation_type text NOT NULL,
    status text NOT NULL,
    server_revision bigint,
    server_version bigint,
    transaction_id text,
    authoritative_payload jsonb,
    conflict_code text,
    validation_code text,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, mutation_id)
);

-- Opaque cursors are server-owned random tokens with stable scope+revision identity.
-- The token contains no client-arithmetic revision representation.
CREATE TABLE public.verto_sync_cursor_tokens (
    cursor_token uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_id uuid NOT NULL REFERENCES public.verto_sync_scopes(scope_id) ON DELETE CASCADE,
    revision bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verto_sync_cursor_revision_nonnegative CHECK (revision >= 0),
    UNIQUE (scope_id, revision)
);

-- Producer-owned adapters (307/310/etc.) will populate this state atomically with their business write/change append.
-- 305 intentionally does not invent business payload adapters or backfill existing data.
CREATE TABLE public.verto_sync_snapshot_state (
    organization_id uuid NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id text NOT NULL,
    entity_version bigint,
    payload_version integer NOT NULL,
    payload jsonb NOT NULL,
    partition_key text NOT NULL DEFAULT 'default',
    visibility_principal_id uuid,
    required_permission text,
    updated_revision bigint NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, aggregate_type, aggregate_id),
    CONSTRAINT verto_sync_snapshot_revision_positive CHECK (updated_revision > 0),
    CONSTRAINT verto_sync_snapshot_payload_version_positive CHECK (payload_version > 0)
);
CREATE INDEX verto_sync_snapshot_partition_idx
    ON public.verto_sync_snapshot_state (organization_id, aggregate_type, partition_key, aggregate_id);

CREATE TABLE public.verto_sync_bootstrap_sessions (
    bootstrap_session_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_id uuid NOT NULL REFERENCES public.verto_sync_scopes(scope_id) ON DELETE CASCADE,
    organization_id uuid NOT NULL,
    principal_id uuid NOT NULL,
    contract_family text NOT NULL DEFAULT 'verto-unified-sync',
    contract_version integer NOT NULL DEFAULT 1,
    scope_definition_version integer NOT NULL DEFAULT 1,
    baseline_revision bigint NOT NULL,
    baseline_cursor text NOT NULL,
    snapshot_row_count bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    completed_at timestamptz,
    state text NOT NULL,
    CONSTRAINT verto_sync_bootstrap_state CHECK (state IN ('IN_PROGRESS','READY','COMPLETED','EXPIRED','INVALIDATED')),
    CONSTRAINT verto_sync_bootstrap_contract CHECK (contract_family = 'verto-unified-sync' AND contract_version = 1 AND scope_definition_version = 1)
);
CREATE INDEX verto_sync_bootstrap_sessions_scope_idx
    ON public.verto_sync_bootstrap_sessions (scope_id, state, expires_at);

CREATE TABLE public.verto_sync_bootstrap_rows (
    bootstrap_session_id uuid NOT NULL REFERENCES public.verto_sync_bootstrap_sessions(bootstrap_session_id) ON DELETE CASCADE,
    ordinal bigint NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id text NOT NULL,
    entity_version bigint,
    payload_version integer NOT NULL,
    payload jsonb NOT NULL,
    partition_key text NOT NULL,
    PRIMARY KEY (bootstrap_session_id, ordinal)
);

CREATE TABLE public.verto_sync_bootstrap_page_tokens (
    page_token uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bootstrap_session_id uuid NOT NULL REFERENCES public.verto_sync_bootstrap_sessions(bootstrap_session_id) ON DELETE CASCADE,
    after_ordinal bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (bootstrap_session_id, after_ordinal)
);

CREATE TABLE public.verto_sync_manifest_partition_tokens (
    partition_token uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_id uuid NOT NULL REFERENCES public.verto_sync_scopes(scope_id) ON DELETE CASCADE,
    aggregate_type text NOT NULL,
    partition_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (scope_id, aggregate_type, partition_key)
);

-- All sync metadata is RPC-only; RLS is defense in depth and no direct client policies are created.
ALTER TABLE public.verto_sync_contract ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_scopes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_change_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_cursor_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_snapshot_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_bootstrap_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_bootstrap_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_bootstrap_page_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verto_sync_manifest_partition_tokens ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION public.verto_expected_payload_version(p_aggregate_type text) RETURNS integer
    LANGUAGE sql IMMUTABLE
    SET search_path TO 'public'
AS $$
    SELECT CASE p_aggregate_type
        WHEN 'CUSTOMER_PROFILE' THEN 2
        WHEN 'SUPPLIER_PROFILE' THEN 2
        WHEN 'BUDGET' THEN 1 WHEN 'CASH_MOVEMENT' THEN 1 WHEN 'CASH_RECONCILIATION' THEN 1
        WHEN 'CASH_REGISTER' THEN 1 WHEN 'CATEGORY' THEN 1 WHEN 'CLIENT_CREDIT' THEN 1
        WHEN 'COMMISSION_PAYMENT' THEN 1 WHEN 'COST_ALLOCATION' THEN 1 WHEN 'EDUCATIONAL_CONTENT' THEN 1
        WHEN 'EXPENSE' THEN 1 WHEN 'GOODS_RECEIPT' THEN 1 WHEN 'INVENTORY_COST_REVISION' THEN 1
        WHEN 'INVENTORY_ITEM' THEN 1 WHEN 'INVENTORY_MOVEMENT' THEN 1 WHEN 'INVENTORY_UNIT' THEN 1
        WHEN 'INVOICE' THEN 1 WHEN 'ITEM_CATEGORY' THEN 1 WHEN 'NOTE' THEN 1 WHEN 'NOTIFICATION' THEN 1
        WHEN 'OPTIMAL_FOLLOW_UP' THEN 1 WHEN 'OPTIMAL_MAINTENANCE' THEN 1 WHEN 'OPTIMAL_VEHICLE' THEN 1
        WHEN 'ORGANIZATION_SETTINGS' THEN 1 WHEN 'PARTY_IDENTITY' THEN 1 WHEN 'PARTY_ROLE' THEN 1
        WHEN 'PAYMENT' THEN 1 WHEN 'PRICE_LIST' THEN 1 WHEN 'PURCHASE_MATCH' THEN 1 WHEN 'PURCHASE_ORDER' THEN 1
        WHEN 'PURCHASE_PAYMENT_OVERRIDE' THEN 1 WHEN 'REMINDER' THEN 1 WHEN 'SHIPMENT' THEN 1
        ELSE NULL
    END;
$$;

CREATE FUNCTION public.verto_current_visibility_fingerprint() RETURNS text
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_org uuid;
    v_role text;
    v_active boolean;
    v_permissions jsonb;
    v_material text;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE = '28000';
    END IF;

    SELECT au.organization_id, au.role, COALESCE(au.is_active, true)
      INTO v_org, v_role, v_active
      FROM public.app_users au
     WHERE au.id = v_uid;

    IF v_org IS NULL OR NOT v_active THEN
        RAISE EXCEPTION 'AUTH: active Verto membership required' USING ERRCODE = '28000';
    END IF;

    SELECT COALESCE(ep.permissions, '{}'::jsonb)
      INTO v_permissions
      FROM public.employee_permissions ep
     WHERE ep.user_id = v_uid AND ep.org_id = v_org;
    v_permissions := COALESCE(v_permissions, '{}'::jsonb);

    v_material := concat_ws('|',
        v_uid::text, v_org::text, v_role, v_active::text,
        v_permissions::text, 'verto-unified-sync', '1', '1'
    );

    RETURN encode(extensions.digest(convert_to(v_material, 'UTF8'), 'sha256'), 'hex');
END;
$$;

CREATE FUNCTION public.verto_validate_sync_scope(p_scope_id uuid)
RETURNS TABLE(organization_id uuid, principal_id uuid, visibility_fingerprint text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_scope public.verto_sync_scopes%ROWTYPE;
    v_current_fp text;
    v_current_org uuid;
    v_active boolean;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE = '28000';
    END IF;

    SELECT * INTO v_scope
      FROM public.verto_sync_scopes s
     WHERE s.scope_id = p_scope_id;

    IF NOT FOUND OR v_scope.principal_id <> v_uid THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: scope does not belong to current principal' USING ERRCODE = '22023';
    END IF;

    SELECT au.organization_id, COALESCE(au.is_active, true)
      INTO v_current_org, v_active
      FROM public.app_users au
     WHERE au.id = v_uid;

    IF v_current_org IS NULL OR NOT v_active THEN
        RAISE EXCEPTION 'AUTH: active Verto membership required' USING ERRCODE = '28000';
    END IF;

    IF v_scope.invalidated_at IS NOT NULL OR v_scope.organization_id <> v_current_org OR
       v_scope.contract_family <> 'verto-unified-sync' OR v_scope.contract_version <> 1 OR
       v_scope.scope_definition_version <> 1 THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: scope is inactive or contract-bound elsewhere' USING ERRCODE = '22023';
    END IF;

    v_current_fp := public.verto_current_visibility_fingerprint();
    IF v_current_fp <> v_scope.visibility_fingerprint THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: visibility contract changed; bootstrap a new scope' USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT v_scope.organization_id, v_scope.principal_id, v_scope.visibility_fingerprint;
END;
$$;

CREATE FUNCTION public.verto_resolve_sync_scope()
RETURNS TABLE(
    scope_id uuid,
    organization_id uuid,
    sync_principal_id uuid,
    contract_family text,
    contract_version integer,
    scope_definition_version integer
)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_org uuid;
    v_active boolean;
    v_fp text;
    v_scope_id uuid;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'AUTH: authentication required' USING ERRCODE = '28000';
    END IF;

    SELECT au.organization_id, COALESCE(au.is_active, true)
      INTO v_org, v_active
      FROM public.app_users au
     WHERE au.id = v_uid;
    IF v_org IS NULL OR NOT v_active THEN
        RAISE EXCEPTION 'AUTH: active Verto membership required' USING ERRCODE = '28000';
    END IF;

    v_fp := public.verto_current_visibility_fingerprint();

    UPDATE public.verto_sync_scopes s
       SET invalidated_at = now()
     WHERE s.principal_id = v_uid
       AND s.organization_id = v_org
       AND s.contract_family = 'verto-unified-sync'
       AND s.contract_version = 1
       AND s.scope_definition_version = 1
       AND s.invalidated_at IS NULL
       AND s.visibility_fingerprint <> v_fp;

    SELECT s.scope_id INTO v_scope_id
      FROM public.verto_sync_scopes s
     WHERE s.principal_id = v_uid
       AND s.organization_id = v_org
       AND s.contract_family = 'verto-unified-sync'
       AND s.contract_version = 1
       AND s.scope_definition_version = 1
       AND s.visibility_fingerprint = v_fp
       AND s.invalidated_at IS NULL
     LIMIT 1;

    IF v_scope_id IS NULL THEN
        INSERT INTO public.verto_sync_scopes (
            organization_id, principal_id, contract_family, contract_version,
            scope_definition_version, visibility_fingerprint
        ) VALUES (v_org, v_uid, 'verto-unified-sync', 1, 1, v_fp)
        ON CONFLICT (organization_id, principal_id, contract_family, contract_version,
                     scope_definition_version, visibility_fingerprint)
        WHERE invalidated_at IS NULL
        DO UPDATE SET visibility_fingerprint = EXCLUDED.visibility_fingerprint
        RETURNING verto_sync_scopes.scope_id INTO v_scope_id;
    END IF;

    RETURN QUERY SELECT v_scope_id, v_org, v_uid, 'verto-unified-sync'::text, 1, 1;
END;
$$;

CREATE FUNCTION public.verto_assign_sync_change_revision() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
AS $$
BEGIN
    IF NEW.organization_id IS NULL THEN
        RAISE EXCEPTION 'VALIDATION: organization_id required' USING ERRCODE = '23502';
    END IF;
    IF NEW.revision IS NOT NULL THEN
        RAISE EXCEPTION 'VALIDATION: revision is server-owned' USING ERRCODE = '22023';
    END IF;
    IF NEW.transaction_id IS NOT NULL THEN
        RAISE EXCEPTION 'VALIDATION: transaction_id is server-owned' USING ERRCODE = '22023';
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('verto-sync-revision:' || NEW.organization_id::text, 0)
    );
    NEW.revision := nextval('public.verto_sync_change_revision_seq'::regclass);
    NEW.transaction_id := pg_catalog.pg_current_xact_id()::text;
    NEW.actor_user_id := COALESCE(NEW.actor_user_id, auth.uid());
    RETURN NEW;
END;
$$;

CREATE FUNCTION public.verto_reject_sync_change_log_mutation() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
AS $$
BEGIN
    RAISE EXCEPTION 'VALIDATION: verto_sync_change_log is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE FUNCTION public.verto_reject_sync_receipt_mutation() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
AS $$
BEGIN
    RAISE EXCEPTION 'VALIDATION: terminal sync receipts are immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER verto_sync_change_assign_revision
    BEFORE INSERT ON public.verto_sync_change_log
    FOR EACH ROW EXECUTE FUNCTION public.verto_assign_sync_change_revision();
CREATE TRIGGER verto_sync_change_immutable
    BEFORE UPDATE OR DELETE ON public.verto_sync_change_log
    FOR EACH ROW EXECUTE FUNCTION public.verto_reject_sync_change_log_mutation();
CREATE TRIGGER verto_sync_receipt_immutable
    BEFORE UPDATE OR DELETE ON public.verto_sync_receipts
    FOR EACH ROW EXECUTE FUNCTION public.verto_reject_sync_receipt_mutation();

CREATE FUNCTION public.verto_append_sync_change(
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
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
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

    RETURN v_revision;
END;
$$;

CREATE FUNCTION public.verto_upsert_sync_snapshot_state(
    p_organization_id uuid,
    p_aggregate_type text,
    p_aggregate_id text,
    p_entity_version bigint,
    p_payload_version integer,
    p_payload jsonb,
    p_updated_revision bigint,
    p_partition_key text DEFAULT 'default',
    p_visibility_principal_id uuid DEFAULT NULL,
    p_required_permission text DEFAULT NULL
) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_expected integer;
BEGIN
    v_expected := public.verto_expected_payload_version(p_aggregate_type);
    IF v_expected IS NULL OR v_expected <> p_payload_version THEN
        RAISE EXCEPTION 'CONTRACT_UNSUPPORTED: aggregate/payload version' USING ERRCODE = '22023';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.verto_sync_change_log c
         WHERE c.organization_id = p_organization_id
           AND c.revision = p_updated_revision
           AND c.aggregate_type = p_aggregate_type
           AND c.aggregate_id = p_aggregate_id
    ) THEN
        RAISE EXCEPTION 'VALIDATION: snapshot state requires a matching committed-in-transaction change revision' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.verto_sync_snapshot_state (
        organization_id, aggregate_type, aggregate_id, entity_version, payload_version,
        payload, partition_key, visibility_principal_id, required_permission, updated_revision, updated_at
    ) VALUES (
        p_organization_id, p_aggregate_type, p_aggregate_id, p_entity_version, p_payload_version,
        p_payload, COALESCE(NULLIF(p_partition_key,''), 'default'), p_visibility_principal_id,
        p_required_permission, p_updated_revision, now()
    )
    ON CONFLICT (organization_id, aggregate_type, aggregate_id) DO UPDATE SET
        entity_version = EXCLUDED.entity_version,
        payload_version = EXCLUDED.payload_version,
        payload = EXCLUDED.payload,
        partition_key = EXCLUDED.partition_key,
        visibility_principal_id = EXCLUDED.visibility_principal_id,
        required_permission = EXCLUDED.required_permission,
        updated_revision = EXCLUDED.updated_revision,
        updated_at = EXCLUDED.updated_at
    WHERE public.verto_sync_snapshot_state.updated_revision < EXCLUDED.updated_revision;
END;
$$;

CREATE FUNCTION public.verto_remove_sync_snapshot_state(
    p_organization_id uuid,
    p_aggregate_type text,
    p_aggregate_id text,
    p_delete_revision bigint
) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.verto_sync_change_log c
         WHERE c.organization_id = p_organization_id
           AND c.revision = p_delete_revision
           AND c.aggregate_type = p_aggregate_type
           AND c.aggregate_id = p_aggregate_id
           AND c.operation_type = 'DELETE'
    ) THEN
        RAISE EXCEPTION 'VALIDATION: snapshot removal requires a matching DELETE change revision' USING ERRCODE = '22023';
    END IF;

    DELETE FROM public.verto_sync_snapshot_state s
     WHERE s.organization_id = p_organization_id
       AND s.aggregate_type = p_aggregate_type
       AND s.aggregate_id = p_aggregate_id
       AND s.updated_revision < p_delete_revision;
END;
$$;

CREATE FUNCTION public.verto_encode_sync_cursor(p_scope_id uuid, p_revision bigint) RETURNS text
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_token uuid;
BEGIN
    PERFORM 1 FROM public.verto_validate_sync_scope(p_scope_id);
    IF p_revision IS NULL OR p_revision < 0 THEN
        RAISE EXCEPTION 'VALIDATION: cursor revision must be non-negative' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.verto_sync_cursor_tokens (scope_id, revision)
    VALUES (p_scope_id, p_revision)
    ON CONFLICT (scope_id, revision) DO UPDATE SET revision = EXCLUDED.revision
    RETURNING cursor_token INTO v_token;
    RETURN v_token::text;
END;
$$;

CREATE FUNCTION public.verto_decode_sync_cursor(p_scope_id uuid, p_cursor text) RETURNS bigint
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_token uuid;
    v_scope uuid;
    v_revision bigint;
BEGIN
    PERFORM 1 FROM public.verto_validate_sync_scope(p_scope_id);
    IF p_cursor IS NULL OR btrim(p_cursor) = '' THEN
        RAISE EXCEPTION 'VALIDATION: cursor must be nonblank' USING ERRCODE = '22023';
    END IF;
    BEGIN
        v_token := p_cursor::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'VALIDATION: malformed cursor' USING ERRCODE = '22023';
    END;

    SELECT t.scope_id, t.revision INTO v_scope, v_revision
      FROM public.verto_sync_cursor_tokens t
     WHERE t.cursor_token = v_token;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'VALIDATION: unknown or tampered cursor' USING ERRCODE = '22023';
    END IF;
    IF v_scope <> p_scope_id THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: cursor belongs to another scope' USING ERRCODE = '22023';
    END IF;
    RETURN v_revision;
END;
$$;

CREATE FUNCTION public.verto_pull_sync_changes(
    p_scope_id uuid,
    p_after_cursor text DEFAULT NULL,
    p_limit integer DEFAULT 100
) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_org uuid;
    v_uid uuid;
    v_after bigint := 0;
    v_limit integer := COALESCE(p_limit, 100);
    v_min bigint;
    v_high bigint;
    v_first_group_bytes bigint;
    v_last bigint;
    v_changes jsonb := '[]'::jsonb;
    v_has_more boolean := false;
    v_next_cursor text;
BEGIN
    SELECT v.organization_id, v.principal_id INTO v_org, v_uid
      FROM public.verto_validate_sync_scope(p_scope_id) v;

    IF v_limit < 1 OR v_limit > 200 THEN
        RAISE EXCEPTION 'VALIDATION: p_limit must be between 1 and 200' USING ERRCODE = '22023';
    END IF;
    IF p_after_cursor IS NOT NULL AND btrim(p_after_cursor) <> '' THEN
        v_after := public.verto_decode_sync_cursor(p_scope_id, p_after_cursor);
    END IF;

    SELECT c.min_available_revision INTO v_min
      FROM public.verto_sync_contract c
     WHERE c.contract_family = 'verto-unified-sync' AND c.contract_version = 1;
    IF v_after < v_min THEN
        RAISE EXCEPTION 'CURSOR_EXPIRED: bootstrap required' USING ERRCODE = '22023';
    END IF;

    SELECT COALESCE(max(cl.revision), v_after) INTO v_high
      FROM public.verto_sync_change_log cl
     WHERE cl.organization_id = v_org
       AND (cl.visibility_principal_id IS NULL OR cl.visibility_principal_id = v_uid)
       AND (cl.required_permission IS NULL OR public.has_perm(cl.required_permission));

    WITH visible AS (
        SELECT cl.*
          FROM public.verto_sync_change_log cl
         WHERE cl.organization_id = v_org
           AND cl.revision > v_after
           AND (cl.visibility_principal_id IS NULL OR cl.visibility_principal_id = v_uid)
           AND (cl.required_permission IS NULL OR public.has_perm(cl.required_permission))
    ), groups AS (
        SELECT transaction_id, min(revision) AS first_revision,
               sum(octet_length(to_jsonb(visible)::text))::bigint AS group_bytes
          FROM visible GROUP BY transaction_id
    )
    SELECT group_bytes INTO v_first_group_bytes
      FROM groups ORDER BY first_revision LIMIT 1;

    IF COALESCE(v_first_group_bytes, 0) > 2097152 THEN
        RAISE EXCEPTION 'CONTRACT_PAYLOAD_TOO_LARGE: transaction group exceeds 2 MiB' USING ERRCODE = '22023';
    END IF;

    WITH visible AS (
        SELECT cl.*
          FROM public.verto_sync_change_log cl
         WHERE cl.organization_id = v_org
           AND cl.revision > v_after
           AND (cl.visibility_principal_id IS NULL OR cl.visibility_principal_id = v_uid)
           AND (cl.required_permission IS NULL OR public.has_perm(cl.required_permission))
    ), groups AS (
        SELECT transaction_id, min(revision) AS first_revision, max(revision) AS last_revision,
               count(*)::bigint AS group_rows,
               sum(octet_length(to_jsonb(visible)::text))::bigint AS group_bytes
          FROM visible GROUP BY transaction_id
    ), ranked AS (
        SELECT g.*,
               row_number() OVER (ORDER BY first_revision) AS group_rank,
               sum(group_rows) OVER (ORDER BY first_revision ROWS UNBOUNDED PRECEDING) AS cumulative_rows,
               sum(group_bytes) OVER (ORDER BY first_revision ROWS UNBOUNDED PRECEDING) AS cumulative_bytes
          FROM groups g
    ), selected_groups AS (
        SELECT * FROM ranked
         WHERE group_bytes <= 2097152
           AND (
               (cumulative_rows <= v_limit AND cumulative_bytes <= 1048576)
               OR (group_rank = 1 AND (group_rows > v_limit OR group_bytes > 1048576))
           )
    ), page_rows AS (
        SELECT v.*,
               row_number() OVER (PARTITION BY v.transaction_id ORDER BY v.revision)::integer AS transaction_order,
               count(*) OVER (PARTITION BY v.transaction_id)::integer AS transaction_size
          FROM visible v
          JOIN selected_groups g ON g.transaction_id = v.transaction_id
    ), page_json AS (
        SELECT COALESCE(jsonb_agg(
            jsonb_build_object(
                'revision', revision,
                'organizationId', organization_id,
                'syncScopeId', p_scope_id,
                'aggregateType', aggregate_type,
                'aggregateId', aggregate_id,
                'operationType', operation_type,
                'entityVersion', entity_version,
                'payloadVersion', payload_version,
                'payload', payload,
                'originMutationId', origin_mutation_id,
                'transactionId', transaction_id,
                'transactionOrder', transaction_order,
                'transactionSize', transaction_size,
                'deletedAtEpochMillis', CASE WHEN deleted_at IS NULL THEN NULL ELSE floor(extract(epoch FROM deleted_at) * 1000)::bigint END,
                'changedAtEpochMillis', floor(extract(epoch FROM changed_at) * 1000)::bigint
            ) ORDER BY revision
        ), '[]'::jsonb) AS changes,
        max(revision) AS last_revision
        FROM page_rows
    )
    SELECT changes, last_revision INTO v_changes, v_last FROM page_json;

    v_last := COALESCE(v_last, v_after);
    SELECT EXISTS (
        SELECT 1 FROM public.verto_sync_change_log cl
         WHERE cl.organization_id = v_org
           AND cl.revision > v_last
           AND (cl.visibility_principal_id IS NULL OR cl.visibility_principal_id = v_uid)
           AND (cl.required_permission IS NULL OR public.has_perm(cl.required_permission))
    ) INTO v_has_more;

    IF v_last = v_after AND p_after_cursor IS NOT NULL AND btrim(p_after_cursor) <> '' THEN
        v_next_cursor := p_after_cursor;
    ELSE
        v_next_cursor := public.verto_encode_sync_cursor(p_scope_id, v_last);
    END IF;

    RETURN jsonb_build_object(
        'contract_family', 'verto-unified-sync',
        'contract_version', 1,
        'scope_id', p_scope_id,
        'coverage', 'GLOBAL_SCOPE',
        'changes', v_changes,
        'next_cursor', v_next_cursor,
        'has_more', v_has_more,
        'min_available_revision', v_min,
        'page_high_watermark', v_high,
        'ends_at_transaction_boundary', true,
        'advances_global_cursor', true
    );
END;
$$;

CREATE FUNCTION public.verto_begin_sync_bootstrap(p_scope_id uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_org uuid;
    v_uid uuid;
    v_session uuid := gen_random_uuid();
    v_baseline bigint;
    v_cursor text;
    v_count bigint;
    v_first_token uuid;
    v_ttl integer;
BEGIN
    SELECT v.organization_id, v.principal_id INTO v_org, v_uid
      FROM public.verto_validate_sync_scope(p_scope_id) v;

    -- Same lock family as revision assignment: snapshot and baseline are one safe handshake.
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('verto-sync-revision:' || v_org::text, 0)
    );

    SELECT COALESCE(max(cl.revision), 0) INTO v_baseline
      FROM public.verto_sync_change_log cl
     WHERE cl.organization_id = v_org
       AND (cl.visibility_principal_id IS NULL OR cl.visibility_principal_id = v_uid)
       AND (cl.required_permission IS NULL OR public.has_perm(cl.required_permission));

    v_cursor := public.verto_encode_sync_cursor(p_scope_id, v_baseline);
    SELECT bootstrap_session_ttl_seconds INTO v_ttl
      FROM public.verto_sync_contract
     WHERE contract_family = 'verto-unified-sync' AND contract_version = 1;

    INSERT INTO public.verto_sync_bootstrap_sessions (
        bootstrap_session_id, scope_id, organization_id, principal_id,
        contract_family, contract_version, scope_definition_version,
        baseline_revision, baseline_cursor, snapshot_row_count,
        expires_at, state
    ) VALUES (
        v_session, p_scope_id, v_org, v_uid,
        'verto-unified-sync', 1, 1,
        v_baseline, v_cursor, 0,
        now() + make_interval(secs => v_ttl), 'IN_PROGRESS'
    );

    INSERT INTO public.verto_sync_bootstrap_rows (
        bootstrap_session_id, ordinal, aggregate_type, aggregate_id,
        entity_version, payload_version, payload, partition_key
    )
    SELECT v_session,
           row_number() OVER (ORDER BY s.aggregate_type, s.partition_key, s.aggregate_id),
           s.aggregate_type, s.aggregate_id, s.entity_version, s.payload_version, s.payload, s.partition_key
      FROM public.verto_sync_snapshot_state s
     WHERE s.organization_id = v_org
       AND s.updated_revision <= v_baseline
       AND (s.visibility_principal_id IS NULL OR s.visibility_principal_id = v_uid)
       AND (s.required_permission IS NULL OR public.has_perm(s.required_permission))
     ORDER BY s.aggregate_type, s.partition_key, s.aggregate_id;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    UPDATE public.verto_sync_bootstrap_sessions
       SET snapshot_row_count = v_count, state = 'READY'
     WHERE bootstrap_session_id = v_session;

    INSERT INTO public.verto_sync_bootstrap_page_tokens (bootstrap_session_id, after_ordinal)
    VALUES (v_session, 0)
    RETURNING page_token INTO v_first_token;

    RETURN jsonb_build_object(
        'contract_family', 'verto-unified-sync',
        'contract_version', 1,
        'scope_id', p_scope_id,
        'bootstrap_session_id', v_session,
        'baseline_revision', v_baseline,
        'baseline_cursor', v_cursor,
        'snapshot_row_count', v_count,
        'first_page_token', v_first_token::text,
        'expires_at_epoch_millis', floor(extract(epoch FROM (now() + make_interval(secs => v_ttl))) * 1000)::bigint
    );
END;
$$;

CREATE FUNCTION public.verto_pull_bootstrap_page(
    p_bootstrap_session_id uuid,
    p_page_token text,
    p_limit integer DEFAULT 100
) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_session public.verto_sync_bootstrap_sessions%ROWTYPE;
    v_token uuid;
    v_after bigint;
    v_limit integer := COALESCE(p_limit, 100);
    v_rows jsonb := '[]'::jsonb;
    v_last bigint;
    v_has_more boolean;
    v_next_token uuid;
    v_first_bytes bigint;
BEGIN
    IF v_limit < 1 OR v_limit > 200 THEN
        RAISE EXCEPTION 'VALIDATION: bootstrap p_limit must be between 1 and 200' USING ERRCODE = '22023';
    END IF;
    BEGIN
        v_token := p_page_token::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'VALIDATION: malformed bootstrap page token' USING ERRCODE = '22023';
    END;

    SELECT * INTO v_session FROM public.verto_sync_bootstrap_sessions s
     WHERE s.bootstrap_session_id = p_bootstrap_session_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'BOOTSTRAP_RESTART_REQUIRED: session not found' USING ERRCODE = '22023';
    END IF;
    PERFORM 1 FROM public.verto_validate_sync_scope(v_session.scope_id);
    IF v_session.principal_id <> auth.uid() OR v_session.expires_at <= now() OR v_session.state NOT IN ('READY','IN_PROGRESS') THEN
        RAISE EXCEPTION 'BOOTSTRAP_RESTART_REQUIRED: expired, invalidated or foreign session' USING ERRCODE = '22023';
    END IF;

    SELECT t.after_ordinal INTO v_after
      FROM public.verto_sync_bootstrap_page_tokens t
     WHERE t.page_token = v_token AND t.bootstrap_session_id = p_bootstrap_session_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'SCOPE_MISMATCH: bootstrap token does not belong to session' USING ERRCODE = '22023';
    END IF;

    SELECT octet_length(jsonb_build_object(
        'aggregateType', r.aggregate_type, 'aggregateId', r.aggregate_id,
        'entityVersion', r.entity_version, 'payloadVersion', r.payload_version,
        'payload', r.payload, 'partitionKey', r.partition_key
    )::text)::bigint
    INTO v_first_bytes
    FROM public.verto_sync_bootstrap_rows r
    WHERE r.bootstrap_session_id = p_bootstrap_session_id AND r.ordinal > v_after
    ORDER BY r.ordinal LIMIT 1;
    IF COALESCE(v_first_bytes, 0) > 1048576 THEN
        RAISE EXCEPTION 'CONTRACT_PAYLOAD_TOO_LARGE: bootstrap row exceeds 1 MiB' USING ERRCODE = '22023';
    END IF;

    WITH candidates AS (
        SELECT r.*,
               octet_length(jsonb_build_object(
                   'aggregateType', r.aggregate_type, 'aggregateId', r.aggregate_id,
                   'entityVersion', r.entity_version, 'payloadVersion', r.payload_version,
                   'payload', r.payload, 'partitionKey', r.partition_key
               )::text)::bigint AS row_bytes
          FROM public.verto_sync_bootstrap_rows r
         WHERE r.bootstrap_session_id = p_bootstrap_session_id AND r.ordinal > v_after
         ORDER BY r.ordinal
    ), ranked AS (
        SELECT c.*,
               row_number() OVER (ORDER BY ordinal) AS rn,
               sum(row_bytes) OVER (ORDER BY ordinal ROWS UNBOUNDED PRECEDING) AS cumulative_bytes
          FROM candidates c
    ), selected AS (
        SELECT * FROM ranked WHERE rn <= v_limit AND cumulative_bytes <= 1048576
    )
    SELECT COALESCE(jsonb_agg(jsonb_build_object(
               'ordinal', ordinal,
               'aggregateType', aggregate_type,
               'aggregateId', aggregate_id,
               'entityVersion', entity_version,
               'payloadVersion', payload_version,
               'payload', payload,
               'partitionKey', partition_key
           ) ORDER BY ordinal), '[]'::jsonb), max(ordinal)
      INTO v_rows, v_last
      FROM selected;

    v_last := COALESCE(v_last, v_after);
    SELECT EXISTS (
        SELECT 1 FROM public.verto_sync_bootstrap_rows r
         WHERE r.bootstrap_session_id = p_bootstrap_session_id AND r.ordinal > v_last
    ) INTO v_has_more;

    IF v_has_more THEN
        INSERT INTO public.verto_sync_bootstrap_page_tokens (bootstrap_session_id, after_ordinal)
        VALUES (p_bootstrap_session_id, v_last)
        ON CONFLICT (bootstrap_session_id, after_ordinal) DO UPDATE SET after_ordinal = EXCLUDED.after_ordinal
        RETURNING page_token INTO v_next_token;
    ELSE
        UPDATE public.verto_sync_bootstrap_sessions
           SET state = 'COMPLETED', completed_at = COALESCE(completed_at, now())
         WHERE bootstrap_session_id = p_bootstrap_session_id;
    END IF;

    RETURN jsonb_build_object(
        'bootstrap_session_id', p_bootstrap_session_id,
        'baseline_cursor', v_session.baseline_cursor,
        'rows', v_rows,
        'has_more', v_has_more,
        'next_page_token', CASE WHEN v_has_more THEN v_next_token::text ELSE NULL END,
        'snapshot_complete', NOT v_has_more
    );
END;
$$;

CREATE FUNCTION public.verto_get_reconciliation_manifest(
    p_scope_id uuid,
    p_partition_token text DEFAULT NULL
) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
AS $$
DECLARE
    v_org uuid;
    v_uid uuid;
    v_token uuid;
    v_aggregate text;
    v_partition text;
    v_next_aggregate text;
    v_next_partition text;
    v_next_token uuid;
    v_count bigint;
    v_digest text;
    v_revision bigint;
BEGIN
    SELECT v.organization_id, v.principal_id INTO v_org, v_uid
      FROM public.verto_validate_sync_scope(p_scope_id) v;

    IF p_partition_token IS NULL OR btrim(p_partition_token) = '' THEN
        SELECT s.aggregate_type, s.partition_key INTO v_aggregate, v_partition
          FROM public.verto_sync_snapshot_state s
         WHERE s.organization_id = v_org
           AND (s.visibility_principal_id IS NULL OR s.visibility_principal_id = v_uid)
           AND (s.required_permission IS NULL OR public.has_perm(s.required_permission))
         GROUP BY s.aggregate_type, s.partition_key
         ORDER BY s.aggregate_type, s.partition_key LIMIT 1;
    ELSE
        BEGIN
            v_token := p_partition_token::uuid;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'VALIDATION: malformed manifest token' USING ERRCODE = '22023';
        END;
        SELECT t.aggregate_type, t.partition_key INTO v_aggregate, v_partition
          FROM public.verto_sync_manifest_partition_tokens t
         WHERE t.partition_token = v_token AND t.scope_id = p_scope_id;
        IF NOT FOUND THEN
            IF EXISTS (SELECT 1 FROM public.verto_sync_manifest_partition_tokens t WHERE t.partition_token = v_token) THEN
                RAISE EXCEPTION 'SCOPE_MISMATCH: manifest token belongs to another scope' USING ERRCODE = '22023';
            END IF;
            RAISE EXCEPTION 'VALIDATION: unknown manifest token' USING ERRCODE = '22023';
        END IF;
    END IF;

    IF v_aggregate IS NULL THEN
        RETURN jsonb_build_object('scope_id', p_scope_id, 'manifest', NULL, 'has_more', false, 'next_partition_token', NULL);
    END IF;

    SELECT count(*),
           encode(extensions.digest(convert_to(COALESCE(string_agg(
               concat_ws('|', s.aggregate_id, COALESCE(s.entity_version::text,'null'), s.payload_version::text, s.payload::text),
               E'\n' ORDER BY s.aggregate_id
           ), ''), 'UTF8'), 'sha256'), 'hex'),
           COALESCE(max(s.updated_revision), 0)
      INTO v_count, v_digest, v_revision
      FROM public.verto_sync_snapshot_state s
     WHERE s.organization_id = v_org
       AND s.aggregate_type = v_aggregate
       AND s.partition_key = v_partition
       AND (s.visibility_principal_id IS NULL OR s.visibility_principal_id = v_uid)
       AND (s.required_permission IS NULL OR public.has_perm(s.required_permission));

    SELECT q.aggregate_type, q.partition_key INTO v_next_aggregate, v_next_partition
      FROM (
        SELECT s.aggregate_type, s.partition_key
          FROM public.verto_sync_snapshot_state s
         WHERE s.organization_id = v_org
           AND (s.visibility_principal_id IS NULL OR s.visibility_principal_id = v_uid)
           AND (s.required_permission IS NULL OR public.has_perm(s.required_permission))
         GROUP BY s.aggregate_type, s.partition_key
      ) q
     WHERE (q.aggregate_type, q.partition_key) > (v_aggregate, v_partition)
     ORDER BY q.aggregate_type, q.partition_key LIMIT 1;

    IF v_next_aggregate IS NOT NULL THEN
        INSERT INTO public.verto_sync_manifest_partition_tokens (scope_id, aggregate_type, partition_key)
        VALUES (p_scope_id, v_next_aggregate, v_next_partition)
        ON CONFLICT (scope_id, aggregate_type, partition_key)
        DO UPDATE SET aggregate_type = EXCLUDED.aggregate_type
        RETURNING partition_token INTO v_next_token;
    END IF;

    RETURN jsonb_build_object(
        'scope_id', p_scope_id,
        'manifest', jsonb_build_object(
            'aggregate_type', v_aggregate,
            'partition_key', v_partition,
            'row_count', v_count,
            'content_hash_or_version_digest', v_digest,
            'manifest_revision', v_revision,
            'scope_id', p_scope_id
        ),
        'has_more', v_next_aggregate IS NOT NULL,
        'next_partition_token', CASE WHEN v_next_aggregate IS NULL THEN NULL ELSE v_next_token::text END
    );
END;
$$;

COMMENT ON SEQUENCE public.verto_sync_change_revision_seq IS 'Verto unified sync global server-owned monotonic revision allocator; gaps are valid.';
COMMENT ON TABLE public.verto_sync_change_log IS 'Append-only Verto unified sync delivery log. changed_at is diagnostic metadata; revision is ordering authority.';
COMMENT ON TABLE public.verto_sync_snapshot_state IS 'Adapter-owned current-state materialization for bootstrap/reconciliation; no 305 business backfill or producer cutover.';
COMMENT ON TABLE public.verto_sync_contract IS 'verto-unified-sync/1 EXPAND_ONLY operational limits and retention decisions from Session 305 Revision 2.';

-- Least privilege: tables/sequences/internal helpers are not directly available to app roles.
REVOKE ALL ON TABLE public.verto_sync_contract, public.verto_sync_scopes, public.verto_sync_change_log,
    public.verto_sync_receipts, public.verto_sync_cursor_tokens, public.verto_sync_snapshot_state,
    public.verto_sync_bootstrap_sessions, public.verto_sync_bootstrap_rows,
    public.verto_sync_bootstrap_page_tokens, public.verto_sync_manifest_partition_tokens
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SEQUENCE public.verto_sync_change_revision_seq FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.verto_expected_payload_version(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_current_visibility_fingerprint() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_validate_sync_scope(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_assign_sync_change_revision() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_reject_sync_change_log_mutation() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_reject_sync_receipt_mutation() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_append_sync_change(uuid,text,text,text,bigint,integer,jsonb,text,text,uuid,text,timestamptz) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_upsert_sync_snapshot_state(uuid,text,text,bigint,integer,jsonb,bigint,text,uuid,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_remove_sync_snapshot_state(uuid,text,text,bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_encode_sync_cursor(uuid,bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.verto_decode_sync_cursor(uuid,text) FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.verto_resolve_sync_scope() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.verto_pull_sync_changes(uuid,text,integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.verto_begin_sync_bootstrap(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.verto_pull_bootstrap_page(uuid,text,integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.verto_get_reconciliation_manifest(uuid,text) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.verto_resolve_sync_scope() TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_pull_sync_changes(uuid,text,integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_begin_sync_bootstrap(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_pull_bootstrap_page(uuid,text,integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.verto_get_reconciliation_manifest(uuid,text) TO authenticated;
