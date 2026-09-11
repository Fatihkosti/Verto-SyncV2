package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.domain.model.PartyInvoiceCategory
import com.verto.app.feature.party.domain.model.PartyInvoiceStatus
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

internal const val MILLIS_PER_DAY: Long = 86_400_000L

internal enum class CustomerCreditDecision { ALLOW_CREDIT, CASH_ONLY, REQUIRES_APPROVAL }
internal enum class CustomerRepurchaseState { INSUFFICIENT_HISTORY, ACTIVE, DUE_SOON, DUE, OVERDUE }
internal enum class CustomerRecommendedAction { NONE, COLLECT_OVERDUE, REQUIRE_CASH, REVIEW_CREDIT, FOLLOW_UP_REPURCHASE }

internal data class CustomerCreditPolicy(
    val minimumSettledCreditInvoicesForAutoApproval: Int = 4,
    val maxAutoApprovalLateRatioBps: Int = 2_000,
    val maxAutoApprovalAverageDaysLate: Int = 7,
    val severeLateDays: Int = 30,
    val severeLateRatioBps: Int = 5_000,
    val minimumInvoicesForSevereRatio: Int = 2,
)

internal data class CustomerDecisionSnapshot(
    val asOf: Long,
    val dataComplete: Boolean,
    val currencies: Set<String>,
    val saleInvoiceCount: Int,
    val creditInvoiceCount: Int,
    val settledCreditInvoiceCount: Int,
    val currentlyOverdueInvoiceCount: Int,
    val overdueHistoryCount: Int,
    val lateRatioBps: Int?,
    val averagePaymentDays: Int?,
    val averageDaysLate: Int?,
    val maxCurrentDaysOverdue: Int,
    val outstandingByCurrencyMinor: Map<String, Long>,
    val overdueByCurrencyMinor: Map<String, Long>,
    val typicalRepurchaseDays: Int?,
    val predictedNextPurchaseAt: Long?,
    val repurchaseState: CustomerRepurchaseState,
    val creditDecision: CustomerCreditDecision,
    val creditReasons: List<String>,
    val recommendedAction: CustomerRecommendedAction,
)

/**
 * Pure, deterministic customer-decision engine.
 *
 * It deliberately refuses to auto-approve credit when financial history is incomplete or currency
 * snapshots are unknown. Policy thresholds are inputs rather than UI literals so organization-level
 * policy can be persisted later without rewriting the engine.
 */
internal object CustomerDecisionEngine {
    private const val UNKNOWN = "UNKNOWN"

