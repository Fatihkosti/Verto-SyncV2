package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import java.util.UUID
import javax.inject.Inject


internal data class LogisticsDocumentWriteSource(
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val type: LogisticsDocumentType,
)

internal data class LogisticsDocumentWriteScope(
    val sourceId: String? = null,
    val milestoneId: String? = null,
    val legId: String? = null,
)

internal data class LogisticsDocumentWriteRequest(
    val shipmentId: String,
    val source: LogisticsDocumentWriteSource,
    val scope: LogisticsDocumentWriteScope,
    val requestId: String,
    val stagedPrivateUri: String? = null,
)

internal class LogisticsPartnerDocumentWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val upsertPartner: UpsertLogisticsPartnerUseCase,
    private val linkPartner: LinkLogisticsPartnerUseCase,
    private val saveDocument: SaveLogisticsDocumentUseCase,
    private val deleteDocument: DeleteLogisticsDocumentUseCase,
    private val openDocumentUseCase: OpenLogisticsDocumentUseCase
) {
    suspend fun saveAndLinkPartner(shipmentId: String, draft: LogisticsPartnerDraft): LogisticsPartner {
        val organizationId = organizationId()
        val partner = LogisticsPartner(
            id = UUID.randomUUID().toString(),
            organizationId = organizationId,
            name = draft.name,
            role = draft.role,
            phone = draft.phone.ifBlank { null },
            representativeName = draft.representativeName.ifBlank { null },
            representativePhone = draft.representativePhone.ifBlank { null },
            notes = draft.notes,
        )
        upsertPartner(partner)
        linkPartner(organizationId, shipmentId, partner.id, draft.role)
        return partner
    }

    suspend fun saveDocument(request: LogisticsDocumentWriteRequest) {
        saveDocument(
            SaveLogisticsDocumentUseCase.Command(
                organizationId = organizationId(),
                shipmentId = request.shipmentId,
                sourceId = request.scope.sourceId,
                milestoneId = request.scope.milestoneId,
                legId = request.scope.legId,
                type = request.source.type,
                sourceUri = request.source.sourceUri,
                stagedPrivateUri = request.stagedPrivateUri,
                displayName = request.source.displayName,
                mimeType = request.source.mimeType,
                createdAt = System.currentTimeMillis(),
                requestId = request.requestId,
            )
        )
    }

    suspend fun stageDocument(
        shipmentId: String,
        draftId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String,
    ) = saveDocument.stageDraft(
        SaveLogisticsDocumentUseCase.DraftCommand(
            organizationId = organizationId(),
            shipmentId = shipmentId,
            draftId = draftId,
            sourceUri = sourceUri,
            displayName = displayName,
            mimeType = mimeType,
        )
    )

    suspend fun removeStagedDocument(shipmentId: String, privateUri: String) {
        saveDocument.removeDraft(organizationId(), shipmentId, privateUri)
    }

    suspend fun openStagedDocument(shipmentId: String, privateUri: String, mimeType: String): Boolean =
        openDocumentUseCase.openDraft(organizationId(), shipmentId, privateUri, mimeType)

    suspend fun deleteDocument(shipmentId: String, documentId: String) {
        deleteDocument(organizationId(), shipmentId, documentId)
    }

    suspend fun openDocument(shipmentId: String, documentId: String): Boolean =
        openDocumentUseCase(organizationId(), shipmentId, documentId)

    private suspend fun organizationId(): String = readService.organizationId()
}
