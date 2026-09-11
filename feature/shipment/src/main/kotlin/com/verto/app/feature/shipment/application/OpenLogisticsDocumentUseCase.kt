package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.port.LogisticsDocumentStoragePort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import javax.inject.Inject

class OpenLogisticsDocumentUseCase @Inject constructor(
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
        val document = aggregate.documents.singleOrNull { it.id == documentId } ?: return false
        require(storage.exists(document.privateUri)) { "Local document file is missing" }
        return storage.open(document.privateUri, document.mimeType)
    }

    suspend fun openDraft(
        organizationId: String,
        shipmentId: String,
        privateUri: String,
        mimeType: String,
    ): Boolean {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(shipmentId.isNotBlank()) { "shipmentId is required" }
        require(privateUri.isNotBlank()) { "privateUri is required" }
        require(mimeType.isNotBlank()) { "mimeType is required" }
        require(storage.ownsPrivate(organizationId, shipmentId, privateUri)) {
            "Draft document does not belong to this shipment"
        }
        require(storage.exists(privateUri)) { "Local draft document file is missing" }
        return storage.open(privateUri, mimeType)
    }
}
