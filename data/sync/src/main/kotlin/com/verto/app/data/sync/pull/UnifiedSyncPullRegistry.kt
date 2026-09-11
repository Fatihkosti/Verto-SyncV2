package com.verto.app.data.sync.pull

import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.SyncContractViolation
import com.verto.app.data.sync.SyncMutationOperation
import com.verto.app.data.sync.UnifiedSyncAggregateRecord
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncDeletePolicy
import com.verto.app.data.sync.UnifiedStrongerBridgeRegistry
import javax.inject.Inject
import javax.inject.Singleton

enum class PullAggregateDisposition {
    SHADOW_APPLIER_READY,
    SERVER_READ_ONLY_APPLIER_READY,
    STRONGER_PULL_READY_SHADOW,
}

@Singleton
class UnifiedSyncPullRegistry @Inject constructor() {
    private val directTargets = setOf(
        "PARTY_IDENTITY", "PARTY_ROLE", "CUSTOMER_PROFILE", "SUPPLIER_PROFILE",
        "NOTE", "REMINDER", "PURCHASE_ORDER", "INVENTORY_ITEM", "INVENTORY_UNIT",
        "CATEGORY", "ITEM_CATEGORY", "BUDGET", "PRICE_LIST", "ORGANIZATION_SETTINGS",
        "SHIPMENT", "EDUCATIONAL_CONTENT", "NOTIFICATION", "TEAM_OBSERVATION",
    )

    private val owner310 = setOf(
        "INVOICE", "PAYMENT", "CLIENT_CREDIT", "GOODS_RECEIPT", "PURCHASE_MATCH",
        "PURCHASE_PAYMENT_OVERRIDE", "INVENTORY_MOVEMENT", "INVENTORY_COST_REVISION",
        "COST_ALLOCATION", "EXPENSE", "CASH_REGISTER", "CASH_MOVEMENT",
        "CASH_RECONCILIATION", "COMMISSION_PAYMENT", "OPTIMAL_VEHICLE",
        "OPTIMAL_MAINTENANCE", "OPTIMAL_FOLLOW_UP",
    )

    init {
        check(UnifiedSyncAggregateRegistry.all.size == 35) { "registry must match the 35-aggregate live M02 contract" }
        check(directTargets.size == 18 && owner310.size == 17 && directTargets.intersect(owner310).isEmpty())
        check(directTargets + owner310 == UnifiedSyncAggregateRegistry.byId.keys) {
            "v308 pull classification must cover all registry aggregates"
        }
    }

    fun record(aggregateType: String): UnifiedSyncAggregateRecord =
        UnifiedSyncAggregateRegistry.findById(aggregateType)
            ?: throw SyncContractViolation("CONTRACT_UNSUPPORTED", "unknown aggregate $aggregateType")

    fun disposition(aggregateType: String): PullAggregateDisposition = when {
        aggregateType == "NOTIFICATION" -> PullAggregateDisposition.SERVER_READ_ONLY_APPLIER_READY
        aggregateType in directTargets -> PullAggregateDisposition.SHADOW_APPLIER_READY
        aggregateType in owner310 -> PullAggregateDisposition.STRONGER_PULL_READY_SHADOW
        else -> throw SyncContractViolation("CONTRACT_UNSUPPORTED", "unclassified aggregate $aggregateType")
    }

    fun validate(change: SyncChange) {
        val record = record(change.aggregateType)
        if (record.payloadVersion != change.payloadVersion) {
            throw SyncContractViolation(
                "CONTRACT_UNSUPPORTED",
                "payload version ${change.payloadVersion} != ${record.payloadVersion} for ${change.aggregateType}",
            )
        }
        if (disposition(change.aggregateType) == PullAggregateDisposition.STRONGER_PULL_READY_SHADOW) {
            UnifiedStrongerBridgeRegistry.validateRemoteChange(change)
        }
        validateDeleteOperation(record, change.operationType)
    }

    private fun validateDeleteOperation(record: UnifiedSyncAggregateRecord, operation: SyncMutationOperation) {
        when (record.deletePolicy) {
            UnifiedSyncDeletePolicy.ARCHIVE -> if (operation == SyncMutationOperation.DELETE) {
                throw UnifiedSyncPullFailure("VALIDATION", "${record.id} requires ARCHIVE, not DELETE")
            }
            UnifiedSyncDeletePolicy.CANCEL_STATE_TRANSITION -> if (operation == SyncMutationOperation.DELETE) {
                throw UnifiedSyncPullFailure("VALIDATION", "${record.id} requires CANCEL/state transition, not hard DELETE")
            }
            UnifiedSyncDeletePolicy.NO_CLIENT_DELETE -> if (operation == SyncMutationOperation.DELETE) {
                throw UnifiedSyncPullFailure("VALIDATION", "${record.id} forbids client delete semantics")
            }
            UnifiedSyncDeletePolicy.VOID_OR_REVERSE -> if (operation == SyncMutationOperation.DELETE) {
                throw UnifiedSyncPullFailure("VALIDATION", "${record.id} requires VOID/REVERSE, not hard DELETE")
            }
            else -> Unit
        }
    }

    fun isPartyAggregate(type: String): Boolean =
        type in setOf("PARTY_IDENTITY", "PARTY_ROLE", "CUSTOMER_PROFILE", "SUPPLIER_PROFILE")
}
