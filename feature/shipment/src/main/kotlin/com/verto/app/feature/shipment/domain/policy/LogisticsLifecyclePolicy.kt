package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState

object LogisticsLifecyclePolicy {
    private val allowedTransitions: Map<LogisticsShipmentState, Set<LogisticsShipmentState>> = mapOf(
        LogisticsShipmentState.DRAFT to setOf(LogisticsShipmentState.READY, LogisticsShipmentState.CANCELLED),
        LogisticsShipmentState.READY to setOf(
            LogisticsShipmentState.DRAFT,
            LogisticsShipmentState.WAITING_DEPARTURE,
            LogisticsShipmentState.CANCELLED,
        ),
        LogisticsShipmentState.WAITING_DEPARTURE to setOf(
            LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.CANCELLED,
        ),
        LogisticsShipmentState.IN_TRANSIT to setOf(
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.CANCELLED,
        ),
        LogisticsShipmentState.AT_STATION to setOf(
            LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.CUSTOMS,
            LogisticsShipmentState.RECEIVING,
            LogisticsShipmentState.CANCELLED,
        ),
        LogisticsShipmentState.CUSTOMS to setOf(
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.CANCELLED,
        ),
        LogisticsShipmentState.RECEIVING to setOf(
            LogisticsShipmentState.CLOSED,
            LogisticsShipmentState.CANCELLED,
        ),
        LogisticsShipmentState.CLOSED to emptySet(),
        LogisticsShipmentState.CANCELLED to emptySet(),
        // v228 persisted compatibility. Migration 55->56 rewrites these values.
        LogisticsShipmentState.ARRIVED to setOf(LogisticsShipmentState.RECEIVING, LogisticsShipmentState.CANCELLED),
        LogisticsShipmentState.PARTIAL to setOf(LogisticsShipmentState.RECEIVING, LogisticsShipmentState.CANCELLED),
        LogisticsShipmentState.RECEIVED to setOf(LogisticsShipmentState.CLOSED),
    )

    private val allowedLegTransitions: Map<LogisticsLegStatus, Set<LogisticsLegStatus>> = mapOf(
        LogisticsLegStatus.PLANNED to setOf(
            LogisticsLegStatus.IN_TRANSIT,
            LogisticsLegStatus.SUPERSEDED,
            LogisticsLegStatus.CANCELLED,
        ),
        LogisticsLegStatus.IN_TRANSIT to setOf(
            LogisticsLegStatus.ARRIVED,
            LogisticsLegStatus.SUPERSEDED,
            LogisticsLegStatus.CANCELLED,
        ),
        LogisticsLegStatus.ARRIVED to emptySet(),
        LogisticsLegStatus.SUPERSEDED to emptySet(),
        LogisticsLegStatus.CANCELLED to emptySet(),
    )

    fun canTransition(from: LogisticsShipmentState, to: LogisticsShipmentState): Boolean =
        to in allowedTransitions.getValue(from)

    fun requireTransition(from: LogisticsShipmentState, to: LogisticsShipmentState) {
        require(canTransition(from, to)) { "Logistics shipment transition $from -> $to is not allowed" }
    }

    fun nextStates(from: LogisticsShipmentState): Set<LogisticsShipmentState> = allowedTransitions.getValue(from)

    fun canTransitionLeg(from: LogisticsLegStatus, to: LogisticsLegStatus): Boolean =
        to in allowedLegTransitions.getValue(from)

    fun isLiveLeg(status: LogisticsLegStatus): Boolean =
        status != LogisticsLegStatus.SUPERSEDED && status != LogisticsLegStatus.CANCELLED

    fun requireCanSupersede(status: LogisticsLegStatus) {
        require(status == LogisticsLegStatus.PLANNED || status == LogisticsLegStatus.IN_TRANSIT) {
            "Only a planned or in-transit leg can be superseded"
        }
        requireLegTransition(status, LogisticsLegStatus.SUPERSEDED)
    }

    fun requireLegTransition(from: LogisticsLegStatus, to: LogisticsLegStatus) {
        require(canTransitionLeg(from, to)) { "Logistics leg transition $from -> $to is not allowed" }
    }
}
