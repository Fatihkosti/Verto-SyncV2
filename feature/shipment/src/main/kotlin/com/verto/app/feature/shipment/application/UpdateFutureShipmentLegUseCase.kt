package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsPlanChangeScope
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevision
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionChange
import com.verto.app.feature.shipment.domain.model.LogisticsPlanRevisionKind
import com.verto.app.feature.shipment.domain.model.LogisticsV234Contract
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.UpdateFutureShipmentLegCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class UpdateFutureShipmentLegUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: UpdateFutureShipmentLegCommand,
    ): LogisticsShipmentLeg {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        val current = aggregate.legs.singleOrNull { it.id == command.leg.id }
            ?: error("Logistics leg not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return current
        if (command.leg == current) return current
        require(aggregate.shipment.state in setOf(
            LogisticsShipmentState.DRAFT,
            LogisticsShipmentState.READY,
            LogisticsShipmentState.WAITING_DEPARTURE,
            LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.CUSTOMS,
            LogisticsShipmentState.ARRIVED,
            LogisticsShipmentState.PARTIAL,
        )) { "Leg edit requires DRAFT, READY, or an active shipment" }
        require(command.leg.organizationId == organizationId && command.leg.shipmentId == command.shipmentId) {
            "Updated leg belongs to another shipment"
        }
        require(command.leg.sequence == current.sequence &&
            command.leg.fromMilestoneId == current.fromMilestoneId &&
            command.leg.toMilestoneId == current.toMilestoneId) {
            "Leg topology must be changed through route planning"
        }
        require(command.leg.status == current.status &&
            command.leg.actualDepartureAt == current.actualDepartureAt &&
            command.leg.actualArrivalAt == current.actualArrivalAt) {
            "Actual leg history cannot be overwritten"
        }
        require(current.status != LogisticsLegStatus.ARRIVED &&
            current.status != LogisticsLegStatus.CANCELLED &&
            current.status != LogisticsLegStatus.SUPERSEDED) {
            "Completed/superseded leg cannot be edited"
        }

        if (current.status == LogisticsLegStatus.IN_TRANSIT) {
            require(command.leg.mode == current.mode &&
                command.leg.carrierPartnerId == current.carrierPartnerId &&
                command.leg.plannedDepartureAt == current.plannedDepartureAt &&
                command.leg.plannedArrivalAt == current.plannedArrivalAt) {
                "Active leg allows descriptive vehicle/reference edits only"
            }
        } else {
            require(current.status == LogisticsLegStatus.PLANNED) { "Only an unstarted leg can change plan" }
            val partner = store.getPartner(organizationId, command.leg.carrierPartnerId)
                ?: error("Route carrier partner not found")
            require(partner.role == LogisticsPartnerRole.CARRIER || partner.role == LogisticsPartnerRole.FREIGHT_FORWARDER) {
                "Leg carrier must be CARRIER or FREIGHT_FORWARDER"
            }
            if (
                aggregate.shipment.state == LogisticsShipmentState.READY &&
                current.sequence == 0 &&
                command.leg.carrierPartnerId != current.carrierPartnerId &&
                aggregate.custodyHandoffs.isNotEmpty()
            ) {
                val positions = LogisticsCustodyResolver.currentForAllSources(aggregate)
                require(aggregate.sources.all { source ->
                    val position = positions.getValue(source.id)
                    position.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER &&
                        position.holderId == command.leg.carrierPartnerId
                }) {
                    "First carrier cannot change after custody handoff until every source is handed to the new carrier"
                }
            }
        }
        val futureApprovedEdit = current.status == LogisticsLegStatus.PLANNED && aggregate.shipment.currentPlanRevision >= 1
        if (futureApprovedEdit) require(command.reason.trim().isNotBlank()) { "سبب تعديل المرحلة المستقبلية مطلوب" }
        LogisticsValidation.validateLeg(command.leg)

        val affectedMilestones = if (current.status == LogisticsLegStatus.PLANNED) {
            val from = aggregate.milestones.single { it.id == current.fromMilestoneId }
            val to = aggregate.milestones.single { it.id == current.toMilestoneId }
            require(from.departedAt == null && to.arrivedAt == null) { "Historical milestone plan cannot be edited" }
            listOf(
                from.copy(plannedDepartureAt = command.leg.plannedDepartureAt),
                to.copy(plannedArrivalAt = command.leg.plannedArrivalAt),
            )
        } else {
            emptyList()
        }
        if (affectedMilestones.isNotEmpty()) {
            val replacements = affectedMilestones.associateBy { it.id }
            LogisticsValidation.validateMilestones(
                aggregate.milestones.map { replacements[it.id] ?: it },
                customsHostMilestoneId = aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId,
            )
        }

        val projectedLegs = aggregate.legs.map { if (it.id == command.leg.id) command.leg else it }
        val operationalShipment = aggregate.shipment.copy(
            transportMode = deriveOperationalTransportMode(projectedLegs),
        )
        validateOperationalRoute(operationalShipment, aggregate.milestones, projectedLegs, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = operationalShipment.id,
            type = LogisticsEventType.LEG_UPDATED,
            occurredAt = command.occurredAt,
            employeeId = command.changedByEmployeeId ?: operationalShipment.assignee?.employeeId,
            employeeNameSnapshot = command.changedByEmployeeName ?: operationalShipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "legId" to current.id,
                "mode" to command.leg.mode.name,
                "carrierPartnerId" to command.leg.carrierPartnerId,
                "reason" to command.reason.trim(),
            ),
        )
        if (futureApprovedEdit) {
            val changes = futureLegRevisionChanges(current, command.leg, identities)
            require(changes.isNotEmpty()) { "لا يوجد تغيير فعلي في المرحلة المستقبلية" }
            val revision = LogisticsPlanRevision(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = command.shipmentId,
                revisionNumber = aggregate.shipment.currentPlanRevision + 1,
                kind = LogisticsPlanRevisionKind.FUTURE_EDIT,
                reason = command.reason.trim(),
                changedByEmployeeId = command.changedByEmployeeId ?: operationalShipment.assignee?.employeeId,
                changedByEmployeeNameSnapshot = command.changedByEmployeeName ?: operationalShipment.assignee?.employeeName,
                recordedAt = command.occurredAt,
                requestId = command.requestId,
                changes = changes,
            )
            val revisionShipment = operationalShipment.copy(currentPlanRevision = revision.revisionNumber)
            LogisticsV234Contract.requireRevision(revision, aggregate.shipment.currentPlanRevision)
            LogisticsV234Contract.requireRevisionShipmentTransition(aggregate.shipment, revisionShipment, revision)
            store.saveFuturePlanLegRevision(revisionShipment, affectedMilestones, command.leg, event, revision)
        } else {
            store.saveOperationalUpdate(operationalShipment, affectedMilestones, listOf(command.leg), event)
        }
        return command.leg
    }
}

