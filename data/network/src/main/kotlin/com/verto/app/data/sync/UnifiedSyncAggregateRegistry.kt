package com.verto.app.data.sync

import kotlinx.serialization.Serializable

@Serializable
enum class UnifiedSyncConflictPolicy {
    OPTIMISTIC_VERSION,
    APPEND_ONLY_IDEMPOTENT,
    SEMANTIC_COMMAND,
    SERVER_STATE_MACHINE,
    IMMUTABLE_REVISION,
    SERVER_AUTHORITATIVE,
    EXPLICIT_SCOPED_LWW,
    BRIDGE_EXISTING_STRONGER_CONTRACT,
}

@Serializable
enum class UnifiedSyncDeletePolicy {
    VERSIONED_DELETE,
    TOMBSTONE,
    ARCHIVE,
    VOID_OR_REVERSE,
    CANCEL_STATE_TRANSITION,
    NO_CLIENT_DELETE,
    SERVER_OWNED,
}

@Serializable
enum class UnifiedSyncAttachmentPolicy {
    NONE,
    METADATA_ONLY_EXTERNAL_BINARY,
}

@Serializable
enum class UnifiedSyncFinancialSensitivity {
    NONE,
    FINANCIAL,
    LEDGER_AFFECTING,
    INVENTORY_LEDGER,
}

@Serializable
data class UnifiedSyncAggregateRecord(
    val id: String,
    val payloadVersion: Int,
    val conflictPolicy: UnifiedSyncConflictPolicy,
    val deletePolicy: UnifiedSyncDeletePolicy,
    val attachmentPolicy: UnifiedSyncAttachmentPolicy,
    val financialSensitivity: UnifiedSyncFinancialSensitivity,
    val currentRuntimeOwner: String,
    val currentServerPaths: List<String>,
    val modernizationSession: Int,
    val notes: String,
)

