package com.verto.app.data.sync

import android.content.Context
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.sync.pull.UnifiedSyncPullEngine
import com.verto.app.data.sync.pull.UnifiedSyncPullResult
import com.verto.app.data.sync.push.SyncV2PushCoordinator
import com.verto.app.data.sync.push.UnifiedSyncPushRunResult
import com.verto.app.data.sync.recovery.RecoveryReason
import com.verto.app.data.sync.recovery.RecoveryRunOutcome
import com.verto.app.data.sync.recovery.SyncHealthMonitor
import com.verto.app.data.sync.recovery.SyncHealthSnapshot
import com.verto.app.data.sync.recovery.UnifiedSyncRecoveryEngine
import com.verto.app.data.sync.migration.LegacySyncV2MigrationCoordinator
import com.verto.app.data.sync.rollout.SyncRolloutPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncManagerPrincipal(
    val organizationId: String,
    val userId: String,
)

/** Manager-scoped runtime boundary; deliberately excludes Room/Context/network implementation details. */
interface SyncManagerRuntimePort {
    suspend fun currentPrincipal(): SyncManagerPrincipal?
    suspend fun sessionEpoch(): Long
    suspend fun isLocalDatabaseEmpty(): Boolean
    suspend fun captureDeletionSnapshot(): SyncDeletionSnapshot
    suspend fun prepareLegacyV2Migration(scope: SyncWorkScope, deletions: SyncDeletionSnapshot, now: Long): Int = 0
    suspend fun getPersistedSyncReport(organizationId: String, userId: String): String?
    suspend fun setPersistedSyncReport(organizationId: String, userId: String, reportJson: String)
    suspend fun clearLocalData()
    suspend fun getLastOrganizationId(): String
    suspend fun setLastOrganizationId(organizationId: String)
    suspend fun activateNextSessionEpoch(): Long
    suspend fun currentOrganizationDisplayName(): String
    suspend fun prepareForSync()
    suspend fun beginOrResumeSyncRun(organizationId: String, startingRevision: Long): SyncRunCheckpoint
    suspend fun markSyncOperationCompleted(organizationId: String, runId: String, operationKey: String)
    suspend fun completeSyncRun(organizationId: String, runId: String)
    fun markSyncCompleted()
    suspend fun pullNotifications(organizationId: String)
    suspend fun pullCommissions(organizationId: String)
}

@Singleton
class DefaultSyncManagerRuntimePort @Inject constructor(
    private val authRepository: AuthRepository,
    private val runtime: SyncRuntime,
    private val legacyMigration: LegacySyncV2MigrationCoordinator,
) : SyncManagerRuntimePort {
    override suspend fun currentPrincipal(): SyncManagerPrincipal? = authRepository.getMyProfile()?.let {
        SyncManagerPrincipal(organizationId = it.organizationId, userId = it.id)
    }

    override suspend fun sessionEpoch(): Long = runtime.getSessionEpoch()
    override suspend fun isLocalDatabaseEmpty(): Boolean = runtime.isLocalDatabaseEmpty()
    override suspend fun captureDeletionSnapshot(): SyncDeletionSnapshot = runtime.captureDeletionSnapshot()
    override suspend fun prepareLegacyV2Migration(scope: SyncWorkScope, deletions: SyncDeletionSnapshot, now: Long): Int =
        legacyMigration.prepare(scope, deletions, now).reviewCount
    override suspend fun getPersistedSyncReport(organizationId: String, userId: String): String? =
        runtime.getPersistedSyncReport(organizationId, userId)

    override suspend fun setPersistedSyncReport(organizationId: String, userId: String, reportJson: String) =
        runtime.setPersistedSyncReport(organizationId, userId, reportJson)

    override suspend fun clearLocalData() = runtime.clearLocalData()
    override suspend fun getLastOrganizationId(): String = runtime.getLastOrganizationId()
    override suspend fun setLastOrganizationId(organizationId: String) = runtime.setLastOrganizationId(organizationId)
    override suspend fun activateNextSessionEpoch(): Long = runtime.activateNextSessionEpoch()
    override suspend fun currentOrganizationDisplayName(): String = runtime.currentOrganizationDisplayName()
    override suspend fun prepareForSync() = runtime.prepareForSync()
    override suspend fun beginOrResumeSyncRun(organizationId: String, startingRevision: Long): SyncRunCheckpoint =
        runtime.beginOrResumeSyncRun(organizationId, startingRevision)

    override suspend fun markSyncOperationCompleted(organizationId: String, runId: String, operationKey: String) =
        runtime.markSyncOperationCompleted(organizationId, runId, operationKey)

    override suspend fun completeSyncRun(organizationId: String, runId: String) = runtime.completeSyncRun(organizationId, runId)
    override fun markSyncCompleted() = runtime.markSyncCompleted()
    override suspend fun pullNotifications(organizationId: String) = runtime.pullNotifications(organizationId)
    override suspend fun pullCommissions(organizationId: String) = runtime.pullCommissions(organizationId)
}

