package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.feature.reports.application.model.ReportsFilters
import com.verto.app.feature.reports.application.model.NetProfitReliabilityIssue
import com.verto.app.utils.ReportPeriod
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

/** Session 364: deterministic report scoping/allocation helpers. */
internal fun previousComparisonRange(
    period: ReportPeriod,
    from: Long,
    to: Long,
): Pair<Long, Long> {
    require(from > 0L && to >= from) { "invalid report range" }
    fun shifted(field: Int, amount: Int): Pair<Long, Long> {
        val previousFrom = Calendar.getInstance().apply { timeInMillis = from; add(field, amount) }.timeInMillis
        val previousTo = Calendar.getInstance().apply { timeInMillis = to; add(field, amount) }.timeInMillis
        return previousFrom to previousTo
    }
    return when (period) {
        ReportPeriod.TODAY -> shifted(Calendar.DAY_OF_YEAR, -1)
        ReportPeriod.WEEK -> shifted(Calendar.DAY_OF_YEAR, -7)
        ReportPeriod.MONTH -> shifted(Calendar.MONTH, -1)
        ReportPeriod.THREE_MONTHS -> shifted(Calendar.MONTH, -3)
        ReportPeriod.SIX_MONTHS -> shifted(Calendar.MONTH, -6)
        ReportPeriod.YEAR -> shifted(Calendar.YEAR, -1)
        ReportPeriod.CUSTOM -> {
            val duration = Math.subtractExact(to, from)
            val previousTo = Math.subtractExact(from, 1L)
            Math.subtractExact(previousTo, duration) to previousTo
        }
    }
}

internal fun scopeSalesInvoices(
    invoices: List<InvoiceEntity>,
    allPayments: List<PaymentEntity>,
    filters: ReportsFilters,
): List<InvoiceEntity> {
    val byPaymentMethod = when (filters.paymentMethod) {
        "نقدي" -> invoices.filter { it.status == InvoiceStatus.CLOSED_CASH }
        "آجل" -> invoices.filter { it.status == InvoiceStatus.CLOSED_CREDIT }
        else -> invoices
    }
    val cashier = filters.cashierName?.trim().orEmpty()
    if (cashier.isEmpty()) return byPaymentMethod

    val matchingPayments = allPayments.filter { it.employeeName.trim() == cashier }
    val employeeIds = matchingPayments.map { it.employeeId.trim() }.filter { it.isNotEmpty() }.toSet()
    val paymentInvoiceIds = matchingPayments.mapTo(mutableSetOf()) { it.invoiceId }
    return byPaymentMethod.filter { invoice ->
        val creator = invoice.createdBy.trim()
        when {
            creator.isNotEmpty() && employeeIds.isNotEmpty() -> creator in employeeIds
            else -> invoice.id in paymentInvoiceIds
        }
    }
}

/**
 * Allocates the immutable invoice-level recognized functional amount back to its lines.
 * This keeps line/category analytics reconciled after invoice-level discounts and FX conversion.
 * Cumulative proportional allocation guarantees that every invoice reconciles exactly, including rounding.
 */
internal fun allocateRecognizedRevenueByLine(
    items: List<InvoiceItemEntity>,
    invoicesById: Map<String, InvoiceEntity>,
    functionalCurrencyCode: String,
): Map<String, Long> {
    val out = HashMap<String, Long>(items.size)
    items.groupBy { it.invoiceId }.forEach { (invoiceId, invoiceItems) ->
        val invoice = invoicesById[invoiceId] ?: return@forEach
        val recognized = invoice.functionalMinorOrNull(functionalCurrencyCode) ?: return@forEach
        val weighted = invoiceItems
            .map { it to lineRevenueBasisMinor(it) }
            .filter { it.second > 0L }
            .sortedBy { it.first.id }
        val totalBasis = sumMinor(weighted.map { it.second })
        if (totalBasis <= 0L) return@forEach

        var cumulativeBasis = 0L
        var previouslyAllocated = 0L
        weighted.forEach { (line, basis) ->
            cumulativeBasis = Math.addExact(cumulativeBasis, basis)
            val cumulativeAllocation = if (cumulativeBasis == totalBasis) recognized else {
                BigDecimal.valueOf(recognized)
                    .multiply(BigDecimal.valueOf(cumulativeBasis))
                    .divide(BigDecimal.valueOf(totalBasis), 0, RoundingMode.HALF_UP)
                    .longValueExact()
            }
            out[line.id] = Math.subtractExact(cumulativeAllocation, previouslyAllocated)
            previouslyAllocated = cumulativeAllocation
        }
    }
    return out
}

internal fun lineRevenueBasisMinor(line: InvoiceItemEntity): Long = when {
    line.lineRevenueSnapshotMinor > 0L -> line.lineRevenueSnapshotMinor
    line.totalPriceMinor > 0L -> line.totalPriceMinor
    else -> 0L
}

internal fun matchesReportCategory(rawCategory: String, selectedCategory: String): Boolean =
    if (selectedCategory == "غير مصنف") rawCategory.isBlank() else rawCategory == selectedCategory

internal fun functionalCommissionMinor(invoice: InvoiceEntity, functionalCurrencyCode: String): Long {
    if (invoice.commissionMinor == 0L) return 0L
    val recognized = invoice.functionalMinorOrNull(functionalCurrencyCode) ?: return 0L
    if (invoice.transactionAmountMinor <= 0L) return 0L
    return scaleMinor(invoice.commissionMinor, recognized, invoice.transactionAmountMinor)
}

internal fun reportNetProfitReliabilityIssues(
    segmentFilterActive: Boolean,
    historicalCostComplete: Boolean,
    salesCurrencyComplete: Boolean,
    returnsCurrencyComplete: Boolean,
): Set<NetProfitReliabilityIssue> = buildSet {
    if (segmentFilterActive) add(NetProfitReliabilityIssue.SEGMENT_FILTER_WITH_UNALLOCATED_EXPENSES)
    if (!historicalCostComplete) add(NetProfitReliabilityIssue.UNKNOWN_HISTORICAL_COST)
    if (!salesCurrencyComplete || !returnsCurrencyComplete) add(NetProfitReliabilityIssue.UNKNOWN_OR_MIXED_CURRENCY)
}
