package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CorrectLogisticsActualTimeCommand
import com.verto.app.feature.shipment.domain.model.LogisticsActualTimeTarget
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class CorrectLogisticsActualTimeUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: CorrectLogisticsActualTimeCommand,
    ): LogisticsMilestone {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.milestoneId.isNotBlank()) { "milestoneId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.correctedAt >= 0L) { "correctedAt must be non-negative" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        val reason = command.reason.trim()
        require(reason.isNotBlank()) { "Correction reason is required" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val milestone = aggregate.milestones.singleOrNull { it.id == command.milestoneId }
            ?: error("Logistics milestone not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return milestone

        val updatedLegs = mutableListOf<LogisticsShipmentLeg>()
        val oldValue: Long
        val updatedMilestone = when (command.target) {
            LogisticsActualTimeTarget.MILESTONE_ARRIVAL -> {
                oldValue = requireNotNull(milestone.arrivedAt) { "Arrival does not exist; use arrival recording instead" }
                val incoming = aggregate.legs.filter { it.toMilestoneId == milestone.id && it.actualArrivalAt != null }
                require(incoming.isNotEmpty()) { "Inbound leg actual arrival is missing" }
                updatedLegs += incoming.map { it.copy(actualArrivalAt = command.correctedAt) }
                milestone.copy(arrivedAt = command.correctedAt)
            }
            LogisticsActualTimeTarget.MILESTONE_DEPARTURE -> {
                oldValue = requireNotNull(milestone.departedAt) { "Departure does not exist; use departure recording instead" }
                val outgoing = aggregate.legs.filter { it.fromMilestoneId == milestone.id && it.actualDepartureAt != null }
                require(outgoing.isNotEmpty()) { "Outbound leg actual departure is missing" }
                updatedLegs += outgoing.map { it.copy(actualDepartureAt = command.correctedAt) }
                milestone.copy(departedAt = command.correctedAt)
            }
        }
        val projectedMilestones = aggregate.milestones.map {
            if (it.id == updatedMilestone.id) updatedMilestone else it
        }
        val updatedById = updatedLegs.associateBy { it.id }
        val projectedLegs = aggregate.legs.map { updatedById[it.id] ?: it }
        LogisticsValidation.validateMilestones(projectedMilestones, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        updatedLegs.forEach(LogisticsValidation::validateLeg)
        validateOperationalRoute(aggregate.shipment, projectedMilestones, projectedLegs, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)

        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = aggregate.shipment.id,
            type = LogisticsEventType.CORRECTION_RECORDED,
            occurredAt = command.occurredAt,
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "milestoneId" to milestone.id,
                "field" to command.target.name,
                "old" to oldValue.toString(),
                "new" to command.correctedAt.toString(),
                "reason" to reason,
                "affectedLegIds" to updatedLegs.joinToString(",") { it.id },
            ),
        )
        store.saveOperationalUpdate(aggregate.shipment, listOf(updatedMilestone), updatedLegs, event)
        return updatedMilestone
    }
}
