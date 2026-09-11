package com.verto.app.data.sync

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.error.RemoteFailureBoundary
import kotlinx.serialization.Serializable

/** Stable per-run key used to make operation replay observable and resumable. */
object SyncOperationIdempotency {
    fun key(
        runId: String,
        organizationId: String,
        participantKey: String,
        stage: SyncStage,
        order: Int
    ): String {
        require(runId.isNotBlank()) { "sync run id is required" }
        require(organizationId.isNotBlank()) { "organization id is required" }
        require(participantKey.isNotBlank()) { "participant key is required" }
        require(order >= 0) { "sync operation order cannot be negative" }
        return listOf(runId, organizationId, participantKey, stage.name, order.toString())
            .joinToString("|")
    }
}

@Serializable
enum class SyncOperationOutcome {
    SUCCEEDED,
    SKIPPED_CHECKPOINT,
    FAILED_COLLECTED,
    FAILED_ABORTED
}

data class SyncOperationTrace(
    val participantKey: String,
    val label: String,
    val idempotencyKey: String,
    val outcome: SyncOperationOutcome,
    val failureType: String? = null
)

data class SyncRunReport(
    val runId: String,
    val organizationId: String,
    val resumed: Boolean,
    val startingRevision: Long,
    val nextRevision: Long?,
    val operations: List<SyncOperationTrace>,
    val completed: Boolean,
    val failureType: String? = null,
    val userId: String = "",
    val attemptedAtMillis: Long = 0L,
    val lastSuccessfulAtMillis: Long? = null,
    val organizationLabel: String = organizationId,
    val failureCategory: SyncReportFailureCategory? = null
)

@Serializable
enum class SyncReportFailureCategory {
    NETWORK,
    AUTHENTICATION,
    PERMISSION,
    RATE_LIMITED,
    SERVER,
    VALIDATION,
    CONTRACT,
    CONFLICT,
    STALE_SCOPE,
    RECOVERY_REQUIRED,
    LOCAL_STORAGE,
    UNKNOWN
}

/**
 * النسخة الوحيدة التي تُحفظ محليًا من التقرير. لا تحتوي payload أو مفاتيح
 * idempotency أو أسماء Exceptions.
 */
@Serializable
data class PersistedSyncOperation(
    val participantKey: String,
    val label: String,
    val outcome: SyncOperationOutcome
)

@Serializable
enum class SyncCoordinatorPhase {
    IDLE,
    REQUESTED,
    RUNNING,
    CONTINUATION_PENDING,
    WAITING_INBOX,
    NEEDS_REVIEW,
    AUTH_BLOCKED,
    COMPLETED,
    FAILED,
}

@Serializable
data class PersistedSyncReport(
    val organizationId: String,
    val userId: String,
    val organizationLabel: String,
    val attemptedAtMillis: Long,
    val lastSuccessfulAtMillis: Long? = null,
    val resumed: Boolean,
    val completed: Boolean,
    val operations: List<PersistedSyncOperation>,
    val failureCategory: SyncReportFailureCategory? = null,
    // M07: V2 is the authoritative runtime report. Defaults keep older persisted JSON readable.
    val coordinatorPhase: SyncCoordinatorPhase = SyncCoordinatorPhase.IDLE,
    val requestedGeneration: Long = 0L,
    val drainedGeneration: Long = 0L,
    val pendingCount: Long = 0L,
    val requiresReviewCount: Long = 0L,
    val rejectedCount: Long = 0L,
)

fun SyncRunReport.toPersisted(): PersistedSyncReport = PersistedSyncReport(
    organizationId = organizationId,
    userId = userId,
    organizationLabel = organizationLabel,
    attemptedAtMillis = attemptedAtMillis,
    lastSuccessfulAtMillis = lastSuccessfulAtMillis,
    resumed = resumed,
    completed = completed,
    operations = operations.map { trace ->
        PersistedSyncOperation(
            participantKey = trace.participantKey,
            label = trace.label,
            outcome = trace.outcome,
        )
    },
    failureCategory = failureCategory,
)

