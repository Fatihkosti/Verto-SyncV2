package com.verto.app.data.sync

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.RemoteFailureBoundary
import com.verto.app.data.sync.pull.UnifiedSyncPullOutcome
import com.verto.app.data.sync.push.UnifiedSyncPushOutcome
import com.verto.app.data.sync.recovery.RecoveryReason
import com.verto.app.data.sync.recovery.RecoveryRunOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.logging.Logger
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Singleton

/**
 * منسق دورة المزامنة.
 *
 * لا يحتوي تفاصيل DAOs أو DTOs أو جداول المجالات؛ كل مجال يعلن عملياته عبر [SyncParticipant].
 */
@Singleton
class SyncManager @Inject constructor(
    private val runtime: SyncManagerRuntimePort,
    private val participants: Set<@JvmSuppressWildcards SyncParticipant>,
    private val orchestration: SyncOrchestrationControl,
    private val v2Engine: SyncV2EnginePort,
) {
    private val syncMutex = Mutex()
    private val logger = Logger.getLogger("SyncManager")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _lastReport = MutableStateFlow<SyncRunReport?>(null)
    private val _persistedReport = MutableStateFlow<PersistedSyncReport?>(null)
    private val _syncInProgress = MutableStateFlow(false)

    private val _syncCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val syncCompleted: SharedFlow<Unit> = _syncCompleted.asSharedFlow()
    val lastReport: StateFlow<SyncRunReport?> = _lastReport
    val persistedReport: StateFlow<PersistedSyncReport?> = _persistedReport
    val syncInProgress: StateFlow<Boolean> = _syncInProgress

    @Volatile
    var lastSyncCompletedAtMillis: Long = 0L
        private set

    val isSyncInProgress: Boolean
        get() = syncMutex.isLocked

    /** يُستخدم من SyncViewModel للتحقق من جهوزية البروفايل قبل المزامنة. */
    suspend fun isProfileReady(): Boolean = runtime.currentPrincipal() != null

    /** M07: initial recovery is V2-only; no legacy full-sync fallback remains reachable. */
    suspend fun shouldScheduleInitialSync(): Boolean {
        val scope = currentWorkScope() ?: return false
        return v2Engine.requiredRecoveryReason(scope.organizationId) { validateScope(scope) } != null
    }

    /** Session 311: captures trusted tenant/account + opaque session epoch for WorkManager input. */
    suspend fun currentWorkScope(): SyncWorkScope? {
        val profile = runtime.currentPrincipal() ?: return null
        val epoch = runtime.sessionEpoch()
        if (epoch <= 0L) return null
        return SyncWorkScope(profile.organizationId, profile.userId, epoch)
    }

    /** M07 public request contract: durable acceptance is distinct from eventual completion. */
    suspend fun request(reason: SyncRequestReason = SyncRequestReason.MANUAL): Result<SyncRequestReceipt> =
        runCatching { requestSync(reason) }

    suspend fun requestSync(reason: SyncRequestReason): SyncRequestReceipt {
        val scope = currentWorkScope() ?: throw StaleSyncWorkScopeException("sync_scope_stale: no active session")
        return requestSync(scope, reason)
    }

    /** Persist intent first. Wake failure is reported in the receipt but never turns an accepted request into failure. */
    suspend fun requestSync(scope: SyncWorkScope, reason: SyncRequestReason, wake: Boolean = true): SyncRequestReceipt {
        validateScope(scope)
        val acceptedAt = orchestration.nowMillis()
        val generation = orchestration.persistRequest(scope)
        logger.fine("sync request committed reason=$reason generation=$generation")
        var wakeEnqueued = false
        if (wake) {
            wakeEnqueued = try {
                orchestration.wakeNow(scope)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warning("sync wake failed after durable acceptance type=${failure::class.java.simpleName}")
                false
            }
        }
        val receipt = SyncRequestReceipt(
            organizationId = scope.organizationId,
            userId = scope.userId,
            generation = generation,
            reason = reason,
            acceptedAtMillis = acceptedAt,
            wakeEnqueued = wakeEnqueued,
        )
        publishV2ReportBestEffort(scope, SyncCoordinatorPhase.REQUESTED, attemptedAtMillis = acceptedAt)
        return receipt
    }

    /** Periodic work is a wake source only; it creates one durable generation for this invocation. */
    suspend fun ensurePeriodicIntent(scope: SyncWorkScope): SyncRequestReceipt =
        requestSync(scope, SyncRequestReason.PERIODIC, wake = false)

    /**
     * V2 durable drain. M05 routes every push through the explicit V2 push coordinator; legacy
     * participant PUSH/DELETE operations are not reachable from this path. Unified revision pull follows.
     * Session 328 preserves the durable drain contract while moving environment mechanics behind seams.
     * Scope validation remains fail-closed.
     * Recovery remains first when required.
     * Push remains ordered before pull.
     * Health markers follow successful engine operations.
     * Requested generations are observed before every pass.
     * Eligible outbox work prevents premature draining.
     * Generation changes force another pass.
     * Drained state is committed only while idle.
     * Continuations remain scheduler requests.
     * Wake mechanics stay outside this class.
     * Room access stays outside this class.
     * Wall-clock access stays outside this class.
     * Existing failure propagation remains unchanged.
     */
    suspend fun drainOrchestration(scope: SyncWorkScope): SyncDrainResult = syncMutex.withLock {
        _syncInProgress.value = true
        try {
            validateScope(scope)
            orchestration.bindScope(scope)
            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.RUNNING)
            var latestRequiresReview = 0
            var latestRejected = 0
            val migrationDeletions = runtime.captureDeletionSnapshot()
            val migrationReviewCount = runtime.prepareLegacyV2Migration(
                scope,
                migrationDeletions,
                orchestration.nowMillis(),
            )
            validateScope(scope)
            val preflightRecovery = v2Engine.requiredRecoveryReason(scope.organizationId) { validateScope(scope) }
            var recoveryPromotionBlockedByMigrationEvidence = false
            var recoveryContinuationDeferredByMigrationEvidence = false
            if (preflightRecovery != null) {
                when (v2Engine.runRecovery(
                    scope.organizationId, preflightRecovery,
                    promotionAllowed = migrationReviewCount == 0,
                    scopeGuard = { validateScope(scope) },
                )) {
                    RecoveryRunOutcome.READY -> Unit
                    RecoveryRunOutcome.CONTINUATION_REQUIRED -> {
                        if (migrationReviewCount > 0) {
                            // M03 permits staging to progress, but unrelated prepared local work must
                            // still get one push opportunity before the continuation is scheduled.
                            recoveryContinuationDeferredByMigrationEvidence = true
                        } else {
                            orchestration.enqueueContinuation(scope)
                            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.CONTINUATION_PENDING)
                            return@withLock SyncDrainResult.CONTINUATION_SCHEDULED
                        }
                    }
                    RecoveryRunOutcome.PROMOTION_BLOCKED -> {
                        check(migrationReviewCount > 0) { "RECOVERY_PROMOTION_BLOCKED_WITHOUT_M03_EVIDENCE" }
                        recoveryPromotionBlockedByMigrationEvidence = true
                    }
                    RecoveryRunOutcome.AUTH_BLOCKED -> {
                        publishV2ReportBestEffort(scope, SyncCoordinatorPhase.AUTH_BLOCKED, failureCategory = SyncReportFailureCategory.AUTHENTICATION)
                        return@withLock SyncDrainResult.AUTH_BLOCKED
                    }
                }
            }
            repeat(MAX_DRAIN_PASSES) { _ ->
                validateScope(scope)
                val observed = orchestration.readGenerations(scope.organizationId)
                // M05: V2 drain has one explicit push coordinator. Legacy participant PUSH/DELETE
                // operations are intentionally not invoked from this path.
                validateScope(scope)
                val push = v2Engine.pushAvailable(scope)
                latestRequiresReview = push.requiresReview
                latestRejected = push.rejected
                if (push.requiresReview > 0 || push.rejected > 0) {
                    v2Engine.markFailure(
                        scope.organizationId,
                        "PUSH_TERMINAL",
                        "REVIEW_${push.requiresReview}_REJECTED_${push.rejected}",
                    )
                } else {
                    v2Engine.markPushSuccess(scope.organizationId)
                }
                if (recoveryPromotionBlockedByMigrationEvidence || recoveryContinuationDeferredByMigrationEvidence) {
                    // Do not pull or promote while M03 organization evidence is unresolved. The
                    // independent push above is safe because it preserves its own frozen scope.
                    orchestration.enqueueContinuation(scope)
                    publishV2ReportBestEffort(scope, SyncCoordinatorPhase.CONTINUATION_PENDING)
                    return@withLock SyncDrainResult.CONTINUATION_SCHEDULED
                }
                validateScope(scope)
                val pull = v2Engine.pull(scope.organizationId)
                if (pull.outcome == UnifiedSyncPullOutcome.CAUGHT_UP) {
                    v2Engine.markPullSuccess(scope.organizationId)
                }
                validateScope(scope)

                if (pull.outcome == UnifiedSyncPullOutcome.BOOTSTRAP_REQUIRED ||
                    pull.outcome == UnifiedSyncPullOutcome.RECOVERY_REQUIRED
                ) {
                    val reason = v2Engine.requiredRecoveryReason(scope.organizationId) { validateScope(scope) }
                        ?: RecoveryReason.CURSOR_CORRUPT
                    when (v2Engine.runRecovery(
                        scope.organizationId, reason, promotionAllowed = migrationReviewCount == 0,
                        scopeGuard = { validateScope(scope) },
                    )) {
                        RecoveryRunOutcome.READY -> return@repeat
                        RecoveryRunOutcome.CONTINUATION_REQUIRED -> {
                            orchestration.enqueueContinuation(scope)
                            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.CONTINUATION_PENDING)
                            return@withLock SyncDrainResult.CONTINUATION_SCHEDULED
                        }
                        RecoveryRunOutcome.PROMOTION_BLOCKED -> {
                            orchestration.enqueueContinuation(scope)
                            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.CONTINUATION_PENDING)
                            return@withLock SyncDrainResult.CONTINUATION_SCHEDULED
                        }
                        RecoveryRunOutcome.AUTH_BLOCKED -> {
                            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.AUTH_BLOCKED, failureCategory = SyncReportFailureCategory.AUTHENTICATION)
                            return@withLock SyncDrainResult.AUTH_BLOCKED
                        }
                    }
                }

                if (pull.outcome in setOf(UnifiedSyncPullOutcome.WAITING_LOCAL_OR_DEPENDENCY,
                        UnifiedSyncPullOutcome.REQUIRES_REVIEW, UnifiedSyncPullOutcome.WAITING_STORAGE_OR_REVIEW)) {
                    v2Engine.markFailure(scope.organizationId, "INBOX_WAIT", pull.outcome.name)
                    val review = pull.outcome == UnifiedSyncPullOutcome.REQUIRES_REVIEW
                    publishV2ReportBestEffort(scope,
                        if (review) SyncCoordinatorPhase.NEEDS_REVIEW else SyncCoordinatorPhase.WAITING_INBOX,
                        failureCategory = if (review) SyncReportFailureCategory.CONFLICT else null)
                    // An inbox wait must not suppress an already scheduled local outbox retry.
                    // Only its persisted future eligibility creates this delayed wake; unchanged
                    // inbox waits themselves never create a fast polling loop.
                    val retryAt = push.nextEligibleAt
                    val waitNow = orchestration.nowMillis()
                    if (push.backlog > 0L && retryAt != null && retryAt > waitNow) {
                        orchestration.enqueueContinuation(scope, retryAt - waitNow)
                    }
                    // No immediate retry of unchanged waits. A committed owner/dependency resolution
                    // or the regular/manual sync request wakes their durable generation.
                    return@withLock if (review) SyncDrainResult.NEEDS_REVIEW else SyncDrainResult.WAITING_INBOX
                }

                val now = orchestration.nowMillis()
                val eligibleNow = orchestration.countEligibleOutboxNow(scope.organizationId, now)
                val after = orchestration.readGenerations(scope.organizationId)
                val more = push.outcome == UnifiedSyncPushOutcome.MORE_AVAILABLE ||
                    pull.outcome == UnifiedSyncPullOutcome.MORE_AVAILABLE || eligibleNow > 0L
                if (more || after.requested != observed.requested) return@repeat

                val nextEligibleAt = push.nextEligibleAt
                if (push.backlog > 0L && nextEligibleAt != null && nextEligibleAt > now) {
                    orchestration.enqueueContinuation(scope, nextEligibleAt - now)
                    publishV2ReportBestEffort(scope, SyncCoordinatorPhase.CONTINUATION_PENDING)
                    return@withLock SyncDrainResult.CONTINUATION_SCHEDULED
                }

                if (orchestration.markDrainedIfIdle(
                        scope.organizationId, observed.requested,
                    )
                ) {
                    val finalState = orchestration.readGenerations(scope.organizationId)
                    if (finalState.requested <= finalState.drained) {
                        if (latestRequiresReview > 0 || latestRejected > 0) {
                            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.NEEDS_REVIEW, failureCategory = SyncReportFailureCategory.CONFLICT)
                            return@withLock SyncDrainResult.NEEDS_REVIEW
                        }
                        lastSyncCompletedAtMillis = orchestration.nowMillis()
                        publishV2ReportBestEffort(scope, SyncCoordinatorPhase.COMPLETED)
                        _syncCompleted.tryEmit(Unit)
                        return@withLock SyncDrainResult.IDLE
                    }
                }
            }
            validateScope(scope)
            orchestration.enqueueContinuation(scope)
            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.CONTINUATION_PENDING)
            SyncDrainResult.CONTINUATION_SCHEDULED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.log(Level.SEVERE, "Sync drain failed", failure)
            v2Engine.markFailure(scope.organizationId, "SYNC_DRAIN", failure::class.java.simpleName)
            publishV2ReportBestEffort(scope, SyncCoordinatorPhase.FAILED, failureCategory = failure.toReportFailureCategory())
            throw failure
        } finally {
            orchestration.finishInboxDrain(scope)
            _syncInProgress.value = false
        }
    }

    /** يستعيد تقرير الحساب والمؤسسة الحاليين فقط، ويمنع تسرب تقرير مؤسسة أخرى. */
    suspend fun restoreLastReportForCurrentSession(): PersistedSyncReport? = withContext(Dispatchers.IO) {
        _persistedReport.value = null
        lastSyncCompletedAtMillis = 0L
        val profile = runtime.currentPrincipal() ?: return@withContext null
        val encoded = runtime.getPersistedSyncReport(profile.organizationId, profile.userId)
            ?: return@withContext null
        val report = runCatching { json.decodeFromString(PersistedSyncReport.serializer(), encoded) }.getOrNull()
            ?.takeIf { it.organizationId == profile.organizationId && it.userId == profile.userId }
        _persistedReport.value = report
        lastSyncCompletedAtMillis = report?.lastSuccessfulAtMillis ?: 0L
        report
    }

    /** M07: local reset is fail-closed while any unconfirmed V2 intent exists. */
    suspend fun clearLocalData(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            syncMutex.withLock {
                val orgId = runtime.getLastOrganizationId().ifBlank { runtime.currentPrincipal()?.organizationId.orEmpty() }
                if (orgId.isNotBlank() && v2Engine.hasUnconfirmedWork(orgId)) {
                    throw BusinessRuleFailureException("SYNC_PENDING_SESSION_END")
                }
                runtime.clearLocalData()
                _lastReport.value = null
                _persistedReport.value = null
                lastSyncCompletedAtMillis = 0L
            }
        }
    }

    /** M07 fail-closed session transition: old work is epoch-invalidated and unconfirmed intent is never erased. */
    suspend fun prepareSessionForOrg(orgId: String) {
        require(orgId.isNotBlank()) { "organization id is required" }
        orchestration.cancelAll() // advisory; epoch validation remains authoritative.
        val lastOrgId = runtime.getLastOrganizationId()
        if (lastOrgId.isNotBlank() && lastOrgId != orgId) {
            if (v2Engine.hasUnconfirmedWork(lastOrgId)) {
                throw BusinessRuleFailureException("SYNC_PENDING_ORG_SWITCH")
            }
            clearLocalData().getOrThrow()
        }
        runtime.setLastOrganizationId(orgId)
        runtime.activateNextSessionEpoch()
    }

    /** Blocks logout/session destruction while V2 still owns unconfirmed local intent. */
    suspend fun ensureSessionCanEnd() {
        val orgId = runtime.getLastOrganizationId().ifBlank { runtime.currentPrincipal()?.organizationId.orEmpty() }
        if (orgId.isNotBlank() && v2Engine.hasUnconfirmedWork(orgId)) {
            throw BusinessRuleFailureException("SYNC_PENDING_SESSION_END")
        }
    }

    @Deprecated("M07: legacy participant fullSync is retained only for M09 deletion; production entry points must use request().")
    internal suspend fun fullSync(expectedScope: SyncWorkScope? = null): Result<Unit> {
        if (syncMutex.isLocked) {
            return Result.failure(BusinessRuleFailureException("SYNC_SKIPPED"))
        }

        return syncMutex.withLock {
            _syncInProgress.value = true
            try {
                withContext(Dispatchers.IO) {
                    var runId = "unstarted"
                    var organizationId = ""
                    var userId = ""
                    var organizationLabel = ""
                    var resumed = false
                    var startingRevision = 0L
                    var nextRevision: Long? = null
                    val attemptedAt = orchestration.nowMillis()
                    val traces = mutableListOf<SyncOperationTrace>()
                    var lastSuccess = lastSyncCompletedAtMillis.takeIf { it > 0L }

                    runCatching {
                        val profile = runtime.currentPrincipal()
                            ?: throw StaleSyncWorkScopeException("sync_scope_stale: user session is unavailable")
                        expectedScope?.let { scope ->
                            if (profile.organizationId != scope.organizationId || profile.userId != scope.userId ||
                                runtime.sessionEpoch() != scope.sessionEpoch
                            ) {
                                throw StaleSyncWorkScopeException("sync_scope_stale: tenant/account/session changed")
                            }
                        }
                        organizationId = profile.organizationId
                        userId = profile.userId
                        organizationLabel = runtime.currentOrganizationDisplayName().ifBlank { organizationId }

                        validateParticipants()
                        runtime.prepareForSync()

                        // Session 308: legacy run checkpoint remains compatibility-only.
                        // Unified V2 continuation authority lives exclusively in Room.sync_cursor
                        // and is advanced only by UnifiedSyncPullEngine's atomic page transaction.
                        startingRevision = 0L
                        val checkpoint = runtime.beginOrResumeSyncRun(organizationId, startingRevision)
                        runId = checkpoint.runId
                        resumed = checkpoint.resumed
                        nextRevision = startingRevision

                        val context = SyncRunContext(
                            organizationId = organizationId,
                            userId = profile.userId,
                            deletions = runtime.captureDeletionSnapshot()
                        )

                        val operations = participants.flatMap { participant ->
                            participant.operations(context).map { operation ->
                                operation.copy(
                                    participantKey = participant.key,
                                    idempotencyKey = SyncOperationIdempotency.key(
                                        runId = checkpoint.runId,
                                        organizationId = organizationId,
                                        participantKey = participant.key,
                                        stage = operation.stage,
                                        order = operation.order
                                    )
                                )
                            }
                        }
                        require(operations.all { it.idempotencyKey.isNotBlank() }) {
                            "Every sync operation requires an idempotency key"
                        }

                        var firstCollectedFailure: Throwable? = null
                        val syncErrors = executeSyncOperations(
                            operations = operations,
                            completedIdempotencyKeys = checkpoint.completedOperationKeys,
                            onOperationCompleted = { operation ->
                                runtime.markSyncOperationCompleted(
                                    organizationId = organizationId,
                                    runId = checkpoint.runId,
                                    operationKey = operation.idempotencyKey
                                )
                            },
                            onTrace = traces::add,
                            onCollectedFailure = { _, throwable ->
                                if (firstCollectedFailure == null) {
                                    firstCollectedFailure = RemoteFailureBoundary.wrap(throwable)
                                }
                                logger.warning(
                                    "Sync operation failed type=${throwable::class.java.simpleName} " +
                                        "reason=${throwable.safeDiagnosticMessage()}"
                                )
                            }
                        )

                        if (syncErrors.isNotEmpty()) {
                            logger.warning("Sync run failed with ${syncErrors.size} operation errors")
                            throw firstCollectedFailure ?: IllegalStateException("sync operation failed")
                        }

                        // Legacy checkpoint completion only; it is not a V2 pull cursor authority.
                        runtime.completeSyncRun(organizationId, checkpoint.runId)
                        runtime.markSyncCompleted()
                        Unit
                    }.also { result ->
                        result.exceptionOrNull()?.let { failure ->
                            logger.warning(
                                "Sync failed type=${failure::class.java.simpleName} " +
                                    "reason=${failure.safeDiagnosticMessage()}"
                            )
                        }
                        if (result.isSuccess) {
                            lastSyncCompletedAtMillis = orchestration.nowMillis()
                            lastSuccess = lastSyncCompletedAtMillis
                            _syncCompleted.tryEmit(Unit)
                        }
                        val report = SyncRunReport(
                            runId = runId,
                            organizationId = organizationId,
                            resumed = resumed,
                            startingRevision = startingRevision,
                            nextRevision = nextRevision,
                            operations = traces.toList(),
                            completed = result.isSuccess,
                            failureType = result.exceptionOrNull()?.let { it::class.simpleName ?: "Throwable" },
                            userId = userId,
                            attemptedAtMillis = attemptedAt,
                            lastSuccessfulAtMillis = lastSuccess,
                            organizationLabel = organizationLabel.ifBlank { organizationId },
                            failureCategory = result.exceptionOrNull()?.toReportFailureCategory(),
                        )
                        _lastReport.value = report
                        if (organizationId.isNotBlank() && userId.isNotBlank()) {
                            val persisted = report.toPersisted()
                            _persistedReport.value = persisted
                            runtime.setPersistedSyncReport(
                                organizationId,
                                userId,
                                json.encodeToString(PersistedSyncReport.serializer(), persisted)
                            )
                        }
                    }
                }
            } finally {
                _syncInProgress.value = false
            }
        }
    }

    suspend fun pullNotifications(orgId: String): Result<Unit> =
        requestForOrganization(orgId, SyncRequestReason.MANUAL).map { Unit }

    suspend fun pullCommissions(orgId: String): Result<Unit> =
        requestForOrganization(orgId, SyncRequestReason.MANUAL).map { Unit }

    /** Realtime is acceleration only; it persists the same V2 request as every other entry point. */
    suspend fun triggerPull() {
        requestSync(SyncRequestReason.REALTIME)
    }

    private suspend fun requestForOrganization(orgId: String, reason: SyncRequestReason): Result<SyncRequestReceipt> = runCatching {
        val scope = currentWorkScope() ?: throw StaleSyncWorkScopeException("sync_scope_stale: no active session")
        if (scope.organizationId != orgId) throw StaleSyncWorkScopeException("sync_scope_stale: organization changed")
        requestSync(scope, reason)
    }

    private suspend fun pullSingle(block: suspend () -> Unit): Result<Unit> {
        if (syncMutex.isLocked) {
            return Result.failure(BusinessRuleFailureException("SYNC_SKIPPED"))
        }

        return syncMutex.withLock {
            _syncInProgress.value = true
            try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        block()
                        Unit
                    }.also { result ->
                        if (result.isSuccess) {
                            lastSyncCompletedAtMillis = orchestration.nowMillis()
                            _syncCompleted.tryEmit(Unit)
                        }
                    }
                }
            } finally {
                _syncInProgress.value = false
            }
        }
    }

    private suspend fun publishV2ReportBestEffort(
        scope: SyncWorkScope,
        phase: SyncCoordinatorPhase,
        attemptedAtMillis: Long? = null,
        failureCategory: SyncReportFailureCategory? = null,
    ) {
        try {
            validateScope(scope)
            val health = v2Engine.healthSnapshot(scope)
            val previous = _persistedReport.value?.takeIf {
                it.organizationId == scope.organizationId && it.userId == scope.userId
            }
            val now = orchestration.nowMillis()
            val completed = phase == SyncCoordinatorPhase.COMPLETED
            val report = PersistedSyncReport(
                organizationId = scope.organizationId,
                userId = scope.userId,
                organizationLabel = runtime.currentOrganizationDisplayName().ifBlank { scope.organizationId },
                attemptedAtMillis = attemptedAtMillis ?: previous?.attemptedAtMillis ?: now,
                lastSuccessfulAtMillis = if (completed) now else previous?.lastSuccessfulAtMillis,
                resumed = phase == SyncCoordinatorPhase.CONTINUATION_PENDING,
                completed = completed,
                operations = emptyList(),
                failureCategory = failureCategory,
                coordinatorPhase = phase,
                requestedGeneration = health.requestedGeneration,
                drainedGeneration = health.drainedGeneration,
                pendingCount = health.pendingCount,
                requiresReviewCount = health.requiresReviewCount,
                rejectedCount = health.rejectedCount,
            )
            _persistedReport.value = report
            runtime.setPersistedSyncReport(
                scope.organizationId,
                scope.userId,
                json.encodeToString(PersistedSyncReport.serializer(), report),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warning("sync report update failed phase=$phase type=${failure::class.java.simpleName}")
        }
    }

    private fun validateParticipants() {
        val participantKeys = participants.map { it.key }
        val duplicateParticipants = participantKeys
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
        require(duplicateParticipants.isEmpty()) {
            "Duplicate sync participants: $duplicateParticipants"
        }
        val missingParticipants = REQUIRED_PARTICIPANTS - participantKeys.toSet()
        require(missingParticipants.isEmpty()) {
            "Missing sync participants: $missingParticipants"
        }
    }

    private fun Throwable.toReportFailureCategory(): SyncReportFailureCategory =
        when (SyncRetryPolicy.classify(this)) {
            SyncFailureKind.TRANSIENT_NETWORK -> SyncReportFailureCategory.NETWORK
            SyncFailureKind.RATE_LIMITED -> SyncReportFailureCategory.RATE_LIMITED
            SyncFailureKind.AUTHENTICATION -> SyncReportFailureCategory.AUTHENTICATION
            SyncFailureKind.PERMISSION -> SyncReportFailureCategory.PERMISSION
            SyncFailureKind.SERVER_FAILURE -> SyncReportFailureCategory.SERVER
            SyncFailureKind.VALIDATION -> SyncReportFailureCategory.VALIDATION
            SyncFailureKind.CONTRACT,
            SyncFailureKind.PERMANENT_PROTOCOL,
            -> SyncReportFailureCategory.CONTRACT
            SyncFailureKind.CONFLICT_DOMAIN -> SyncReportFailureCategory.CONFLICT
            SyncFailureKind.STALE_SCOPE -> SyncReportFailureCategory.STALE_SCOPE
            SyncFailureKind.RECOVERY_REQUIRED -> SyncReportFailureCategory.RECOVERY_REQUIRED
            SyncFailureKind.LOCAL_STORAGE_FAILURE -> SyncReportFailureCategory.LOCAL_STORAGE
            SyncFailureKind.CANCELLED -> SyncReportFailureCategory.UNKNOWN
        }

    private suspend fun validateScope(scope: SyncWorkScope) {
        val profile = runtime.currentPrincipal()
            ?: throw StaleSyncWorkScopeException("sync_scope_stale: user session is unavailable")
        if (profile.organizationId != scope.organizationId || profile.userId != scope.userId ||
            runtime.sessionEpoch() != scope.sessionEpoch
        ) throw StaleSyncWorkScopeException("sync_scope_stale: tenant/account/session changed")
    }

    private companion object {
        const val MAX_DRAIN_PASSES = 4
        val REQUIRED_PARTICIPANTS = setOf(
            "clients", "invoices", "inventory", "logistics-v2", "cash",
            "organization", "optimal_outbox", "educational_content",
        )
    }
}

private fun Throwable.safeDiagnosticMessage(): String =
    generateSequence(this) { it.cause }
        .joinToString(" <- ") { throwable ->
            val message = throwable.message.orEmpty()
                .replace(
                    Regex("(?i)(access[_ -]?token|refresh[_ -]?token|authorization|bearer|api[_ -]?key)\\s*[:=]\\s*[^\\s,;]+"),
                    "$1=[REDACTED]"
                )
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(240)
            "${throwable::class.java.simpleName}${if (message.isBlank()) "" else ": $message"}"
        }


enum class SyncRequestReason { MANUAL, FOREGROUND, REALTIME, STARTUP, PERIODIC, OUTBOX_WRITE, CONTINUATION }

data class SyncRequestReceipt(
    val organizationId: String,
    val userId: String,
    val generation: Long,
    val reason: SyncRequestReason,
    val acceptedAtMillis: Long,
    val wakeEnqueued: Boolean,
)

enum class SyncDrainResult { IDLE, WAITING_INBOX, NEEDS_REVIEW, CONTINUATION_SCHEDULED, AUTH_BLOCKED, RECOVERY_REQUIRED, STALE_SCOPE }
