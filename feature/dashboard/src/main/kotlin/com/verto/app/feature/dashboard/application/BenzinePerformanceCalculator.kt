package com.verto.app.feature.dashboard.application

import kotlin.math.round

/** Pure weekly read-model calculator. No persistence and no backend calls. */
object BenzinePerformanceCalculator {
    private const val CLOSED_CASH = "CLOSED_CASH"
    private const val CLOSED_CREDIT = "CLOSED_CREDIT"

    fun calculate(
        stats: List<MarketerStatsItem>,
        eligibility: List<CommissionEligibilityItem>,
        nowMillis: Long,
    ): BenzinePerformanceResult {
        val invoicesByClient = eligibility.asSequence()
            .filter { it.invoiceStatus == CLOSED_CASH || it.invoiceStatus == CLOSED_CREDIT }
            .mapNotNull { item ->
                BenzineWeekPolicy.parseIsoMillis(item.createdAt)?.let { createdAt -> item to createdAt }
            }
            .groupBy({ it.first.clientId }, { TimedEligibility(it.first, it.second) })

        val rows = stats.map { stat ->
            calculateRow(stat, invoicesByClient[stat.clientId].orEmpty(), nowMillis)
        }
        val currentStart = BenzineWeekPolicy.currentWeekStart(nowMillis)
        val previousStart = BenzineWeekPolicy.previousWeekStart(nowMillis)

        // Historical follow-up uses the previous completed week only. Accounts that joined
        // in the current week and invoices created after that historical window are excluded.
        val previousFollowUp = stats.count { stat ->
            val joinedAt = BenzineWeekPolicy.parseIsoMillis(stat.joinedAt)
            val existedBeforeCurrentWeek = joinedAt == null || joinedAt < currentStart
            val historicalInvoices = invoicesByClient[stat.clientId].orEmpty()
                .filter { it.createdAt < currentStart }
            existedBeforeCurrentWeek &&
                calculateRow(stat, historicalInvoices, previousStart).followUpReason != null
        }

        return BenzinePerformanceResult(
            summary = BenzineWeeklySummary(
                currentWeekStart = currentStart,
                previousWeekStart = previousStart,
                activeCurrent = rows.count { it.currentWeekInvoices > 0 },
                activePrevious = rows.count { it.previousWeekInvoices > 0 },
                salesCurrent = rows.sumOf { it.currentWeekSales },
                salesPrevious = rows.sumOf { it.previousWeekSales },
                commissionsCurrent = rows.sumOf { it.currentWeekCommission },
                commissionsPrevious = rows.sumOf { it.previousWeekCommission },
                followUpCurrent = rows.count { it.followUpReason != null },
                followUpPrevious = previousFollowUp,
            ),
            rows = rows,
        )
    }

    fun deltaPercent(current: Double, previous: Double): Double? =
        if (previous > 0.0) ((current - previous) / previous) * 100.0 else null

    fun trendLabel(current: Double, previous: Double): String = when {
        previous > 0.0 -> formatPercent(deltaPercent(current, previous) ?: 0.0)
        current > 0.0 -> "بدأ هذا الأسبوع"
        else -> "—"
    }

    private fun calculateRow(
        stat: MarketerStatsItem,
        invoices: List<TimedEligibility>,
        nowMillis: Long,
    ): BenzineWeeklyPerformance {
        val currentStart = BenzineWeekPolicy.currentWeekStart(nowMillis)
        val previousStart = BenzineWeekPolicy.previousWeekStart(nowMillis)

        val currentRows = invoices.filter { BenzineWeekPolicy.weekStartOf(it.createdAt) == currentStart }
        val previousRows = invoices.filter { BenzineWeekPolicy.weekStartOf(it.createdAt) == previousStart }

        val currentSales = currentRows.sumOf { it.item.totalAmount }
        val previousSales = previousRows.sumOf { it.item.totalAmount }
        val currentCommission = currentRows.sumOf { it.item.commission }
        val previousCommission = previousRows.sumOf { it.item.commission }
        val firstPurchaseAt = invoices.minOfOrNull { it.createdAt }
        val lastPurchaseAt = invoices.maxOfOrNull { it.createdAt }
        val joinedAtMillis = BenzineWeekPolicy.parseIsoMillis(stat.joinedAt)

        val status = deriveStatus(
            hasHistory = invoices.isNotEmpty(),
            joinedAtMillis = joinedAtMillis,
            previousWeekStart = previousStart,
            currentWeekInvoices = currentRows.size,
            previousWeekInvoices = previousRows.size,
            currentWeekSales = currentSales,
            previousWeekSales = previousSales,
        )
        val followUp = when (status) {
            BenzinePerformanceStatus.NEW -> BenzineFollowUpReason.NEW_NOT_STARTED
            BenzinePerformanceStatus.DECLINING -> BenzineFollowUpReason.DECLINING
            BenzinePerformanceStatus.INACTIVE -> BenzineFollowUpReason.INACTIVE
            BenzinePerformanceStatus.ACTIVE,
            BenzinePerformanceStatus.GROWING -> null
        }

        return BenzineWeeklyPerformance(
            clientId = stat.clientId,
            fullName = stat.fullName,
            phone = stat.phone,
            accountType = stat.accountType,
            workshopName = stat.workshopName,
            joinedAt = stat.joinedAt,
            lastSeenAt = stat.lastSeenAt,
            currentWeekSales = currentSales,
            previousWeekSales = previousSales,
            currentWeekCommission = currentCommission,
            previousWeekCommission = previousCommission,
            currentWeekInvoices = currentRows.size,
            previousWeekInvoices = previousRows.size,
            firstPurchaseAt = firstPurchaseAt,
            lastPurchaseAt = lastPurchaseAt,
            status = status,
            followUpReason = followUp,
            salesDeltaPercent = deltaPercent(currentSales, previousSales),
            salesTrendLabel = trendLabel(currentSales, previousSales),
        )
    }

    private fun deriveStatus(
        hasHistory: Boolean,
        joinedAtMillis: Long?,
        previousWeekStart: Long,
        currentWeekInvoices: Int,
        previousWeekInvoices: Int,
        currentWeekSales: Double,
        previousWeekSales: Double,
    ): BenzinePerformanceStatus = when {
        !hasHistory && joinedAtMillis != null && joinedAtMillis >= previousWeekStart ->
            BenzinePerformanceStatus.NEW
        !hasHistory && (joinedAtMillis == null || joinedAtMillis < previousWeekStart) ->
            BenzinePerformanceStatus.INACTIVE
        currentWeekInvoices == 0 && previousWeekInvoices == 0 ->
            BenzinePerformanceStatus.INACTIVE
        previousWeekSales > 0.0 && currentWeekSales < previousWeekSales ->
            BenzinePerformanceStatus.DECLINING
        currentWeekSales > previousWeekSales ->
            BenzinePerformanceStatus.GROWING
        currentWeekInvoices > 0 ->
            BenzinePerformanceStatus.ACTIVE
        else -> BenzinePerformanceStatus.INACTIVE
    }

    private fun formatPercent(value: Double): String {
        val rounded = round(value * 10.0) / 10.0
        val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        return "${if (rounded > 0.0) "+" else ""}$text%"
    }

    private data class TimedEligibility(
        val item: CommissionEligibilityItem,
        val createdAt: Long,
    )
}
