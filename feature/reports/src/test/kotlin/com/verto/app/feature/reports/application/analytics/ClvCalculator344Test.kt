package com.verto.app.feature.reports.application.analytics

import com.verto.app.feature.reports.application.model.ReportRfmMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClvCalculator344Test {
    private val calculator = ClvCalculator()

    @Test
    fun projectedValueUsesFutureHorizonInsteadOfHistoricalLifespan() {
        val rfm = ReportRfmMetrics(
            clientId = "c1",
            totalInvoiceCount = 10,
            totalProfit = 1_000.0,
            customerLifespanDays = 730,
        )

        assertEquals(500.0, calculator.projectedClv(rfm), 0.001)
        assertNotEquals(calculator.historicClv(rfm), calculator.projectedClv(rfm), 0.001)
        assertEquals(1_000.0, calculator.projectedClv(rfm, projectionYears = 2.0), 0.001)
    }

    @Test
    fun noInvoicesHasNoDefensibleForwardProjection() {
        val rfm = ReportRfmMetrics(clientId = "c2", totalInvoiceCount = 0, totalProfit = 250.0)
        assertEquals(0.0, calculator.projectedClv(rfm), 0.001)
    }
}