interface SyncWakeScheduler {
    fun wakeNow(scope: SyncWorkScope)
    fun enqueueContinuation(scope: SyncWorkScope, delayMillis: Long = 0L)
    fun enqueueInboxContinuation(scope: SyncWorkScope, delayMillis: Long) = enqueueContinuation(scope, delayMillis)
    fun cancelAll()
}

@Singleton
class WorkManagerSyncWakeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncWakeScheduler {
    override fun wakeNow(scope: SyncWorkScope) = SyncWorker.wakeNow(context, scope)
    override fun enqueueContinuation(scope: SyncWorkScope, delayMillis: Long) = SyncWorker.enqueueContinuation(context, scope, delayMillis)
    override fun enqueueInboxContinuation(scope: SyncWorkScope, delayMillis: Long) = SyncWorker.enqueueInboxContinuation(context, scope, delayMillis)
    override fun cancelAll() = SyncWorker.cancelAll(context)
}

data class SyncOrchestrationGenerations(val requested: Long, val drained: Long)

interface SyncOrchestrationStore {
    fun bindInboxWake(scope: SyncWorkScope) = Unit
    fun unbindInboxWake() = Unit
    fun finishInboxDrain(scope: SyncWorkScope) = Unit
    suspend fun requestGeneration(organizationId: String, updatedAt: Long): Long
    suspend fun readGenerations(organizationId: String): SyncOrchestrationGenerations
    suspend fun countEligibleOutboxNow(organizationId: String, now: Long): Long
    suspend fun markDrainedIfIdle(organizationId: String, observedRequested: Long, updatedAt: Long): Boolean
}

@Singleton
class RoomSyncOrchestrationStore @Inject constructor(
    private val database: AppDatabase,
    private val inboxWake: com.verto.app.data.sync.pull.DurableInboxWakeObserver,
) : SyncOrchestrationStore {
    override fun bindInboxWake(scope: SyncWorkScope) = inboxWake.bind(scope)
    override fun unbindInboxWake() = inboxWake.unbind()
    override fun finishInboxDrain(scope: SyncWorkScope) = inboxWake.finishDrain(scope)
    override suspend fun requestGeneration(organizationId: String, updatedAt: Long): Long =
        database.unifiedSyncDao().requestOrchestrationGeneration(organizationId, updatedAt)

    override suspend fun readGenerations(organizationId: String): SyncOrchestrationGenerations =
        database.unifiedSyncDao().readOrchestrationGenerations(organizationId).let {
            SyncOrchestrationGenerations(it.requested, it.drained)
        }

    override suspend fun countEligibleOutboxNow(organizationId: String, now: Long): Long =
        database.unifiedSyncDao().countRunnableOutboxNow(organizationId, now)

    override suspend fun markDrainedIfIdle(
        organizationId: String,
        observedRequested: Long,
        updatedAt: Long,
    ): Boolean = database.unifiedSyncDao().markOrchestrationDrainedIfIdle(
        organizationId,
        observedRequested,
        updatedAt,
    )
}

fun interface SyncClock {
    fun nowMillis(): Long
}

@Singleton
class SystemSyncClock @Inject constructor() : SyncClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * Cohesive durable-orchestration capability: persistence, time and wake mechanics stay outside SyncManager.
 * It contains behavior (persist-before-wake assembly), not a generic dependency bag.
 */