object UnifiedSyncAggregateRegistry {
    val all: List<UnifiedSyncAggregateRecord> = listOf(
        record("PARTY_IDENTITY", 2, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.TOMBSTONE,
            runtime = "clients / Party normalized contract", server = listOf("clients", "verto_upsert_party_v2"), session = 307,
            notes = "Legacy Party TOMBSTONE maps to unified DELETE; Party contract v2 is preserved."),
        record("PARTY_ROLE", 2, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.TOMBSTONE,
            runtime = "clients / Party normalized contract", server = listOf("clients", "verto_upsert_party_v2"), session = 307,
            notes = "Normalized party role state remains behind Party contract v2 until adapter migration."),
        record("CUSTOMER_PROFILE", 2, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.TOMBSTONE,
            runtime = "clients / Party normalized contract", server = listOf("clients", "verto_upsert_party_v2"), session = 307,
            notes = "Customer profile uses Party payload v2 evidence."),
        record("SUPPLIER_PROFILE", 2, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.TOMBSTONE,
            runtime = "clients / Party normalized contract", server = listOf("clients", "verto_upsert_party_v2"), session = 307,
            notes = "Supplier profile uses Party payload v2 evidence."),
        record("NOTE", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "clients / SyncMisc", server = listOf("notes"), session = 307),
        record("REMINDER", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "clients / SyncMisc", server = listOf("client_reminders"), session = 307),
        record("INVOICE", 2, UnifiedSyncConflictPolicy.SEMANTIC_COMMAND, UnifiedSyncDeletePolicy.VOID_OR_REVERSE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "invoices / financial_outbox + legacy invoice sync",
            server = listOf("invoices", "invoice_items", "financial_sync_apply_event_v1", "financial_sync_pull_events_v1"), session = 310,
            notes = "Draft uses optimistic concurrency; posted/void/reverse are semantic commands and immutable effects."),
        record("PAYMENT", 2, UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncDeletePolicy.VOID_OR_REVERSE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "invoices / financial_outbox + payment sync",
            server = listOf("payments", "payment_allocations", "realized_fx_events", "financial_sync_apply_event_v1"), session = 310),
        record("CLIENT_CREDIT", 2, UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "invoices / SyncClientCredits", server = listOf("client_credits"), session = 310),
        record("PURCHASE_ORDER", 1, UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE, UnifiedSyncDeletePolicy.CANCEL_STATE_TRANSITION,
            sensitivity = UnifiedSyncFinancialSensitivity.FINANCIAL, runtime = "invoices / purchase cycle", server = listOf("purchase_orders", "purchase_order_lines", "verto_purchase_cycle_push_pre_v253"), session = 307),
        record("GOODS_RECEIPT", 2, UnifiedSyncConflictPolicy.SEMANTIC_COMMAND, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.INVENTORY_LEDGER, runtime = "invoices / purchase cycle", server = listOf("goods_receipts", "goods_receipt_lines", "purchase_cycle_attachments", "verto_purchase_cycle_push_pre_v253"), session = 310,
            attachment = UnifiedSyncAttachmentPolicy.METADATA_ONLY_EXTERNAL_BINARY),
        record("PURCHASE_MATCH", 2, UnifiedSyncConflictPolicy.SEMANTIC_COMMAND, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.FINANCIAL, runtime = "invoices / purchase cycle", server = listOf("purchase_invoice_matches", "purchase_invoice_match_lines", "purchase_invoice_receipt_allocations", "verto_purchase_cycle_push_post_v253"), session = 310),
        record("PURCHASE_PAYMENT_OVERRIDE", 2, UnifiedSyncConflictPolicy.SEMANTIC_COMMAND, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.FINANCIAL, runtime = "invoices / purchase cycle", server = listOf("purchase_payment_overrides", "verto_purchase_cycle_push_post_v253"), session = 310),
        record("INVENTORY_ITEM", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.ARCHIVE,
            runtime = "inventory / SyncInventory", server = listOf("inventory_items", "archive_inventory_item_v2"), session = 307),
        record("INVENTORY_UNIT", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "inventory / SyncInventory", server = listOf("inventory_units"), session = 307),
        record("CATEGORY", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "inventory / SyncInventory", server = listOf("categories"), session = 307),
        record("ITEM_CATEGORY", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "inventory / SyncInventory", server = listOf("item_categories"), session = 307),
        record("INVENTORY_MOVEMENT", 2, UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.INVENTORY_LEDGER, runtime = "inventory / inventory_stock_outbox",
            server = listOf("inventory_apply_commands_v2", "inventory_pull_movements_v2", "inventory_prepare_reconciliation_batch_v2"), session = 310,
            notes = "Stock balance is derived materialization; movement identity is authoritative; reconciliation RPC is current evidence."),
        record("INVENTORY_COST_REVISION", 2, UnifiedSyncConflictPolicy.IMMUTABLE_REVISION, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.INVENTORY_LEDGER, runtime = "inventory / inventory_cost_outbox",
            server = listOf("inventory_apply_cost_revisions_v2", "inventory_pull_cost_revisions_v2"), session = 310),
        record("COST_ALLOCATION", 2, UnifiedSyncConflictPolicy.IMMUTABLE_REVISION, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.INVENTORY_LEDGER, runtime = "inventory / SyncCash cost allocations", server = listOf("cost_allocations"), session = 310),
        record("EXPENSE", 2, UnifiedSyncConflictPolicy.SEMANTIC_COMMAND, UnifiedSyncDeletePolicy.VOID_OR_REVERSE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "cash / SyncMisc", server = listOf("expenses"), session = 310,
            notes = "Current legacy hard-delete is evidence only; unified target forbids ledger hard delete."),
        record("BUDGET", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.FINANCIAL, runtime = "cash / SyncCash", server = listOf("budgets"), session = 307),
        record("CASH_REGISTER", 1, UnifiedSyncConflictPolicy.SERVER_AUTHORITATIVE, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "cash / SyncCash", server = listOf("cash_register"), session = 310),
        record("CASH_MOVEMENT", 2, UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "cash / SyncCash", server = listOf("cash_register_movements"), session = 310),
        record("CASH_RECONCILIATION", 2, UnifiedSyncConflictPolicy.SEMANTIC_COMMAND, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "cash / SyncCash", server = listOf("cash_reconciliation_sessions", "cash_denominations"), session = 310),
        record("COMMISSION_PAYMENT", 1, UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            sensitivity = UnifiedSyncFinancialSensitivity.LEDGER_AFFECTING, runtime = "cash / pullCommissions", server = listOf("commission_payments"), session = 310),
        record("PRICE_LIST", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "inventory / unified outbox templates", server = listOf("verto_sync_change_log", "verto_sync_snapshot_state"), session = 376),
        record("NOTIFICATION", 1, UnifiedSyncConflictPolicy.SERVER_AUTHORITATIVE, UnifiedSyncDeletePolicy.SERVER_OWNED,
            runtime = "organization / SyncNotifications", server = listOf("get_my_notifications_cache"), session = 308),
        record("ORGANIZATION_SETTINGS", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            runtime = "organization / OrgSettingsRepository", server = listOf("organization_settings"), session = 307),
        record("SHIPMENT", 1, UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE, UnifiedSyncDeletePolicy.CANCEL_STATE_TRANSITION,
            runtime = "logistics-v2 (inert behind SERVER_CONTRACT_VERIFIED=false)",
            server = listOf(
                "logistics_shipments", "logistics_shipment_sources", "logistics_shipment_lines", "logistics_milestones",
                "logistics_assignments", "logistics_events", "logistics_partners", "logistics_shipment_legs",
                "logistics_custody_handoffs", "logistics_shipment_partner_links", "logistics_transport_details",
                "logistics_documents", "logistics_costs", "logistics_payments", "logistics_route_templates",
                "logistics_route_template_stops", "logistics_receiving_batches", "logistics_receiving_lines",
                "logistics_inventory_postings", "logistics_cost_allocations", "logistics_shortages", "logistics_recoveries",
                "logistics_recovery_lines", "logistics_recovery_postings"
            ), session = 307, attachment = UnifiedSyncAttachmentPolicy.METADATA_ONLY_EXTERNAL_BINARY,
            notes = "Runtime participant key is logistics-v2 while SyncManager currently requires shipments; recorded, not fixed in 304."),
        record("EDUCATIONAL_CONTENT", 1, UnifiedSyncConflictPolicy.OPTIMISTIC_VERSION, UnifiedSyncDeletePolicy.VERSIONED_DELETE,
            runtime = "educational_content / EducationalContentSyncParticipant", server = listOf("educational_topics", "educational_topic_targets"), session = 307),
        record("TEAM_OBSERVATION", 1, UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            runtime = "team_observations / unified outbox + legacy fallback",
            server = listOf("team_observations", "verto_apply_team_observation_v403", "verto_apply_sync_mutation"), session = 403,
            notes = "Submit/update is durable in sync_outbox; legacy participant remains available until later cutover stages."),
        record("OPTIMAL_VEHICLE", 1, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.SERVER_OWNED,
            runtime = "optimal_outbox", server = listOf("optimal_sync_change_log", "optimal_apply_sync_operation_v2", "optimal_pull_sync_changes"), session = 310,
            notes = "Server evidence is reference-only until exact dump bytes are rebound."),
        record("OPTIMAL_MAINTENANCE", 1, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            runtime = "optimal_outbox", server = listOf("optimal_sync_change_log", "optimal_apply_sync_operation_v2", "optimal_pull_sync_changes"), session = 310,
            attachment = UnifiedSyncAttachmentPolicy.METADATA_ONLY_EXTERNAL_BINARY,
            notes = "Existing Optimal durable outbox/lease/idempotency guard remains stronger than generic dirty sync."),
        record("OPTIMAL_FOLLOW_UP", 1, UnifiedSyncConflictPolicy.BRIDGE_EXISTING_STRONGER_CONTRACT, UnifiedSyncDeletePolicy.NO_CLIENT_DELETE,
            runtime = "optimal_outbox", server = listOf("optimal_sync_change_log", "optimal_apply_sync_operation_v2", "optimal_pull_sync_changes"), session = 310,
            notes = "Existing Optimal server change-log family is preserved as bridge evidence."),
    ).sortedBy { it.id }

