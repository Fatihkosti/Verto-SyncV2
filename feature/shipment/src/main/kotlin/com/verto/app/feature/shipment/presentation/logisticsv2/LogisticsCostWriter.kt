package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.RecordLogisticsCostCommand
import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import javax.inject.Inject

internal class LogisticsCostWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val recordCost: RecordLogisticsCostUseCase,
    private val confirmPaidCost: ConfirmLogisticsPaidCostUseCase,
    private val adjustPaidCost: AdjustLogisticsPaidCostUseCase,
    private val costProofCoordinator: LogisticsCostProofCoordinator,
) {
    suspend fun addCost(shipmentId: String, draft: LogisticsCostDraft, requestId: String): LogisticsCost {
        val amount = requireNotNull(draft.amountDecimal)
        val rate = requireNotNull(draft.exchangeRateDecimal)
        return recordCost(
            organizationId(),
            RecordLogisticsCostCommand(
                shipmentId = shipmentId,
                type = draft.type,
                amount = amount,
                currency = draft.currency,
                exchangeRateSnapshot = rate,
                exchangeRateDate = if (draft.currency.trim().uppercase() == "SDG") null else System.currentTimeMillis(),
                baseCurrencyAmount = amount.multiply(rate),
                status = draft.status,
                servicePartnerId = draft.servicePartnerId,
                legId = draft.legId,
                milestoneId = draft.milestoneId,
                sourceId = draft.sourceId,
                reference = draft.reference.ifBlank { null },
                note = draft.note,
                requestId = requestId,
            ),
        )
    }

    suspend fun confirmCostPayment(shipmentId: String, costId: String, requestId: String) {
        val actor = readService.actor()
        confirmPaidCost(
            organizationId(),
            ConfirmLogisticsPaidCostUseCase.Command(
                shipmentId = shipmentId,
                costId = costId,
                requestId = requestId,
                actor = LogisticsCashActor(actor.id, actor.name, System.currentTimeMillis()),
            ),
        )
    }

    suspend fun adjustPaidCost(shipmentId: String, costId: String, draft: LogisticsPaidCostEditDraft, requestId: String) {
        val actor = readService.actor()
        adjustPaidCost(
            organizationId(),
            AdjustLogisticsPaidCostUseCase.Command(
                shipmentId = shipmentId,
                costId = costId,
                edit = AdjustLogisticsPaidCostUseCase.Edit(
                    draft.amount.toBigDecimal(),
                    draft.currency,
                    draft.exchangeRate.toBigDecimal(),
                    draft.description,
                    draft.note,
                ),
                requestId = requestId,
                actor = LogisticsCashActor(actor.id, actor.name, System.currentTimeMillis()),
            ),
        )
    }

    suspend fun saveCostProof(shipmentId: String, costId: String, draft: LogisticsCostProofDraft, requestId: String) {
        val actor = readService.actor()
        costProofCoordinator.saveProof(
            LogisticsCostProofCoordinator.Command(
                organizationId = organizationId(),
                shipmentId = shipmentId,
                costId = costId,
                source = LogisticsCostProofCoordinator.Source(draft.sourceUri, draft.displayName, draft.mimeType),
                audit = LogisticsCostProofCoordinator.Audit(requestId, actor.id, actor.name, System.currentTimeMillis()),
            ),
        )
    }

    private suspend fun organizationId(): String = readService.organizationId()
}
