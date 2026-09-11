package com.verto.app.feature.integration.optimal.domain.model

const val OPTIMAL_OUTBOX_SYNC_PARTICIPANT_KEY: String = "optimal_outbox"

enum class OptimalSyncState {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    BLOCKED,
}

data class OptimalOutboxEvent(
    val organizationId: String,
    val eventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operation: String,
    val payloadJson: String,
    val payloadVersion: Int,
    val idempotencyKey: String,
    val sequence: Long,
    /** Local aggregate version; currently identical to the monotonic sequence. */
    val aggregateVersion: Long,
    val state: OptimalSyncState,
    val attemptCount: Int,
    val lastError: String?,
    val lastAttemptAt: Long?,
    val nextAttemptAt: Long?,
    val remoteId: String?,
    val remoteVersion: Long?,
    val syncedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class OptimalOutboxLease(
    val event: OptimalOutboxEvent,
    val owner: String,
    val token: String,
    val expiresAt: Long,
)

sealed interface OptimalOutboxExecutionResult {
    data class Synced(
        val remoteId: String? = null,
        val remoteVersion: Long? = null,
    ) : OptimalOutboxExecutionResult

    data class Failed(val reason: String) : OptimalOutboxExecutionResult

    /** Permanent or unresolved conflict. The event stays visible and is not retried automatically. */
    data class Blocked(
        val reason: String,
        val remoteId: String? = null,
        val remoteVersion: Long? = null,
    ) : OptimalOutboxExecutionResult
}

data class OptimalSyncRunSummary(
    val claimed: Int = 0,
    val synced: Int = 0,
    val failed: Int = 0,
    val blocked: Int = 0,
    val recoveredLeases: Int = 0,
    val stoppedBySessionChange: Boolean = false,
)
