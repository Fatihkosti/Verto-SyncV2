package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.port.LogisticsDocumentStoragePort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import javax.inject.Inject

class DeleteLogisticsDocumentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val storage: LogisticsDocumentStoragePort,
) {
    suspend operator fun invoke(
        organizationId: String,
        shipmentId: String,
        documentId: String,
    ): Boolean {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(documentId.isNotBlank()) { "documentId is required" }
        val aggregate = store.getShipment(organizationId, shipmentId) ?: return false
        require(aggregate.shipment.state != LogisticsShipmentState.CLOSED &&
            aggregate.shipment.state != LogisticsShipmentState.CANCELLED) {
            "Terminal shipment documents are immutable"
        }
        val document = aggregate.documents.singleOrNull { it.id == documentId } ?: return false
        val deleted = store.deleteDocument(organizationId, shipmentId, documentId)
        // Session 307: the binary must survive while a durable attachment transfer may still be non-terminal.
        // Physical orphan cleanup is deliberately deferred to the later attachment/uploader reconciliation owner.
        return deleted
    }
}
