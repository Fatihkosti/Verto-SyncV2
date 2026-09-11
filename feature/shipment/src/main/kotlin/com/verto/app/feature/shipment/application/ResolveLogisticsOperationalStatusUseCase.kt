package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCustodyHolderType
import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneHandlingStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestoneType
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalAction
import com.verto.app.feature.shipment.domain.model.LogisticsOperationalStatus
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsCustodyResolver
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.policy.LogisticsV240ExecutionPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class ResolveLogisticsOperationalStatusUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val clock: LogisticsClockPort,
    private val evaluateDelay: EvaluateLogisticsDelayUseCase = EvaluateLogisticsDelayUseCase(store, clock),
) {
    suspend fun delay(
        organizationId: String,
        shipmentId: String,
    ) = evaluateDelay(organizationId, shipmentId)

    suspend operator fun invoke(
        organizationId: String,
        shipmentId: String,
    ): LogisticsOperationalStatus {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        val aggregate = store.getShipment(organizationId, shipmentId)
            ?: error("Logistics shipment not found")

        val orderedMilestones = aggregate.milestones.sortedBy { it.order }
        val orderedLegs = aggregate.legs.sortedBy { it.sequence }
        val activeLeg = orderedLegs.singleOrNull { it.status == LogisticsLegStatus.IN_TRANSIT }
        val currentMilestone = currentMilestone(aggregate, orderedMilestones, activeLeg)
        val nextMovement = activeLeg ?: currentMilestone?.let { stop ->
            orderedLegs.firstOrNull { it.fromMilestoneId == stop.id && it.status == LogisticsLegStatus.PLANNED }
        } ?: if (aggregate.shipment.startedAt == null) orderedLegs.firstOrNull() else null
        val nextMilestone = nextMovement?.let { leg -> orderedMilestones.singleOrNull { it.id == leg.toMilestoneId } }

        val positions = LogisticsCustodyResolver.currentForAllSources(aggregate).values.toList()
        val uniquePositions = positions.distinctBy { Triple(it.holderType, it.holderId, it.holderName) }
        val custodianName = when {
            uniquePositions.isEmpty() -> "غير محدد"
            uniquePositions.size == 1 -> uniquePositions.single().holderName
            else -> uniquePositions.joinToString(" / ") { it.holderName }
        }
        val custodianPhone = uniquePositions.singleOrNull()?.let { position ->
            if (position.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER && position.holderId != null) {
                store.getPartner(organizationId, position.holderId)?.phone
            } else null
        }
        val nextCarrier = nextMovement?.let { store.getPartner(organizationId, it.carrierPartnerId) }
        val expectedArrivalAt = nextMovement?.plannedArrivalAt
        val delayMillis = calculateDelay(clock.now(), expectedArrivalAt, nextMovement?.actualArrivalAt)
        val hasInventoryPosting = store.hasInventoryPosting(organizationId, shipmentId)

        val actions = mutableSetOf<LogisticsOperationalAction>()
        val terminal = aggregate.shipment.state == LogisticsShipmentState.CLOSED ||
            aggregate.shipment.state == LogisticsShipmentState.CANCELLED
        if (!terminal) {
            if (!custodianPhone.isNullOrBlank()) actions += LogisticsOperationalAction.CALL
            actions += LogisticsOperationalAction.FOLLOW_UP
            if (nextMovement != null && nextMovement.status != LogisticsLegStatus.ARRIVED &&
                nextMovement.status != LogisticsLegStatus.CANCELLED && nextMovement.actualArrivalAt == null) {
                actions += LogisticsOperationalAction.UPDATE_ETA
            }
            if (activeLeg != null && activeLeg.actualArrivalAt == null) {
                actions += LogisticsOperationalAction.RECORD_ARRIVAL
            }
            when (currentMilestone?.handlingStatus) {
                LogisticsMilestoneHandlingStatus.ARRIVED -> actions += LogisticsOperationalAction.CONFIRM_UNLOAD
                LogisticsMilestoneHandlingStatus.UNLOADED -> {
                    val currentPositions = LogisticsCustodyResolver.currentForAllSources(aggregate).values
                    val customsHost = LogisticsV240ExecutionPolicy.isCustomsHost(aggregate, currentMilestone)
                    when {
                        customsHost && currentMilestone.customsStartedAt == null ->
                            actions += LogisticsOperationalAction.START_CUSTOMS
                        customsHost && currentMilestone.customsCompletedAt == null ->
                            actions += LogisticsOperationalAction.COMPLETE_CUSTOMS
                        currentMilestone.type == LogisticsMilestoneType.DESTINATION -> {
                            val warehouseReady = currentPositions.isNotEmpty() && currentPositions.all {
                                it.holderType == LogisticsCustodyHolderType.WAREHOUSE
                            }
                            if (!warehouseReady) actions += LogisticsOperationalAction.HANDOFF
                        }
                        else -> {
                            val outgoing = orderedLegs.singleOrNull { it.fromMilestoneId == currentMilestone.id }
                            if (outgoing != null) {
                                val outgoingReady = currentPositions.isNotEmpty() && currentPositions.all {
                                    it.holderType == LogisticsCustodyHolderType.LOGISTICS_PARTNER &&
                                        it.holderId == outgoing.carrierPartnerId
                                }
                                if (!outgoingReady) actions += LogisticsOperationalAction.HANDOFF
                                if (outgoingReady) actions += LogisticsOperationalAction.CONFIRM_LOAD
                            }
                        }
                    }
                }
                LogisticsMilestoneHandlingStatus.LOADED -> actions += LogisticsOperationalAction.RECORD_DEPARTURE
                else -> Unit
            }
            if (aggregate.shipment.state in setOf(
                    LogisticsShipmentState.READY,
                    LogisticsShipmentState.WAITING_DEPARTURE,
                    LogisticsShipmentState.IN_TRANSIT,
                    LogisticsShipmentState.AT_STATION,
                    LogisticsShipmentState.CUSTOMS,
                    LogisticsShipmentState.ARRIVED,
                    LogisticsShipmentState.PARTIAL,
                ) && orderedLegs.any { it.status == LogisticsLegStatus.PLANNED }
            ) {
                actions += LogisticsOperationalAction.EDIT_FUTURE_PLAN
            }
            if (LogisticsLifecyclePolicy.canTransition(aggregate.shipment.state, LogisticsShipmentState.CANCELLED) &&
                !hasInventoryPosting
            ) {
                actions += LogisticsOperationalAction.CANCEL_SHIPMENT
            }
            val destination = orderedMilestones.lastOrNull()
            val warehouseOwnsAll = positions.isNotEmpty() && positions.all {
                it.holderType == LogisticsCustodyHolderType.WAREHOUSE
            }
            if (aggregate.shipment.state in setOf(LogisticsShipmentState.AT_STATION, LogisticsShipmentState.ARRIVED, LogisticsShipmentState.PARTIAL) &&
                destination?.handlingStatus == LogisticsMilestoneHandlingStatus.UNLOADED && warehouseOwnsAll
            ) {
                actions += LogisticsOperationalAction.START_RECEIVING
            }
        }

        val currentLocation = when {
            activeLeg != null -> {
                val from = orderedMilestones.singleOrNull { it.id == activeLeg.fromMilestoneId }?.location
                    ?: aggregate.shipment.sourceLocation
                val to = orderedMilestones.singleOrNull { it.id == activeLeg.toMilestoneId }?.location
                    ?: aggregate.shipment.destinationLocation
                "$from → $to"
            }
            currentMilestone != null -> currentMilestone.location
            else -> orderedMilestones.firstOrNull()?.location ?: aggregate.shipment.sourceLocation
        }

        return LogisticsOperationalStatus(
            currentLocation = currentLocation,
            currentCustodianName = custodianName,
            currentCustodianPhone = custodianPhone,
            nextMilestoneName = nextMilestone?.location,
            nextCarrierName = nextCarrier?.name,
            nextCarrierPhone = nextCarrier?.phone,
            expectedArrivalAt = expectedArrivalAt,
            delayMillis = delayMillis,
            activeLegId = activeLeg?.id,
            availableActions = actions,
        )
    }

    private fun currentMilestone(
        aggregate: LogisticsShipmentAggregate,
        orderedMilestones: List<LogisticsMilestone>,
        activeLeg: LogisticsShipmentLeg?,
    ): LogisticsMilestone? {
        if (activeLeg != null) return null
        val reached = orderedMilestones.filter { it.arrivedAt != null }
        if (reached.isNotEmpty()) return reached.maxBy { it.order }
        return if (aggregate.shipment.startedAt == null) orderedMilestones.firstOrNull() else null
    }

    private fun calculateDelay(now: Long, planned: Long?, actual: Long?): Long {
        if (planned == null) return 0L
        if (actual != null) return (actual - planned).coerceAtLeast(0L)
        return (now - planned).coerceAtLeast(0L)
    }
}