@Singleton
class SyncOrchestrationControl @Inject constructor(
    private val scheduler: SyncWakeScheduler,
    private val store: SyncOrchestrationStore,
    private val clock: SyncClock,
) {
    suspend fun persistRequest(scope: SyncWorkScope): Long =
        store.requestGeneration(scope.organizationId, clock.nowMillis())

    fun wakeNow(scope: SyncWorkScope) = scheduler.wakeNow(scope)
    fun enqueueContinuation(scope: SyncWorkScope, delayMillis: Long = 0L) = scheduler.enqueueContinuation(scope, delayMillis)
    fun bindScope(scope: SyncWorkScope) = store.bindInboxWake(scope)
    fun finishInboxDrain(scope: SyncWorkScope) = store.finishInboxDrain(scope)
    fun cancelAll() { store.unbindInboxWake(); scheduler.cancelAll() }
    fun nowMillis(): Long = clock.nowMillis()
    suspend fun readGenerations(organizationId: String): SyncOrchestrationGenerations =
        store.readGenerations(organizationId)
    suspend fun countEligibleOutboxNow(organizationId: String, now: Long): Long =
        store.countEligibleOutboxNow(organizationId, now)
    suspend fun markDrainedIfIdle(organizationId: String, observedRequested: Long): Boolean =
        store.markDrainedIfIdle(organizationId, observedRequested, clock.nowMillis())
}

fun interface SyncRolloutAuthority {
    fun usesV2(organizationId: String): Boolean
}

@Singleton
class DefaultSyncRolloutAuthority @Inject constructor() : SyncRolloutAuthority {
    override fun usesV2(organizationId: String): Boolean =
        SyncRolloutPolicy.snapshot(organizationId).anyV2AuthoritativeTransport
}

interface SyncV2EnginePort {
    suspend fun requiredRecoveryReason(organizationId: String, scopeGuard: suspend () -> Unit): RecoveryReason?
    suspend fun runRecovery(
        organizationId: String,
        reason: RecoveryReason,
        promotionAllowed: Boolean,
        scopeGuard: suspend () -> Unit,
    ): RecoveryRunOutcome
    suspend fun pushAvailable(scope: SyncWorkScope): UnifiedSyncPushRunResult
    suspend fun pull(organizationId: String): UnifiedSyncPullResult
    suspend fun markPushSuccess(organizationId: String)
    suspend fun markPullSuccess(organizationId: String)
    suspend fun markFailure(organizationId: String, category: String, code: String)
    suspend fun healthSnapshot(scope: SyncWorkScope): SyncHealthSnapshot
    suspend fun hasUnconfirmedWork(organizationId: String): Boolean
}

/** Adapter over the existing V2 engines; SyncManager sees one behavioral engine boundary. */
@Singleton
class DefaultSyncV2EnginePort @Inject constructor(
    private val push: SyncV2PushCoordinator,
    private val pull: UnifiedSyncPullEngine,
    private val recovery: UnifiedSyncRecoveryEngine,
    private val health: SyncHealthMonitor,
) : SyncV2EnginePort {
    override suspend fun requiredRecoveryReason(
        organizationId: String,
        scopeGuard: suspend () -> Unit,
    ): RecoveryReason? = recovery.requiredReason(organizationId, scopeGuard)

    override suspend fun runRecovery(
        organizationId: String,
        reason: RecoveryReason,
        promotionAllowed: Boolean,
        scopeGuard: suspend () -> Unit,
    ): RecoveryRunOutcome = recovery.run(organizationId, reason, promotionAllowed = promotionAllowed, scopeGuard = scopeGuard)

    override suspend fun pushAvailable(scope: SyncWorkScope): UnifiedSyncPushRunResult = push.pushAvailable(scope)
    override suspend fun pull(organizationId: String): UnifiedSyncPullResult = pull.pull(organizationId)
    override suspend fun markPushSuccess(organizationId: String) = health.markPushSuccess(organizationId)
    override suspend fun markPullSuccess(organizationId: String) = health.markPullSuccess(organizationId)
    override suspend fun markFailure(organizationId: String, category: String, code: String) =
        health.markFailure(organizationId, category, code)
    override suspend fun healthSnapshot(scope: SyncWorkScope): SyncHealthSnapshot = health.snapshot(scope)
    override suspend fun hasUnconfirmedWork(organizationId: String): Boolean = health.hasUnconfirmedWork(organizationId)
}
