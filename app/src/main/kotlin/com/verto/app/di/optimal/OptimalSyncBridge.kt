package com.verto.app.di.optimal

import android.content.Context
import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.SyncManager
import com.verto.app.data.sync.SyncRequestReason
import com.verto.app.data.sync.SyncWorker
import com.verto.app.data.sync.SyncWorkScope
import com.verto.app.data.sync.push.SyncV2SpecializedPushBridge
import com.verto.app.data.sync.push.SyncV2SpecializedPushResult
import com.verto.app.data.local.dao.OptimalOutboxDao
import com.verto.app.core.concurrency.AppCoroutineScope
import kotlinx.coroutines.launch
import com.verto.app.feature.integration.optimal.application.OptimalOutboxSyncParticipant
import com.verto.app.feature.integration.optimal.domain.model.OPTIMAL_OUTBOX_SYNC_PARTICIPANT_KEY
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScheduler
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerOptimalSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncManager: SyncManager,
    private val appScope: AppCoroutineScope,
) : OptimalSyncScheduler {
    override fun enqueueImmediate(scope: OptimalSyncScope) {
        appScope.launch {
            val trusted = syncManager.currentWorkScope() ?: return@launch
            if (trusted.organizationId == scope.organizationId && trusted.userId == scope.userId) {
                syncManager.requestSync(trusted, SyncRequestReason.OUTBOX_WRITE)
            }
        }
    }

    override fun ensurePeriodic(scope: OptimalSyncScope) {
        appScope.launch {
            val trusted = syncManager.currentWorkScope() ?: return@launch
            if (trusted.organizationId == scope.organizationId && trusted.userId == scope.userId) {
                SyncWorker.schedule(context, trusted)
            }
        }
    }

    override fun cancel(scope: OptimalSyncScope) {
        appScope.launch {
            syncManager.currentWorkScope()?.takeIf {
                it.organizationId == scope.organizationId && it.userId == scope.userId
            }?.let { SyncWorker.cancel(context, it) }
        }
    }

    override fun cancelAll() {
        SyncWorker.cancelAll(context)
    }
}

/** Adds Optimal durable Outbox consumption to the existing global sync plan. */
@Singleton
class OptimalOutboxSyncParticipantAdapter @Inject constructor(
    private val participant: OptimalOutboxSyncParticipant,
    private val outboxDao: OptimalOutboxDao,
) : SyncParticipant, SyncV2SpecializedPushBridge {
    override val key: String = OPTIMAL_OUTBOX_SYNC_PARTICIPANT_KEY

    override suspend fun pushV2(scope: SyncWorkScope): SyncV2SpecializedPushResult {
        val summary = participant.syncOrganization(
            scope = OptimalSyncScope(scope.organizationId, scope.userId),
            workerId = "v2-optimal:${scope.userId}:${UUID.randomUUID()}",
        )
        if (summary.stoppedBySessionChange) {
            error("SCOPE_MISMATCH: Optimal V2 push stopped by session change")
        }
        val now = System.currentTimeMillis()
        return SyncV2SpecializedPushResult(
            sent = summary.claimed,
            acknowledged = summary.synced,
            retried = summary.failed,
            requiresReview = outboxDao.countBlockedReview(scope.organizationId).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            backlog = outboxDao.countSyncBacklog(scope.organizationId),
            immediateMore = summary.claimed >= 50,
            nextEligibleAt = outboxDao.nextRetryAt(scope.organizationId, now),
        )
    }

    override fun operations(context: SyncRunContext): List<SyncOperation> = listOf(
        SyncOperation(
            slot = SyncOperationSlot.PUSH_OPTIMAL_OUTBOX,
            label = "dispatch Optimal Outbox",
            failureMode = SyncFailureMode.COLLECT,
            execute = {
                val summary = participant.syncOrganization(
                    scope = OptimalSyncScope(
                        organizationId = context.organizationId,
                        userId = context.userId,
                    ),
                    workerId = "optimal:${context.userId}:${UUID.randomUUID()}",
                )
                if (!summary.stoppedBySessionChange && summary.failed > 0) {
                    error("Optimal Outbox has ${summary.failed} retryable failure(s)")
                }
            },
        ),
    )
}
