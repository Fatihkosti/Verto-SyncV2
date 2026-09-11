package com.verto.app.data.sync.recovery

import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.ownership.SyncOwnershipRegistry

/** Session 313 recovery disposition. No aggregate may remain UNKNOWN. */
enum class PendingLocalRecoveryStrategy {
    REPLAY_FROM_UNIFIED_OUTBOX,
    PRESERVE_STRONGER_LOCAL_STATE,
    SERVER_READ_ONLY_NO_LOCAL_PENDING,
    BLOCK_REVIEW_IF_PENDING,
}

data class RecoveryAggregateRule(
    val aggregateType: String,
    val materializationOrder: Int,
    val pendingLocalStrategy: PendingLocalRecoveryStrategy,
    val outboxOwner: String,
    val strongerOwner: String,
    val pruneSql: List<String> = emptyList(),
)

/**
 * Explicit 35/35 bootstrap registry. pruneSql only touches recoverable mirrors and always derives
 * authoritative presence from sync_bootstrap_stage for the current scope+session.
 */
object UnifiedSyncRecoveryRegistry {
    private fun r(
        id: String, order: Int, pending: PendingLocalRecoveryStrategy,
        @Suppress("UNUSED_PARAMETER") outbox: String = "sync_outbox", stronger: String = "owner307",
        vararg prune: String,
    ) = RecoveryAggregateRule(
        id,
        order,
        pending,
        SyncOwnershipRegistry.byAggregateType.getValue(id).sourceOwner.tableName,
        stronger,
        prune.toList(),
    )

