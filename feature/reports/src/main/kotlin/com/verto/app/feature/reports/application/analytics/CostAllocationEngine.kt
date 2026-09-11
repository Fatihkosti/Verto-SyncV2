package com.verto.app.feature.reports.application.analytics

import com.verto.app.feature.reports.application.model.ItemRealMargin
import com.verto.app.feature.reports.application.model.ReportInvoiceLine
import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * F250 margin comparison.
 * Realized margin uses immutable cost-at-sale snapshots; replacement margin uses today's latest purchase price.
 */
class CostAllocationEngine {

    fun computeRealMargins(salesItems: List<ReportInvoiceLine>): List<ItemRealMargin> =
        salesItems.groupBy { it.inventoryItemId.ifBlank { it.itemName } }.mapNotNull { (key, rows) ->
            val revenueMinor = sum(rows.map { it.revenueMinor })
            if (revenueMinor <= 0L) return@mapNotNull null
            val knownRows = rows.filter { it.costSnapshotKnown }
            val realizedCostMinor = sum(knownRows.map { it.costAtSaleMinor })
            val replacementCostMinor = sum(rows.map { row ->
                Math.multiplyExact(row.currentReplacementUnitCostMinor, row.quantity.toLong())
            })
            val realizedProfitMinor = Math.subtractExact(revenueMinor, realizedCostMinor)
            val replacementProfitMinor = Math.subtractExact(revenueMinor, replacementCostMinor)
            val currency = rows.first().currencyCode.ifBlank { Money.TRANSACTION_CURRENCY }
            val totalQty = rows.sumOf { it.quantity }
            val replacementUnitMinor = if (totalQty > 0) BigDecimal.valueOf(replacementCostMinor)
                .divide(BigDecimal.valueOf(totalQty.toLong()), 0, RoundingMode.HALF_UP).longValueExact() else 0L

            ItemRealMargin(
                itemId = key,
                itemName = rows.first().itemName,
                revenue = Money.ofMinor(revenueMinor, currency).toLegacyDouble(),
                qty = totalQty,
                realizedMarginPct = pct(realizedProfitMinor, revenueMinor),
                replacementMarginPct = pct(replacementProfitMinor, revenueMinor),
                replacementCostPerUnit = Money.ofMinor(replacementUnitMinor, currency).toLegacyDouble(),
                historicalCostComplete = knownRows.size == rows.size,
            )
        }.sortedByDescending { it.realizedMarginPct }

    private fun sum(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)

    private fun pct(numerator: Long, denominator: Long): Float = if (denominator == 0L) 0f else
        BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100L))
            .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP).toFloat()
}
