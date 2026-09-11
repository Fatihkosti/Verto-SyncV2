package com.verto.app.data.sync.recovery

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.SyncWorkScope
import com.verto.app.data.sync.UNIFIED_SYNC_CONTRACT_FAMILY
import com.verto.app.data.sync.UNIFIED_SYNC_CONTRACT_VERSION
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.data.sync.rollout.SyncRolloutPolicy
import javax.inject.Inject
import javax.inject.Singleton

/** Privacy-safe diagnostics; no payload, token, URI, Authorization or raw exception body. */
data class SyncHealthSnapshot(
    val currentTenant: String,
    val scopeId: String?,
    val recoveryState: String,
    val lastObservedServerRevision: Long?,
    val lastAppliedRevision: Long?,
    val syncLag: Long?,
    val unifiedOutboxDepth: Long,
    val strongerOutboxDepth: Long,
    val attachmentOutboxDepth: Long,
    val oldestPendingMutationAgeMillis: Long?,
    val retryCount: Long,
    val conflictCount: Long,
    val deadLetterOrReviewCount: Long,
    val pendingCount: Long,
    val requiresReviewCount: Long,
    val rejectedCount: Long,
    val lastSuccessfulPushAt: Long?,
    val lastSuccessfulPullAt: Long?,
    val lastFailureCategory: String?,
    val lastFailureCode: String?,
    val realtimeState: String,
    val requestedGeneration: Long,
    val drainedGeneration: Long,
    val fullResyncCount: Long,
    val lastReconciliationStatus: String?,
    val rolloutWave: String,
    val ownershipMode: String,
    val shadowMismatchCount: Long?,
    val legacyFallbackUseCount: Long?,
    val v2PullEnabled: Boolean,
    val v2PushEnabled: Boolean,
    val financialEnabled: Boolean,
    val inventoryEnabled: Boolean,
    val realtimeEnabled: Boolean,
    val pendingInboxGroups: Long = 0L,
    val inboxStorageWait: Boolean = false,
)

