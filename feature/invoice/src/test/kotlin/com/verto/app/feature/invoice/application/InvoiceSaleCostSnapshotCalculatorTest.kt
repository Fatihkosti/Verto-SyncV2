package com.verto.app.feature.invoice.application

import org.junit.Assert.assertEquals
import org.junit.Test

class InvoiceSaleCostSnapshotCalculatorTest {
    @Test
    fun `sale economics are frozen from current cost at sale`() {
        val snapshot = InvoiceSaleCostSnapshotCalculator.calculate(
            quantity = 1,
            unitSellPriceMinor = 15_000,
            unitCostAtSaleMinor = 10_000,
        )
        assertEquals(15_000L, snapshot.lineRevenueSnapshotMinor)
        assertEquals(10_000L, snapshot.lineCostSnapshotMinor)
        assertEquals(5_000L, snapshot.grossProfitSnapshotMinor)
    }

    @Test
    fun `fixed point multiplication preserves line totals`() {
        val snapshot = InvoiceSaleCostSnapshotCalculator.calculate(
            quantity = 3,
            unitSellPriceMinor = 10,
            unitCostAtSaleMinor = 3,
        )
        assertEquals(30L, snapshot.lineRevenueSnapshotMinor)
        assertEquals(9L, snapshot.lineCostSnapshotMinor)
        assertEquals(21L, snapshot.grossProfitSnapshotMinor)
    }
}
