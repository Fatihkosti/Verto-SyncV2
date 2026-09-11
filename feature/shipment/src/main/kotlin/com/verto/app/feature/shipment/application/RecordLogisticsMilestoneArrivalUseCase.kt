package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordLogisticsMilestoneArrivalCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class RecordLogisticsMilestoneArrivalUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: RecordLogisticsMilestoneArrivalCommand,
    ): LogisticsMilestone {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.milestoneId.isNotBlank()) { "milestoneId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.arrivedAt >= 0L) { "arrivedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val milestone = aggregate.milestones.singleOrNull { it.id == command.milestoneId }
            ?: error("Logistics milestone not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return milestone

        require(aggregate.shipment.state == LogisticsShipmentState.IN_TRANSIT) {
            "Milestone arrival requires an IN_TRANSIT shipment"
        }
        require(milestone.arrivedAt == null) { "Milestone arrival was already recorded" }
        require(milestone.departedAt == null) { "Milestone departure already exists" }
        LogisticsValidation.requireHandlingTransition(milestone, LogisticsMilestoneHandlingStatus.ARRIVED)
        val incomingLeg = aggregate.legs.singleOrNull {
            it.toMilestoneId == milestone.id && it.status == LogisticsLegStatus.IN_TRANSIT
        } ?: error("Milestone has no active incoming route leg")
        require(incomingLeg.actualArrivalAt == null) { "Incoming leg arrival was already recorded" }
        incomingLeg.actualDepartureAt?.let { departure ->
            require(command.arrivedAt >= departure) { "Arrival cannot be before leg departure" }
        }

        val updatedMilestone = milestone.copy(
            arrivedAt = command.arrivedAt,
            handlingStatus = LogisticsMilestoneHandlingStatus.ARRIVED,
            note = command.note.trim().ifBlank { milestone.note },
        )
        val arrivedLeg = incomingLeg.copy(
            status = LogisticsLegStatus.ARRIVED,
            actualArrivalAt = command.arrivedAt,
        )
        val projectedMilestones = aggregate.milestones.map {
            if (it.id == updatedMilestone.id) updatedMilestone else it
        }
        val projectedLegs = aggregate.legs.map { if (it.id == arrivedLeg.id) arrivedLeg else it }
        LogisticsValidation.validateMilestones(projectedMilestones, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        LogisticsValidation.validateLeg(arrivedLeg)
        validateOperationalRoute(aggregate.shipment, projectedMilestones, projectedLegs, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)

        val destination = updatedMilestone.type == LogisticsMilestoneType.DESTINATION
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.AT_STATION)
        val updatedShipment = aggregate.shipment.copy(state = LogisticsShipmentState.AT_STATION)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = updatedShipment.id,
            type = if (destination) LogisticsEventType.DESTINATION_ARRIVED else LogisticsEventType.MILESTONE_ARRIVED,
            occurredAt = command.arrivedAt,
            recordedAt = clock.now(),
            employeeId = updatedShipment.assignee?.employeeId,
            employeeNameSnapshot = updatedShipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "milestoneId" to updatedMilestone.id,
                "legId" to arrivedLeg.id,
                "milestoneType" to updatedMilestone.type.name,
                "location" to updatedMilestone.location,
            ),
        )
        store.saveOperationalUpdate(updatedShipment, listOf(updatedMilestone), listOf(arrivedLeg), event)
        return updatedMilestone
    }
}
