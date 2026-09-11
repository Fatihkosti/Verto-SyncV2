package com.verto.app.feature.payment.application.port

import com.verto.app.feature.payment.application.model.PurchaseShipmentCreateCommand
import com.verto.app.feature.payment.application.model.PurchaseShipmentOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseShipmentGatewayContractTest {
    @Test
    fun `active shipment read and create command preserve stable values`() = runTest {
        val expected = PurchaseShipmentOption("s1", "Shipment 1", "READY")
        val fake = FakeGateway(listOf(expected))
        val subject: PurchaseShipmentGateway = fake
        val command = PurchaseShipmentCreateCommand(
            shipmentNumber = "AUTO",
            origin = "Cairo",
            destination = "Abu Hamed",
            routeStops = listOf("Halfa"),
            createdAt = 123L,
        )

        assertEquals(listOf(expected), subject.observeActiveShipments().first())
        subject.createShipment(command)
        assertEquals(command, fake.created.single())
    }

    private class FakeGateway(private val options: List<PurchaseShipmentOption>) : PurchaseShipmentGateway {
        val created = mutableListOf<PurchaseShipmentCreateCommand>()
        override fun observeActiveShipments(): Flow<List<PurchaseShipmentOption>> = flowOf(options)
        override suspend fun createShipment(command: PurchaseShipmentCreateCommand) {
            created += command
        }
    }
}
