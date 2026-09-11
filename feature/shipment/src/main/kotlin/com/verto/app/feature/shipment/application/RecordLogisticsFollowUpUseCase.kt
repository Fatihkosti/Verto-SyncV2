package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.RecordLogisticsFollowUpCommand
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class RecordLogisticsFollowUpUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    suspend operator fun invoke(
        organizationId: String,
        command: RecordLogisticsFollowUpCommand,
    ) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.occurredAt >= 0L) { "occurredAt must be non-negative" }
        require(command.note.isNotBlank()) { "Follow-up note is required" }
        command.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            require(phone.all { it in '0'..'9' }) { "Follow-up phone must contain digits only" }
        }
        command.nextFollowUpAt?.let { require(it >= command.occurredAt) { "nextFollowUpAt cannot be in the past" } }

        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        if (store.isRequestProcessed(organizationId, command.requestId)) return
        require(aggregate.shipment.state != LogisticsShipmentState.CLOSED &&
            aggregate.shipment.state != LogisticsShipmentState.CANCELLED) {
            "Follow-up cannot be recorded for terminal shipment"
        }
        command.partnerId?.let { require(store.getPartner(organizationId, it) != null) { "Follow-up partner not found" } }
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = organizationId,
            shipmentId = command.shipmentId,
            type = LogisticsEventType.FOLLOW_UP_RECORDED,
            occurredAt = command.occurredAt,
            employeeId = aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = buildMap {
                command.partnerId?.let { put("partnerId", it) }
                command.phone?.let { put("phone", it) }
                put("note", command.note.trim())
                command.nextFollowUpAt?.let { put("nextFollowUpAt", it.toString()) }
            },
        )
        store.appendEvent(event)
    }
}
