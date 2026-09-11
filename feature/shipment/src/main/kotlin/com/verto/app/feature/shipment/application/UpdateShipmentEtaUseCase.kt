package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.UpdateShipmentEtaCommand
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class UpdateShipmentEtaUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: UpdateShipmentEtaCommand,
    ): LogisticsShipmentLeg {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.legId.isNotBlank()) { "legId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        command.plannedArrivalAt?.let { require(it >= 0L) { "ETA must be non-negative" } }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val current = aggregate.legs.singleOrNull { it.id == command.legId }
            ?: error("Logistics leg not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return current
        require(aggregate.shipment.state in setOf(
            LogisticsShipmentState.DRAFT,
            LogisticsShipmentState.READY,
            LogisticsShipmentState.WAITING_DEPARTURE,
            LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.CUSTOMS,
            LogisticsShipmentState.ARRIVED,
            LogisticsShipmentState.PARTIAL,
        )) { "ETA edit requires DRAFT, READY, or an active shipment" }
        require(current.status != LogisticsLegStatus.ARRIVED && current.status != LogisticsLegStatus.CANCELLED) {
            "ETA can be edited only before leg arrival"
        }
        require(current.actualArrivalAt == null) { "Actual arrived leg ETA cannot be overwritten" }

        val updatedLeg = current.copy(plannedArrivalAt = command.plannedArrivalAt)
        LogisticsValidation.validateLeg(updatedLeg)
        val destination = aggregate.milestones.single { it.id == current.toMilestoneId }
        require(destination.arrivedAt == null) { "Reached milestone planned arrival cannot be edited" }
        val updatedMilestone = destination.copy(plannedArrivalAt = command.plannedArrivalAt)
        val updatedShipment = if (destination.type == LogisticsMilestoneType.DESTINATION) {
            aggregate.shipment.copy(expectedArrivalAt = command.plannedArrivalAt)
        } else aggregate.shipment
        LogisticsValidation.validateMilestones(
            aggregate.milestones.map { if (it.id == updatedMilestone.id) updatedMilestone else it },
            customsHostMilestoneId = aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId,
        )
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = aggregate.shipment.id,
            type = LogisticsEventType.ETA_UPDATED,
            occurredAt = command.occurredAt,
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "legId" to current.id,
                "oldEta" to (current.plannedArrivalAt?.toString() ?: ""),
                "newEta" to (command.plannedArrivalAt?.toString() ?: ""),
            ),
        )
        store.saveOperationalUpdate(updatedShipment, listOf(updatedMilestone), listOf(updatedLeg), event)
        return updatedLeg
    }
}
