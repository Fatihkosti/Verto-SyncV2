package com.verto.app.data.sync

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Session 310 bridge metadata. Stronger domain intent remains authoritative; no generic dirty-row copy is created. */
@Serializable
enum class StrongerBridgeRuntimeState {
    STRONGER_BRIDGE_READY_SHADOW,
    STRONGER_PULL_READY_SHADOW,
    SERVER_AUTHORITATIVE_NO_CLIENT_PUSH,
    PRESERVED_EXISTING_STRONGER_RUNTIME,
}

@Serializable
enum class StrongerSnapshotPolicy {
    NO_SNAPSHOT_LEDGER_FACT,
    SERVER_AUTH_CURRENT_STATE,
    IMMUTABLE_EVENT_SNAPSHOT,
    DOMAIN_MATERIALIZATION_ONLY,
}

@Serializable
data class StrongerBridgeRecord(
    val aggregateType: String,
    val localAuthority: String,
    val businessIdentity: String,
    val serverAuthority: String,
    val pushMapping: String,
    val pullMapping: String,
    val receiptMapping: String,
    val conflictPolicy: UnifiedSyncConflictPolicy,
    val deletePolicy: UnifiedSyncDeletePolicy,
    val snapshotPolicy: StrongerSnapshotPolicy,
    val runtimeState: StrongerBridgeRuntimeState,
)

/** Immutable source view over the already-durable stronger intent. */
@Serializable
data class StrongerMutationSource(
    val organizationId: String,
    val aggregateType: String,
    val aggregateId: String,
    val businessIdentity: String,
    val domainOperation: String,
    val localSequence: Long,
    val aggregateSequence: Long,
    val payloadVersion: Int,
    val payload: JsonObject,
    val createdAtEpochMillis: Long,
    val baseVersion: Long? = null,
    val transactionId: String? = null,
)

@Serializable
data class StrongerReceiptResult(
    val status: SyncReceiptStatus,
    val businessIdentity: String,
    val serverVersion: Long? = null,
    val serverRevision: Long? = null,
    val conflictCode: String? = null,
)

interface UnifiedStrongerSyncBridge {
    val aggregateTypes: Set<String>
    fun toUnifiedMutation(source: StrongerMutationSource): SyncMutation
    fun reconcileReceipt(source: StrongerMutationSource, receipt: SyncReceipt): StrongerReceiptResult
    fun validateRemoteChange(change: SyncChange)
}

/**
 * Protocol-only mapper. Claim/lease remains owned by financial_outbox, inventory_*_outbox, or optimal_outbox.
 * This prevents duplicate durable intents and preserves the source domain CAS/ordering rules.
 */
