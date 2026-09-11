package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.CalculateShipmentLandedCostUseCase
import com.verto.app.feature.shipment.application.CloseLogisticsShipmentUseCase
import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.SettleShipmentLandedCostUseCase
import com.verto.app.feature.shipment.domain.model.CloseLogisticsShipmentCommand
import javax.inject.Inject

/** Coordinates receiving/finalization without owning every low-level receiving use case. */
internal class LogisticsReceivingWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val batchWriter: LogisticsReceivingBatchWriter,
    private val recoveryWriter: LogisticsRecoveryWriter,
    private val calculateLandedCost: CalculateShipmentLandedCostUseCase,
    private val settleLandedCost: SettleShipmentLandedCostUseCase,
    private val closeShipment: CloseLogisticsShipmentUseCase
) {
    suspend fun ensureReceivingStarted(shipmentId: String, requestId: String) =
        batchWriter.ensureStarted(shipmentId, requestId)

    suspend fun finalizeReceiving(shipmentId: String, draft: LogisticsReceivingDraft, requestId: String) {
        require(draft.isValid) { "بيانات الاستلام النهائي غير صحيحة" }
        val missingLines = draft.lines.filter { it.missingAfterBatch > 0 }
        if (draft.receivedCompletely) {
            require(missingLines.isEmpty()) { "الاستلام الكامل يجب أن يغطي كل الكميات المتبقية" }
        } else {
            require(missingLines.isNotEmpty()) { "لا توجد كمية ناقصة لتسجيلها" }
        }
        if (draft.lines.any { it.receivedQuantity > 0 }) {
            batchWriter.receive(shipmentId, draft, "$requestId:receipt")
        }
        if (!draft.receivedCompletely) {
            batchWriter.reconcileShortages(shipmentId, draft, "$requestId:reconcile")
        }
        val organizationId = readService.organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val actualCost = calculateLandedCost.totalActualCost(aggregate)
        if (actualCost.signum() > 0 && aggregate.costAllocations.isEmpty()) {
            settleLandedCost(organizationId, shipmentId, "$requestId:landed-cost", System.currentTimeMillis())
        }
        closeShipment(
            organizationId,
            CloseLogisticsShipmentCommand(
                shipmentId = shipmentId,
                closedAt = System.currentTimeMillis(),
                noAdditionalCosts = actualCost.signum() == 0,
                requestId = "$requestId:close",
            ),
        )
    }

    suspend fun recoverMissingGoods(shipmentId: String, draft: LogisticsRecoveryDraft, requestId: String) =
        recoveryWriter.recoverMissingGoods(shipmentId, draft, requestId)

    suspend fun settleShortage(shipmentId: String, draft: LogisticsShortageSettlementDraft, requestId: String) =
        recoveryWriter.settleShortage(shipmentId, draft, requestId)

    suspend fun addLateCost(shipmentId: String, draft: LogisticsCostDraft, requestId: String) =
        recoveryWriter.addLateCost(shipmentId, draft, requestId)

    suspend fun permanentlyDeleteShipment(shipmentId: String, shipmentNumber: String?, reason: String, requestId: String) =
        recoveryWriter.permanentlyDeleteShipment(shipmentId, shipmentNumber, reason, requestId)
}
