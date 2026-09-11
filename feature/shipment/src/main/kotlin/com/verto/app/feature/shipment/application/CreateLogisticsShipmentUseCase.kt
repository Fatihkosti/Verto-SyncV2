package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CreateLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentNumberPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class CreateLogisticsShipmentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val shipmentNumbers: LogisticsShipmentNumberPort,
) {
    suspend operator fun invoke(command: CreateLogisticsShipmentCommand): LogisticsShipment {
        require(command.organizationId.isNotBlank()) { "organizationId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.createdAt >= 0L) { "createdAt must be non-negative" }
        require(!store.isRequestProcessed(command.organizationId, command.requestId)) {
            "requestId has already been processed"
        }
        val allocatedNumber = requireNotNull(shipmentNumbers.allocate(command.organizationId)) {
            "Unable to allocate shipment number"
        }
        require(allocatedNumber > 0) { "Allocated shipment number must be positive" }
        val shipmentNumber = allocatedNumber.toString()
        require(!store.shipmentNumberExists(command.organizationId, shipmentNumber)) {
            "Allocated shipment number already exists in this organization"
        }

        val shipment = LogisticsShipment(
            id = identities.newId(),
            organizationId = command.organizationId,
            shipmentNumber = shipmentNumber,
            sourceLocation = command.sourceLocation.trim(),
            destinationLocation = command.destinationLocation.trim(),
            state = LogisticsShipmentState.DRAFT,
            createdAt = command.createdAt,
            notes = command.notes.trim(),
        )
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = shipment.organizationId,
            shipmentId = shipment.id,
            type = LogisticsEventType.SHIPMENT_CREATED,
            occurredAt = command.createdAt,
            requestId = command.requestId,
        )
        store.createShipment(shipment, event)
        return shipment
    }
}