object UnifiedStrongerBridgeRegistry : UnifiedStrongerSyncBridge {
    val records: List<StrongerBridgeRecord> = listOf(
        r("INVOICE", "financial_outbox", "event_id/write_id", "financial_sync_apply_event_v1", "FINANCIAL_EVENT", "financial_inbox/REMOTE_APPLY", "UNIFIED_RECEIPT+FINANCIAL_IDENTITY", StrongerSnapshotPolicy.NO_SNAPSHOT_LEDGER_FACT),
        r("PAYMENT", "financial_outbox", "event_id/write_id", "financial_sync_apply_event_v1", "FINANCIAL_EVENT", "financial_inbox/REMOTE_APPLY", "UNIFIED_RECEIPT+FINANCIAL_IDENTITY", StrongerSnapshotPolicy.NO_SNAPSHOT_LEDGER_FACT),
        r("CLIENT_CREDIT", "client_credits immutable row", "credit_id", "verto_apply_client_credit_v310", "APPEND_ONLY_COMMAND", "REMOTE_APPLY", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.IMMUTABLE_EVENT_SNAPSHOT),
        r("GOODS_RECEIPT", "purchase-cycle immutable fact", "write_id/receipt_id", "verto_purchase_cycle_push_pre_v253", "PURCHASE_COMMAND", "PurchaseCycleDao.applyRemotePreCycle", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.IMMUTABLE_EVENT_SNAPSHOT),
        r("PURCHASE_MATCH", "purchase-cycle immutable fact", "write_id/match_id", "verto_purchase_cycle_push_post_v253", "PURCHASE_COMMAND", "PurchaseCycleDao.applyRemotePostCycle", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.IMMUTABLE_EVENT_SNAPSHOT),
        r("PURCHASE_PAYMENT_OVERRIDE", "purchase-cycle immutable fact", "request_id/write_id", "verto_purchase_cycle_push_post_v253", "PURCHASE_COMMAND", "PurchaseCycleDao.applyRemotePostCycle", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.IMMUTABLE_EVENT_SNAPSHOT),
        r("INVENTORY_MOVEMENT", "inventory_stock_outbox", "movement_id/command_id/idempotency_key", "inventory_apply_commands_v2", "INVENTORY_COMMAND", "InventoryDao.applyPulledInventoryMovements", "UNIFIED_RECEIPT+SERVER_SEQUENCE", StrongerSnapshotPolicy.NO_SNAPSHOT_LEDGER_FACT),
        r("INVENTORY_COST_REVISION", "inventory_cost_outbox", "cost_revision_id/command_id/idempotency_key", "inventory_apply_cost_revisions_v2", "INVENTORY_COST_COMMAND", "InventoryDao.applyPulledInventoryCostRevisions", "UNIFIED_RECEIPT+COST_SEQUENCE", StrongerSnapshotPolicy.NO_SNAPSHOT_LEDGER_FACT),
        r("COST_ALLOCATION", "cost_allocations immutable row", "allocation_id", "verto_apply_cost_allocation_v310", "IMMUTABLE_REVISION_COMMAND", "REMOTE_APPLY", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.IMMUTABLE_EVENT_SNAPSHOT),
        r("EXPENSE", "expenses lifecycle row", "expense_id/reversal_write_id", "verto_apply_expense_command_v310", "SEMANTIC_COMMAND", "REMOTE_APPLY", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.DOMAIN_MATERIALIZATION_ONLY),
        r("CASH_REGISTER", "server read model", "organization_id/register_id", "cash_register", "NO_CLIENT_PUSH", "REMOTE_APPLY", "SERVER_AUTHORITATIVE", StrongerSnapshotPolicy.SERVER_AUTH_CURRENT_STATE, StrongerBridgeRuntimeState.SERVER_AUTHORITATIVE_NO_CLIENT_PUSH),
        r("CASH_MOVEMENT", "cash movement immutable row", "write_id/movement_id", "verto_apply_cash_movement_v310", "APPEND_ONLY_COMMAND", "REMOTE_APPLY", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.NO_SNAPSHOT_LEDGER_FACT),
        r("CASH_RECONCILIATION", "cash reconciliation state machine", "reconciliation_id/command", "verto_apply_cash_reconciliation_v310", "SEMANTIC_COMMAND", "REMOTE_APPLY", "UNIFIED_RECEIPT", StrongerSnapshotPolicy.DOMAIN_MATERIALIZATION_ONLY),
        r("COMMISSION_PAYMENT", "server read model", "client_request_id/payment_id", "commission_payments", "NO_CLIENT_PUSH", "REMOTE_APPLY", "SERVER_AUTHORITATIVE", StrongerSnapshotPolicy.IMMUTABLE_EVENT_SNAPSHOT, StrongerBridgeRuntimeState.SERVER_AUTHORITATIVE_NO_CLIENT_PUSH),
        r("OPTIMAL_VEHICLE", "optimal_outbox", "event_id/idempotency_key", "optimal_apply_sync_operation_v2", "OPTIMAL_STRONGER_OPERATION", "optimal unified revision bridge", "UNIFIED_RECEIPT+OPTIMAL_RESULT", StrongerSnapshotPolicy.DOMAIN_MATERIALIZATION_ONLY, StrongerBridgeRuntimeState.PRESERVED_EXISTING_STRONGER_RUNTIME),
        r("OPTIMAL_MAINTENANCE", "optimal_outbox", "event_id/idempotency_key", "optimal_apply_sync_operation_v2", "OPTIMAL_STRONGER_OPERATION", "optimal unified revision bridge", "UNIFIED_RECEIPT+OPTIMAL_RESULT", StrongerSnapshotPolicy.DOMAIN_MATERIALIZATION_ONLY, StrongerBridgeRuntimeState.PRESERVED_EXISTING_STRONGER_RUNTIME),
        r("OPTIMAL_FOLLOW_UP", "optimal_outbox", "event_id/idempotency_key", "optimal_apply_sync_operation_v2", "OPTIMAL_STRONGER_OPERATION", "optimal unified revision bridge", "UNIFIED_RECEIPT+OPTIMAL_RESULT", StrongerSnapshotPolicy.DOMAIN_MATERIALIZATION_ONLY, StrongerBridgeRuntimeState.PRESERVED_EXISTING_STRONGER_RUNTIME),
    ).sortedBy { it.aggregateType }

    private val byType = records.associateBy { it.aggregateType }
    override val aggregateTypes: Set<String> = byType.keys

