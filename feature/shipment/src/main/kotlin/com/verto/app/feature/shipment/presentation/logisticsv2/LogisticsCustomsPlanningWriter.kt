package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import javax.inject.Inject

internal class LogisticsCustomsPlanningWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val saveCustomsPlan: SaveLogisticsCustomsPlanUseCase,
    private val documentWriter: LogisticsPartnerDocumentWriter,
) {
    suspend fun saveCustomsPlan(shipmentId: String, workspace: LogisticsRouteWorkspaceSnapshot): com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlan {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val staged = workspace.pendingDocuments.filter { it.milestoneId == V238_CUSTOMS_DOCUMENT_TARGET }
        require(staged.all { it.isReady }) { "أكمل تجهيز مستندات الجمارك أو احذف المستند المتعذر" }
        val inputs = aggregate.customsPlanDocuments.map { existing ->
            LogisticsCustomsPlanDocumentInput(
                id = existing.id, displayName = existing.displayName, mimeType = existing.mimeType,
                sizeBytes = existing.sizeBytes, privateUri = existing.privateUri, sha256 = existing.sha256,
            )
        } + staged.map { pending ->
            LogisticsCustomsPlanDocumentInput(
                id = pending.draftId,
                displayName = pending.displayName,
                mimeType = pending.mimeType,
                sizeBytes = requireNotNull(pending.sizeBytes),
                privateUri = requireNotNull(pending.privateUri),
                sha256 = requireNotNull(pending.sha256),
            )
        }
        return saveCustomsPlan(
            organizationId,
            SaveLogisticsCustomsPlanCommand(
                shipmentId = shipmentId,
                checkpointName = workspace.customsCheckpointName,
                afterStationId = workspace.customsAfterStationId,
                expectedDurationMinutes = requireNotNull(workspace.customsExpectedDurationMinutes),
                documents = inputs.distinctBy { it.id },
            ),
        )
    }

    suspend fun deleteCustomsPlanDocument(
        shipmentId: String,
        document: com.verto.app.feature.shipment.domain.model.LogisticsCustomsPlanDocument,
    ) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val plan = aggregate.customsPlan ?: error("لا يوجد تخطيط جمارك")
        require(document.shipmentId == shipmentId && document.customsPlanId == plan.id) { "المستند لا يتبع تخطيط الجمارك الحالي" }
        saveCustomsPlan(
            organizationId,
            SaveLogisticsCustomsPlanCommand(
                shipmentId = shipmentId,
                checkpointName = plan.checkpointName,
                afterStationId = plan.afterStationId,
                expectedDurationMinutes = plan.expectedDurationMinutes,
                documents = aggregate.customsPlanDocuments.filterNot { it.id == document.id }.map { existing ->
                    LogisticsCustomsPlanDocumentInput(
                        id = existing.id, displayName = existing.displayName, mimeType = existing.mimeType,
                        sizeBytes = existing.sizeBytes, privateUri = existing.privateUri, sha256 = existing.sha256,
                    )
                },
            ),
        )
        runCatching { documentWriter.removeStagedDocument(shipmentId, document.privateUri) }
    }


    private suspend fun organizationId(): String = readService.organizationId()
}
