package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.ReopenShipmentPlanningCommand
import com.verto.app.feature.shipment.domain.policy.LogisticsLifecyclePolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class ReopenShipmentPlanningUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: ReopenShipmentPlanningCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.reopenedAt >= 0L) { "reopenedAt must be non-negative" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment
        require(aggregate.shipment.state == LogisticsShipmentState.READY) {
            "Only READY shipment can reopen planning"
        }
        require(aggregate.shipment.startedAt == null) { "Started shipment cannot reopen planning" }
        require(aggregate.custodyHandoffs.isEmpty()) {
            "Planning cannot be reopened after custody handoff; edit future plan instead"
        }
        require(aggregate.milestones.none {
            it.arrivedAt != null || it.unloadedAt != null || it.loadedAt != null || it.departedAt != null
        } && aggregate.legs.none {
            it.status != com.verto.app.feature.shipment.domain.model.LogisticsLegStatus.PLANNED ||
                it.actualDepartureAt != null || it.actualArrivalAt != null
        }) { "Executed route facts cannot reopen planning; use correction/unplanned-route workflow" }
        LogisticsLifecyclePolicy.requireTransition(aggregate.shipment.state, LogisticsShipmentState.DRAFT)
        val updated = aggregate.shipment.copy(state = LogisticsShipmentState.DRAFT)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = updated.id,
            type = LogisticsEventType.SHIPMENT_PLAN_UPDATED,
            occurredAt = command.reopenedAt,
            employeeId = updated.assignee?.employeeId,
            employeeNameSnapshot = updated.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf("action" to "REOPEN_PLANNING"),
        )
        store.saveShipmentState(updated, event)
        return updated
    }
}
