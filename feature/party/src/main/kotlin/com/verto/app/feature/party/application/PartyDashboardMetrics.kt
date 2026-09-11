package com.verto.app.feature.party.application

import com.verto.app.feature.party.domain.ledger.LedgerEventType
import com.verto.app.feature.party.domain.ledger.PartyLedger
import com.verto.app.feature.party.domain.model.PartyInvoiceCategory
import com.verto.app.feature.party.domain.model.PartyInvoiceItem
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency-safe values used by Party dashboards and the future Party Intelligence layer.
 *
 * A list is intentional: amounts from different currencies are never added together. Presentation
 * may render every entry, but it must not collapse this list into one scalar without an explicit FX
 * conversion policy.
 */
internal data class PartyCurrencyAmount(
    val currencyCode: String,
    val amountMinor: Long,
) {
    init {
        require(currencyCode.isNotBlank())
    }

    fun toMoney(): Money = Money.ofMinor(amountMinor, currencyCode)
}

internal enum class PartyBalanceDirection {
    RECEIVABLE,
    PAYABLE,
    SETTLED,
    MIXED,
}

internal data class PartyLedgerDashboardMetrics(
    val closingByCurrency: List<PartyCurrencyAmount>,
    val invoicedByCurrency: List<PartyCurrencyAmount>,
    val paidByCurrency: List<PartyCurrencyAmount>,
    val direction: PartyBalanceDirection,
)

internal data class PartyCommercialDashboardMetrics(
    val turnoverByCurrency: List<PartyCurrencyAmount>,
    val commissionsByCurrency: List<PartyCurrencyAmount>,
    /** Profit is functional-currency profit and is empty when [profitComplete] is false. */
    val profitByCurrency: List<PartyCurrencyAmount>,
    val profitComplete: Boolean,
)

/** Pure application calculator. No Room, Android or presentation dependencies. */
internal object PartyDashboardMetricsCalculator {
    private const val UNKNOWN_CURRENCY = "UNKNOWN"

    fun ledger(ledger: PartyLedger?): PartyLedgerDashboardMetrics {
        if (ledger == null) {
            return PartyLedgerDashboardMetrics(emptyList(), emptyList(), emptyList(), PartyBalanceDirection.SETTLED)
        }

        val closing = ledger.currencies
            .map { PartyCurrencyAmount(normalizeCurrency(it.currencyCode), it.closing.amountMinor) }
            .filterNot { it.amountMinor == 0L }
            .sortedBy { it.currencyCode }

        val invoiced = aggregateLedgerEvents(ledger, LedgerEventType.INVOICE) { it }
        val paid = aggregateLedgerEvents(ledger, LedgerEventType.PAYMENT) { Math.negateExact(it) }

        return PartyLedgerDashboardMetrics(
            closingByCurrency = closing,
            invoicedByCurrency = invoiced,
            paidByCurrency = paid,
            direction = directionOf(closing),
        )
    }

    fun customerCommercial(
        summaries: List<PartyInvoiceSummary>,
        items: List<PartyInvoiceItem>,
    ): PartyCommercialDashboardMetrics = commercial(
        summaries = summaries.filter { it.invoice.category == PartyInvoiceCategory.SALE },
        items = items,
        subtractCommissionFromTurnover = true,
        calculateProfit = true,
    )

    fun supplierCommercial(
        summaries: List<PartyInvoiceSummary>,
    ): PartyCommercialDashboardMetrics = commercial(
        summaries = summaries.filter { it.invoice.category == PartyInvoiceCategory.PURCHASE },
        items = emptyList(),
        subtractCommissionFromTurnover = false,
        calculateProfit = false,
    )

