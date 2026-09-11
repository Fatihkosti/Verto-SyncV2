package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsDocumentStoragePort
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsStoredDocument
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class SaveLogisticsDocumentUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val storage: LogisticsDocumentStoragePort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    data class Command(
        val organizationId: String,
        val shipmentId: String,
        val sourceId: String? = null,
        val milestoneId: String? = null,
        val legId: String? = null,
        val handoffId: String? = null,
        val costId: String? = null,
        val recoveryId: String? = null,
        val employeeId: String? = null,
        val employeeNameSnapshot: String? = null,
        val type: LogisticsDocumentType,
        val sourceUri: String = "",
        val stagedPrivateUri: String? = null,
        val displayName: String,
        val mimeType: String,
        val createdAt: Long,
        val requestId: String,
    )

    data class DraftCommand(
        val organizationId: String,
        val shipmentId: String,
        val draftId: String,
        val sourceUri: String,
        val displayName: String,
        val mimeType: String,
    )

    suspend fun stageDraft(command: DraftCommand): LogisticsStoredDocument {
        require(command.organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.draftId.matches(Regex("[A-Za-z0-9._-]{1,96}"))) { "invalid draftId" }
        require(command.sourceUri.isNotBlank()) { "sourceUri is required" }
        require(command.mimeType.isNotBlank()) { "mimeType is required" }
        val stored = storage.importPrivate(
            organizationId = command.organizationId,
            shipmentId = command.shipmentId,
            documentId = "draft-${command.draftId}",
            sourceUri = command.sourceUri,
            displayName = command.displayName,
            mimeType = command.mimeType,
        )
        require(stored.sizeBytes > 0L) { "Empty documents are not allowed" }
        require(storage.ownsPrivate(command.organizationId, command.shipmentId, stored.privateUri)) {
            "Staged document escaped shipment storage"
        }
        require(storage.exists(stored.privateUri)) { "Staged document copy was not created" }
        return stored
    }

    suspend fun removeDraft(
        organizationId: String,
        shipmentId: String,
        privateUri: String,
    ) {
        require(storage.ownsPrivate(organizationId, shipmentId, privateUri)) {
            "Draft document does not belong to this shipment"
        }
        storage.deleteDraft(organizationId, shipmentId, privateUri)
    }

    suspend operator fun invoke(command: Command): LogisticsDocument {
        require(command.organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        val hasSource = command.sourceUri.isNotBlank()
        val hasStaged = !command.stagedPrivateUri.isNullOrBlank()
        require(hasSource.xor(hasStaged)) { "exactly one document source is required" }
        require(command.mimeType.isNotBlank()) { "mimeType is required" }
        require(command.createdAt >= 0L) { "createdAt must be non-negative" }
        require(command.requestId.isNotBlank()) { "requestId is required" }

        val aggregate = store.getShipment(command.organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        require(!store.isRequestProcessed(command.organizationId, command.requestId)) {
            "requestId has already been processed"
        }
        require(aggregate.shipment.state != LogisticsShipmentState.CLOSED) { "Closed shipment cannot add documents" }
        require(aggregate.shipment.state != LogisticsShipmentState.CANCELLED) { "Cancelled shipment cannot add documents" }
        require(listOf(command.sourceId, command.milestoneId, command.legId, command.handoffId, command.costId, command.recoveryId).count { it != null } <= 1) {
            "Logistics document can have only one scope"
        }
        command.sourceId?.let { sourceId ->
            require(aggregate.sources.any { it.id == sourceId }) { "Document source does not belong to shipment" }
        }
        command.milestoneId?.let { milestoneId ->
            require(aggregate.milestones.any { it.id == milestoneId }) { "Document milestone does not belong to shipment" }
        }
        command.legId?.let { legId ->
            require(aggregate.legs.any { it.id == legId }) { "Document leg does not belong to shipment" }
        }
        command.handoffId?.let { handoffId ->
            require(aggregate.custodyHandoffs.any { it.id == handoffId }) { "Document handoff does not belong to shipment" }
        }
        command.costId?.let { costId ->
            require(aggregate.costs.any { it.id == costId }) { "Document cost does not belong to shipment" }
        }
        command.recoveryId?.let { recoveryId ->
            require(aggregate.recoveries.any { it.id == recoveryId }) { "Document recovery does not belong to shipment" }
        }

        val documentId = identities.newId()
        val stored = command.stagedPrivateUri?.takeIf { it.isNotBlank() }?.let { stagedPrivateUri ->
            storage.promoteDraft(
                organizationId = command.organizationId,
                shipmentId = command.shipmentId,
                documentId = documentId,
                draftPrivateUri = stagedPrivateUri,
                displayName = command.displayName,
                mimeType = command.mimeType,
            )
        } ?: storage.importPrivate(
            organizationId = command.organizationId,
            shipmentId = command.shipmentId,
            documentId = documentId,
            sourceUri = command.sourceUri,
            displayName = command.displayName,
            mimeType = command.mimeType,
        )
        require(stored.sizeBytes > 0L) { "Empty documents are not allowed" }
        require(storage.exists(stored.privateUri)) { "Private document copy was not created" }

        val document = LogisticsDocument(
            id = documentId,
            organizationId = command.organizationId,
            shipmentId = command.shipmentId,
            milestoneId = command.milestoneId,
            sourceId = command.sourceId,
            legId = command.legId,
            handoffId = command.handoffId,
            costId = command.costId,
            recoveryId = command.recoveryId,
            employeeId = command.employeeId ?: aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = command.employeeNameSnapshot ?: aggregate.shipment.assignee?.employeeName,
            type = command.type,
            displayName = stored.displayName,
            mimeType = stored.mimeType,
            sizeBytes = stored.sizeBytes,
            privateUri = stored.privateUri,
            sha256 = stored.sha256,
            createdAt = command.createdAt,
        )
        LogisticsValidation.validateDocument(document)
        val event = LogisticsEvent(
            id = identities.newId(),
            organizationId = command.organizationId,
            shipmentId = command.shipmentId,
            type = LogisticsEventType.DOCUMENT_ADDED,
            occurredAt = command.createdAt,
            recordedAt = clock.now(),
            employeeId = command.employeeId ?: aggregate.shipment.assignee?.employeeId,
            employeeNameSnapshot = command.employeeNameSnapshot ?: aggregate.shipment.assignee?.employeeName,
            requestId = command.requestId,
            payload = mapOf("documentId" to document.id, "documentType" to document.type.name),
        )
        try {
            store.saveDocument(document, event)
        } catch (cancellation: CancellationException) {
            runCatching { storage.deletePrivate(stored.privateUri) }
            throw cancellation
        } catch (error: Exception) {
            runCatching { storage.deletePrivate(stored.privateUri) }
            throw error
        }
        return document
    }
}

