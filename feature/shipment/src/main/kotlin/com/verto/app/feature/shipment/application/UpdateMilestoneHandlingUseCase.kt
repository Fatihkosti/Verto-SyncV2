package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.UpdateMilestoneHandlingCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class UpdateMilestoneHandlingUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: UpdateMilestoneHandlingCommand,
    ): LogisticsMilestone {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.milestoneId.isNotBlank()) { "milestoneId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        require(command.targetStatus == LogisticsMilestoneHandlingStatus.UNLOADED ||
            command.targetStatus == LogisticsMilestoneHandlingStatus.LOADED) {
            "Arrival/departure handling is owned by dedicated arrival/departure use cases"
        }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val milestone = aggregate.milestones.singleOrNull { it.id == command.milestoneId }
            ?: error("Logistics milestone not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return milestone
        require(aggregate.shipment.state == LogisticsShipmentState.AT_STATION ||
            aggregate.shipment.state == LogisticsShipmentState.CUSTOMS ||
            aggregate.shipment.state == LogisticsShipmentState.ARRIVED) {
            "Milestone handling requires active/arrived shipment"
        }
        LogisticsValidation.requireHandlingTransition(milestone, command.targetStatus)

        if (command.targetStatus == LogisticsMilestoneHandlingStatus.LOADED) {
            LogisticsV240ExecutionPolicy.requireCustomsCompletedBeforeLoading(aggregate, milestone)
            require(milestone.type != LogisticsMilestoneType.DESTINATION) { "Destination milestone cannot be loaded onward" }
            val nextLeg = aggregate.legs.singleOrNull {
                it.fromMilestoneId == milestone.id && it.status == com.verto.app.feature.shipment.domain.model.LogisticsLegStatus.PLANNED
            } ?: error("No next non-superseded leg exists for milestone")
            val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
            require(positions.isNotEmpty() && positions.all {
                it.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER &&
                    it.holderId == nextLeg.carrierPartnerId
            }) {
                "All shipment sources must be under the outgoing carrier before loading"
            }
        }

        val updated = when (command.targetStatus) {
            LogisticsMilestoneHandlingStatus.UNLOADED -> milestone.copy(
                handlingStatus = LogisticsMilestoneHandlingStatus.UNLOADED,
                unloadedAt = command.occurredAt,
            )
            LogisticsMilestoneHandlingStatus.LOADED -> milestone.copy(
                handlingStatus = LogisticsMilestoneHandlingStatus.LOADED,
                loadedAt = command.occurredAt,
            )
            else -> error("Unsupported handling target")
        }
        val projectedMilestones = aggregate.milestones.map { if (it.id == updated.id) updated else it }
        LogisticsValidation.validateMilestones(projectedMilestones, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        validateOperationalRoute(aggregate.shipment, projectedMilestones, aggregate.legs, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = aggregate.shipment.id,
            type = LogisticsEventType.MILESTONE_HANDLING_UPDATED,
            occurredAt = command.occurredAt,
            recordedAt = clock.now(),
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "milestoneId" to milestone.id,
                "oldStatus" to milestone.handlingStatus.name,
                "newStatus" to updated.handlingStatus.name,
            ),
        )
        store.saveOperationalUpdate(aggregate.shipment, listOf(updated), emptyList(), event)
        return updated
    }
}
