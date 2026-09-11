package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsDocument
import com.verto.app.feature.shipment.domain.model.LogisticsDocumentType
import javax.inject.Inject

/** Persists a private proof document while keeping cost-document rules in the application layer. */
class LogisticsCostProofCoordinator @Inject constructor(
    private val saveDocument: SaveLogisticsDocumentUseCase,
) {
    data class Source(
        val sourceUri: String,
        val displayName: String,
        val mimeType: String,
    )

    data class Audit(
        val requestId: String,
        val employeeId: String,
        val employeeName: String,
        val occurredAt: Long,
    )

    data class Command(
        val organizationId: String,
        val shipmentId: String,
        val costId: String,
        val source: Source,
        val audit: Audit,
    )

    suspend fun saveProof(command: Command): LogisticsDocument = saveDocument(
        SaveLogisticsDocumentUseCase.Command(
            organizationId = command.organizationId,
            shipmentId = command.shipmentId,
            costId = command.costId,
            employeeId = command.audit.employeeId,
            employeeNameSnapshot = command.audit.employeeName,
            type = LogisticsDocumentType.OTHER,
            sourceUri = command.source.sourceUri,
            displayName = command.source.displayName,
            mimeType = command.source.mimeType,
            createdAt = command.audit.occurredAt,
            requestId = command.audit.requestId,
        ),
    )
}