    fun evaluate(
        summaries: List<PartyInvoiceSummary>,
        now: Long,
        policy: CustomerCreditPolicy = CustomerCreditPolicy(),
    ): CustomerDecisionSnapshot {
        require(now >= 0L)
        val sales = summaries
            .asSequence()
            .filter { it.invoice.category == PartyInvoiceCategory.SALE && !it.invoice.voided }
            .sortedBy { it.invoice.createdAt }
            .toList()

        val converted = sales.map { summary -> toSafeInvoice(summary, now) }
        val dataComplete = converted.all { it.complete }
        val currencies = converted.map { it.currency }.filterNot { it == UNKNOWN }.toSet()
        val safe = converted.filter { it.complete }
        val credit = safe.filter { it.isCredit }
        val settledCredit = credit.filter { it.remainingMinor <= 0L }
        val currentlyOverdue = credit.filter { it.remainingMinor > 0L && it.dueDate in 1 until now }

        val settledLateDays = settledCredit.mapNotNull { row ->
            val settledAt = row.settledAt ?: return@mapNotNull null
            if (row.dueDate <= 0L) null else daysBetween(row.dueDate, settledAt).coerceAtLeast(0)
        }
        val overdueHistoryCount = settledLateDays.count { it > 0 }
        val lateRatioBps = settledLateDays.takeIf { it.isNotEmpty() }?.let {
            ((overdueHistoryCount.toLong() * 10_000L) / it.size.toLong()).toInt()
        }
        val averageDaysLate = settledLateDays.averageRounded()
        val averagePaymentDays = settledCredit.mapNotNull { row ->
            row.settledAt?.let { settledAt -> daysBetween(row.createdAt, settledAt).coerceAtLeast(0) }
        }.averageRounded()
        val maxCurrentDaysOverdue = currentlyOverdue.maxOfOrNull { daysBetween(it.dueDate, now).coerceAtLeast(0) } ?: 0

        val outstanding = safe.filter { it.remainingMinor > 0L }.sumByCurrency { it.remainingMinor }
        val overdue = currentlyOverdue.sumByCurrency { it.remainingMinor }
        val repurchase = repurchase(safe, now)
        val creditEvaluation = creditDecision(
            dataComplete = dataComplete,
            currencies = currencies,
            creditInvoices = credit.size,
            settledCreditInvoices = settledCredit.size,
            currentlyOverdue = currentlyOverdue,
            maxCurrentDaysOverdue = maxCurrentDaysOverdue,
            lateRatioBps = lateRatioBps,
            averageDaysLate = averageDaysLate,
            policy = policy,
        )
        val recommended = when {
            currentlyOverdue.isNotEmpty() -> CustomerRecommendedAction.COLLECT_OVERDUE
            creditEvaluation.first == CustomerCreditDecision.CASH_ONLY -> CustomerRecommendedAction.REQUIRE_CASH
            repurchase.state == CustomerRepurchaseState.DUE || repurchase.state == CustomerRepurchaseState.OVERDUE -> CustomerRecommendedAction.FOLLOW_UP_REPURCHASE
            creditEvaluation.first == CustomerCreditDecision.REQUIRES_APPROVAL && credit.isNotEmpty() -> CustomerRecommendedAction.REVIEW_CREDIT
            else -> CustomerRecommendedAction.NONE
        }

        return CustomerDecisionSnapshot(
            asOf = now,
            dataComplete = dataComplete,
            currencies = currencies,
            saleInvoiceCount = sales.size,
            creditInvoiceCount = credit.size,
            settledCreditInvoiceCount = settledCredit.size,
            currentlyOverdueInvoiceCount = currentlyOverdue.size,
            overdueHistoryCount = overdueHistoryCount,
            lateRatioBps = lateRatioBps,
            averagePaymentDays = averagePaymentDays,
            averageDaysLate = averageDaysLate,
            maxCurrentDaysOverdue = maxCurrentDaysOverdue,
            outstandingByCurrencyMinor = outstanding,
            overdueByCurrencyMinor = overdue,
            typicalRepurchaseDays = repurchase.typicalDays,
            predictedNextPurchaseAt = repurchase.predictedAt,
            repurchaseState = repurchase.state,
            creditDecision = creditEvaluation.first,
            creditReasons = creditEvaluation.second,
            recommendedAction = recommended,
        )
    }

    private fun creditDecision(
        dataComplete: Boolean,
        currencies: Set<String>,
        creditInvoices: Int,
        settledCreditInvoices: Int,
        currentlyOverdue: List<SafeInvoice>,
        maxCurrentDaysOverdue: Int,
        lateRatioBps: Int?,
        averageDaysLate: Int?,
        policy: CustomerCreditPolicy,
    ): Pair<CustomerCreditDecision, List<String>> {
        val reasons = mutableListOf<String>()
        if (!dataComplete) reasons += "INCOMPLETE_FINANCIAL_HISTORY"
        if (currencies.size > 1) reasons += "MULTI_CURRENCY_REQUIRES_EXPLICIT_POLICY"
        if (creditInvoices == 0) reasons += "NO_CREDIT_HISTORY"

        val severeCurrentLate = currentlyOverdue.isNotEmpty() && maxCurrentDaysOverdue >= policy.severeLateDays
        val severeHistory = settledCreditInvoices >= policy.minimumInvoicesForSevereRatio &&
            lateRatioBps != null && lateRatioBps >= policy.severeLateRatioBps
        if (severeCurrentLate) reasons += "SEVERELY_OVERDUE_NOW"
        if (severeHistory) reasons += "HIGH_LATE_PAYMENT_RATIO"
        if (severeCurrentLate || severeHistory) return CustomerCreditDecision.CASH_ONLY to reasons

        if (!dataComplete || currencies.size > 1 || creditInvoices == 0) {
            return CustomerCreditDecision.REQUIRES_APPROVAL to reasons
        }
        if (currentlyOverdue.isNotEmpty()) {
            reasons += "CURRENT_OVERDUE_BALANCE"
            return CustomerCreditDecision.REQUIRES_APPROVAL to reasons
        }
        if (settledCreditInvoices < policy.minimumSettledCreditInvoicesForAutoApproval) {
            reasons += "INSUFFICIENT_SETTLED_CREDIT_HISTORY"
            return CustomerCreditDecision.REQUIRES_APPROVAL to reasons
        }
        if ((lateRatioBps ?: 10_000) > policy.maxAutoApprovalLateRatioBps) reasons += "LATE_RATIO_ABOVE_POLICY"
        if ((averageDaysLate ?: Int.MAX_VALUE) > policy.maxAutoApprovalAverageDaysLate) reasons += "AVERAGE_LATENESS_ABOVE_POLICY"
        if (reasons.isNotEmpty()) return CustomerCreditDecision.REQUIRES_APPROVAL to reasons

        return CustomerCreditDecision.ALLOW_CREDIT to listOf("GOOD_SETTLEMENT_HISTORY")
    }

