package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.CreateLogisticsShipmentUseCase
import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.LogisticsHeaderLocationCommand
import com.verto.app.feature.shipment.application.OpenLogisticsShipmentDraftResult
import com.verto.app.feature.shipment.application.OpenLogisticsShipmentDraftUseCase
import com.verto.app.feature.shipment.application.SaveLogisticsShipmentHeaderUseCase
import com.verto.app.feature.shipment.application.SaveLogisticsShipmentHeaderCommand
import com.verto.app.feature.shipment.application.SaveShipmentPurchasePlanUseCase
import com.verto.app.feature.shipment.application.SaveShipmentPurchasePlanCommand
import com.verto.app.feature.shipment.domain.model.CreateLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsShipment
import javax.inject.Inject

internal class LogisticsDraftPlanningWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val createShipment: CreateLogisticsShipmentUseCase,
    private val openDraftUseCase: OpenLogisticsShipmentDraftUseCase,
    private val saveHeaderUseCase: SaveLogisticsShipmentHeaderUseCase,
    private val savePurchasePlanUseCase: SaveShipmentPurchasePlanUseCase,
) {
    suspend fun openDraft(draftShipmentId: String?, requestId: String): OpenLogisticsShipmentDraftResult =
        openDraftUseCase(
            organizationId = organizationId(),
            draftShipmentId = draftShipmentId,
            createdAt = System.currentTimeMillis(),
            requestId = requestId,
        )

    suspend fun saveHeader(draft: LogisticsShipmentHeaderDraft): LogisticsShipment =
        saveHeaderUseCase(
            organizationId(),
            SaveLogisticsShipmentHeaderCommand(
                shipmentId = draft.shipmentId,
                origin = LogisticsHeaderLocationCommand(draft.originCountryName, draft.originCity),
                destination = LogisticsHeaderLocationCommand(draft.destinationCountryName, draft.destinationCity),
                employeeId = draft.employeeId,
                savedAt = System.currentTimeMillis(),
            ),
        )

    suspend fun savePurchasePlan(draft: LogisticsPlanningDraft): LogisticsShipment =
        savePurchasePlanUseCase(
            organizationId = organizationId(),
            command = SaveShipmentPurchasePlanCommand(draft.shipmentId, draft.sources, draft.lines),
        )

    suspend fun create(draft: CreateLogisticsShipmentDraft, requestId: String): LogisticsShipment =
        createShipment(
            CreateLogisticsShipmentCommand(
                organizationId = organizationId(),
                shipmentNumber = "",
                sourceLocation = draft.sourceLocation.trim(),
                destinationLocation = draft.destinationLocation.trim(),
                createdAt = System.currentTimeMillis(),
                requestId = requestId,
                notes = draft.notes.trim(),
            ),
        )

    private suspend fun organizationId(): String = readService.organizationId()
}
