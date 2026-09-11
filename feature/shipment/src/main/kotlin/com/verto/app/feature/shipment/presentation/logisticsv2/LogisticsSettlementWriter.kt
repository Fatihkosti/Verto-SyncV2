package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.CalculateShipmentLandedCostUseCase
import com.verto.app.feature.shipment.application.CloseLogisticsShipmentUseCase
import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.SettleShipmentLandedCostUseCase
import com.verto.app.feature.shipment.domain.model.CloseLogisticsShipmentCommand
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import javax.inject.Inject

internal class LogisticsSettlementWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val costWriter: LogisticsCostWriter,
    private val calculateLandedCost: CalculateShipmentLandedCostUseCase,
    private val settleLandedCost: SettleShipmentLandedCostUseCase,
    private val closeShipment: CloseLogisticsShipmentUseCase,
) {
    suspend fun addCost(shipmentId: String, draft: LogisticsCostDraft, requestId: String): LogisticsCost =
        costWriter.addCost(shipmentId, draft, requestId)

    suspend fun confirmCostPayment(shipmentId: String, costId: String, requestId: String) =
        costWriter.confirmCostPayment(shipmentId, costId, requestId)

    suspend fun adjustPaidCost(shipmentId: String, costId: String, draft: LogisticsPaidCostEditDraft, requestId: String) =
        costWriter.adjustPaidCost(shipmentId, costId, draft, requestId)

    suspend fun saveCostProof(shipmentId: String, costId: String, draft: LogisticsCostProofDraft, requestId: String) =
        costWriter.saveCostProof(shipmentId, costId, draft, requestId)

    suspend fun settle(shipmentId: String, requestId: String) {
        settleLandedCost(readService.organizationId(), shipmentId, requestId, System.currentTimeMillis())
    }

    suspend fun close(shipmentId: String, requestId: String) {
        val organizationId = readService.organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        closeShipment(
            organizationId,
            CloseLogisticsShipmentCommand(
                shipmentId = shipmentId,
                closedAt = System.currentTimeMillis(),
                requestId = requestId,
                noAdditionalCosts = calculateLandedCost.totalActualCost(aggregate).signum() == 0,
            ),
        )
    }
}
