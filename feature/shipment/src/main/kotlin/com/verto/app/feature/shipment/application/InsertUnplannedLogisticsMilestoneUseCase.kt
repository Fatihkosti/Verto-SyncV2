package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsLegTransportMode
import com.verto.app.feature.shipment.domain.model.LogisticsLocation
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsPartnerRole
import com.verto.app.feature.shipment.domain.model.LogisticsPlanKind
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsTransportMode
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject


internal fun validateOperationalRoute(
    shipment: com.verto.app.feature.shipment.domain.model.LogisticsShipment,
    milestones: List<LogisticsMilestone>,
    legs: List<LogisticsShipmentLeg>,
    customsHostMilestoneId: String? = null,
) {
    require(milestones.size >= 2) { "Operational route requires origin and destination milestones" }
    require(milestones.all { it.shipmentId == shipment.id }) { "Route milestone belongs to another shipment" }
    require(legs.all { it.shipmentId == shipment.id && it.organizationId == shipment.organizationId }) { "Route leg belongs to another shipment or organization" }
    require(milestones.map { it.id }.distinct().size == milestones.size) { "Duplicate milestone id" }
    require(legs.map { it.id }.distinct().size == legs.size) { "Duplicate leg id" }
    LogisticsValidation.validateMilestones(milestones, customsHostMilestoneId)
    val ordered = milestones.sortedBy { it.order }
    require(ordered.map { it.order } == ordered.indices.toList()) { "Operational milestone order must start at 0 and be contiguous" }
    require(ordered.first().type == LogisticsMilestoneType.ORIGIN && ordered.last().type == LogisticsMilestoneType.DESTINATION) { "Operational route must start at ORIGIN and end at DESTINATION" }
    require(ordered.first().location.trim() == shipment.sourceLocation.trim()) { "Origin milestone must remain anchored to shipment source" }
    require(ordered.last().location.trim() == shipment.destinationLocation.trim()) { "Destination milestone must remain anchored to shipment destination" }
    legs.forEach { leg ->
        LogisticsValidation.validateLeg(leg)
        if (leg.status == LogisticsLegStatus.SUPERSEDED) {
            require(leg.supersededAt != null && !leg.supersededByLegId.isNullOrBlank()) { "Superseded leg requires supersession metadata" }
        } else require(leg.supersededAt == null && leg.supersededByLegId == null) { "Only SUPERSEDED leg can carry supersession metadata" }
    }
    val live = legs.filter { LogisticsLifecyclePolicy.isLiveLeg(it.status) }.sortedBy { it.sequence }
    require(live.size == ordered.size - 1) { "Every operational milestone pair requires exactly one live leg" }
    require(live.map { it.sequence } == live.indices.toList()) { "Live leg sequence must start at 0 and be contiguous" }
    live.forEachIndexed { index, leg ->
        require(leg.fromMilestoneId == ordered[index].id && leg.toMilestoneId == ordered[index + 1].id) { "Live route must connect each operational milestone to the next milestone" }
    }
}

internal fun deriveOperationalTransportMode(legs: List<LogisticsShipmentLeg>): LogisticsTransportMode =
    LogisticsValidation.deriveTransportMode(legs.filter { LogisticsLifecyclePolicy.isLiveLeg(it.status) })

data class UnplannedContinuation(
    val carrierPartnerId: String? = null,
    val mode: LogisticsLegTransportMode? = null,
)

data class InsertUnplannedLogisticsMilestoneCommand(
    val shipmentId: String,
    val location: LogisticsLocation,
    val stationType: LogisticsMilestoneType,
    val reason: String,
    val continuation: UnplannedContinuation = UnplannedContinuation(),
    val occurredAt: Long,
    val requestId: String
)

/**
 * Splits the currently active leg without deleting any executed/planned history.
 * The original leg becomes SUPERSEDED and is retained as audit-visible planned history.
 */
class InsertUnplannedLogisticsMilestoneUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: InsertUnplannedLogisticsMilestoneCommand,
    ): LogisticsMilestone {
        validateCommand(organizationId, command)
        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        val milestoneId = stableId("milestone", command.requestId)
        if (store.isRequestProcessed(organizationId, command.requestId)) {
            return aggregate.milestones.singleOrNull { it.id == milestoneId }
                ?: error("Processed unplanned-station request is missing its milestone")
        }
        val reason = command.reason.trim()
        val context = buildContext(organizationId, aggregate, command, reason)
        val projection = projectRoute(aggregate, context)
        val event = routeDivergenceEvent(organizationId, command, reason, context, projection)
        store.saveOperationalUpdate(projection.shipment, projection.milestones, projection.legs, event)
        return context.inserted
    }

    private fun validateCommand(organizationId: String, command: InsertUnplannedLogisticsMilestoneCommand) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        require(command.reason.trim().isNotBlank()) { "Route divergence reason is required" }
        require(command.stationType == LogisticsMilestoneType.TRANSIT || command.stationType == LogisticsMilestoneType.CUSTOMS) {
            "Unplanned station must be TRANSIT or CUSTOMS"
        }
        LogisticsValidation.validateLocation(command.location)
    }

    private suspend fun buildContext(
        organizationId: String,
        aggregate: LogisticsShipmentAggregate,
        command: InsertUnplannedLogisticsMilestoneCommand,
        reason: String,
    ): DivergenceContext {
        require(aggregate.shipment.state == LogisticsShipmentState.IN_TRANSIT) { "Unplanned station insertion requires an IN_TRANSIT shipment" }
        val active = aggregate.legs.singleOrNull { it.status == LogisticsLegStatus.IN_TRANSIT }
            ?: error("Exactly one active leg is required for route divergence")
        require(active.actualDepartureAt != null && active.actualArrivalAt == null) { "Only a departed, not-yet-arrived leg can be superseded" }
        LogisticsLifecyclePolicy.requireCanSupersede(active.status)
        val destination = aggregate.milestones.singleOrNull { it.id == active.toMilestoneId }
            ?: error("Active leg destination milestone not found")
        require(destination.arrivedAt == null) { "Arrived milestone cannot be bypassed by route divergence" }
        val carrierId = command.continuation.carrierPartnerId?.trim().orEmpty().ifBlank { active.carrierPartnerId }
        val carrier = store.getPartner(organizationId, carrierId) ?: error("Continuation carrier partner not found")
        require(carrier.role == LogisticsPartnerRole.CARRIER || carrier.role == LogisticsPartnerRole.FREIGHT_FORWARDER) {
            "Continuation partner must be CARRIER or FREIGHT_FORWARDER"
        }
        val inserted = LogisticsMilestone(
            id = stableId("milestone", command.requestId), shipmentId = command.shipmentId, type = command.stationType,
            order = destination.order, location = command.location.placeName.trim(), note = reason,
            handlingStatus = LogisticsMilestoneHandlingStatus.PENDING, countryCode = command.location.countryCode,
            countryNameSnapshot = command.location.countryNameSnapshot.trim(), city = command.location.city.trim(),
            placeName = command.location.placeName.trim(), planKind = LogisticsPlanKind.UNPLANNED,
        )
        val replacement = active.copy(
            id = stableId("replacement-leg", command.requestId), toMilestoneId = inserted.id,
            status = LogisticsLegStatus.IN_TRANSIT, plannedArrivalAt = null, actualArrivalAt = null,
            note = reason, planKind = LogisticsPlanKind.UNPLANNED, supersededAt = null, supersededByLegId = null,
        )
        val continuation = LogisticsShipmentLeg(
            id = stableId("continuation-leg", command.requestId), organizationId = organizationId, shipmentId = command.shipmentId,
            sequence = active.sequence + 1, fromMilestoneId = inserted.id, toMilestoneId = destination.id,
            mode = command.continuation.mode ?: active.mode, carrierPartnerId = carrier.id, status = LogisticsLegStatus.PLANNED,
            plannedArrivalAt = active.plannedArrivalAt, note = reason, planKind = LogisticsPlanKind.UNPLANNED,
            expectedTransitDays = active.expectedTransitDays, packageCount = active.packageCount, weightKg = active.weightKg,
        )
        val superseded = active.copy(
            status = LogisticsLegStatus.SUPERSEDED, supersededAt = command.occurredAt, supersededByLegId = replacement.id,
        )
        return DivergenceContext(active, destination, inserted, replacement, continuation, superseded)
    }

    private fun projectRoute(aggregate: LogisticsShipmentAggregate, context: DivergenceContext): DivergenceProjection {
        val milestones = aggregate.milestones.map { milestone ->
            if (milestone.order >= context.destination.order) milestone.copy(order = milestone.order + 1) else milestone
        }.plus(context.inserted).sortedBy { it.order }
        val currentLive = aggregate.legs.filter { LogisticsLifecyclePolicy.isLiveLeg(it.status) }.sortedBy { it.sequence }
        val activeIndex = currentLive.indexOfFirst { it.id == context.active.id }
        require(activeIndex >= 0) { "Active leg is absent from the live route" }
        val live = buildList {
            addAll(currentLive.take(activeIndex)); add(context.replacement); add(context.continuation); addAll(currentLive.drop(activeIndex + 1))
        }.mapIndexed { index, leg -> leg.copy(sequence = index) }
        val archives = aggregate.legs
            .filterNot { LogisticsLifecyclePolicy.isLiveLeg(it.status) || it.id == context.active.id }
            .sortedBy { it.sequence }
            .mapIndexed { index, leg -> if (leg.sequence >= ARCHIVE_SEQUENCE_BASE) leg else leg.copy(sequence = ARCHIVE_SEQUENCE_BASE + index) }
        val nextArchive = maxOf(ARCHIVE_SEQUENCE_BASE, (archives.maxOfOrNull { it.sequence } ?: (ARCHIVE_SEQUENCE_BASE - 1)) + 1)
        val legs = live + archives + context.superseded.copy(sequence = nextArchive)
        val shipment = aggregate.shipment.copy(transportMode = deriveOperationalTransportMode(legs))
        validateOperationalRoute(shipment, milestones, legs, aggregate.customsPlan?.afterStationId ?: aggregate.shipment.customsMilestoneId)
        return DivergenceProjection(shipment, milestones, legs)
    }

    private fun routeDivergenceEvent(
        organizationId: String,
        command: InsertUnplannedLogisticsMilestoneCommand,
        reason: String,
        context: DivergenceContext,
        projection: DivergenceProjection,
    ) = LogisticsEvent(
        id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
        type = LogisticsEventType.EXCEPTION_RECORDED, occurredAt = command.occurredAt,
        employeeId = projection.shipment.assignee?.employeeId, employeeNameSnapshot = projection.shipment.assignee?.employeeName,
        requestId = command.requestId,
        payload = mapOf(
            "action" to "ROUTE_DIVERGENCE", "reason" to reason, "originalLegId" to context.active.id,
            "replacementLegId" to context.replacement.id, "continuationLegId" to context.continuation.id,
            "unplannedMilestoneId" to context.inserted.id, "originalDestinationMilestoneId" to context.destination.id,
            "continuationCarrierPartnerId" to context.continuation.carrierPartnerId,
            "continuationMode" to context.continuation.mode.name,
        ),
    )

    private fun stableId(kind: String, requestId: String): String = "v205-$kind-$requestId"

    private data class DivergenceContext(
        val active: LogisticsShipmentLeg,
        val destination: LogisticsMilestone,
        val inserted: LogisticsMilestone,
        val replacement: LogisticsShipmentLeg,
        val continuation: LogisticsShipmentLeg,
        val superseded: LogisticsShipmentLeg,
    )

    private data class DivergenceProjection(
        val shipment: com.verto.app.feature.shipment.domain.model.LogisticsShipment,
        val milestones: List<LogisticsMilestone>,
        val legs: List<LogisticsShipmentLeg>,
    )

    private companion object { const val ARCHIVE_SEQUENCE_BASE = 2_000_000 }
}
