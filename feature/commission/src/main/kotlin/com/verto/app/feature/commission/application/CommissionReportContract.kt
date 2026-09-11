package com.verto.app.feature.commission.application

import kotlinx.coroutines.flow.StateFlow

data class MarketerCommissionRowItem(
    val invoiceNumber: Int,
    val dateLabel: String,
    val invoiceTotal: Double,
    val commission: Double,
    val statusLabel: String
)

data class MarketerCommissionReportItem(
    val marketerName: String,
    val periodLabel: String,
    val rows: List<MarketerCommissionRowItem>,
    val totalCommission: Double,
    val withdrawableTotal: Double,
    val paidTotal: Double,
    val pendingTotal: Double
)

interface CommissionReportGateway {
    val role: StateFlow<String?>

    suspend fun buildMarketerCommissionReport(
        clientId: String,
        from: Long?,
        to: Long?
    ): Result<MarketerCommissionReportItem>

    suspend fun sendAdminReminder(
        clientId: String,
        message: String,
        navRoute: String
    ): Result<Unit>
}
