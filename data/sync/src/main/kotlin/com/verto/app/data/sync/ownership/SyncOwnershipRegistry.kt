package com.verto.app.data.sync.ownership

import com.verto.app.data.sync.UnifiedSyncAggregateRegistry

enum class SyncSourceOwner(
    val tableName: String,
    val sourceIdColumn: String,
    val terminalStates: Set<String>,
) {
    UNIFIED("sync_outbox", "mutation_id", setOf("ACKNOWLEDGED", "SUPERSEDED_WITH_PROOF")),
    FINANCIAL("financial_outbox", "event_id", setOf("ACKNOWLEDGED", "SYNCED")),
    PARTY_ROLE("party_sync_outbox", "id", setOf("ACKNOWLEDGED", "SYNCED")),
    INVENTORY_STOCK("inventory_stock_outbox", "id", setOf("ACKNOWLEDGED", "SYNCED")),
    INVENTORY_COST("inventory_cost_outbox", "id", setOf("ACKNOWLEDGED", "SYNCED")),
    OPTIMAL("optimal_outbox", "event_id", setOf("ACKNOWLEDGED", "SYNCED")),
    ATTACHMENT("sync_attachment_transfer", "transfer_id", setOf("COMPLETED", "CANCELLED")),
    SERVER_ONLY("none", "none", emptySet()),
    ;

    fun isPending(state: String): Boolean = when (this) {
        SERVER_ONLY -> false
        else -> state.trim().uppercase() !in terminalStates
    }

    companion object {
        fun fromTable(tableName: String): SyncSourceOwner = entries.singleOrNull { it.tableName == tableName }
            ?: throw UnknownSyncOwnerException(tableName)
    }
}

class UnknownSyncOwnerException(owner: String) : IllegalArgumentException("BLOCKED_OWNER_UNPROVEN: $owner")

data class SyncOwnershipRecord(
    val aggregateType: String,
    val producer: String,
    val sourceOwner: SyncSourceOwner,
    val businessIdentity: String,
    val protectedKeys: Set<String>,
    val mutability: String,
    val serializer: String,
    val applier: String,
    val recovery: String,
)

data class SpecializedSyncOwnerRecord(
    val sourceOwner: SyncSourceOwner,
    val producer: String,
    val businessIdentity: String,
    val protectedKeys: Set<String>,
    val serializer: String,
    val recovery: String,
)

/**
 * One exhaustive owner decision for the 35 aggregate types and each specialized queue.
 * There is deliberately no name-based/default owner path: an unknown type fails closed.
 */
object SyncOwnershipRegistry {
    private fun record(
        type: String,
        producer: String,
        owner: SyncSourceOwner,
        identity: String,
        protected: Set<String>,
        serializer: String,
        applier: String = "UnifiedSyncChangeApplier.apply",
        recovery: String = "UnifiedSyncSnapshotApplier.materializeAndPrune",
    ): SyncOwnershipRecord {
        val contract = UnifiedSyncAggregateRegistry.requireById(type)
        return SyncOwnershipRecord(
            aggregateType = type,
            producer = producer,
            sourceOwner = owner,
            businessIdentity = identity,
            protectedKeys = protected,
            mutability = "${contract.conflictPolicy}/${contract.deletePolicy}",
            serializer = serializer,
            applier = applier,
            recovery = recovery,
        )
    }

