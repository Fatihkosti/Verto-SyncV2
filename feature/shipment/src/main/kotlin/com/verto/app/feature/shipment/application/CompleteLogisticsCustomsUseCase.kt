package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CompleteLogisticsCustomsCommand
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class CompleteLogisticsCustomsUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(organizationId: String, command: CompleteLogisticsCustomsCommand): LogisticsMilestone {
        require(organizationId.isNotBlank() && command.shipmentId.isNotBlank() && command.milestoneId.isNotBlank())
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.completedAt >= 0L) { "completedAt must be non-negative" }
        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        val milestone = aggregate.milestones.singleOrNull { it.id == command.milestoneId } ?: error("Customs host station not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return milestone
        require(aggregate.shipment.state == LogisticsShipmentState.CUSTOMS) { "Customs completion requires CUSTOMS state" }
        LogisticsV240ExecutionPolicy.requireCustomsHost(aggregate, milestone)
        require(aggregate.shipment.customsMilestoneId == null || aggregate.shipment.customsMilestoneId == milestone.id) {
            "Another customs event is active"
        }
        require(milestone.customsStartedAt != null && milestone.customsCompletedAt == null) {
            "Customs event is not in progress"
        }
        require(command.completedAt >= milestone.customsStartedAt) { "Customs completion cannot precede start" }
        val brokerId = requireNotNull(milestone.customsBrokerPartnerId) { "Customs broker is missing" }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
        require(positions.isNotEmpty() && positions.all {
            it.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER && it.holderId == brokerId
        }) { "Customs broker must retain custody until completion" }
        val updatedMilestone = milestone.copy(customsCompletedAt = command.completedAt)
        LogisticsValidation.validateMilestones(
            aggregate.milestones.map { if (it.id == milestone.id) updatedMilestone else it },
            customsHostMilestoneId = milestone.id,
        )
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.AT_STATION)
        val updatedShipment = aggregate.shipment.copy(state = LogisticsShipmentState.AT_STATION)
        val event = LogisticsEvent(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
            type = LogisticsEventType.CUSTOMS_COMPLETED, occurredAt = command.completedAt, recordedAt = clock.now(),
            employeeId = updatedShipment.assignee?.employeeId, employeeNameSnapshot = updatedShipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf("milestoneId" to milestone.id, "brokerPartnerId" to brokerId),
        )
        store.saveCustomsTransition(updatedShipment, updatedMilestone, null, event)
        return updatedMilestone
    }
}
