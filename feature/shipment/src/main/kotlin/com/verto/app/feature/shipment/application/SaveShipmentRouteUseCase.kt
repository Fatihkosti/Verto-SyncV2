package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsRouteTransportPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.SaveShipmentRouteCommand
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

class SaveShipmentRouteUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: SaveShipmentRouteCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment

        requireRouteCanBeReplaced(aggregate)
        LogisticsValidation.validatePlanningRoute(aggregate.shipment, command.milestones, command.legs)
        require(command.milestones.all {
            it.arrivedAt == null && it.unloadedAt == null && it.loadedAt == null && it.departedAt == null &&
                it.handlingStatus == LogisticsMilestoneHandlingStatus.PENDING
        }) { "Planned route cannot contain actual milestone history" }
        require(command.legs.all {
            it.status == LogisticsLegStatus.PLANNED && it.actualDepartureAt == null && it.actualArrivalAt == null
        }) { "Planned route cannot contain started leg history" }
        val transportMode = LogisticsValidation.deriveTransportMode(command.legs)
        validateTransportIntent(command)
        val customsMilestoneId = command.milestones.singleOrNull { it.type == com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType.CUSTOMS }?.id
        val updatedShipment = aggregate.shipment.copy(
            transportMode = transportMode,
            customsMilestoneId = customsMilestoneId,
            routeTransportPlanKind = command.transportIntent?.kind ?: aggregate.shipment.routeTransportPlanKind,
            unifiedTransportMode = when (command.transportIntent?.kind) {
                LogisticsRouteTransportPlanKind.UNIFIED -> command.transportIntent.unifiedMode
                LogisticsRouteTransportPlanKind.MIXED -> null
                null -> aggregate.shipment.unifiedTransportMode
            },
        )
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = updatedShipment.id,
            type = LogisticsEventType.SHIPMENT_PLAN_UPDATED,
            occurredAt = command.occurredAt,
            employeeId = updatedShipment.assignee?.employeeId,
            employeeNameSnapshot = updatedShipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf(
                "milestoneCount" to command.milestones.size.toString(),
                "legCount" to command.legs.size.toString(),
                "transportMode" to transportMode.name,
                "routeKind" to (command.transportIntent?.kind?.name ?: updatedShipment.routeTransportPlanKind?.name ?: "LEGACY"),
            ),
        )
        store.saveRoute(updatedShipment, command.milestones, command.legs, event)
        return updatedShipment
    }

    private fun validateTransportIntent(command: SaveShipmentRouteCommand) {
        val intent = command.transportIntent ?: return
        when (intent.kind) {
            LogisticsRouteTransportPlanKind.UNIFIED -> {
                val mode = requireNotNull(intent.unifiedMode) { "Unified route requires a transport mode" }
                require(mode != LogisticsLegTransportMode.UNSPECIFIED) { "Unified route mode must be ROAD, SEA or AIR" }
                require(command.legs.all { it.mode == mode }) { "Every unified route leg must use the selected transport mode" }
            }
            LogisticsRouteTransportPlanKind.MIXED -> {
                require(intent.unifiedMode == null) { "Mixed route cannot persist a unified transport mode" }
                require(command.legs.all { it.mode != LogisticsLegTransportMode.UNSPECIFIED }) {
                    "Every mixed route movement requires an explicit transport mode"
                }
            }
        }
    }

    private fun requireRouteCanBeReplaced(aggregate: LogisticsShipmentAggregate) {
        require(aggregate.shipment.state == LogisticsShipmentState.DRAFT) {
            "Route can be replaced only before execution begins"
        }
        require(aggregate.shipment.startedAt == null) { "Started route cannot be destructively replaced" }
        require(aggregate.milestones.none {
            it.arrivedAt != null || it.unloadedAt != null || it.loadedAt != null || it.departedAt != null ||
                it.handlingStatus != LogisticsMilestoneHandlingStatus.PENDING
        }) { "Executed milestone history requires correction/unplanned-route workflow" }
        require(aggregate.legs.none {
            it.status != LogisticsLegStatus.PLANNED || it.actualDepartureAt != null || it.actualArrivalAt != null
        }) { "Executed leg history requires correction/unplanned-route workflow" }
    }
}
