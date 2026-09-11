package com.verto.app.feature.invoice.application

/** F247 fixed-point calculator for immutable realized sale economics. */
internal data class InvoiceSaleCostSnapshot(
    val unitSellPriceMinor: Long,
    val unitCostAtSaleMinor: Long,
    val lineRevenueSnapshotMinor: Long,
    val lineCostSnapshotMinor: Long,
    val grossProfitSnapshotMinor: Long,
)

internal object InvoiceSaleCostSnapshotCalculator {
    fun calculate(
        quantity: Int,
        unitSellPriceMinor: Long,
        unitCostAtSaleMinor: Long,
    ): InvoiceSaleCostSnapshot {
        require(quantity > 0) { "quantity must be positive" }
        require(unitSellPriceMinor >= 0L) { "sell price must be non-negative" }
        require(unitCostAtSaleMinor >= 0L) { "sale cost must be non-negative" }
        val quantityLong = quantity.toLong()
        val revenue = Math.multiplyExact(unitSellPriceMinor, quantityLong)
        val cost = Math.multiplyExact(unitCostAtSaleMinor, quantityLong)
        return InvoiceSaleCostSnapshot(
            unitSellPriceMinor = unitSellPriceMinor,
            unitCostAtSaleMinor = unitCostAtSaleMinor,
            lineRevenueSnapshotMinor = revenue,
            lineCostSnapshotMinor = cost,
            grossProfitSnapshotMinor = Math.subtractExact(revenue, cost),
        )
    }
}
