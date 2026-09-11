package com.verto.app.feature.reports.domain.model

import java.util.UUID

enum class ReportBudgetPeriod(val label: String) {
    DAILY("يومي"), WEEKLY("أسبوعي"), MONTHLY("شهري"), QUARTERLY("ربع سنوي"), YEARLY("سنوي")
}

enum class ReportBudgetType(val label: String) {
    SALES_TARGET("مبيعات مستهدفة"),
    PROFIT_TARGET("أرباح مستهدفة"),
    EXPENSE_LIMIT("حد أقصى للمصروفات"),
    CATEGORY_TARGET("هدف لتصنيف معين")
}

data class ReportBudget(
    val id: String = UUID.randomUUID().toString(),
    val periodType: ReportBudgetPeriod = ReportBudgetPeriod.MONTHLY,
    val periodStart: Long,
    val periodEnd: Long,
    val budgetType: ReportBudgetType = ReportBudgetType.SALES_TARGET,
    val category: String = "",
    val targetAmount: Double,
    val note: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ReportCashStatus { OPEN, CLOSED, DISPUTED }

data class ReportCashSession(
    val id: String = UUID.randomUUID().toString(),
    val employeeId: String = "",
    val employeeName: String = "",
    val openingBalance: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val totalCashIn: Double = 0.0,
    val totalCashOut: Double = 0.0,
    val expectedBalance: Double = 0.0,
    val actualCountedBalance: Double = 0.0,
    val variance: Double = 0.0,
    val varianceReason: String = "",
    val status: ReportCashStatus = ReportCashStatus.OPEN,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val notes: String = ""
)

data class ReportCashDenomination(
    val id: String = UUID.randomUUID().toString(),
    val reconciliationId: String,
    val denominationValue: Double,
    val count: Int = 0,
    val subtotal: Double = 0.0,
    val isCoin: Boolean = false
)

data class SaveReportBudgetCommand(val budget: ReportBudget)
data class DeleteReportBudgetCommand(val budgetId: String)
data class StartReportShiftCommand(
    val employeeId: String,
    val employeeName: String,
    val openingBalance: Double
)
data class CloseReportShiftCommand(
    val session: ReportCashSession,
    val actualCounted: Double,
    val expectedBalance: Double,
    val varianceReason: String,
    val denominations: List<ReportCashDenomination>,
    val totalSales: Double
)
