package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsPayment
import com.verto.app.feature.shipment.domain.model.LogisticsPaymentState
import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingContext
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingPort
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingRequest
import com.verto.app.feature.shipment.domain.port.LogisticsClockPort
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.RoundingMode
import javax.inject.Inject

/** Confirms that an ACTUAL logistics cost is paid and posts exactly one cash expense. */
class ConfirmLogisticsPaidCostUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val cash: LogisticsCashPostingPort,
    private val identities: LogisticsIdentityPort,
    private val clock: LogisticsClockPort,
) {
    data class Command(
        val shipmentId: String,
        val costId: String,
        val requestId: String,
        val actor: LogisticsCashActor,
    )

    suspend operator fun invoke(organizationId: String, command: Command): LogisticsCost {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.costId.isNotBlank()) { "costId is required" }
        require(command.requestId.isNotBlank()) { "requestId is required" }
        require(command.actor.occurredAt >= 0L) { "occurredAt must be non-negative" }
        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        val cost = aggregate.costs.singleOrNull { it.id == command.costId } ?: error("Logistics cost not found")
        store.findPaymentByRequest(organizationId, command.requestId)?.let { existing ->
            require(existing.costId == command.costId && existing.shipmentId == command.shipmentId) {
                "Payment requestId is already used by another logistics payment"
            }
            return cost
        }
        require(cost.status == LogisticsCostStatus.ACTUAL) { "Only ACTUAL cost can be confirmed as paid" }
        require(cost.paymentState in setOf(LogisticsCostPaymentState.UNPAID, LogisticsCostPaymentState.POSTING, LogisticsCostPaymentState.PAID)) {
            "Cost is not in a payable state"
        }
        val wholeBaseAmount = cost.baseCurrencyAmount.setScale(0, RoundingMode.HALF_UP)
        require(wholeBaseAmount.signum() > 0) { "Rounded base cash amount must be positive" }
        if (cost.paymentState == LogisticsCostPaymentState.PAID) {
            require(cost.cashPostedBaseAmount?.compareTo(wholeBaseAmount) == 0) { "Paid cash amount does not match the current confirmed cost" }
        }
        val reference = cost.cashReference ?: expenseReference(organizationId, command.shipmentId, command.costId)
        val paidCost = cash.postExpense(
            LogisticsCashPostingRequest(
                context = LogisticsCashPostingContext(
                    organizationId, command.shipmentId, command.costId, command.requestId,
                    cost.description.ifBlank { cost.note }.ifBlank { cost.type.name }, reference,
                ),
                exactWholeBaseAmount = wholeBaseAmount,
                actor = command.actor,
            ),
        )
        val recordedAt = clock.now()
        store.savePayment(
            LogisticsPayment(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = command.shipmentId,
                costId = command.costId,
                state = LogisticsPaymentState.PAID,
                amount = wholeBaseAmount,
                currency = "SDG",
                paidAt = command.actor.occurredAt,
                reference = cost.reference,
                cashReference = reference,
                requestId = command.requestId,
                createdAt = recordedAt,
            ),
            LogisticsEvent(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = command.shipmentId,
                type = LogisticsEventType.PAYMENT_RECORDED,
                occurredAt = command.actor.occurredAt,
                recordedAt = recordedAt,
                employeeId = command.actor.actorId,
                employeeNameSnapshot = command.actor.actorName,
                requestId = command.requestId,
                payload = mapOf("costId" to command.costId, "cashReference" to reference),
            ),
        )
        return paidCost
    }

    internal companion object {
        fun expenseReference(organizationId: String, shipmentId: String, costId: String): String =
            "logistics:$organizationId:$shipmentId:$costId:expense"
    }
}