    init {
        require(records.size == 17 && byType.size == 17) { "Session 310 bridge must cover exactly 17 aggregates" }
        require(records.none { it.conflictPolicy == UnifiedSyncConflictPolicy.EXPLICIT_SCOPED_LWW })
        require(records.none { it.snapshotPolicy == StrongerSnapshotPolicy.SERVER_AUTH_CURRENT_STATE && it.aggregateType !in setOf("CASH_REGISTER") })
    }

    fun requireRecord(aggregateType: String): StrongerBridgeRecord = byType[aggregateType]
        ?: throw SyncContractViolation("CONTRACT_UNSUPPORTED", "aggregate is not owned by Session 310: $aggregateType")

    override fun toUnifiedMutation(source: StrongerMutationSource): SyncMutation {
        val record = requireRecord(source.aggregateType)
        if (record.runtimeState == StrongerBridgeRuntimeState.SERVER_AUTHORITATIVE_NO_CLIENT_PUSH) {
            throw SyncContractViolation("SERVER_AUTHORITATIVE_NO_CLIENT_PUSH", "${source.aggregateType} has no client push authority")
        }
        require(source.businessIdentity.isNotBlank()) { "stronger business identity is required" }
        val operation = when (source.domainOperation.uppercase()) {
            "VOID", "INVOICE_VOIDED" -> SyncMutationOperation.VOID
            "REVERSE", "PAYMENT_REVERSED", "REVERSAL" -> SyncMutationOperation.REVERSE
            else -> SyncMutationOperation.COMMAND
        }
        return SyncMutation(
            mutationId = stableMutationId(source.aggregateType, source.businessIdentity),
            organizationId = source.organizationId,
            aggregateType = source.aggregateType,
            aggregateId = source.aggregateId,
            operationType = operation,
            baseVersion = source.baseVersion,
            localSequence = source.localSequence,
            aggregateSequence = source.aggregateSequence,
            payloadVersion = source.payloadVersion,
            payload = source.payload,
            createdAtEpochMillis = source.createdAtEpochMillis,
            commandBatchId = source.transactionId,
        ).also(UnifiedSyncContractRules::requireValidMutation)
    }

    override fun reconcileReceipt(source: StrongerMutationSource, receipt: SyncReceipt): StrongerReceiptResult {
        val expected = stableMutationId(source.aggregateType, source.businessIdentity)
        if (receipt.mutationId != expected || receipt.aggregateId != source.aggregateId) {
            throw SyncContractViolation("ORIGIN_MUTATION_MISMATCH", "stronger receipt identity mismatch")
        }
        return StrongerReceiptResult(
            status = receipt.status,
            businessIdentity = source.businessIdentity,
            serverVersion = receipt.serverVersion,
            serverRevision = receipt.serverRevision,
            conflictCode = receipt.conflictCode ?: receipt.validationCode,
        )
    }

    override fun validateRemoteChange(change: SyncChange) {
        val record = requireRecord(change.aggregateType)
        if (change.operationType == SyncMutationOperation.DELETE) {
            throw SyncContractViolation("FAIL_STRONGER_HARD_DELETE", "${record.aggregateType} cannot use generic hard DELETE")
        }
        if (record.snapshotPolicy == StrongerSnapshotPolicy.NO_SNAPSHOT_LEDGER_FACT && change.payload["balance"] != null) {
            throw SyncContractViolation("FAIL_DERIVED_STATE_PUSH", "ledger fact cannot be replaced by balance snapshot")
        }
    }

    fun stableMutationId(aggregateType: String, businessIdentity: String): String {
        val canonical = "verto-stronger-v1\u0000${aggregateType.trim()}\u0000${businessIdentity.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "v310:${aggregateType.lowercase()}:${digest.joinToString("") { "%02x".format(it) }}"
    }

    private fun r(
        type: String,
        local: String,
        identity: String,
        server: String,
        push: String,
        pull: String,
        receipt: String,
        snapshot: StrongerSnapshotPolicy,
        runtime: StrongerBridgeRuntimeState = StrongerBridgeRuntimeState.STRONGER_BRIDGE_READY_SHADOW,
    ): StrongerBridgeRecord {
        val source = UnifiedSyncAggregateRegistry.requireById(type)
        return StrongerBridgeRecord(
            aggregateType = type,
            localAuthority = local,
            businessIdentity = identity,
            serverAuthority = server,
            pushMapping = push,
            pullMapping = pull,
            receiptMapping = receipt,
            conflictPolicy = source.conflictPolicy,
            deletePolicy = source.deletePolicy,
            snapshotPolicy = snapshot,
            runtimeState = runtime,
        )
    }
}