private fun futureLegRevisionChanges(
    current: LogisticsShipmentLeg,
    updated: LogisticsShipmentLeg,
    identities: LogisticsIdentityPort,
): List<LogisticsPlanRevisionChange> {
    val fields = listOf(
        "mode" to (current.mode to updated.mode),
        "carrierPartnerId" to (current.carrierPartnerId to updated.carrierPartnerId),
        "plannedDepartureAt" to (current.plannedDepartureAt to updated.plannedDepartureAt),
        "plannedArrivalAt" to (current.plannedArrivalAt to updated.plannedArrivalAt),
        "expectedTransitDays" to (current.expectedTransitDays to updated.expectedTransitDays),
        "expectedTransitMinutes" to (current.expectedTransitMinutes to updated.expectedTransitMinutes),
        "plannedCarrierPartnerId" to (current.plannedCarrierPartnerId to updated.plannedCarrierPartnerId),
        "plannedCarrierNameSnapshot" to (current.plannedCarrierNameSnapshot to updated.plannedCarrierNameSnapshot),
        "plannedRepresentativeNameSnapshot" to (current.plannedRepresentativeNameSnapshot to updated.plannedRepresentativeNameSnapshot),
        "plannedRepresentativePhoneSnapshot" to (current.plannedRepresentativePhoneSnapshot to updated.plannedRepresentativePhoneSnapshot),
        "plannedPackageCount" to (current.plannedPackageCount to updated.plannedPackageCount),
        "plannedWeightKg" to (current.plannedWeightKg to updated.plannedWeightKg),
        "plannedCost" to (current.plannedCost to updated.plannedCost),
        "plannedProof" to (current.plannedProof to updated.plannedProof),
        "roadVehicleNumber" to (current.roadVehicleNumber to updated.roadVehicleNumber),
        "roadDriverName" to (current.roadDriverName to updated.roadDriverName),
        "roadDriverPhone" to (current.roadDriverPhone to updated.roadDriverPhone),
        "seaContainerNumber" to (current.seaContainerNumber to updated.seaContainerNumber),
        "seaBillOfLading" to (current.seaBillOfLading to updated.seaBillOfLading),
        "seaVesselReference" to (current.seaVesselReference to updated.seaVesselReference),
        "airWaybillNumber" to (current.airWaybillNumber to updated.airWaybillNumber),
        "airFlightReference" to (current.airFlightReference to updated.airFlightReference),
        "note" to (current.note to updated.note),
    )
    return fields.mapNotNull { (key, values) ->
        val oldValue = values.first?.toString()
        val newValue = values.second?.toString()
        if (oldValue == newValue) null else LogisticsPlanRevisionChange(
            id = identities.newId(),
            scope = LogisticsPlanChangeScope.LEG,
            scopeId = current.id,
            fieldKey = key,
            previousValue = oldValue,
            newValue = newValue,
        )
    }
}

