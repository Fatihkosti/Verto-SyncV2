package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CancelLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsV234CancellationPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class CancelLogisticsShipmentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: CancelLogisticsShipmentCommand,
    ): LogisticsShipment {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.cancelledAt >= 0L) { "cancelledAt must be non-negative" }
        val reason = command.reason.trim()
        require(reason.isNotBlank()) { "Cancellation reason is required" }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return aggregate.shipment
        LogisticsV234CancellationPolicy.requireCancellable(
            aggregate.shipment,
            hasInventoryPosting = store.hasInventoryPosting(organizationId, command.shipmentId),
        )
        if (aggregate.shipment.startedAt != null || aggregate.shipment.state in setOf(
                LogisticsShipmentState.WAITING_DEPARTURE,
                LogisticsShipmentState.IN_TRANSIT,
                LogisticsShipmentState.AT_STATION,
                LogisticsShipmentState.CUSTOMS,
                LogisticsShipmentState.RECEIVING,
                LogisticsShipmentState.ARRIVED,
                LogisticsShipmentState.PARTIAL,
            )
        ) {
            require(reason.isNotBlank()) { "Started shipment cancellation requires a reason" }
        }

        val cancelled = aggregate.shipment.copy(
            state = LogisticsShipmentState.CANCELLED,
            cancelledAt = command.cancelledAt,
            cancelReason = reason,
        )
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = cancelled.id,
            type = LogisticsEventType.SHIPMENT_CANCELLED,
            occurredAt = command.cancelledAt,
            employeeId = cancelled.assignee?.employeeId,
            employeeNameSnapshot = cancelled.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf("reason" to reason, "previousState" to aggregate.shipment.state.name),
        )
        store.saveShipmentState(cancelled, event)
        return cancelled
    }
}
