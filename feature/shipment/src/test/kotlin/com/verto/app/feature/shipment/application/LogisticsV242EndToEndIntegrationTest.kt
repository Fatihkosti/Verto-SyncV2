package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CloseLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsInventoryPosting
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingBatch
import com.verto.app.feature.shipment.domain.model.LogisticsReceivingLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentSource
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordLogisticsReceivingBatchCommand
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryCostPort
import com.verto.app.feature.shipment.domain.port.LogisticsReceivingTransactionPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v242 critical integration: purchase-invoice shipment line -> accepted stock -> landed cost -> close.
 * The same stable request IDs are retried to prove no duplicate inventory/cost/close side effects.
 */
class LogisticsV242EndToEndIntegrationTest {
    @Test
    fun `accepted invoice quantity reaches inventory then landed cost settles and shipment closes idempotently`() = runBlocking {
        val harness = Harness(initialAggregate())
        val receive = RecordShipmentReceivingBatchUseCase(harness.store, harness.identities, harness.receivingTransaction)
        val settle = SettleShipmentLandedCostUseCase(
            store = harness.store,
            calculator = CalculateShipmentLandedCostUseCase(),
            inventoryCost = harness.inventoryCost,
            identities = harness.identities,
        )
        val close = CloseLogisticsShipmentUseCase(
            store = harness.store,
            calculator = CalculateShipmentLandedCostUseCase(),
            identities = harness.identities,
        )
        val command = RecordLogisticsReceivingBatchCommand(
            shipmentId = "shipment",
            batchId = "batch-final",
            receivedAt = 1_000L,
            receivedByEmployeeId = "employee",
            receivedByEmployeeNameSnapshot = "Employee",
            lines = listOf(
                LogisticsReceivingLine(
                    id = "receipt-line",
                    shipmentId = "shipment",
                    shipmentLineId = "line",
                    expectedQuantitySnapshot = 10,
                    receivedQuantity = 10,
                    acceptedQuantity = 10,
                    damagedQuantity = 0,
                    rejectedQuantity = 0,
                    quarantinedQuantity = 0,
                ),
            ),
            requestId = "receive-final",
        )

        receive("org", command)
        receive("org", command)

        assertEquals(1, harness.inventoryPostings.size)
        assertEquals(10, harness.inventoryPostings.single().quantity)
        assertEquals(BigDecimal("100"), harness.inventoryPostings.single().unitPrice)
        assertEquals("supplier", harness.inventoryPostings.single().supplierId)

        val allocations = settle("org", "shipment", "settle-final", settledAt = 1_100L)
        val retryAllocations = settle("org", "shipment", "settle-final", settledAt = 1_100L)

        assertEquals(1, harness.inventoryCostUpdates.size)
        assertEquals(BigDecimal("102.00000000"), harness.inventoryCostUpdates.single().third)
        assertEquals(BigDecimal("20"), allocations.single().amount)
        assertEquals(allocations, retryAllocations)

        val closed = close(
            "org",
            CloseLogisticsShipmentCommand(
                shipmentId = "shipment",
                closedAt = 1_200L,
                requestId = "close-final",
            ),
        )
        val retriedClose = close(
            "org",
            CloseLogisticsShipmentCommand(
                shipmentId = "shipment",
                closedAt = 1_200L,
                requestId = "close-final",
            ),
        )

        assertEquals(LogisticsShipmentState.CLOSED, closed.state)
        assertEquals(LogisticsShipmentState.CLOSED, retriedClose.state)
        assertEquals(1, harness.closeWrites)
        assertTrue(harness.processed.containsAll(listOf("receive-final", "settle-final", "close-final")))
    }

    private fun initialAggregate() = LogisticsShipmentAggregate(
        shipment = LogisticsShipment(
            id = "shipment",
            organizationId = "org",
            shipmentNumber = "S-242",
            sourceLocation = "Cairo",
            destinationLocation = "Abu Hamed",
            state = LogisticsShipmentState.RECEIVING,
            createdAt = 1L,
            startedAt = 2L,
        ),
        sources = listOf(
            LogisticsShipmentSource(
                id = "source",
                shipmentId = "shipment",
                invoiceId = "invoice",
                supplierId = "supplier",
                supplierNameSnapshot = "Supplier",
                invoiceNumberSnapshot = "INV-242",
            ),
        ),
        lines = listOf(
            LogisticsShipmentLine(
                id = "line",
                shipmentId = "shipment",
                sourceInvoiceId = "invoice",
                sourceInvoiceItemId = "invoice-item",
                inventoryItemId = "item",
                itemNameSnapshot = "Item",
                expectedQuantity = 10,
                basePurchaseUnitPrice = BigDecimal("100"),
            ),
        ),
        costs = listOf(
            LogisticsCost(
                id = "freight",
                organizationId = "org",
                shipmentId = "shipment",
                type = LogisticsCostType.FREIGHT,
                amount = BigDecimal("20"),
                currency = "SDG",
                exchangeRateSnapshot = BigDecimal.ONE,
                baseCurrencyAmount = BigDecimal("20"),
                status = LogisticsCostStatus.ACTUAL,
            ),
        ),
    )

    private class Harness(initial: LogisticsShipmentAggregate) {
        var aggregate = initial
        val processed = linkedSetOf<String>()
        val inventoryPostings = mutableListOf<LogisticsInventoryPosting>()
        val inventoryCostUpdates = mutableListOf<Triple<String, String, BigDecimal>>()
        var closeWrites = 0
        private var identitySequence = 0

        val identities = object : LogisticsIdentityPort {
            override fun newId(): String = "v242-${++identitySequence}"
        }

        val receivingTransaction = object : LogisticsReceivingTransactionPort {
            override suspend fun commitReceiving(
                postings: List<LogisticsInventoryPosting>,
                batch: LogisticsReceivingBatch,
                updatedShipment: LogisticsShipment,
                event: LogisticsEvent,
            ) {
                inventoryPostings += postings
                aggregate = aggregate.copy(
                    shipment = updatedShipment,
                    receivingBatches = aggregate.receivingBatches + batch,
                )
                processed += event.requestId
            }
        }

        val inventoryCost = object : LogisticsInventoryCostPort {
            override suspend fun applyReceivingPostingUnitPrice(
                postingId: String,
                shipmentId: String,
                unitPrice: BigDecimal,
            ): Result<Unit> {
                inventoryCostUpdates += Triple(postingId, shipmentId, unitPrice)
                return Result.success(Unit)
            }
        }

        val store: LogisticsShipmentStorePort = Proxy.newProxyInstance(
            LogisticsShipmentStorePort::class.java.classLoader,
            arrayOf(LogisticsShipmentStorePort::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getShipment" -> aggregate
                "isRequestProcessed" -> args?.get(1) in processed
                "saveCostAllocations" -> {
                    @Suppress("UNCHECKED_CAST")
                    val allocations = args?.get(2) as List<LogisticsCostAllocation>
                    val event = args[3] as LogisticsEvent
                    aggregate = aggregate.copy(costAllocations = allocations)
                    processed += event.requestId
                    Unit
                }
                "saveShipmentState" -> {
                    val shipment = args?.get(0) as LogisticsShipment
                    val event = args[1] as LogisticsEvent
                    aggregate = aggregate.copy(shipment = shipment)
                    processed += event.requestId
                    closeWrites += 1
                    Unit
                }
                "toString" -> "LogisticsV242StoreHarness"
                else -> error("Unexpected store method: ${method.name}")
            }
        } as LogisticsShipmentStorePort
    }
}
