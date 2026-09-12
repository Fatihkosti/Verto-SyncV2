package com.verto.app.data.sync.push

import com.verto.app.data.sync.SyncWorkScope
import com.verto.app.data.local.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Explicit V2-only extension point for specialized durable outboxes that are not SyncParticipant operations. */
interface SyncV2SpecializedPushBridge {
    val key: String
    suspend fun pushV2(scope: SyncWorkScope): SyncV2SpecializedPushResult
}

data class SyncV2SpecializedPushResult(
    val sent: Int = 0,
    val acknowledged: Int = 0,
    val retried: Int = 0,
    val requiresReview: Int = 0,
    val rejected: Int = 0,
    val backlog: Long = 0,
    val immediateMore: Boolean = false,
    val nextEligibleAt: Long? = null,
)

/** M05 single push coordinator. No legacy SyncParticipant PUSH/DELETE is reachable from this class. */
@Singleton
class SyncV2PushCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val atomicBatches: SyncV2AtomicBatchPushEngine,
    private val generic: UnifiedSyncPushEngine,
    private val party: UnifiedSyncPartyPushBridge,
    private val stronger: UnifiedStrongerOutboxPushEngine,
    private val specialized: Set<@JvmSuppressWildcards SyncV2SpecializedPushBridge>,
) {
    suspend fun pushAvailable(scope: SyncWorkScope): UnifiedSyncPushRunResult {
        val batchResult = atomicBatches.pushAvailable(scope.organizationId)
        val genericResult = generic.pushAvailable(scope.organizationId, scope.sessionEpoch)
        val partyResult = party.pushAvailable(scope.organizationId)
        val strongerResult = stronger.pushAvailable(scope.organizationId)
        val specializedResults = specialized.sortedBy { it.key }.map { it.pushV2(scope) }

        val specialSent = specializedResults.sumOf { it.sent }
        val specialAck = specializedResults.sumOf { it.acknowledged }
        val specialRetry = specializedResults.sumOf { it.retried }
        val specialReview = specializedResults.sumOf { it.requiresReview }
        val specialRejected = specializedResults.sumOf { it.rejected }
        val persistentReview = database.unifiedSyncDao().countRequiresReview(scope.organizationId) +
            database.partyRoleDao().countPartyRoleReview() +
            database.invoiceDao().countFinancialOutboxReview(scope.organizationId) +
            database.inventoryDao().countInventoryStockReview(scope.organizationId) +
            database.inventoryDao().countInventoryCostReview(scope.organizationId)
        val persistentRejected = database.unifiedSyncDao().countRejected(scope.organizationId) +
            database.partyRoleDao().countPartyRoleRejected()
        val specialBacklog = specializedResults.sumOf { it.backlog }
        val immediateMore = batchResult.immediateMore || genericResult.outcome == UnifiedSyncPushOutcome.MORE_AVAILABLE ||
            partyResult.immediateMore || strongerResult.immediateMore || specializedResults.any { it.immediateMore }
        val next = listOfNotNull(
            batchResult.nextEligibleAt,
            genericResult.nextEligibleAt,
            partyResult.nextEligibleAt,
            strongerResult.nextEligibleAt,
            *specializedResults.mapNotNull { it.nextEligibleAt }.toTypedArray(),
        ).minOrNull()

        return UnifiedSyncPushRunResult(
            outcome = if (immediateMore) UnifiedSyncPushOutcome.MORE_AVAILABLE else UnifiedSyncPushOutcome.CAUGHT_UP,
            sent = batchResult.sent + genericResult.sent + partyResult.attempted + strongerResult.sent + specialSent,
            acknowledged = batchResult.acknowledged + genericResult.acknowledged + partyResult.acknowledged + strongerResult.acknowledged + specialAck,
            retried = batchResult.retried + genericResult.retried + partyResult.retried + strongerResult.retried + specialRetry,
            requiresReview = persistentReview.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + specialReview,
            rejected = persistentRejected.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() + specialRejected,
            staleLeaseResults = genericResult.staleLeaseResults,
            blocked = genericResult.blocked,
            backlog = genericResult.backlog + partyResult.backlog + strongerResult.backlog + specialBacklog,
            eligibleNow = genericResult.eligibleNow + if (immediateMore) 1 else 0,
            nextEligibleAt = next,
        )
    }
}
