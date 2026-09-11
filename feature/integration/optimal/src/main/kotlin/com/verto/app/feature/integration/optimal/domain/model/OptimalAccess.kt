package com.verto.app.feature.integration.optimal.domain.model

import com.verto.app.core.session.model.ManagementOptimalPermission

enum class OptimalRemoteContract {
    ISSUE_COMPANY_JOIN_CODE,
    SEND_MESSAGE,
    ARCHIVE_CONVERSATION,
    READ_VEHICLES,
    UPSERT_INVOICE,
    UPSERT_PAYMENT,
    REVERSE_PAYMENT,
    VOID_INVOICE,
    UPSERT_MAINTENANCE,
}

enum class OptimalBackendProtectionStatus {
    VERIFIED,
    BLOCKED,
}

enum class OptimalOperation(
    val permission: ManagementOptimalPermission,
    val remoteContract: OptimalRemoteContract? = null,
) {
    VIEW_MANAGEMENT(ManagementOptimalPermission.VIEW_MANAGEMENT),
    VIEW_OPTIMAL(ManagementOptimalPermission.VIEW_OPTIMAL),
    VIEW_COMPANIES(ManagementOptimalPermission.VIEW_OPTIMAL_COMPANIES),
    REQUEST_COMPANY_JOIN_CODE(ManagementOptimalPermission.ISSUE_OPTIMAL_CODE),
    ISSUE_COMPANY_JOIN_CODE(
        ManagementOptimalPermission.ISSUE_OPTIMAL_CODE,
        OptimalRemoteContract.ISSUE_COMPANY_JOIN_CODE,
    ),
    VIEW_MESSAGES(ManagementOptimalPermission.VIEW_OPTIMAL_MESSAGES),
    /** Local message + durable Outbox only; remote dispatch is a separate guarded operation. */
    SEND_MESSAGES(ManagementOptimalPermission.SEND_OPTIMAL_MESSAGES),
    DISPATCH_MESSAGES(
        ManagementOptimalPermission.SEND_OPTIMAL_MESSAGES,
        OptimalRemoteContract.SEND_MESSAGE,
    ),
    ARCHIVE_CONVERSATION(
        ManagementOptimalPermission.SEND_OPTIMAL_MESSAGES,
        OptimalRemoteContract.ARCHIVE_CONVERSATION,
    ),
    VIEW_INVOICES(ManagementOptimalPermission.VIEW_OPTIMAL_INVOICES),
    VIEW_MAINTENANCE(ManagementOptimalPermission.VIEW_OPTIMAL_MAINTENANCE),
    PULL_VEHICLES(
        ManagementOptimalPermission.VIEW_OPTIMAL_MAINTENANCE,
        OptimalRemoteContract.READ_VEHICLES,
    ),
    VIEW_SYNC_ISSUES(ManagementOptimalPermission.VIEW_OPTIMAL_SYNC_ISSUES),
    RETRY_SYNC(ManagementOptimalPermission.RETRY_OPTIMAL_SYNC),
}

enum class OptimalGuardLayer {
    USE_CASE,
    REPOSITORY,
}

sealed interface OptimalAccessDecision {
    data object Granted : OptimalAccessDecision
    data object PermissionDenied : OptimalAccessDecision
    data object BackendContractBlocked : OptimalAccessDecision
}

interface OptimalOperationGuard {
    suspend fun check(
        operation: OptimalOperation,
        layer: OptimalGuardLayer,
        details: String = "",
    ): OptimalAccessDecision
}

interface OptimalBackendContractGate {
    fun status(contract: OptimalRemoteContract): OptimalBackendProtectionStatus

    fun allows(contract: OptimalRemoteContract): Boolean =
        status(contract) == OptimalBackendProtectionStatus.VERIFIED
}
