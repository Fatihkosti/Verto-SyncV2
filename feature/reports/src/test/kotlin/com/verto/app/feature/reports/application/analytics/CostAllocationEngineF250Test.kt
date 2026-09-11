package com.verto.app.feature.reports.application.analytics

import com.verto.app.feature.reports.application.model.ReportInvoiceLine
import org.junit.Assert.assertEquals
import org.junit.Test

class CostAllocationEngineF250Test {
    private val engine = CostAllocationEngine()

    @Test
    fun realizedMargin_staysHistorical_whenReplacementCostChanges() {
        val historical = ReportInvoiceLine(
            inventoryItemId = "item-1",
            itemName = "قطعة",
            revenueMinor = 20_000L,
            costAtSaleMinor = 10_000L,
            currentReplacementUnitCostMinor = 20_000L,
            quantity = 1,
            costSnapshotKnown = true,
            currencyCode = "SDG",
        )
        val first = engine.computeRealMargins(listOf(historical)).single()
        val repriced = engine.computeRealMargins(listOf(historical.copy(currentReplacementUnitCostMinor = 30_000L))).single()

        assertEquals(50f, first.realizedMarginPct, 0.001f)
        assertEquals(50f, repriced.realizedMarginPct, 0.001f)
        assertEquals(0f, first.replacementMarginPct, 0.001f)
        assertEquals(-50f, repriced.replacementMarginPct, 0.001f)
    }
}
