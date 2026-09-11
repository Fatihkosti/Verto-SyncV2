package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.StartLogisticsReceivingCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class StartShipmentReceivingUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(organizationId: String, command: StartLogisticsReceivingCommand): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.startedAt >= 0L) { "startedAt must be non-negative" }
        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment
        require(aggregate.shipment.state == LogisticsShipmentState.AT_STATION) {
            "Receiving can start only after destination arrival at the final station"
        }
        val destination = aggregate.milestones.singleOrNull { it.type == LogisticsMilestoneType.DESTINATION }
            ?: error("Destination milestone is required before receiving")
        require(destination.arrivedAt != null) {
            "Destination arrival is required before receiving"
        }
        require(destination.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED) {
            "Destination must be unloaded before receiving"
        }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate)
        require(positions.isNotEmpty() && positions.values.all { it.holderType == LogisticsCustodyHolderType.WAREHOUSE }) {
            "All shipment sources must be under warehouse custody before receiving"
        }
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.RECEIVING)
        val updated = aggregate.shipment.copy(state = LogisticsShipmentState.RECEIVING)
        store.saveShipmentState(
            updated,
            LogisticsEvent(
                id = identities.newId(), organizationId = organizationId, shipmentId = updated.id,
                type = LogisticsEventType.RECEIVING_STARTED, occurredAt = command.startedAt,
                employeeId = updated.assignee?.employeeId, employeeNameSnapshot = updated.assignee?.employeeName,
                requestId = command.requestId,
            ),
        )
        return updated
    }
}
