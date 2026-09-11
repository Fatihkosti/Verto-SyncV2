package com.verto.app.feature.integration.optimal.domain.model

enum class OptimalSyncIssueState {
    FAILED,
    BLOCKED,
    RETRYING,
}

data class OptimalSyncIssue(
    val organizationId: String,
    val eventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operation: String,
    val dataTypeLabel: String,
    val operationLabel: String,
    val clientId: String?,
    val companyName: String?,
    val invoiceId: String?,
    val reason: String,
    val state: OptimalSyncIssueState,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val canRetry: Boolean
        get() = state == OptimalSyncIssueState.FAILED || state == OptimalSyncIssueState.BLOCKED
}

sealed interface RetryOptimalSyncResult {
    data class Retried(val syncScheduled: Boolean) : RetryOptimalSyncResult
    data object NotFoundOrNotRetryable : RetryOptimalSyncResult
    data object PermissionDenied : RetryOptimalSyncResult
    data object SessionUnavailable : RetryOptimalSyncResult
}
