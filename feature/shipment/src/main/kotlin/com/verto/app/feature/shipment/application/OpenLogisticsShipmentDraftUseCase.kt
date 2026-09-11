package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.CreateLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

data class OpenLogisticsShipmentDraftResult(
    val shipment: LogisticsShipment,
) {
    val shipmentId: String get() = shipment.id
    val shipmentNumber: String get() = shipment.shipmentNumber
}

class OpenLogisticsShipmentDraftUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val createShipment: CreateLogisticsShipmentUseCase,
) {
    suspend operator fun invoke(
        organizationId: String,
        draftShipmentId: String?,
        createdAt: Long,
        requestId: String,
    ): OpenLogisticsShipmentDraftResult {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(createdAt >= 0L) { "createdAt must be non-negative" }
        require(requestId.isNotBlank()) { "requestId is required" }

        val existingId = draftShipmentId?.trim().orEmpty()
        if (existingId.isNotBlank()) {
            val aggregate = store.getShipment(organizationId, existingId)
                ?: error("Logistics shipment draft not found")
            require(aggregate.shipment.state == LogisticsShipmentState.DRAFT) {
                "Only a DRAFT shipment can be opened in the creation flow"
            }
            return OpenLogisticsShipmentDraftResult(aggregate.shipment)
        }

        val shipment = createShipment(
            CreateLogisticsShipmentCommand(
                organizationId = organizationId,
                shipmentNumber = "",
                sourceLocation = "",
                destinationLocation = "",
                createdAt = createdAt,
                requestId = requestId,
            ),
        )
        return OpenLogisticsShipmentDraftResult(shipment)
    }
}
