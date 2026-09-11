package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCargoSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyPosition
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason
import com.verto.app.feature.shipment.domain.model.LogisticsPartner
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.StartLogisticsCustomsCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class StartLogisticsCustomsUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(organizationId: String, command: StartLogisticsCustomsCommand): LogisticsCustodyHandoff {
        require(organizationId.isNotBlank() && command.shipmentId.isNotBlank() && command.milestoneId.isNotBlank())
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.receivedAt >= 0L) { "receivedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) {
            return aggregate.custodyHandoffs.singleOrNull { it.requestId == command.requestId }
                ?: error("Request was already processed by another logistics operation")
        }
        require(aggregate.shipment.state == LogisticsShipmentState.AT_STATION) { "Customs start requires AT_STATION" }
        val milestone = aggregate.milestones.singleOrNull { it.id == command.milestoneId }
            ?: error("Customs host station not found")
        LogisticsV240ExecutionPolicy.requireCustomsHost(aggregate, milestone)
        require(milestone.arrivedAt != null && milestone.customsStartedAt == null && milestone.customsCompletedAt == null) {
            "Customs event is not ready to start at the designated station"
        }
        require(milestone.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED) {
            "Cargo must be unloaded before broker pickup"
        }
        require(command.receivedAt >= milestone.arrivedAt) { "Broker pickup cannot precede customs arrival" }

        val broker = store.getPartner(organizationId, command.brokerPartnerId) ?: error("Customs broker not found")
        require(broker.role == LogisticsPartnerRole.CUSTOMS_BROKER) { "Selected partner is not a customs broker" }
        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values.toList()
        require(positions.isNotEmpty()) { "Shipment has no purchase sources" }
        val current = positions.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }.singleOrNull()
            ?: error("Broker pickup requires one current custodian for the whole cargo")
        require(current.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER) {
            "Broker pickup requires carrier custody"
        }
        require(current.holderId != broker.id) { "Broker already owns custody" }

        val cargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate) ?: error("Confirmed cargo snapshot is required")
        require(command.receipt.handoverPackageCount == cargo.packageCount) {
            "Handover package count must equal the latest confirmed cargo snapshot; record repack first"
        }
        val discrepancy = command.receipt.handoverPackageCount != command.receipt.receivedPackageCount
        if (discrepancy) require(command.receipt.discrepancyNote.isNotBlank()) { "Discrepancy note is required" }

        val handoff = buildHandoff(organizationId, command, milestone.id, current, broker, cargo)
        LogisticsValidation.validateConfirmedCustodyHandoff(handoff)
        val updatedMilestone = milestone.copy(
            customsBrokerPartnerId = broker.id, customsBrokerNameSnapshot = broker.name,
            customsBrokerPhoneSnapshot = broker.phone, customsStartedAt = command.receivedAt,
        )
        LogisticsValidation.validateMilestones(
            aggregate.milestones.map { if (it.id == milestone.id) updatedMilestone else it },
            customsHostMilestoneId = milestone.id,
        )
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.CUSTOMS)
        val updatedShipment = aggregate.shipment.copy(state = LogisticsShipmentState.CUSTOMS, customsMilestoneId = milestone.id)
        val event = buildEvent(organizationId, command, updatedShipment, milestone.id, broker)
        store.saveCustomsTransition(updatedShipment, updatedMilestone, handoff, event)
        return handoff
    }

    private fun buildHandoff(
        organizationId: String, command: StartLogisticsCustomsCommand, milestoneId: String,
        current: LogisticsCustodyPosition, broker: LogisticsPartner, cargo: LogisticsCargoSnapshot
    ): LogisticsCustodyHandoff {
        val discrepancy = command.receipt.handoverPackageCount != command.receipt.receivedPackageCount
        return LogisticsCustodyHandoff(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId, milestoneId = milestoneId,
            fromHolderType = current.holderType, fromHolderId = current.holderId, fromHolderNameSnapshot = current.holderName,
            toHolderType = LogisticsCustodyHolderType.LOGISTICS_PARTNER, toHolderId = broker.id, toHolderNameSnapshot = broker.name,
            transferredAt = command.receivedAt, receivedAt = command.receivedAt, requestId = command.requestId,
            note = command.receipt.discrepancyNote.trim(),
            handoverPackageCount = command.receipt.handoverPackageCount, receivedPackageCount = command.receipt.receivedPackageCount,
            handoverWeightKg = cargo.weightKg, receivedWeightKg = cargo.weightKg,
            packageChangeReason = if (discrepancy) LogisticsPackageChangeReason.OTHER else null,
            packageChangeNote = command.receipt.discrepancyNote.trim().takeIf { discrepancy },
            openedPackageCount = command.receipt.openedPackageCount, damagedPackageCount = command.receipt.damagedPackageCount,
        )
    }

    private fun buildEvent(
        organizationId: String, command: StartLogisticsCustomsCommand, shipment: LogisticsShipment,
        milestoneId: String, broker: LogisticsPartner,
    ): LogisticsEvent = LogisticsEvent(
        id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
        type = LogisticsEventType.CUSTOMS_STARTED, occurredAt = command.receivedAt, recordedAt = clock.now(),
        employeeId = shipment.assignee?.employeeId, employeeNameSnapshot = shipment.assignee?.employeeName,
        requestId = command.requestId,
        payload = mapOf(
            "milestoneId" to milestoneId, "brokerPartnerId" to broker.id, "brokerName" to broker.name,
            "handoverPackageCount" to command.receipt.handoverPackageCount.toString(),
            "receivedPackageCount" to command.receipt.receivedPackageCount.toString(),
            "openedPackageCount" to command.receipt.openedPackageCount.toString(),
            "damagedPackageCount" to command.receipt.damagedPackageCount.toString(),
        ),
    )

}