    private fun commercial(
        summaries: List<PartyInvoiceSummary>,
        items: List<PartyInvoiceItem>,
        subtractCommissionFromTurnover: Boolean,
        calculateProfit: Boolean,
    ): PartyCommercialDashboardMetrics {
        val live = summaries.filterNot { it.invoice.voided }
        val turnover = linkedMapOf<String, Long>()
        val commissions = linkedMapOf<String, Long>()

        live.forEach { summary ->
            val invoice = summary.invoice
            val currency = normalizeCurrency(invoice.transactionCurrencyCode)
            val amount = effectiveMinor(invoice.transactionAmountMinor, invoice.totalAmount, currency)
            val commission = effectiveMinor(invoice.commissionMinor, invoice.commission, currency)
            val net = if (subtractCommissionFromTurnover) Math.subtractExact(amount, commission) else amount
            turnover[currency] = addExact(turnover[currency] ?: 0L, net)
            if (commission != 0L) commissions[currency] = addExact(commissions[currency] ?: 0L, commission)
        }

        if (!calculateProfit) {
            return PartyCommercialDashboardMetrics(
                turnoverByCurrency = turnover.toCurrencyAmounts(),
                commissionsByCurrency = commissions.toCurrencyAmounts(),
                profitByCurrency = emptyList(),
                profitComplete = true,
            )
        }

        val itemsByInvoice = items.groupBy { it.invoiceId }
        val profit = linkedMapOf<String, Long>()
        var profitComplete = true

        invoiceLoop@ for (summary in live) {
            val invoice = summary.invoice
            val lines = itemsByInvoice[invoice.id].orEmpty()
            if (!invoice.legacyCurrencyKnown || lines.isEmpty() || lines.any { it.costSnapshotStatus == "LEGACY_UNKNOWN" }) {
                profitComplete = false
                continue
            }

            val functionalCurrency = normalizeFunctionalCurrency(invoice.functionalCurrencyCode, invoice.transactionCurrencyCode)
            var invoiceProfit = 0L
            for (line in lines) {
                val revenue = lineFunctionalRevenueMinor(
                    lineRevenueMinor = line.lineRevenueSnapshotMinor,
                    transactionCurrency = invoice.transactionCurrencyCode,
                    functionalCurrency = invoice.functionalCurrencyCode,
                    transactionAmountMinor = invoice.transactionAmountMinor,
                    functionalAmountMinor = invoice.functionalAmountAtRecognitionMinor,
                )
                if (revenue == null) {
                    profitComplete = false
                    continue@invoiceLoop
                }
                invoiceProfit = addExact(invoiceProfit, Math.subtractExact(revenue, line.lineCostSnapshotMinor))
            }

            val commissionFunctional = convertTransactionMinorToFunctional(
                amountMinor = invoice.commissionMinor,
                transactionCurrency = invoice.transactionCurrencyCode,
                functionalCurrency = invoice.functionalCurrencyCode,
                transactionAmountMinor = invoice.transactionAmountMinor,
                functionalAmountMinor = invoice.functionalAmountAtRecognitionMinor,
            )
            if (commissionFunctional == null) {
                profitComplete = false
                continue
            }
            invoiceProfit = Math.subtractExact(invoiceProfit, commissionFunctional)
            profit[functionalCurrency] = addExact(profit[functionalCurrency] ?: 0L, invoiceProfit)
        }

        return PartyCommercialDashboardMetrics(
            turnoverByCurrency = turnover.toCurrencyAmounts(),
            commissionsByCurrency = commissions.toCurrencyAmounts(),
            profitByCurrency = if (profitComplete) profit.toCurrencyAmounts() else emptyList(),
            profitComplete = profitComplete,
        )
    }

    private fun aggregateLedgerEvents(
        ledger: PartyLedger,
        type: LedgerEventType,
        transform: (Long) -> Long,
    ): List<PartyCurrencyAmount> = ledger.currencies.mapNotNull { currency ->
        val amount = currency.rows
            .asSequence()
            .filter { it.event.type == type }
            .fold(0L) { acc, row -> addExact(acc, transform(row.event.delta.amountMinor)) }
        amount.takeIf { it != 0L }?.let { PartyCurrencyAmount(normalizeCurrency(currency.currencyCode), it) }
    }.sortedBy { it.currencyCode }

    private fun lineFunctionalRevenueMinor(
        lineRevenueMinor: Long,
        transactionCurrency: String,
        functionalCurrency: String,
        transactionAmountMinor: Long,
        functionalAmountMinor: Long,
    ): Long? = convertTransactionMinorToFunctional(
        amountMinor = lineRevenueMinor,
        transactionCurrency = transactionCurrency,
        functionalCurrency = functionalCurrency,
        transactionAmountMinor = transactionAmountMinor,
        functionalAmountMinor = functionalAmountMinor,
    )

    private fun convertTransactionMinorToFunctional(
        amountMinor: Long,
        transactionCurrency: String,
        functionalCurrency: String,
        transactionAmountMinor: Long,
        functionalAmountMinor: Long,
    ): Long? {
        if (amountMinor == 0L) return 0L
        val transaction = normalizeCurrency(transactionCurrency)
        val functional = normalizeFunctionalCurrency(functionalCurrency, transactionCurrency)
        if (transaction == functional) return amountMinor
        if (transaction == UNKNOWN_CURRENCY || functional == UNKNOWN_CURRENCY) return null
        if (transactionAmountMinor == 0L || functionalAmountMinor == 0L) return null
        return runCatching {
            BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(functionalAmountMinor))
                .divide(BigDecimal.valueOf(transactionAmountMinor), 0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
    }

    private fun directionOf(amounts: List<PartyCurrencyAmount>): PartyBalanceDirection {
        val signs = amounts.map { it.amountMinor.compareTo(0L) }.filterNot { it == 0 }.toSet()
        return when {
            signs.isEmpty() -> PartyBalanceDirection.SETTLED
            signs == setOf(1) -> PartyBalanceDirection.RECEIVABLE
            signs == setOf(-1) -> PartyBalanceDirection.PAYABLE
            else -> PartyBalanceDirection.MIXED
        }
    }

    private fun normalizeCurrency(raw: String): String = raw.trim().uppercase().ifBlank { UNKNOWN_CURRENCY }

    private fun normalizeFunctionalCurrency(functional: String, transaction: String): String =
        functional.trim().uppercase().ifBlank { transaction.trim().uppercase().ifBlank { UNKNOWN_CURRENCY } }

    private fun effectiveMinor(snapshotMinor: Long, legacyMajor: Double, currency: String): Long =
        if (snapshotMinor != 0L || legacyMajor == 0.0) snapshotMinor
        else Money.fromLegacyDouble(legacyMajor, currency).amountMinor

    private fun addExact(left: Long, right: Long): Long = Math.addExact(left, right)

    private fun Map<String, Long>.toCurrencyAmounts(): List<PartyCurrencyAmount> = entries
        .filterNot { it.value == 0L }
        .sortedBy { it.key }
        .map { PartyCurrencyAmount(it.key, it.value) }
}
