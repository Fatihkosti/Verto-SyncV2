package com.verto.app.feature.shipment.application

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsPurchaseInvoiceQueryPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsV236InvoiceConflictTest {
    @Test
    fun `invoice rejected by eligibility query never reaches persistence`() = runTest {
        var saveCalled = false
        val shipment = LogisticsShipment(
            id = "shipment",
            organizationId = "org",
            shipmentNumber = "0001",
            sourceLocation = "القاهرة",
            destinationLocation = "أبوحمد",
            createdAt = 1L,
        )
        val aggregate = LogisticsShipmentAggregate(shipment = shipment)
        val store = Proxy.newProxyInstance(
            LogisticsShipmentStorePort::class.java.classLoader,
            arrayOf(LogisticsShipmentStorePort::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getShipment" -> aggregate
                "savePurchasePlan" -> {
                    saveCalled = true
                    Unit
                }
                "toString" -> "V236ConflictStore"
                else -> throw UnsupportedOperationException("Unexpected ${method.name}")
            }
        } as LogisticsShipmentStorePort
        val invoices = object : LogisticsPurchaseInvoiceQueryPort {
            override suspend fun getPurchaseInvoice(
                organizationId: String,
                invoiceId: String,
                excludeShipmentId: String?,
            ) = null
        }
        val identities = object : LogisticsIdentityPort {
            override fun newId(): String = "new-id"
        }
        val source = LogisticsShipmentSource(
            id = "source",
            shipmentId = shipment.id,
            invoiceId = "invoice",
            supplierId = "supplier",
            supplierNameSnapshot = "المورد",
            invoiceNumberSnapshot = "4582",
            plannedPackageCount = 3,
            plannedWeightKg = BigDecimal("18.5"),
            expectedReadyAt = 2L,
        )
        val line = LogisticsShipmentLine(
            id = "line",
            shipmentId = shipment.id,
            sourceInvoiceId = source.invoiceId,
            sourceInvoiceItemId = "invoice-item",
            inventoryItemId = "inventory-item",
            itemNameSnapshot = "صنف",
            expectedQuantity = 1,
            basePurchaseUnitPrice = BigDecimal.TEN,
        )

        val failure = runCatching {
            SaveShipmentPurchasePlanUseCase(store, invoices, identities)(
                organizationId = "org",
                command = SaveShipmentPurchasePlanCommand(shipment.id, listOf(source), listOf(line)),
            )
        }.exceptionOrNull()

        assertTrue(failure is BusinessRuleFailureException)
        assertTrue((failure as BusinessRuleFailureException).code == "SHIPMENT_PURCHASE_INVOICE_UNAVAILABLE")
        assertFalse(saveCalled)
    }
}
