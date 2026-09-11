package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import com.verto.app.feature.shipment.domain.port.LogisticsCashAdjustmentRequest
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingContext
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** Edits a paid logistics cost with delta-only cash correction. */
class AdjustLogisticsPaidCostUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val cash: LogisticsCashPostingPort,
) {
    data class Edit(
        val amount: BigDecimal,
        val currency: String,
        val exchangeRateSnapshot: BigDecimal,
        val description: String,
        val note: String = "",
    )

    data class Command(
        val shipmentId: String,
        val costId: String,
        val edit: Edit,
        val requestId: String,
        val actor: LogisticsCashActor,
    )

    suspend operator fun invoke(organizationId: String, command: Command): LogisticsCost {
        validateCommand(organizationId, command)
        val current = store.getShipment(organizationId, command.shipmentId)?.costs?.singleOrNull { it.id == command.costId }
            ?: error("Logistics cost not found")
        require(current.status == LogisticsCostStatus.ACTUAL) { "Only ACTUAL paid cost can be adjusted" }
        require(current.paymentState in setOf(LogisticsCostPaymentState.PAID, LogisticsCostPaymentState.ADJUSTING)) {
            "Only PAID or interrupted ADJUSTING cost can be adjusted"
        }
        val target = if (current.paymentState == LogisticsCostPaymentState.ADJUSTING) current else editedCost(current, command.edit)
        LogisticsValidation.validateCost(target)
        val previousWhole = requireNotNull(current.cashPostedBaseAmount) { "Paid cost is missing posted cash amount" }
        val targetWhole = target.baseCurrencyAmount.setScale(0, RoundingMode.HALF_UP)
        require(targetWhole.signum() > 0) { "Rounded base cash amount must be positive" }
        if (current.paymentState == LogisticsCostPaymentState.PAID && financialFactsEqual(current, target) && previousWhole.compareTo(targetWhole) == 0) return current
        val reference = if (current.paymentState == LogisticsCostPaymentState.ADJUSTING) {
            requireNotNull(current.cashReference) { "Interrupted adjustment is missing deterministic cash reference" }
        } else adjustmentReference(organizationId, command.shipmentId, command.costId, command.requestId)
        return cash.postAdjustment(
            LogisticsCashAdjustmentRequest(
                context = LogisticsCashPostingContext(
                    organizationId, command.shipmentId, command.costId, command.requestId,
                    target.description.ifBlank { target.type.name }, reference,
                ),
                previousWholeBaseAmount = previousWhole,
                exactWholeBaseAmount = targetWhole,
                updatedCost = target,
                actor = command.actor,
            ),
        )
    }

    private fun validateCommand(organizationId: String, command: Command) {
        require(organizationId.isNotBlank() && command.shipmentId.isNotBlank() && command.costId.isNotBlank()) { "Cost identity is incomplete" }
        require(command.edit.amount.signum() > 0 && command.edit.exchangeRateSnapshot.signum() > 0) { "Cost amount and exchange rate must be positive" }
        require(command.edit.currency.isNotBlank() && command.requestId.isNotBlank()) { "currency and requestId are required" }
        require(command.actor.occurredAt >= 0L) { "occurredAt must be non-negative" }
    }

    private fun editedCost(current: LogisticsCost, edit: Edit): LogisticsCost = current.copy(
        amount = edit.amount,
        currency = edit.currency.trim().uppercase(),
        exchangeRateSnapshot = edit.exchangeRateSnapshot,
        baseCurrencyAmount = edit.amount.multiply(edit.exchangeRateSnapshot),
        description = edit.description.trim().ifBlank { current.description }.ifBlank { current.type.name },
        note = edit.note.trim(),
    )

    private fun financialFactsEqual(left: LogisticsCost, right: LogisticsCost): Boolean =
        left.amount.compareTo(right.amount) == 0 && left.currency == right.currency &&
            left.exchangeRateSnapshot.compareTo(right.exchangeRateSnapshot) == 0 &&
            left.baseCurrencyAmount.compareTo(right.baseCurrencyAmount) == 0 &&
            left.description == right.description && left.note == right.note

    internal companion object {
        fun adjustmentReference(organizationId: String, shipmentId: String, costId: String, requestId: String): String =
            "logistics:$organizationId:$shipmentId:$costId:adjust:$requestId"
    }
}