enum class SyncFailureKind {
    TRANSIENT_NETWORK,
    RATE_LIMITED,
    AUTHENTICATION,
    PERMISSION,
    SERVER_FAILURE,
    VALIDATION,
    CONTRACT,
    PERMANENT_PROTOCOL,
    CONFLICT_DOMAIN,
    STALE_SCOPE,
    RECOVERY_REQUIRED,
    LOCAL_STORAGE_FAILURE,
    CANCELLED,
}

data class SyncRetryDecision(
    val kind: SyncFailureKind,
    val retry: Boolean
)

/**
 * Sync retry taxonomy based exclusively on Verto's structured failure contract.
 * Provider prose and Throwable.message are never parsed for classification.
 */
object SyncRetryPolicy {
    const val MAX_ATTEMPTS: Int = 5

    fun decide(throwable: Throwable, runAttemptCount: Int): SyncRetryDecision {
        val kind = classify(throwable)
        return SyncRetryDecision(
            kind = kind,
            retry = kind in setOf(
                SyncFailureKind.TRANSIENT_NETWORK,
                SyncFailureKind.RATE_LIMITED,
                SyncFailureKind.SERVER_FAILURE,
            ) && runAttemptCount < MAX_ATTEMPTS - 1
        )
    }

    fun classify(throwable: Throwable): SyncFailureKind {
        if (throwable is kotlinx.coroutines.CancellationException) return SyncFailureKind.CANCELLED
        if (throwable is StaleSyncWorkScopeException) return SyncFailureKind.STALE_SCOPE

        val failure = ErrorClassifier.classify(RemoteFailureBoundary.wrap(throwable))
        return when (failure) {
            is AppFailure.NetworkUnavailable,
            is AppFailure.ConnectionFailed,
            is AppFailure.Timeout,
            -> SyncFailureKind.TRANSIENT_NETWORK

            is AppFailure.RateLimited -> SyncFailureKind.RATE_LIMITED
            is AppFailure.Unauthorized -> SyncFailureKind.AUTHENTICATION
            is AppFailure.PermissionDenied -> SyncFailureKind.PERMISSION
            is AppFailure.Server -> SyncFailureKind.SERVER_FAILURE
            is AppFailure.Validation -> SyncFailureKind.VALIDATION
            is AppFailure.Conflict -> SyncFailureKind.CONFLICT_DOMAIN
            is AppFailure.LocalStorage -> SyncFailureKind.LOCAL_STORAGE_FAILURE
            is AppFailure.BusinessRule -> when (failure.code) {
                "SYNC_SKIPPED" -> SyncFailureKind.PERMANENT_PROTOCOL
                "SYNC_RECOVERY_REQUIRED" -> SyncFailureKind.RECOVERY_REQUIRED
                "SYNC_CONFLICT" -> SyncFailureKind.CONFLICT_DOMAIN
                else -> SyncFailureKind.CONTRACT
            }
            is AppFailure.NotFound,
            is AppFailure.RemoteRejected,
            -> SyncFailureKind.CONTRACT
            is AppFailure.Unknown -> SyncFailureKind.PERMANENT_PROTOCOL
        }
    }
}

/** Collapses realtime bursts and suppresses the echo generated by the just-finished push. */
class RealtimeTriggerGate(
    private val cooldownMillis: Long = 2_000L
) {
    init {
        require(cooldownMillis >= 0L)
    }

    private var activeOrganizationId: String? = null
    private var lastAcceptedAt: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldTrigger(
        organizationId: String,
        nowMillis: Long,
        syncInProgress: Boolean,
        lastSyncCompletedAtMillis: Long
    ): Boolean {
        if (organizationId.isBlank() || syncInProgress) return false
        if (lastSyncCompletedAtMillis > 0L && elapsed(nowMillis, lastSyncCompletedAtMillis) < cooldownMillis) {
            return false
        }
        if (activeOrganizationId == organizationId && elapsed(nowMillis, lastAcceptedAt) < cooldownMillis) {
            return false
        }
        activeOrganizationId = organizationId
        lastAcceptedAt = nowMillis
        return true
    }

    @Synchronized
    fun reset() {
        activeOrganizationId = null
        lastAcceptedAt = Long.MIN_VALUE
    }

    private fun elapsed(now: Long, then: Long): Long =
        if (then == Long.MIN_VALUE || now < then) Long.MAX_VALUE else now - then
}
