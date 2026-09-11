package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryCostPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class SettleShipmentLandedCostUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val calculator: CalculateShipmentLandedCostUseCase,
    private val inventoryCost: LogisticsInventoryCostPort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        shipmentId: String,
        requestId: String,
        settledAt: Long,
    ): List<LogisticsCostAllocation> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(requestId.isNotBlank()) { "requestId is required" }
        require(settledAt >= 0L) { "settledAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, requestId)) {
            return aggregate.costAllocations
        }
        require(aggregate.shipment.state == LogisticsShipmentState.RECEIVING) {
            "Landed cost can be settled only during final receiving"
        }

        val allocations = calculator(aggregate)
        require(allocations.isNotEmpty()) {
            "No ACTUAL logistics costs to settle; close explicitly with NO_ADDITIONAL_COSTS"
        }

        val lineById = aggregate.lines.associateBy { it.id }
        val acceptedLinesByShipmentLine = aggregate.receivingBatches
            .flatMap { batch -> batch.lines.map { batch to it } }
            .filter { (_, line) -> line.acceptedQuantity > 0 }
            .groupBy { (_, line) -> line.shipmentLineId }

        allocations.forEach { allocation ->
            val shipmentLine = lineById[allocation.shipmentLineId]
                ?: error("Cost allocation references unknown shipment line")
            val acceptedRows = acceptedLinesByShipmentLine[allocation.shipmentLineId].orEmpty()
            val acceptedQuantity = acceptedRows.sumOf { (_, line) -> line.acceptedQuantity.toLong() }
            require(acceptedQuantity > 0L) { "Allocated shipment line has no accepted inventory posting" }

            val acceptedPurchaseValue = shipmentLine.basePurchaseUnitPrice
                .multiply(BigDecimal(acceptedQuantity))
            val landedLineTotal = acceptedPurchaseValue.add(allocation.amount)
            val receivingUnitPrice = landedLineTotal.divide(
                BigDecimal(acceptedQuantity),
                CalculateShipmentLandedCostUseCase.UNIT_PRICE_SCALE,
                RoundingMode.HALF_UP,
            )

            acceptedRows.sortedWith(compareBy({ it.first.id }, { it.second.id })).forEach { (batch, line) ->
                inventoryCost.applyReceivingPostingUnitPrice(
                    postingId = postingId(batch.id, line.id),
                    shipmentId = shipmentId,
                    unitPrice = receivingUnitPrice,
                ).getOrThrow()
            }
        }

        store.saveCostAllocations(
            organizationId = organizationId,
            shipmentId = shipmentId,
            allocations = allocations,
            event = LogisticsEvent(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = shipmentId,
                type = LogisticsEventType.COST_SETTLED,
                occurredAt = settledAt,
                employeeId = aggregate.shipment.assignee?.employeeId,
                employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
                requestId = requestId,
                payload = mapOf(
                    "totalActualBaseCost" to calculator.totalActualCost(aggregate).toPlainString(),
                    "allocationCount" to allocations.size.toString(),
                ),
            ),
        )
        return allocations
    }

    internal fun postingId(batchId: String, receivingLineId: String): String =
        "logistics-receipt:$batchId:$receivingLineId"
}
