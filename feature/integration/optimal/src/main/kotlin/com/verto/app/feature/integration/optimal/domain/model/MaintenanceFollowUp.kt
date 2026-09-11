package com.verto.app.feature.integration.optimal.domain.model

enum class MaintenanceFollowUpStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

data class MaintenanceFollowUp(
    val organizationId: String,
    val recordId: String,
    val status: MaintenanceFollowUpStatus,
    val startedAt: Long,
    val expectedAt: Long?,
    val updatedAt: Long,
) {
    val isInProgress: Boolean get() = status == MaintenanceFollowUpStatus.IN_PROGRESS

    fun isOverdue(nowMillis: Long): Boolean =
        isInProgress && expectedAt?.let { it < nowMillis } == true
}

data class OperationalMaintenanceFollowUp(
    val followUp: MaintenanceFollowUp,
    val invoiceId: String,
    val overdue: Boolean,
    val vehicleName: String = "",
    val vehicleType: String = "",
    val plateNumber: String = "",
)
