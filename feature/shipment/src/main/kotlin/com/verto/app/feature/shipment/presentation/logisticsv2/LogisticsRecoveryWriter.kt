package com.verto.app.feature.shipment.presentation.logisticsv2

import com.verto.app.feature.shipment.application.*
import com.verto.app.feature.shipment.domain.model.*
import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import javax.inject.Inject

internal class LogisticsRecoveryWriter @Inject constructor(
    private val readService: LogisticsPresentationReadService,
    private val recordRecovery: RecordShipmentRecoveryUseCase,
    private val postRecoveredStock: PostRecoveredShipmentStockUseCase,
    private val settleShortageUseCase: SettleShipmentShortageUseCase,
    private val recordLateCost: RecordLateShipmentCostUseCase,
    private val deleteShipment: DeleteLogisticsShipmentUseCase
) {
    suspend fun recoverMissingGoods(shipmentId: String, draft: LogisticsRecoveryDraft, requestId: String) {
        require(draft.isValid) { "Recovery data is incomplete" }
        val organizationId = organizationId()
        val aggregate = readService.aggregate(organizationId, shipmentId) ?: error("لم يتم العثور على الشحنة")
        val actor = readService.actor()
        val employee = LogisticsAssigneeSnapshot(
            employeeId = actor.id ?: aggregate.shipment.assignee?.employeeId ?: error("لا يوجد موظف منفذ للاسترجاع"),
            employeeName = actor.name ?: aggregate.shipment.assignee?.employeeName ?: error("لا يوجد اسم للموظف المنفذ"),
        )
        val result = recordRecovery(
            organizationId,
            RecordShipmentRecoveryUseCase.Command(
                shipmentId = shipmentId,
                lines = draft.lines.filter { it.isSelected }.map { line ->
                    RecordShipmentRecoveryUseCase.LineInput(line.shortageId, requireNotNull(line.quantity))
                },
                noteOrLocation = draft.noteOrLocation,
                costs = draft.costs.filterNot { it.isEmpty }.map { cost ->
                    RecordShipmentRecoveryUseCase.CostInput(
                        type = cost.type,
                        money = RecordShipmentRecoveryUseCase.CostMoney(
                            requireNotNull(cost.amountDecimal), cost.currency, requireNotNull(cost.rateDecimal),
                        ),
                        description = cost.description,
                        confirmPaid = cost.confirmPaid,
                    )
                },
                audit = RecordShipmentRecoveryUseCase.RecoveryAudit(System.currentTimeMillis(), employee, requestId),
            ),
        )
        postRecoveredStock(organizationId, shipmentId, result.recovery.id)
    }

    suspend fun settleShortage(shipmentId: String, draft: LogisticsShortageSettlementDraft, requestId: String) {
        require(draft.isValid) { "بيانات تسوية النقص غير صحيحة" }
        val actor = readService.actor()
        val amount = draft.compensationAmount.toBigDecimalOrNull()
        val rate = draft.exchangeRate.toBigDecimalOrNull()
        settleShortageUseCase(
            organizationId(),
            SettleShipmentShortageUseCase.Command(
                shipmentId = shipmentId,
                shortageId = draft.shortageId,
                type = draft.type,
                quantity = draft.quantity,
                compensation = if (draft.type == LogisticsShortageSettlementType.COMPENSATED) {
                    SettleShipmentShortageUseCase.CompensationInput(
                        amount = requireNotNull(amount),
                        currency = draft.currency,
                        exchangeRateSnapshot = requireNotNull(rate),
                        exchangeRateDate = if (draft.currency.uppercase() == "SDG") null else System.currentTimeMillis(),
                    )
                } else null,
                audit = SettleShipmentShortageUseCase.AuditInput(
                    occurredAt = System.currentTimeMillis(),
                    employee = LogisticsAssigneeSnapshot(actor.id, actor.name),
                    note = draft.note,
                    requestId = requestId,
                ),
            ),
        )
    }

    suspend fun addLateCost(shipmentId: String, draft: LogisticsCostDraft, requestId: String) {
        require(draft.isValid) { "بيانات التكلفة المتأخرة غير صحيحة" }
        val actor = readService.actor()
        recordLateCost(
            organizationId(),
            RecordLateShipmentCostUseCase.Command(
                shipmentId = shipmentId,
                type = draft.type,
                money = RecordLateShipmentCostUseCase.MoneyInput(
                    amount = requireNotNull(draft.amountDecimal),
                    currency = draft.currency,
                    exchangeRateSnapshot = requireNotNull(draft.exchangeRateDecimal),
                    exchangeRateDate = if (draft.currency.uppercase() == "SDG") null else System.currentTimeMillis(),
                ),
                reference = draft.reference.ifBlank { null },
                note = draft.note,
                audit = RecordLateShipmentCostUseCase.AuditInput(
                    recordedAt = System.currentTimeMillis(),
                    employee = LogisticsAssigneeSnapshot(actor.id, actor.name),
                    requestId = requestId,
                ),
            ),
        )
    }

    suspend fun permanentlyDeleteShipment(shipmentId: String, shipmentNumber: String?, reason: String, requestId: String) {
        val actor = readService.actor()
        deleteShipment(
            organizationId(),
            DeleteLogisticsShipmentUseCase.Command(
                shipmentId = shipmentId,
                confirmationShipmentNumber = shipmentNumber,
                reason = reason,
                requestId = requestId,
                actor = LogisticsCashActor(actor.id, actor.name, System.currentTimeMillis()),
            ),
        )
    }

    private suspend fun organizationId(): String = readService.organizationId()
}
