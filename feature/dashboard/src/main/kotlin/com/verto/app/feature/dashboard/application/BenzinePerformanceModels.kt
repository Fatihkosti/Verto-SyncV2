package com.verto.app.feature.dashboard.application

enum class BenzinePerformanceStatus {
    NEW, ACTIVE, GROWING, DECLINING, INACTIVE
}

enum class BenzineFollowUpReason {
    NEW_NOT_STARTED, DECLINING, INACTIVE
}

enum class BenzineUserFilter {
    ALL, MARKETERS, WORKSHOPS, ACTIVE_THIS_WEEK, NEW, NEEDS_FOLLOW_UP
}

data class BenzineWeeklyPerformance(
    val clientId: String,
    val fullName: String,
    val phone: String,
    val accountType: String,
    val workshopName: String?,
    val joinedAt: String?,
    val lastSeenAt: String?,
    val currentWeekSales: Double,
    val previousWeekSales: Double,
    val currentWeekCommission: Double,
    val previousWeekCommission: Double,
    val currentWeekInvoices: Int,
    val previousWeekInvoices: Int,
    val firstPurchaseAt: Long?,
    val lastPurchaseAt: Long?,
    val status: BenzinePerformanceStatus,
    val followUpReason: BenzineFollowUpReason?,
    val salesDeltaPercent: Double?,
    val salesTrendLabel: String?,
)

data class BenzineWeeklySummary(
    val currentWeekStart: Long,
    val previousWeekStart: Long,
    val activeCurrent: Int,
    val activePrevious: Int,
    val salesCurrent: Double,
    val salesPrevious: Double,
    val commissionsCurrent: Double,
    val commissionsPrevious: Double,
    val followUpCurrent: Int,
    val followUpPrevious: Int,
)

data class BenzinePerformanceResult(
    val summary: BenzineWeeklySummary,
    val rows: List<BenzineWeeklyPerformance>,
)