    val all = listOf(
        r("PARTY_IDENTITY", 10, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "party-v2"),
        r("PARTY_ROLE", 11, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "party_sync_outbox", "party-v2",
            "DELETE FROM party_roles WHERE organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='PARTY_ROLE' AND s.aggregate_id=(party_roles.party_id || ':' || party_roles.role)) AND NOT EXISTS (SELECT 1 FROM party_sync_outbox o WHERE o.aggregate_id=party_roles.party_id AND o.state IN ('PENDING','RETRY'))"),
        r("CUSTOMER_PROFILE", 12, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "party-v2",
            "DELETE FROM customer_profiles WHERE customer_profiles.organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='CUSTOMER_PROFILE' AND s.aggregate_id=customer_profiles.party_id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='CUSTOMER_PROFILE' AND o.aggregate_id=customer_profiles.party_id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("SUPPLIER_PROFILE", 13, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "party-v2",
            "DELETE FROM supplier_profiles WHERE supplier_profiles.organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='SUPPLIER_PROFILE' AND s.aggregate_id=supplier_profiles.party_id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='SUPPLIER_PROFILE' AND o.aggregate_id=supplier_profiles.party_id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("CATEGORY", 20, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM categories WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='CATEGORY' AND s.aggregate_id=categories.id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='CATEGORY' AND o.aggregate_id=categories.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("INVENTORY_UNIT", 21, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM inventory_units WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='INVENTORY_UNIT' AND s.aggregate_id=inventory_units.id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='INVENTORY_UNIT' AND o.aggregate_id=inventory_units.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("INVENTORY_ITEM", 22, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX),
        r("ITEM_CATEGORY", 23, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM item_categories WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='ITEM_CATEGORY' AND s.aggregate_id=item_categories.id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='ITEM_CATEGORY' AND o.aggregate_id=item_categories.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("PRICE_LIST", 24, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307",
            "DELETE FROM price_list_templates WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='PRICE_LIST' AND s.aggregate_id=price_list_templates.id) AND organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=price_list_templates.organization_id AND o.aggregate_type='PRICE_LIST' AND o.aggregate_id=price_list_templates.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("PURCHASE_ORDER", 30, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX),
        r("GOODS_RECEIPT", 31, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "inventory_stock_outbox", "owner310"),
        r("PURCHASE_MATCH", 32, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("PURCHASE_PAYMENT_OVERRIDE", 33, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("INVOICE", 40, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("PAYMENT", 41, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("CLIENT_CREDIT", 42, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("EXPENSE", 43, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("CASH_REGISTER", 44, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("CASH_MOVEMENT", 45, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("CASH_RECONCILIATION", 46, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("COMMISSION_PAYMENT", 47, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "financial_outbox", "owner310"),
        r("INVENTORY_MOVEMENT", 50, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "inventory_stock_outbox", "owner310"),
        r("INVENTORY_COST_REVISION", 51, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "inventory_cost_outbox", "owner310"),
        r("COST_ALLOCATION", 52, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "inventory_cost_outbox", "owner310"),
        r("BUDGET", 60, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM budgets WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='BUDGET' AND s.aggregate_id=budgets.id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='BUDGET' AND o.aggregate_id=budgets.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("NOTE", 61, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM notes WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='NOTE' AND s.aggregate_id=notes.id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='NOTE' AND o.aggregate_id=notes.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("REMINDER", 62, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM client_reminders WHERE NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='REMINDER' AND s.aggregate_id=client_reminders.id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='REMINDER' AND o.aggregate_id=client_reminders.id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("ORGANIZATION_SETTINGS", 63, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX),
        r("EDUCATIONAL_CONTENT", 64, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner307", "DELETE FROM educational_topics WHERE organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='EDUCATIONAL_CONTENT' AND s.aggregate_id=educational_topics.topic_id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='EDUCATIONAL_CONTENT' AND o.aggregate_id=educational_topics.topic_id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("NOTIFICATION", 65, PendingLocalRecoveryStrategy.SERVER_READ_ONLY_NO_LOCAL_PENDING, "none", "server", "DELETE FROM notifications WHERE organizationId=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='NOTIFICATION' AND s.aggregate_id=notifications.id)"),
        r("TEAM_OBSERVATION", 66, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX, "sync_outbox", "owner403",
            "DELETE FROM team_observations WHERE organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='TEAM_OBSERVATION' AND s.aggregate_id=team_observations.observation_id) AND NOT EXISTS (SELECT 1 FROM sync_outbox o WHERE o.organization_id=? AND o.aggregate_type='TEAM_OBSERVATION' AND o.aggregate_id=team_observations.observation_id AND o.state IN ('PENDING','RETRY','LEASED','REQUIRES_REVIEW','SUPERSEDED_PENDING_PROOF'))"),
        r("SHIPMENT", 70, PendingLocalRecoveryStrategy.REPLAY_FROM_UNIFIED_OUTBOX),
        r("OPTIMAL_VEHICLE", 80, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "optimal_outbox", "owner310", "DELETE FROM optimal_vehicles WHERE organization_id=? AND NOT EXISTS (SELECT 1 FROM sync_bootstrap_stage s WHERE s.scope_id=? AND s.bootstrap_session_id=? AND s.aggregate_type='OPTIMAL_VEHICLE' AND s.aggregate_id=optimal_vehicles.remote_vehicle_id) AND NOT EXISTS (SELECT 1 FROM optimal_outbox o WHERE o.organization_id=? AND o.aggregate_type='VEHICLE' AND o.aggregate_id=optimal_vehicles.remote_vehicle_id AND o.status NOT IN ('SYNCED','ACKNOWLEDGED'))"),
        r("OPTIMAL_MAINTENANCE", 81, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "optimal_outbox", "owner310"),
        r("OPTIMAL_FOLLOW_UP", 82, PendingLocalRecoveryStrategy.PRESERVE_STRONGER_LOCAL_STATE, "optimal_outbox", "owner310"),
    ).sortedBy { it.materializationOrder }

    val byId = all.associateBy { it.aggregateType }

    init {
        require(all.size == 35) { "FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE: recovery registry must be exactly 35" }
        require(byId.keys == UnifiedSyncAggregateRegistry.byId.keys) { "FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE: registry set mismatch" }
    }
}
