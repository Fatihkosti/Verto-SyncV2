package com.verto.app.feature.reports.infrastructure.analytics

import com.verto.app.application.presentationboundary.InvoiceCategory
import com.verto.app.application.presentationboundary.InvoiceLineView
import com.verto.app.application.presentationboundary.InvoiceViewData
import com.verto.app.application.presentationboundary.RfmClientMetrics
import com.verto.app.application.presentationboundary.RfmSegment
import com.verto.app.data.local.entity.LegacyCurrencyStatus
import com.verto.app.data.repository.RfmRepository
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RFM cache builder.
 *
 * F250: monetary values are derived from persisted historical functional-currency snapshots.
 * Unknown/mixed functional-currency data is never added together. Doubles exist only at the
 * legacy cache boundary because the current RfmCacheEntity schema predates Money minor units.
 */
@Singleton
class RfmCalculator @Inject constructor(
    private val rfmRepo: RfmRepository,
) {

    suspend fun recalculate(
        clients: List<PartyClient>,
        invoices: List<InvoiceViewData>,
        invoiceItems: List<InvoiceLineView>,
    ) = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val yearAgo = now - 365L * 24 * 60 * 60 * 1000

        val knownSales = invoices.filter {
            it.category == InvoiceCategory.SALE &&
                it.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN &&
                it.functionalCurrencyCode.isNotBlank()
        }
        val functionalCurrencies = knownSales.map { it.functionalCurrencyCode.trim().uppercase() }.distinct()
        if (functionalCurrencies.size != 1) {
            // Cache is derivative data. Clearing is safer than serving a mixed/ambiguous monetary sum.
            rfmRepo.clearAll()
            return@withContext
        }
        val functionalCurrency = functionalCurrencies.single()
        val allSales = knownSales.filter { it.functionalCurrencyCode.equals(functionalCurrency, ignoreCase = true) }
        val salesLastYear = allSales.filter { it.createdAt >= yearAgo }
        val itemsByInvoice = invoiceItems.groupBy { it.invoiceId }

        data class Metrics(
            val clientId: String,
            val recencyDays: Int,
            val frequency: Int,
            val monetaryMinor: Long,
            val totalProfitMinor: Long,
            val firstPurchaseAt: Long,
            val lastPurchaseAt: Long,
            val lifespanDays: Int,
            val avgInvoiceMinor: Long,
        )

        val metrics = clients.mapNotNull { client ->
            val clientSales = salesLastYear.filter { it.clientId == client.id }
            if (clientSales.isEmpty()) return@mapNotNull null

            val allClientSales = allSales.filter { it.clientId == client.id }
            val lastPurchase = clientSales.maxOf { it.createdAt }
            val firstPurchase = allClientSales.minOfOrNull { it.createdAt } ?: lastPurchase
            val recencyDays = ((now - lastPurchase) / 86_400_000L).toInt()
            val frequency = clientSales.size
            val monetaryMinor = clientSales.fold(0L) { acc, invoice ->
                Math.addExact(acc, invoice.functionalAmountAtRecognitionMinor)
            }
            val totalProfitMinor = clientSales.fold(0L) { invoiceAcc, invoice ->
                val invoiceProfit = itemsByInvoice[invoice.id].orEmpty()
                    .filter { it.costSnapshotStatus != "LEGACY_UNKNOWN" }
                    .fold(0L) { lineAcc, line ->
                        val revenueMinor = lineFunctionalRevenueMinor(line, invoice)
                        if (revenueMinor == null) lineAcc
                        else Math.addExact(lineAcc, Math.subtractExact(revenueMinor, line.lineCostSnapshotMinor))
                    }
                Math.addExact(invoiceAcc, invoiceProfit)
            }
            val lifespanDays = ((lastPurchase - firstPurchase) / 86_400_000L).toInt()
            val avgInvoiceMinor = BigDecimal.valueOf(monetaryMinor)
                .divide(BigDecimal.valueOf(frequency.toLong()), 0, RoundingMode.HALF_UP)
                .longValueExact()

            Metrics(
                client.id, recencyDays, frequency, monetaryMinor, totalProfitMinor,
                firstPurchase, lastPurchase, lifespanDays, avgInvoiceMinor,
            )
        }

        if (metrics.isEmpty()) {
            rfmRepo.clearAll()
            return@withContext
        }

        val rScores = quintileScores(metrics.map { it.recencyDays.toLong() }, higherIsBetter = false)
        val fScores = quintileScores(metrics.map { it.frequency.toLong() }, higherIsBetter = true)
        val mScores = quintileScores(metrics.map { it.monetaryMinor }, higherIsBetter = true)

        val entities = metrics.mapIndexed { i, m ->
            val r = rScores[i]
            val f = fScores[i]
            val ms = mScores[i]
            RfmClientMetrics(
                clientId = m.clientId,
                recencyScore = r,
                frequencyScore = f,
                monetaryScore = ms,
                daysSinceLastPurchase = m.recencyDays.coerceAtMost(9999),
                totalInvoiceCount = m.frequency,
                totalSpent = Money.ofMinor(m.monetaryMinor, functionalCurrency).toLegacyDouble(),
                avgInvoiceValue = Money.ofMinor(m.avgInvoiceMinor, functionalCurrency).toLegacyDouble(),
                segment = classifySegment(r, f, ms),
                totalProfit = Money.ofMinor(m.totalProfitMinor, functionalCurrency).toLegacyDouble(),
                firstPurchaseAt = m.firstPurchaseAt,
                lastPurchaseAt = m.lastPurchaseAt,
                customerLifespanDays = m.lifespanDays,
                calculatedAt = System.currentTimeMillis(),
            )
        }

        rfmRepo.clearAll()
        rfmRepo.upsertAll(entities)
    }

    private fun lineFunctionalRevenueMinor(line: InvoiceLineView, invoice: InvoiceViewData): Long? {
        if (invoice.transactionAmountMinor == 0L) {
            return if (line.lineRevenueSnapshotMinor == 0L) 0L else null
        }
        if (invoice.transactionCurrencyCode.equals(invoice.functionalCurrencyCode, ignoreCase = true)) {
            return line.lineRevenueSnapshotMinor
        }
        return BigDecimal.valueOf(line.lineRevenueSnapshotMinor)
            .multiply(BigDecimal.valueOf(invoice.functionalAmountAtRecognitionMinor))
            .divide(BigDecimal.valueOf(invoice.transactionAmountMinor), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun quintileScores(values: List<Long>, higherIsBetter: Boolean): List<Int> {
        if (values.isEmpty()) return emptyList()
        val sorted = if (higherIsBetter) values.sortedDescending() else values.sorted()
        val n = values.size
        return values.map { value ->
            val rank = sorted.indexOfFirst { it == value }.coerceAtLeast(0)
            val bucket = (rank * 5) / n
            (5 - bucket).coerceIn(1, 5)
        }
    }

    fun classifySegment(r: Int, f: Int, m: Int): RfmSegment = when {
        r >= 4 && f >= 4 && m >= 4 -> RfmSegment.CHAMPIONS
        r >= 3 && f >= 4 -> RfmSegment.LOYAL
        r >= 4 && f in 2..3 -> RfmSegment.POTENTIAL_LOYALIST
        r >= 4 && f == 1 -> RfmSegment.NEW_CUSTOMERS
        r == 3 && m in 1..3 -> RfmSegment.PROMISING
        r in 2..3 && f in 1..2 -> RfmSegment.NEEDS_ATTENTION
        r == 2 && f >= 3 && m >= 3 -> RfmSegment.AT_RISK
        r <= 2 && f >= 4 && m >= 4 -> RfmSegment.CANT_LOSE
        r in 1..2 && f in 1..2 && m <= 2 -> RfmSegment.HIBERNATING
        else -> RfmSegment.LOST
    }
}
