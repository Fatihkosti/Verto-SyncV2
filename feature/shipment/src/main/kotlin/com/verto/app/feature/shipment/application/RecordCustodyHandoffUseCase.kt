package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHandoff
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordCustodyHandoffCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class RecordCustodyHandoffUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: RecordCustodyHandoffCommand,
    ): LogisticsCustodyHandoff {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) {
            return aggregate.custodyHandoffs.singleOrNull { it.requestId == command.requestId }
                ?: error("Request was already processed by another logistics operation")
        }
        require(aggregate.shipment.state in setOf(
            LogisticsShipmentState.READY,
            LogisticsShipmentState.WAITING_DEPARTURE,
            LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.CUSTOMS,
            LogisticsShipmentState.ARRIVED,
            LogisticsShipmentState.PARTIAL,
        )) { "Custody handoff is outside the allowed operational states" }
        if (aggregate.shipment.startedAt == null) {
            require(aggregate.shipment.state == LogisticsShipmentState.READY) {
                "Pre-start custody handoff requires READY shipment"
            }
            require(command.sourceId != null) { "Pre-start custody handoff must be source-specific" }
        }
        command.sourceId?.let { sourceId ->
            require(aggregate.sources.any { it.id == sourceId }) { "Handoff source does not belong to shipment" }
        }
        command.milestoneId?.let { milestoneId ->
            require(aggregate.milestones.any { it.id == milestoneId }) { "Handoff milestone does not belong to shipment" }
        }

        val currentPositions = if (command.sourceId != null) {
            val source = aggregate.sources.single { it.id == command.sourceId }
            listOf(LogisticsCustodyResolver.currentForSource(aggregate, source))
        } else {
            LogisticsCustodyResolver.currentForAllSources(aggregate).values.toList()
        }
        require(currentPositions.isNotEmpty()) { "Shipment has no purchase sources" }
        require(currentPositions.all {
            it.holderType == command.fromHolderType &&
                if (it.holderId != null) it.holderId == command.fromHolderId
                else command.fromHolderId == null && it.holderName == command.fromHolderNameSnapshot.trim()
        }) { "Handoff from-holder does not match current custodian" }
        val fromName = currentPositions.map { it.holderName }.distinct().singleOrNull()
            ?: error("Shipment-wide custody handoff requires one current holder")

        val toName = when (command.toHolderType) {
            LogisticsCustodyHolderType.LOGISTICS_PARTNER -> {
                val partnerId = requireNotNull(command.toHolderId) { "Logistics partner holder id is required" }
                store.getPartner(organizationId, partnerId)?.name ?: error("Handoff logistics partner not found")
            }
            else -> command.toHolderNameSnapshot.trim().also { require(it.isNotBlank()) { "to holder is required" } }
        }
        require(currentPositions.any { it.holderType != command.toHolderType || it.holderId != command.toHolderId }) {
            "Handoff must change custody holder"
        }

        val currentCargo = LogisticsCustodyResolver.currentCargoSnapshot(aggregate)
            ?: error("Confirmed cargo snapshot is required before custody handoff")
        val handoverPackageCount = requireNotNull(command.handoverPackageCount) {
            "Sender handover package count is required"
        }
        val receivedPackageCount = requireNotNull(command.receivedPackageCount) {
            "Receiver confirmed package count is required"
        }
        require(handoverPackageCount == currentCargo.packageCount) {
            "Handover package count must equal the latest confirmed cargo snapshot; record repack first"
        }
        command.handoverWeightKg?.let { handoverWeight ->
            currentCargo.weightKg?.let { currentWeight ->
                require(handoverWeight.compareTo(currentWeight) == 0) {
                    "Handover weight must equal the latest confirmed cargo snapshot"
                }
            }
        }
        val discrepancy = handoverPackageCount != receivedPackageCount
        val discrepancyNote = command.discrepancyNote.trim()
        if (discrepancy) require(discrepancyNote.isNotBlank()) {
            "Discrepancy note is required when receiver count differs from sender count"
        }

        val handoff = LogisticsCustodyHandoff(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            sourceId = command.sourceId,
            milestoneId = command.milestoneId,
            fromHolderType = command.fromHolderType,
            fromHolderId = command.fromHolderId,
            fromHolderNameSnapshot = fromName,
            toHolderType = command.toHolderType,
            toHolderId = command.toHolderId,
            toHolderNameSnapshot = toName,
            transferredAt = command.transferredAt,
            receivedAt = command.receivedAt,
            requestId = command.requestId,
            note = command.note.trim(),
            handoverPackageCount = handoverPackageCount,
            receivedPackageCount = receivedPackageCount,
            handoverWeightKg = command.handoverWeightKg ?: currentCargo.weightKg,
            receivedWeightKg = command.receivedWeightKg ?: command.handoverWeightKg ?: currentCargo.weightKg,
            packageChangeReason = if (discrepancy) com.verto.app.feature.shipment.domain.model.LogisticsPackageChangeReason.CORRECTION else null,
            packageChangeNote = discrepancyNote.takeIf { discrepancy },
            openedPackageCount = command.openedPackageCount,
            damagedPackageCount = command.damagedPackageCount,
        )
        LogisticsValidation.validateConfirmedCustodyHandoff(handoff)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            type = LogisticsEventType.HANDOFF_RECORDED,
            occurredAt = command.receivedAt,
            recordedAt = clock.now(),
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = buildMap {
                command.sourceId?.let { put("sourceId", it) }
                command.milestoneId?.let { put("milestoneId", it) }
                put("fromHolderType", command.fromHolderType.name)
                command.fromHolderId?.let { put("fromHolderId", it) }
                put("toHolderType", command.toHolderType.name)
                command.toHolderId?.let { put("toHolderId", it) }
                put("toHolderName", toName)
                put("handoverPackageCount", handoverPackageCount.toString())
                put("receivedPackageCount", receivedPackageCount.toString())
                put("countDiscrepancy", discrepancy.toString())
                if (discrepancy) put("discrepancyNote", discrepancyNote)
                put("openedPackageCount", command.openedPackageCount.toString())
                put("damagedPackageCount", command.damagedPackageCount.toString())
            },
        )
        store.saveCustodyHandoff(handoff, event)
        return handoff
    }
}
