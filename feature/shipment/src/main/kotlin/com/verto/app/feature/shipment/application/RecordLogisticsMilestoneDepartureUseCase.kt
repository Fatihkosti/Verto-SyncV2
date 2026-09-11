package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordLogisticsMilestoneDepartureCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class RecordLogisticsMilestoneDepartureUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: RecordLogisticsMilestoneDepartureCommand,
    ): LogisticsMilestone {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.milestoneId.isNotBlank()) { "milestoneId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.departedAt >= 0L) { "departedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val milestone = aggregate.milestones.singleOrNull { it.id == command.milestoneId }
            ?: error("Logistics milestone not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return milestone

        require(aggregate.shipment.state == LogisticsShipmentState.IN_TRANSIT) {
            "Milestone departure requires an IN_TRANSIT shipment"
        }
        require(milestone.type != LogisticsMilestoneType.DESTINATION) { "Destination milestone cannot depart" }
        val arrivedAt = requireNotNull(milestone.arrivedAt) { "Milestone cannot depart before arrival" }
        require(milestone.departedAt == null) { "Milestone departure was already recorded" }
        require(command.departedAt >= arrivedAt) { "Milestone departure cannot be before arrival" }
        require(milestone.handlingStatus == LogisticsMilestoneHandlingStatus.LOADED) {
            "Milestone must be LOADED before departure"
        }
        LogisticsValidation.requireHandlingTransition(milestone, LogisticsMilestoneHandlingStatus.DEPARTED)
        val nextLeg = aggregate.legs.singleOrNull {
            it.fromMilestoneId == milestone.id && it.status == LogisticsLegStatus.PLANNED
        } ?: error("Milestone has no next non-superseded route leg")
        require(nextLeg.actualDepartureAt == null) { "Outgoing leg has already started" }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
        require(positions.isNotEmpty() && positions.all {
                it.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER &&
                    it.holderId == nextLeg.carrierPartnerId
            }) {
            "All shipment sources must be under outgoing carrier custody before departure"
        }

        val updatedMilestone = milestone.copy(
            departedAt = command.departedAt,
            handlingStatus = LogisticsMilestoneHandlingStatus.DEPARTED,
            note = command.note.trim().ifBlank { milestone.note },
        )
        val activeNextLeg = nextLeg.copy(
            status = LogisticsLegStatus.IN_TRANSIT,
            actualDepartureAt = command.departedAt,
        )
        val projectedMilestones = aggregate.milestones.map {
            if (it.id == updatedMilestone.id) updatedMilestone else it
        }
        val projectedLegs = aggregate.legs.map { if (it.id == activeNextLeg.id) activeNextLeg else it }
        LogisticsValidation.validateMilestones(projectedMilestones, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        LogisticsValidation.validateLeg(activeNextLeg)
        validateOperationalRoute(aggregate.shipment, projectedMilestones, projectedLegs, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = aggregate.shipment.id,
            type = LogisticsEventType.MILESTONE_DEPARTED,
            occurredAt = command.departedAt,
            recordedAt = clock.now(),
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "milestoneId" to updatedMilestone.id,
                "nextLegId" to activeNextLeg.id,
                "location" to updatedMilestone.location,
            ),
        )
        store.saveOperationalUpdate(aggregate.shipment, listOf(updatedMilestone), listOf(activeNextLeg), event)
        return updatedMilestone
    }
}