    private data class Repurchase(val typicalDays: Int?, val predictedAt: Long?, val state: CustomerRepurchaseState)

    private fun repurchase(rows: List<SafeInvoice>, now: Long): Repurchase {
        val purchaseDates = rows.map { it.createdAt }.distinct().sorted()
        if (purchaseDates.size < 3) return Repurchase(null, null, CustomerRepurchaseState.INSUFFICIENT_HISTORY)
        val intervals = purchaseDates.zipWithNext { a, b -> daysBetween(a, b).coerceAtLeast(1) }.sorted()
        val typical = median(intervals) ?: return Repurchase(null, null, CustomerRepurchaseState.INSUFFICIENT_HISTORY)
        val predicted = purchaseDates.last() + Math.multiplyExact(typical.toLong(), MILLIS_PER_DAY)
        val deltaDays = daysBetween(now, predicted)
        val tolerance = max(2, (typical * 20) / 100)
        val state = when {
            deltaDays > tolerance -> CustomerRepurchaseState.ACTIVE
            deltaDays in 1..tolerance -> CustomerRepurchaseState.DUE_SOON
            deltaDays in -tolerance..0 -> CustomerRepurchaseState.DUE
            else -> CustomerRepurchaseState.OVERDUE
        }
        return Repurchase(typical, predicted, state)
    }

    private data class SafeInvoice(
        val complete: Boolean,
        val currency: String,
        val amountMinor: Long,
        val remainingMinor: Long,
        val createdAt: Long,
        val dueDate: Long,
        val isCredit: Boolean,
        val settledAt: Long?,
    )

    private fun toSafeInvoice(summary: PartyInvoiceSummary, now: Long): SafeInvoice {
        val invoice = summary.invoice
        val currency = invoice.transactionCurrencyCode.trim().uppercase().ifBlank { UNKNOWN }
        val currencyKnown = invoice.legacyCurrencyKnown && currency != UNKNOWN
        val amountMinor = if (currencyKnown) invoice.transactionAmountMinor else 0L
        val paymentRows = summary.payments
        val paymentsComplete = currencyKnown && paymentRows.all { payment ->
            payment.currencyCode.trim().uppercase() == currency && payment.currencyKnown
        }
        val isCash = invoice.status == PartyInvoiceStatus.CLOSED_CASH
        val paidMinor = if (paymentsComplete) paymentRows.fold(0L) { acc, payment -> Math.addExact(acc, payment.amountMinor) } else 0L
        val remaining = when {
            !currencyKnown || !paymentsComplete -> 0L
            isCash -> 0L
            else -> Math.subtractExact(amountMinor, paidMinor)
        }
        val settledAt = when {
            isCash && currencyKnown && paymentsComplete -> invoice.createdAt
            currencyKnown && paymentsComplete && remaining <= 0L && paymentRows.isNotEmpty() -> settlementTimestamp(amountMinor, paymentRows.sortedBy { it.paidAt })
            else -> null
        }
        return SafeInvoice(
            complete = currencyKnown && paymentsComplete && amountMinor >= 0L,
            currency = currency,
            amountMinor = amountMinor,
            remainingMinor = remaining,
            createdAt = invoice.createdAt,
            dueDate = invoice.dueDate,
            isCredit = invoice.status == PartyInvoiceStatus.CLOSED_CREDIT,
            settledAt = settledAt?.coerceAtMost(now),
        )
    }

    private fun settlementTimestamp(amountMinor: Long, payments: List<com.verto.app.feature.party.domain.model.PartyPayment>): Long? {
        var running = 0L
        payments.forEach { payment ->
            running = Math.addExact(running, payment.amountMinor)
            if (running >= amountMinor) return payment.paidAt
        }
        return null
    }

    private fun List<SafeInvoice>.sumByCurrency(selector: (SafeInvoice) -> Long): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        forEach { row -> result[row.currency] = Math.addExact(result[row.currency] ?: 0L, selector(row)) }
        return result.filterValues { it != 0L }.toSortedMap()
    }

    private fun List<Int>.averageRounded(): Int? = if (isEmpty()) null else BigDecimal.valueOf(sumOf { it.toLong() })
        .divide(BigDecimal.valueOf(size.toLong()), 0, RoundingMode.HALF_UP).intValueExact()

    private fun daysBetween(from: Long, to: Long): Int = ((to - from) / MILLIS_PER_DAY).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else ((values[middle - 1].toLong() + values[middle]) / 2L).toInt()
    }
}
