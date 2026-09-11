package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.application.LargestRemainderCostAllocator
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlement
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementAudit
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV232FinalizationContractTest {
    @Test
    fun `late cost allocation remains exact on purchase value basis`() {
        val rows = LargestRemainderCostAllocator.allocate(
            BigDecimal("101"),
            listOf(
                LargestRemainderCostAllocator.Basis("cheap", BigDecimal("100")),
                LargestRemainderCostAllocator.Basis("expensive", BigDecimal("300")),
            ),
        )
        assertEquals(BigDecimal("101"), rows.fold(BigDecimal.ZERO) { total, row -> total.add(row.amount) })
        assertEquals(BigDecimal("25"), rows.single { it.shipmentLineId == "cheap" }.amount)
        assertEquals(BigDecimal("76"), rows.single { it.shipmentLineId == "expensive" }.amount)
    }

    @Test
    fun `final loss settlement carries quantity but no compensation money`() {
        val settlement = LogisticsShortageSettlement(
            identity = LogisticsShortageSettlementIdentity("settlement", "org", "shipment", "shortage"),
            type = LogisticsShortageSettlementType.FINAL_LOSS,
            quantity = 2,
            audit = LogisticsShortageSettlementAudit(occurredAt = 1L, employee = null, note = "", requestId = "req"),
        )
        assertTrue(settlement.quantity > 0)
        assertNull(settlement.compensationAmount)
        assertNull(settlement.baseCurrencyAmount)
    }
}