    val aggregates: List<SyncOwnershipRecord> = listOf(
        record("PARTY_IDENTITY", "ClientRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "partyId", setOf("PARTY_IDENTITY:partyId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("PARTY_ROLE", "PartyRoleDao.enqueueSyncOperation", SyncSourceOwner.PARTY_ROLE, "organizationId/partyId/role", setOf("PARTY_IDENTITY:partyId", "PARTY_ROLE:partyId:role"), "Party normalized payload v2"),
        record("CUSTOMER_PROFILE", "ClientRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "partyId", setOf("PARTY_IDENTITY:partyId", "CUSTOMER_PROFILE:partyId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("SUPPLIER_PROFILE", "ClientRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "partyId", setOf("PARTY_IDENTITY:partyId", "SUPPLIER_PROFILE:partyId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("NOTE", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "noteId", setOf("NOTE:noteId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("REMINDER", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "reminderId", setOf("REMINDER:reminderId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("INVOICE", "FinancialOutboxWriter", SyncSourceOwner.FINANCIAL, "invoiceId/writeId", setOf("INVOICE:invoiceId", "INVOICE_ITEMS:invoiceId"), "UnifiedStrongerSourceFactory.financial"),
        record("PAYMENT", "FinancialOutboxWriter", SyncSourceOwner.FINANCIAL, "invoiceId/paymentId/writeId", setOf("INVOICE:invoiceId", "INVOICE_ITEMS:invoiceId", "PAYMENT:paymentId", "PAYMENT_ALLOCATIONS:paymentId", "PAYMENT_ORIGIN:originalPaymentId"), "UnifiedStrongerSourceFactory.financial"),
        record("CLIENT_CREDIT", "PaymentAppAdapters + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "creditId", setOf("CLIENT_CREDIT:creditId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("PURCHASE_ORDER", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "purchaseOrderId", setOf("PURCHASE_ORDER:purchaseOrderId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("GOODS_RECEIPT", "LogisticsReceivingAdapters + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "goodsReceiptId", setOf("GOODS_RECEIPT:goodsReceiptId", "PURCHASE_ORDER:purchaseOrderId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("PURCHASE_MATCH", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "purchaseMatchId", setOf("PURCHASE_MATCH:purchaseMatchId", "PURCHASE_ORDER:purchaseOrderId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("PURCHASE_PAYMENT_OVERRIDE", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "overrideId", setOf("PURCHASE_PAYMENT_OVERRIDE:overrideId", "PURCHASE_ORDER:purchaseOrderId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("INVENTORY_ITEM", "InventoryRoomAdapters + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "itemId", setOf("INVENTORY_ITEM:itemId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("INVENTORY_UNIT", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "unitId", setOf("INVENTORY_UNIT:unitId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("CATEGORY", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "categoryId", setOf("CATEGORY:categoryId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("ITEM_CATEGORY", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "itemCategoryId", setOf("ITEM_CATEGORY:itemCategoryId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("INVENTORY_MOVEMENT", "InventoryStockWriter", SyncSourceOwner.INVENTORY_STOCK, "movementId/idempotencyKey", setOf("INVENTORY_MOVEMENT:movementId", "INVENTORY_ITEM:itemId"), "UnifiedStrongerSourceFactory.inventoryMovement"),
        record("INVENTORY_COST_REVISION", "InventoryStockWriter", SyncSourceOwner.INVENTORY_COST, "costRevisionId", setOf("INVENTORY_COST_REVISION:costRevisionId", "INVENTORY_ITEM:itemId"), "UnifiedStrongerSourceFactory.inventoryCost"),
        record("COST_ALLOCATION", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "costAllocationId", setOf("COST_ALLOCATION:costAllocationId", "INVENTORY_ITEM:itemId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("EXPENSE", "ExpenseRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "expenseId", setOf("EXPENSE:expenseId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("BUDGET", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "budgetId", setOf("BUDGET:budgetId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("CASH_REGISTER", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "registerId", setOf("CASH_REGISTER:registerId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("CASH_MOVEMENT", "LogisticsCashPostingAdapter + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "cashMovementId", setOf("CASH_MOVEMENT:cashMovementId", "CASH_REGISTER:registerId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("CASH_RECONCILIATION", "CashReconciliationRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "reconciliationId", setOf("CASH_RECONCILIATION:reconciliationId", "CASH_DENOMINATIONS:reconciliationId", "CASH_REGISTER:registerId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("COMMISSION_PAYMENT", "UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "commissionPaymentId", setOf("COMMISSION_PAYMENT:commissionPaymentId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("PRICE_LIST", "InventoryPresentationAdapters + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "priceListId", setOf("PRICE_LIST:priceListId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("NOTIFICATION", "server read path only: SyncNotifications/get_my_notifications_cache", SyncSourceOwner.SERVER_ONLY, "notificationId", setOf("NOTIFICATION:notificationId"), "server DTO", applier = "UnifiedSyncChangeApplier.apply", recovery = "UnifiedSyncSnapshotApplier.materializeAndPrune"),
        record("ORGANIZATION_SETTINGS", "OrgSettingsRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "organizationId", setOf("ORGANIZATION_SETTINGS:organizationId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("SHIPMENT", "LogisticsV2AppAdapters + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "shipmentId", setOf("SHIPMENT:shipmentId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("EDUCATIONAL_CONTENT", "EducationalContentSyncParticipant + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "topicId", setOf("EDUCATIONAL_CONTENT:topicId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("TEAM_OBSERVATION", "RoomTeamObservationRepository + UnifiedOutboxWriter.enqueue", SyncSourceOwner.UNIFIED, "observationId", setOf("TEAM_OBSERVATION:observationId"), "UnifiedOutboxWriter.canonicalPayload"),
        record("OPTIMAL_VEHICLE", "Optimal outbox adapters", SyncSourceOwner.OPTIMAL, "remoteVehicleId/idempotencyKey", setOf("OPTIMAL_VEHICLE:remoteVehicleId"), "Optimal payload_json"),
        record("OPTIMAL_MAINTENANCE", "Optimal outbox adapters", SyncSourceOwner.OPTIMAL, "maintenanceId/idempotencyKey", setOf("OPTIMAL_MAINTENANCE:maintenanceId", "OPTIMAL_VEHICLE:vehicleId"), "Optimal payload_json"),
        record("OPTIMAL_FOLLOW_UP", "Optimal outbox adapters", SyncSourceOwner.OPTIMAL, "followUpId/idempotencyKey", setOf("OPTIMAL_FOLLOW_UP:followUpId"), "Optimal payload_json"),
    ).sortedBy { it.aggregateType }

    val specializedOwners: List<SpecializedSyncOwnerRecord> = listOf(
        SpecializedSyncOwnerRecord(SyncSourceOwner.FINANCIAL, "FinancialOutboxWriter", "event_id + org/write_id", setOf("invoice aggregate and payment content keys"), "UnifiedStrongerSourceFactory.financial", "FinancialMaterializerV2"),
        SpecializedSyncOwnerRecord(SyncSourceOwner.PARTY_ROLE, "PartyRoleDao.enqueueSyncOperation", "id + org/partyId/role proof", setOf("party identity and exact role"), "Party normalized payload v2", "UnifiedSyncSnapshotApplier"),
        SpecializedSyncOwnerRecord(SyncSourceOwner.INVENTORY_STOCK, "InventoryStockWriter", "id + org/movementId", setOf("movement and item"), "UnifiedStrongerSourceFactory.inventoryMovement", "Inventory materializer"),
        SpecializedSyncOwnerRecord(SyncSourceOwner.INVENTORY_COST, "InventoryStockWriter", "id + org/costRevisionId", setOf("cost revision and item"), "UnifiedStrongerSourceFactory.inventoryCost", "Inventory materializer"),
        SpecializedSyncOwnerRecord(SyncSourceOwner.OPTIMAL, "Optimal outbox adapters", "org/eventId", setOf("Optimal aggregate and parent"), "Optimal payload_json", "Optimal bridge applier"),
        SpecializedSyncOwnerRecord(SyncSourceOwner.ATTACHMENT, "UnifiedOutboxWriter.enqueueAttachmentIntent", "org/transferId/content checksum", setOf("document and parent aggregate"), "attachment metadata", "AttachmentTransferCoordinatorV2"),
    )

    val byAggregateType: Map<String, SyncOwnershipRecord> = aggregates.associateBy { it.aggregateType }

    init {
        check(aggregates.size == 35 && byAggregateType.size == aggregates.size) { "BLOCKED_OWNER_UNPROVEN: ownership registry must cover 35 unique aggregates" }
        check(byAggregateType.keys == UnifiedSyncAggregateRegistry.byId.keys) { "BLOCKED_OWNER_UNPROVEN: ownership set differs from contract" }
        check(specializedOwners.map { it.sourceOwner }.toSet() == setOf(
            SyncSourceOwner.FINANCIAL,
            SyncSourceOwner.PARTY_ROLE,
            SyncSourceOwner.INVENTORY_STOCK,
            SyncSourceOwner.INVENTORY_COST,
            SyncSourceOwner.OPTIMAL,
            SyncSourceOwner.ATTACHMENT,
        )) { "BLOCKED_OWNER_UNPROVEN: specialized owner coverage mismatch" }
    }

    fun requireAggregate(aggregateType: String): SyncOwnershipRecord = byAggregateType[aggregateType]
        ?: throw UnknownSyncOwnerException(aggregateType)
}