@Singleton
class SyncHealthMonitor @Inject constructor(
    private val database: AppDatabase,
    private val pendingProtection: SyncPendingProtection,
) {
    suspend fun markPushSuccess(organizationId: String) {
        database.syncRecoveryDao().markPushSuccess(organizationId, System.currentTimeMillis())
    }

    suspend fun markPullSuccess(organizationId: String) {
        database.syncRecoveryDao().markPullSuccess(organizationId, System.currentTimeMillis())
    }

    suspend fun markFailure(organizationId: String, category: String, code: String) {
        val safeCategory = normalize(category)
        val safeCode = normalize(code)
        database.syncRecoveryDao().markFailure(organizationId, safeCategory, safeCode, System.currentTimeMillis())
    }

    suspend fun snapshot(scope: SyncWorkScope, now: Long = System.currentTimeMillis()): SyncHealthSnapshot {
        val dao = database.syncRecoveryDao()
        val cursor = database.unifiedSyncDao().getActiveCursorForPrincipal(
            scope.organizationId, scope.userId, UNIFIED_SYNC_CONTRACT_FAMILY, UNIFIED_SYNC_CONTRACT_VERSION,
        )
        val recovery = cursor?.let { dao.getRecoveryState(it.scopeId) }
        val health = cursor?.let { dao.getHealthState(it.scopeId) }
        val generations = database.unifiedSyncDao().readOrchestrationGenerations(scope.organizationId)
        val observed = listOfNotNull(health?.lastObservedServerRevision, cursor?.pageHighWatermark).maxOrNull()
        val applied = cursor?.lastAppliedChangeRevision
        val lag = if (observed != null && applied != null) (observed - applied).coerceAtLeast(0L) else null
        val party = dao.partyOutboxDepth(scope.organizationId)
        val financial = dao.financialOutboxDepth(scope.organizationId)
        val stock = dao.inventoryStockOutboxDepth(scope.organizationId)
        val cost = dao.inventoryCostOutboxDepth(scope.organizationId)
        val optimal = dao.optimalOutboxDepth(scope.organizationId)
        val oldest = dao.oldestUnifiedPendingAt(scope.organizationId)?.let { (now - it).coerceAtLeast(0L) }
        val unifiedReview = dao.unifiedRequiresReviewCount(scope.organizationId)
        val partyReview = dao.partyRequiresReviewCount(scope.organizationId)
        val financialReview = dao.financialRequiresReviewCount(scope.organizationId)
        val stockReview = dao.inventoryStockRequiresReviewCount(scope.organizationId)
        val costReview = dao.inventoryCostRequiresReviewCount(scope.organizationId)
        val optimalReview = dao.optimalRequiresReviewCount(scope.organizationId)
        val inboxReview = cursor?.let { database.unifiedSyncDao().reviewInboxGroupCount(it.scopeId) } ?: 0L
        val inboxPending = cursor?.let { database.unifiedSyncDao().unappliedInboxGroupCount(it.scopeId) } ?: 0L
        val requiresReview = unifiedReview + partyReview + financialReview + stockReview + costReview + optimalReview + inboxReview
        val rejected = dao.unifiedRejectedCount(scope.organizationId) + dao.partyRejectedCount(scope.organizationId)
        val pending = (
            dao.unifiedOutboxDepth(scope.organizationId) + party + financial + stock + cost + optimal +
                dao.attachmentOutboxDepth(scope.organizationId) -
                unifiedReview - financialReview - stockReview - costReview - optimalReview
            ).coerceAtLeast(0L)
        val rollout = SyncRolloutPolicy.snapshot(scope.organizationId)
        val ownerships = SyncRolloutPolicy.aggregateOwnerships(rollout).values.toSet()
        return SyncHealthSnapshot(
            currentTenant = scope.organizationId,
            scopeId = cursor?.scopeId,
            recoveryState = recovery?.state ?: "NOT_STARTED",
            lastObservedServerRevision = observed,
            lastAppliedRevision = applied,
            syncLag = lag,
            unifiedOutboxDepth = dao.unifiedOutboxDepth(scope.organizationId),
            strongerOutboxDepth = party + financial + stock + cost + optimal,
            attachmentOutboxDepth = dao.attachmentOutboxDepth(scope.organizationId),
            oldestPendingMutationAgeMillis = oldest,
            retryCount = dao.unifiedRetryCount(scope.organizationId) + dao.partyRetryCount(scope.organizationId) + dao.financialRetryCount(scope.organizationId) + dao.inventoryStockRetryCount(scope.organizationId) + dao.inventoryCostRetryCount(scope.organizationId) + dao.optimalRetryCount(scope.organizationId),
            conflictCount = dao.unifiedConflictCount(scope.organizationId) + dao.inventoryConflictCount(scope.organizationId) + dao.partyConflictCount(scope.organizationId),
            deadLetterOrReviewCount = requiresReview + rejected,
            pendingCount = pending + (inboxPending - inboxReview).coerceAtLeast(0L),
            requiresReviewCount = requiresReview,
            rejectedCount = rejected,
            lastSuccessfulPushAt = health?.lastSuccessfulPushAt,
            lastSuccessfulPullAt = health?.lastSuccessfulPullAt,
            lastFailureCategory = health?.lastFailureCategory,
            lastFailureCode = health?.lastFailureCode,
            realtimeState = if (!rollout.realtimeEnabled) "DISABLED" else "DEGRADED",
            requestedGeneration = generations.requested,
            drainedGeneration = generations.drained,
            fullResyncCount = health?.fullResyncCount ?: 0L,
            lastReconciliationStatus = health?.lastReconciliationStatus,
            rolloutWave = rollout.wave.name,
            ownershipMode = if (ownerships.size == 1) ownerships.first().name else "MIXED",
            shadowMismatchCount = null,
            legacyFallbackUseCount = null,
            v2PullEnabled = rollout.v2PullEnabled,
            v2PushEnabled = rollout.v2PushEnabled,
            financialEnabled = rollout.financialEnabled,
            inventoryEnabled = rollout.inventoryEnabled,
            realtimeEnabled = rollout.realtimeEnabled,
            pendingInboxGroups = inboxPending,
            inboxStorageWait = cursor?.let { database.unifiedSyncDao().getInboxApplyRequest(it.scopeId)?.storageWaitReason != null } ?: false,
        )
    }

    suspend fun hasUnconfirmedWork(organizationId: String): Boolean {
        return pendingProtection.hasUnconfirmedWork(organizationId) ||
            database.unifiedSyncDao().unconfirmedInboxRowCount(organizationId) > 0L
    }

    private fun normalize(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9_]+"), "_").take(80)
}
