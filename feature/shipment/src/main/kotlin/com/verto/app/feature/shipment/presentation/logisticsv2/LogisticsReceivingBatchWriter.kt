package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.LogisticsPresentationReadService
import com.verto.app.feature.shipment.application.ReconcileShipmentShortageUseCase
import com.verto.app.feature.shipment.application.RecordShipmentReceivingBatchUseCase
import com.verto.app.feature.shipment.application.StartShipmentReceivingUseCase
import com.verto.app.feature.shipment.domain.model.*
import java.util.UUID
import javax.inject.Inject

internal class LogisticsReceivingBatchWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val startReceiving: StartShipmentReceivingUseCase,
    private val recordReceivingBatch: RecordShipmentReceivingBatchUseCase,
    private val reconcileShortage: ReconcileShipmentShortageUseCase,
) {
    suspend fun ensureStarted(shipmentId: String, requestId: String) {
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        when (aggregate.shipment.state) {
            LogisticsShipmentState.RECEIVING -> Unit
            LogisticsShipmentState.AT_STATION,
            LogisticsShipmentState.ARRIVED,
            LogisticsShipmentState.PARTIAL -> startReceiving(
                organizationId,
                StartLogisticsReceivingCommand(shipmentId, System.currentTimeMillis(), requestId),
            )
            else -> error("الشحنة ليست جاهزة للاستلام")
        }
    }

    suspend fun receive(shipmentId: String, draft: LogisticsReceivingDraft, requestId: String) {
        val organizationId = organizationId()
        val actor = readService.actor()
        val batchId = stableRequestId("receiving-batch:$requestId")
        val lines = draft.lines.filter { it.receivedQuantity > 0 }.map { line ->
            LogisticsReceivingLine(
                id = stableRequestId("receiving-line:$requestId:${line.shipmentLineId}"),
                shipmentId = shipmentId,
                shipmentLineId = line.shipmentLineId,
                expectedQuantitySnapshot = line.expectedQuantity,
                receivedQuantity = line.receivedQuantity,
                acceptedQuantity = line.receivedQuantity,
                damagedQuantity = 0,
                rejectedQuantity = 0,
                quarantinedQuantity = 0,
            )
        }
        recordReceivingBatch(
            organizationId,
            RecordLogisticsReceivingBatchCommand(
                shipmentId = shipmentId,
                batchId = batchId,
                receivedAt = System.currentTimeMillis(),
                receivedByEmployeeId = actor.id,
                receivedByEmployeeNameSnapshot = actor.name,
                lines = lines,
                requestId = requestId,
            ),
        )
    }

    suspend fun reconcileShortages(shipmentId: String, draft: LogisticsReceivingDraft, requestId: String) {
        val missingLines = draft.lines.mapNotNull { line ->
            val missing = line.missingAfterBatch
            if (missing <= 0) return@mapNotNull null
            ReconcileShipmentShortageUseCase.MissingLine(
                shipmentLineId = line.shipmentLineId,
                missingQuantity = missing,
                note = "نقص مثبت عند الاستلام النهائي",
            )
        }
        reconcileShortage(
            organizationId(),
            ReconcileShipmentShortageUseCase.Command(
                shipmentId = shipmentId,
                detectedAt = System.currentTimeMillis(),
                lines = missingLines,
                requestId = requestId,
            ),
        )
    }

    private fun stableRequestId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

    private suspend fun organizationId(): String = readService.organizationId()
}
