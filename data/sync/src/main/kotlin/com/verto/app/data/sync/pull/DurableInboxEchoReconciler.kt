package com.verto.app.data.sync.pull

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.*
import javax.inject.Inject
import javax.inject.Singleton

/** Only a frozen, matching unified echo is auto-acknowledged. Other owners need their real receipt path. */
@Singleton
class DurableInboxEchoReconciler @Inject constructor(
    private val database: AppDatabase,
    private val frozen: FrozenMutationStore,
    private val mapper: UnifiedSyncInboxMapper,
) {
    suspend fun reconcile(change: SyncChange) {
        check(database.inTransaction()) { "REMOTE_APPLY_TRANSACTION_REQUIRED" }
        val id = change.originMutationId?.takeIf { it.isNotBlank() } ?: return
        val row = database.unifiedSyncDao().getOutbox(id) ?: return
        if (row.organizationId != change.organizationId || row.aggregateType != change.aggregateType || row.aggregateId != change.aggregateId)
            throw UnifiedSyncPullFailure("ORIGIN_MUTATION_MISMATCH", "echo identity differs from captured intent")
        when (row.state) {
            "ACKNOWLEDGED" -> return
            "PENDING", "LEASED", "RETRY" -> {
                val original = SyncContractV2Codec.json.parseToJsonElement(row.payloadJson)
                if (mapper.canonicalJson(original) != mapper.canonicalJson(change.payload))
                    throw UnifiedSyncPullFailure("SERVER_PROTOCOL_INCONSISTENCY", "echo differs from original captured content")
                when (frozen.acknowledgeAuthoritativeEcho(change.organizationId, id,
                change.aggregateType, change.aggregateId, sha256Utf8(row.payloadJson),
                change.revision, change.entityVersion, System.currentTimeMillis())) {
                FrozenAckOutcome.ACKNOWLEDGED_CURRENT, FrozenAckOutcome.ACKNOWLEDGED_LOCAL_CHANGED -> Unit
                FrozenAckOutcome.STALE_LEASE, FrozenAckOutcome.RECEIPT_MISMATCH ->
                    throw UnifiedSyncPullFailure("SERVER_PROTOCOL_INCONSISTENCY", "echo does not prove frozen business content")
                }
            }
            else -> throw UnifiedSyncPullFailure("SERVER_PROTOCOL_INCONSISTENCY", "unresolved terminal intent cannot acquire an automatic echo ACK")
        }
    }
}