    val byId: Map<String, UnifiedSyncAggregateRecord> = all.associateBy { it.id }

    init {
        require(all.size == 35) { "Unified sync registry must contain exactly 35 aggregates for the M02 server contract" }
        require(byId.size == all.size) { "Unified sync aggregate ids must be unique" }
        require(all.all { it.id.matches(Regex("[A-Z][A-Z0-9_]*")) }) { "Aggregate ids must be UPPER_SNAKE_CASE" }
        require(all.all { it.payloadVersion > 0 }) { "Payload versions must be positive" }
        require(all.none {
            it.financialSensitivity != UnifiedSyncFinancialSensitivity.NONE &&
                it.conflictPolicy == UnifiedSyncConflictPolicy.EXPLICIT_SCOPED_LWW
        }) { "Financial/ledger aggregates cannot use scoped LWW" }
        require(requireById("PAYMENT").conflictPolicy == UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT)
        require(requireById("INVENTORY_MOVEMENT").conflictPolicy == UnifiedSyncConflictPolicy.APPEND_ONLY_IDEMPOTENT)
        require(requireById("INVENTORY_COST_REVISION").conflictPolicy == UnifiedSyncConflictPolicy.IMMUTABLE_REVISION)
        require(requireById("SHIPMENT").conflictPolicy == UnifiedSyncConflictPolicy.SERVER_STATE_MACHINE)
    }

    fun findById(id: String): UnifiedSyncAggregateRecord? = byId[id]

    fun requireById(id: String): UnifiedSyncAggregateRecord =
        byId[id] ?: throw SyncContractViolation("CONTRACT_UNSUPPORTED", "unknown aggregate $id")

    private fun record(
        id: String,
        payloadVersion: Int,
        conflict: UnifiedSyncConflictPolicy,
        delete: UnifiedSyncDeletePolicy,
        runtime: String,
        server: List<String>,
        session: Int,
        sensitivity: UnifiedSyncFinancialSensitivity = UnifiedSyncFinancialSensitivity.NONE,
        attachment: UnifiedSyncAttachmentPolicy = UnifiedSyncAttachmentPolicy.NONE,
        notes: String = "",
    ) = UnifiedSyncAggregateRecord(
        id = id,
        payloadVersion = payloadVersion,
        conflictPolicy = conflict,
        deletePolicy = delete,
        attachmentPolicy = attachment,
        financialSensitivity = sensitivity,
        currentRuntimeOwner = runtime,
        currentServerPaths = server,
        modernizationSession = session,
        notes = notes,
    )
}
