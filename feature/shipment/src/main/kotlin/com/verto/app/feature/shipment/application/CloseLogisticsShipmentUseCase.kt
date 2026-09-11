package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CloseLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.BigDecimal
import javax.inject.Inject

class CloseLogisticsShipmentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val calculator: CalculateShipmentLandedCostUseCase,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: CloseLogisticsShipmentCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.closedAt >= 0L) { "closedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment

        require(aggregate.shipment.state == LogisticsShipmentState.RECEIVING) {
            "Only a RECEIVING shipment with fully reconciled lines can be closed"
        }

        requireReconciled(aggregate)

        val totalActualCost = calculator.totalActualCost(aggregate)
        if (totalActualCost.signum() > 0) {
            require(aggregate.costAllocations.isNotEmpty()) { "Landed-cost settlement is required before close" }
            val allocated = aggregate.costAllocations.fold(BigDecimal.ZERO) { acc, allocation -> acc.add(allocation.amount) }
            require(allocated == totalActualCost) { "Persisted landed-cost settlement is incomplete" }
        } else {
            require(command.noAdditionalCosts) { "Explicit NO_ADDITIONAL_COSTS is required before close" }
        }

        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.CLOSED)
        val closed = aggregate.shipment.copy(state = LogisticsShipmentState.CLOSED)
        store.saveShipmentState(
            closed,
            LogisticsEvent(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = closed.id,
                type = LogisticsEventType.SHIPMENT_CLOSED,
                occurredAt = command.closedAt,
                employeeId = closed.assignee?.employeeId,
                employeeNameSnapshot = closed.assignee?.employeeName,
                requestId = command.requestId,
                payload = mapOf("noAdditionalCosts" to command.noAdditionalCosts.toString()),
            ),
        )
        return closed
    }
    private fun requireReconciled(aggregate: LogisticsShipmentAggregate) {
        val receivingByLine = aggregate.receivingBatches.flatMap { it.lines }.groupBy { it.shipmentLineId }
        val shortagesByLine = aggregate.shortages.groupBy { it.identity.shipmentLineId }
        aggregate.lines.forEach { line ->
            val shortages = shortagesByLine[line.id].orEmpty()
            require(shortages.size <= 1) { "Shipment line has duplicate shortages" }
            val receiving = receivingByLine[line.id].orEmpty()
            val accounted = receiving.sumOf {
                it.acceptedQuantity.toLong() + it.damagedQuantity + it.rejectedQuantity + it.quarantinedQuantity
            } + (shortages.singleOrNull()?.quantity?.remainingMissingQuantity?.toLong() ?: 0L)
            require(accounted == line.expectedQuantity.toLong()) {
                "Shipment line is not fully reconciled"
            }
        }
    }
}
