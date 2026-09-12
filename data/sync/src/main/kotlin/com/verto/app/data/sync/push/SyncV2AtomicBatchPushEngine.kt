package com.verto.app.data.sync.push

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.FrozenMutationStore
import com.verto.app.data.sync.SyncBatchCoordinatorV2
import com.verto.app.data.sync.UnifiedSyncPushRemote
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

data class AtomicBatchPushRunResult(
    val sent: Int = 0,
    val acknowledged: Int = 0,
    val retried: Int = 0,
    val immediateMore: Boolean = false,
)

/** Dispatches each durable sealed manifest as one immutable server transaction. */
@Singleton
class SyncV2AtomicBatchPushEngine @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncPushRemote,
    private val batches: SyncBatchCoordinatorV2,
    private val frozenStore: FrozenMutationStore,
) {
    suspend fun pushAvailable(organizationId: String, limit: Int = 25): AtomicBatchPushRunResult {
        require(organizationId.isNotBlank()) { "SCOPE_MISMATCH" }
        require(limit in 1..100)
        val candidates = database.unifiedSyncDao().listDispatchableWriteBatches(organizationId, limit)
        var sent = 0
        var acknowledged = 0
        var retried = 0
        candidates.forEach { batch ->
            val members = database.unifiedSyncDao().listWriteBatchMembers(organizationId, batch.batchId)
            check(members.size == batch.memberCount) { "BATCH_MEMBERSHIP_MISMATCH" }
            val frozen = batches.prepareOnce(organizationId, batch.batchId, System.currentTimeMillis())
            val responses = try {
                sent += members.size
                remote.applyFrozenBatch(frozen.wireJson, frozen.wireSha256)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (isRetryable(failure)) {
                    retried += members.size
                    return@forEach
                }
                throw failure
            }
            acknowledged += frozenStore.acknowledgeSealedBatch(
                organizationId, batch.batchId, members, responses.map { it.receipt }, System.currentTimeMillis(),
            )
        }
        return AtomicBatchPushRunResult(sent, acknowledged, retried, candidates.size >= limit)
    }

    private fun isRetryable(failure: Throwable): Boolean {
        val message = generateSequence(failure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
        return message.contains("429") || message.contains("too many requests", true) ||
            message.contains("timeout", true) || message.contains("network", true) ||
            message.contains("connection", true)
    }
}
