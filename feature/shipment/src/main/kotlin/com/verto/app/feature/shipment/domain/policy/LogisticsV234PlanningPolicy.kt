package com.verto.app.feature.shipment.domain.policy

import com.verto.app.feature.shipment.domain.model.LogisticsLegStatus
import com.verto.app.feature.shipment.domain.model.LogisticsMilestone
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentLeg
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsV234Contract

/** v234 policy shared by later wizard/approval/execution use-cases. */
object LogisticsV234PlanningPolicy {
    fun canEditFutureLeg(leg: LogisticsShipmentLeg): Boolean = LogisticsV234Contract.isFutureLeg(leg)

    fun requireFutureOnlyEdit(current: LogisticsShipmentAggregate, proposedLegs: List<LogisticsShipmentLeg>) {
        val currentById = current.legs.associateBy { it.id }
        proposedLegs.forEach { proposed ->
            val persisted = currentById[proposed.id] ?: return@forEach
            if (!canEditFutureLeg(persisted)) {
                require(proposed == persisted) { "Started/completed movement history cannot be edited" }
            }
        }
        val removed = current.legs.filterNot { old -> proposedLegs.any { it.id == old.id } }
        require(removed.all(::canEditFutureLeg)) { "Started/completed movements cannot be removed" }
    }

    fun requireStationHistoryUnchanged(
        current: List<LogisticsMilestone>,
        proposed: List<LogisticsMilestone>,
    ) {
        val proposedById = proposed.associateBy { it.id }
        current.filter { station ->
            station.arrivedAt != null || station.unloadedAt != null || station.loadedAt != null ||
                station.departedAt != null || station.handlingStatus.name != "PENDING"
        }.forEach { executed ->
            require(proposedById[executed.id] == executed) { "Executed station history cannot be edited" }
        }
    }
}

/** Cancellation semantics are intentionally separated from deletion semantics. */
object LogisticsV234CancellationPolicy {
    fun releasesInvoiceReservation(shipment: LogisticsShipment): Boolean =
        shipment.state == LogisticsShipmentState.CANCELLED && shipment.startedAt == null

    fun preservesExecutionHistory(shipment: LogisticsShipment): Boolean =
        shipment.startedAt != null || shipment.state in setOf(
            LogisticsShipmentState.WAITING_DEPARTURE,
            LogisticsShipmentState.IN_TRANSIT,
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.CUSTOMS,
            LogisticsShipmentState.RECEIVING,
            LogisticsShipmentState.CLOSED,
        )

    fun canPermanentlyDeleteInProduction(shipment: LogisticsShipment): Boolean =
        shipment.state == LogisticsShipmentState.DRAFT && shipment.startedAt == null

    fun requireCancellable(shipment: LogisticsShipment, hasInventoryPosting: Boolean) {
        LogisticsLifecyclePolicy.requireTransition(shipment.state, LogisticsShipmentState.CANCELLED)
        // v241: cancellation preserves posted inventory/cost/history; only hard deletion is forbidden.
        @Suppress("UNUSED_VARIABLE")
        val preservedInventoryHistory = hasInventoryPosting
    }
}
