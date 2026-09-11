package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

internal const val DAY_MS_F255 = 86_400_000L
internal const val LOGISTICS_BASE_CURRENCY_F255 = "SDG"

internal fun periodDaysInclusive(from: Long, to: Long): Int =
    (((to - from).coerceAtLeast(0L) / DAY_MS_F255) + 1L).coerceAtMost(3_650L).toInt().coerceAtLeast(1)

internal fun netCreditSalesMinor(
    invoices: List<InvoiceEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    functionalCurrencyCode: String,
    from: Long,
    to: Long,
): Long = netCreditFlowMinor(
    invoices = invoices,
    returnDocuments = returnDocuments,
    functionalCurrencyCode = functionalCurrencyCode,
    period = from..to,
    kind = CreditFlowKind(InvoiceCategory.SALE, "SALES_RETURN_CREDIT_NOTE"),
)

internal fun netCreditPurchasesMinor(
    invoices: List<InvoiceEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    functionalCurrencyCode: String,
    from: Long,
    to: Long,
): Long = netCreditFlowMinor(
    invoices = invoices,
    returnDocuments = returnDocuments,
    functionalCurrencyCode = functionalCurrencyCode,
    period = from..to,
    kind = CreditFlowKind(InvoiceCategory.PURCHASE, "PURCHASE_RETURN_DEBIT_NOTE"),
)

private data class CreditFlowKind(val category: InvoiceCategory, val returnType: String)

private fun netCreditFlowMinor(
    invoices: List<InvoiceEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    functionalCurrencyCode: String,
    period: LongRange,
    kind: CreditFlowKind,
): Long {
    if (functionalCurrencyCode.isBlank()) return 0L
    val gross = sumMinor(invoices.asSequence()
        .filter { it.category == kind.category && it.status == InvoiceStatus.CLOSED_CREDIT && it.createdAt in period }
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID && !it.voided }
        .mapNotNull { it.functionalMinorOrNull(functionalCurrencyCode) }.toList())
    val returns = sumMinor(returnDocuments.asSequence()
        .filter { it.documentType == kind.returnType && it.occurredAt in period }
        .filter { it.functionalCurrencyCode.equals(functionalCurrencyCode, ignoreCase = true) }
        .map { it.functionalAmountMinor }.toList())
    return Math.subtractExact(gross, returns).coerceAtLeast(0L)
}

internal fun ratioDays(openBalanceMinor: Long, periodNetFlowMinor: Long, periodDays: Int): Float {
    if (periodNetFlowMinor <= 0L || openBalanceMinor <= 0L) return 0f
    return BigDecimal.valueOf(openBalanceMinor)
        .multiply(BigDecimal.valueOf(periodDays.toLong()))
        .divide(BigDecimal.valueOf(periodNetFlowMinor), 4, RoundingMode.HALF_UP)
        .toFloat()
}

internal fun parseBaseMinor(raw: String): Long? =
    Money.parseOrNull(raw, LOGISTICS_BASE_CURRENCY_F255)?.amountMinor

internal fun allocateMinorByBasis(totalMinor: Long, bases: List<Pair<String, Long>>): Map<String, Long> {
    if (totalMinor <= 0L || bases.isEmpty()) return emptyMap()
    val positive = bases.filter { it.second > 0L }.sortedBy { it.first }
    if (positive.isEmpty()) return emptyMap()
    val denominator = positive.fold(0L) { acc, (_, value) -> Math.addExact(acc, value) }
    if (denominator <= 0L) return emptyMap()

    data class Share(val id: String, val floor: Long, val remainder: BigDecimal)
    val total = BigDecimal.valueOf(totalMinor)
    val den = BigDecimal.valueOf(denominator)
    val shares = positive.map { (id, basis) ->
        val exact = total.multiply(BigDecimal.valueOf(basis)).divide(den, 12, RoundingMode.DOWN)
        val floor = exact.setScale(0, RoundingMode.DOWN).longValueExact()
        Share(id, floor, exact.subtract(BigDecimal.valueOf(floor)))
    }
    val result = shares.associate { it.id to it.floor }.toMutableMap()
    val floorTotal = shares.fold(0L) { acc, share -> Math.addExact(acc, share.floor) }
    var remainderUnits = Math.subtractExact(totalMinor, floorTotal)
    val order = shares.sortedWith(compareByDescending<Share> { it.remainder }.thenBy { it.id })
    var index = 0
    while (remainderUnits > 0L) {
        val share = order[index % order.size]
        result[share.id] = Math.addExact(result[share.id] ?: 0L, 1L)
        remainderUnits--
        index++
    }
    check(result.values.fold(0L, Math::addExact) == totalMinor) { "F255 allocation drift" }
    return result
}

internal fun absExactMinor(value: Long): Long {
    require(value != Long.MIN_VALUE) { "minor value overflow" }
    return kotlin.math.abs(value)
}
