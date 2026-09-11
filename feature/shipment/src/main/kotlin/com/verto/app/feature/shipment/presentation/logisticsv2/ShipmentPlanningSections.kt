package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsTransportMode
import java.util.UUID

/** v230 planning route helpers. Operational facts are intentionally absent from route construction. */
internal fun reconcileLegs(
    draft: LogisticsPlanningDraft,
    milestones: List<com.verto.app.feature.shipment.domain.model.LogisticsMilestone>,
    previous: List<LogisticsShipmentLeg>,
): List<LogisticsShipmentLeg> {
    val ordered = milestones.sortedBy { it.order }
    if (ordered.size < 2) return emptyList()
    val byPair = previous.associateBy { it.fromMilestoneId to it.toMilestoneId }
    return (0 until ordered.lastIndex).map { index ->
        val from = ordered[index]
        val to = ordered[index + 1]
        val reusable = byPair[from.id to to.id]
        (reusable ?: LogisticsShipmentLeg(
            id = "ui-leg:${UUID.randomUUID()}",
            organizationId = draft.organizationId,
            shipmentId = draft.shipmentId,
            sequence = index,
            fromMilestoneId = from.id,
            toMilestoneId = to.id,
            mode = LogisticsLegTransportMode.ROAD,
            carrierPartnerId = "",
        )).copy(
            organizationId = draft.organizationId,
            shipmentId = draft.shipmentId,
            sequence = index,
            fromMilestoneId = from.id,
            toMilestoneId = to.id,
            carrierPartnerId = "",
            packageCount = null,
            weightKg = null,
        ).forMode(reusable?.mode ?: LogisticsLegTransportMode.ROAD)
    }
}

internal fun LogisticsShipmentLeg.asV230PlanningLeg(mode: LogisticsLegTransportMode = this.mode): LogisticsShipmentLeg = copy(
    mode = mode,
    carrierPartnerId = "",
    status = com.verto.app.feature.shipment.domain.model.LogisticsLegStatus.PLANNED,
    plannedDepartureAt = null,
    plannedArrivalAt = null,
    actualDepartureAt = null,
    actualArrivalAt = null,
    roadVehicleNumber = null,
    roadDriverName = null,
    roadDriverPhone = null,
    seaContainerNumber = null,
    seaBillOfLading = null,
    seaVesselReference = null,
    airWaybillNumber = null,
    airFlightReference = null,
    representativeNameSnapshot = null,
    representativePhoneSnapshot = null,
    packageCount = null,
    weightKg = null,
    supersededAt = null,
    supersededByLegId = null,
)

internal fun LogisticsShipmentLeg.forMode(mode: LogisticsLegTransportMode): LogisticsShipmentLeg = when (mode) {
    LogisticsLegTransportMode.ROAD -> copy(
        mode = mode,
        seaContainerNumber = null,
        seaBillOfLading = null,
        seaVesselReference = null,
        airWaybillNumber = null,
        airFlightReference = null,
    )
    LogisticsLegTransportMode.SEA -> copy(
        mode = mode,
        roadVehicleNumber = null,
        roadDriverName = null,
        roadDriverPhone = null,
        airWaybillNumber = null,
        airFlightReference = null,
    )
    LogisticsLegTransportMode.AIR -> copy(
        mode = mode,
        roadVehicleNumber = null,
        roadDriverName = null,
        roadDriverPhone = null,
        seaContainerNumber = null,
        seaBillOfLading = null,
        seaVesselReference = null,
    )
    LogisticsLegTransportMode.UNSPECIFIED -> copy(
        mode = mode,
        carrierPartnerId = "",
        packageCount = null,
        weightKg = null,
        roadVehicleNumber = null,
        roadDriverName = null,
        roadDriverPhone = null,
        seaContainerNumber = null,
        seaBillOfLading = null,
        seaVesselReference = null,
        airWaybillNumber = null,
        airFlightReference = null,
    )
}

internal fun deriveShipmentMode(legs: List<LogisticsShipmentLeg>): LogisticsTransportMode? {
    if (legs.isEmpty()) return null
    val modes = legs.map { it.mode }.toSet()
    if (modes.size > 1 || LogisticsLegTransportMode.UNSPECIFIED in modes) return LogisticsTransportMode.MULTIMODAL
    return when (modes.single()) {
        LogisticsLegTransportMode.ROAD -> LogisticsTransportMode.ROAD
        LogisticsLegTransportMode.SEA -> LogisticsTransportMode.SEA
        LogisticsLegTransportMode.AIR -> LogisticsTransportMode.AIR
        LogisticsLegTransportMode.UNSPECIFIED -> LogisticsTransportMode.MULTIMODAL
    }
}
