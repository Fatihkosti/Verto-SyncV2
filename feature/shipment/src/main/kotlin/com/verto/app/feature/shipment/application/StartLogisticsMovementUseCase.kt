package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.StartLogisticsMovementCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject

/** The only command that turns a planned leg into IN_TRANSIT and starts transit timing. */
class StartLogisticsMovementUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    suspend operator fun invoke(organizationId: String, command: StartLogisticsMovementCommand): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.movedAt >= 0L) { "movedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment
        require(aggregate.shipment.state == LogisticsShipmentState.WAITING_DEPARTURE ||
            aggregate.shipment.state == LogisticsShipmentState.AT_STATION) {
            "Movement can start only while waiting for departure or at a station"
        }

        val leg = when (aggregate.shipment.state) {
            LogisticsShipmentState.WAITING_DEPARTURE -> aggregate.legs.single { it.sequence == 0 }
            else -> aggregate.legs
                .filter { it.status == LogisticsLegStatus.PLANNED && it.actualDepartureAt == null }
                .minByOrNull { it.sequence }
                ?: error("No planned leg is ready to move")
        }
        require(leg.status == LogisticsLegStatus.PLANNED && leg.actualDepartureAt == null) {
            "Movement requires a planned leg"
        }
        require(leg.carrierPartnerId.isNotBlank() && (leg.packageCount ?: 0) > 0 && (leg.weightKg?.signum() ?: 0) > 0) {
            "جهز الحركة وسجل شركة النقل والكراتين والوزن قبل البدء"
        }
        val custody = LogisticsCustodyResolver.currentForAllSources(aggregate).values
        require(custody.isNotEmpty() && custody.all {
            it.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER && it.holderId == leg.carrierPartnerId
        }) { "يجب تسليم كامل مسؤولية الشحنة للناقل الفعلي قبل بدء الحركة" }
        aggregate.shipment.startedAt?.let { require(command.movedAt >= it) { "Movement cannot precede journey start" } }
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.IN_TRANSIT)
        LogisticsLifecyclePolicy.requireLegTransition(leg.status, LogisticsLegStatus.IN_TRANSIT)

        val expectedMinutes = leg.expectedTransitMinutes ?: leg.expectedTransitDays?.times(24 * 60)
            ?: error("Expected transit duration is missing")
        require(expectedMinutes > 0) { "Expected transit duration must be positive" }
        val eta = safeAddMinutes(command.movedAt, expectedMinutes)
        val activeLeg = leg.copy(
            status = LogisticsLegStatus.IN_TRANSIT,
            actualDepartureAt = command.movedAt,
            plannedArrivalAt = eta,
        )
        LogisticsValidation.validateLeg(activeLeg)
        val departureMilestone = if (aggregate.shipment.state == LogisticsShipmentState.AT_STATION) {
            aggregate.milestones.singleOrNull { it.id == leg.fromMilestoneId }?.let { milestone ->
                require(milestone.arrivedAt != null) { "Station movement requires a recorded arrival" }
                require(milestone.handlingStatus == com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus.LOADED) {
                    "Station must be LOADED before movement starts"
                }
                milestone.copy(
                    departedAt = command.movedAt,
                    handlingStatus = com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus.DEPARTED,
                )
            }
        } else null
        val moving = aggregate.shipment.copy(
            state = LogisticsShipmentState.IN_TRANSIT,
            startedAt = aggregate.shipment.startedAt ?: command.movedAt,
        )
        store.saveOperationalUpdate(
            moving,
            departureMilestone?.let(::listOf).orEmpty(),
            listOf(activeLeg),
            LogisticsEvent(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = moving.id,
                type = LogisticsEventType.SHIPMENT_MOVEMENT_STARTED,
                occurredAt = command.movedAt,
                recordedAt = clock.now(),
                employeeId = moving.assignee?.employeeId,
                employeeNameSnapshot = moving.assignee?.employeeName,
                requestId = command.requestId,
                payload = mapOf("legId" to activeLeg.id, "carrierPartnerId" to activeLeg.carrierPartnerId),
            ),
        )
        return moving
    }

    private fun safeAddMinutes(start: Long, minutes: Int): Long {
        val millis = minutes.toLong() * 60_000L
        require(start <= Long.MAX_VALUE - millis) { "ETA timestamp overflow" }
        return start + millis
    }
}
