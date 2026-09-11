package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV241CancellationPolicyTest {
    @Test
    fun `started receiving shipment remains cancellable after inventory posting while hard delete stays forbidden`() {
        val started = shipment(LogisticsShipmentState.RECEIVING, startedAt = 100L)

        LogisticsV234CancellationPolicy.requireCancellable(started, hasInventoryPosting = true)

        assertTrue(LogisticsV234CancellationPolicy.preservesExecutionHistory(started))
        assertFalse(LogisticsV234CancellationPolicy.canPermanentlyDeleteInProduction(started))
    }

    @Test
    fun `only untouched draft can be permanently deleted in production`() {
        assertTrue(LogisticsV234CancellationPolicy.canPermanentlyDeleteInProduction(shipment()))
        assertFalse(LogisticsV234CancellationPolicy.canPermanentlyDeleteInProduction(shipment(LogisticsShipmentState.READY)))
    }

    private fun shipment(
        state: LogisticsShipmentState = LogisticsShipmentState.DRAFT,
        startedAt: Long? = null,
    ) = LogisticsShipment(
        id = "shipment",
        organizationId = "org",
        shipmentNumber = "S-241",
        sourceLocation = "Cairo",
        destinationLocation = "Abu Hamed",
        state = state,
        createdAt = 1L,
        startedAt = startedAt,
    )
}
