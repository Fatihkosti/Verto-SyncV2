package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsPlannedCost
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV239MovementPreparationTest {
    @Test
    fun `planned values become editable defaults while actual leg remains untouched`() {
        val leg = plannedLeg()
        val draft = leg.v239PreparationDefaults(listOf(LogisticsPartner("carrier", "org", "Carrier", LogisticsPartnerRole.CARRIER)))

        assertEquals("Carrier", draft.carrierName)
        assertEquals("8", draft.packageCount)
        assertEquals("400", draft.weightKg)
        assertEquals("250", draft.amount)
        assertEquals("USD", draft.currency)
        assertTrue(draft.isValid)
        assertEquals("", leg.carrierPartnerId)
        assertNull(leg.packageCount)
        assertFalse(leg.v239IsPrepared())
    }

    @Test
    fun `cost and payment remain separate decisions`() {
        val base = plannedLeg().v239PreparationDefaults(emptyList()).updateFinancial { it.copy(amount = "") }
        assertTrue(base.updateFinancial { it.copy(confirmPaid = false) }.isValid)
        assertFalse(base.updateFinancial { it.copy(confirmPaid = true) }.isValid)
        val cost = base.updateFinancial { it.copy(amount = "100", currency = "SDG", exchangeRate = "1") }
        assertTrue(cost.updateFinancial { it.copy(confirmPaid = false) }.isValid)
        assertTrue(cost.updateFinancial { it.copy(confirmPaid = true) }.isValid)
    }

    private fun plannedLeg() = LogisticsShipmentLeg(
        id = "leg", organizationId = "org", shipmentId = "shipment", sequence = 0,
        fromMilestoneId = "origin", toMilestoneId = "destination", mode = LogisticsLegTransportMode.ROAD,
        carrierPartnerId = "", expectedTransitMinutes = 60,
        plannedCarrierPartnerId = "carrier", plannedCarrierNameSnapshot = "Carrier",
        plannedRepresentativePhoneSnapshot = "+249912345678",
        plannedPackageCount = 8, plannedWeightKg = BigDecimal("400"),
        plannedCost = LogisticsPlannedCost(BigDecimal("250"), "USD", BigDecimal("600"), BigDecimal("150000")),
    )
}
