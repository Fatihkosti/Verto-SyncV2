package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.model.LogisticsShortage
import com.verto.app.feature.shipment.domain.model.LogisticsShortageCompensation
import com.verto.app.feature.shipment.domain.model.LogisticsShortageQuantity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlement
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementAudit
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsShortageSettlementType
import com.verto.app.feature.shipment.domain.model.LogisticsShortageStatus
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.BigDecimal
import javax.inject.Inject

/** Append-only non-stock settlement for a shortage that remains after shipment close. */
class SettleShipmentShortageUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
) {
    data class CompensationInput(
        val amount: BigDecimal,
        val currency: String,
        val exchangeRateSnapshot: BigDecimal,
        val exchangeRateDate: Long? = null,
    )

    data class AuditInput(
        val occurredAt: Long,
        val employee: LogisticsAssigneeSnapshot?,
        val note: String = "",
        val requestId: String,
    )

    data class Command(
        val shipmentId: String,
        val shortageId: String,
        val type: LogisticsShortageSettlementType,
        val quantity: Int,
        val compensation: CompensationInput? = null,
        val audit: AuditInput,
    )

    suspend operator fun invoke(organizationId: String, command: Command): LogisticsShortageSettlement {
        validateIdentity(organizationId, command)
        store.findShortageSettlementByRequest(organizationId, command.shipmentId, command.audit.requestId)?.let { return it }
        val aggregate = store.getShipment(organizationId, command.shipmentId) ?: error("Logistics shipment not found")
        require(aggregate.shipment.state == LogisticsShipmentState.CLOSED) { "Shortage settlement is available only after shipment close" }
        val shortage = aggregate.shortages.singleOrNull { it.identity.id == command.shortageId } ?: error("Shortage not found")
        require(shortage.quantity.remainingMissingQuantity > 0) { "Shortage is already fully settled" }
        require(command.quantity <= shortage.quantity.remainingMissingQuantity) { "Settlement quantity cannot exceed remaining shortage" }

        val money = compensationMoney(command)
        val settlement = LogisticsShortageSettlement(
            identity = LogisticsShortageSettlementIdentity(identities.newId(), organizationId, command.shipmentId, command.shortageId),
            type = command.type,
            quantity = command.quantity,
            compensation = money,
            audit = LogisticsShortageSettlementAudit(
                occurredAt = command.audit.occurredAt,
                employee = command.audit.employee,
                note = command.audit.note.trim(),
                requestId = command.audit.requestId,
            ),
        )
        val updated = reduceShortage(shortage, command.quantity)
        val event = LogisticsEvent(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
            type = LogisticsEventType.EXCEPTION_RECORDED, occurredAt = command.audit.occurredAt,
            employeeId = command.audit.employee?.employeeId, employeeNameSnapshot = command.audit.employee?.employeeName,
            requestId = command.audit.requestId,
            payload = buildMap {
                put("kind", "SHORTAGE_${command.type.name}"); put("shortageId", command.shortageId)
                put("quantity", command.quantity.toString()); put("remaining", updated.quantity.remainingMissingQuantity.toString())
                money?.let { put("baseCurrencyAmount", it.baseCurrencyAmount.toPlainString()) }
            },
        )
        return store.saveShortageSettlement(settlement, updated, event)
    }

    private fun compensationMoney(command: Command): LogisticsShortageCompensation? {
        if (command.type == LogisticsShortageSettlementType.FINAL_LOSS) {
            require(command.compensation == null) { "Final-loss settlement cannot contain compensation money" }
            return null
        }
        val input = requireNotNull(command.compensation) { "Compensation money is required" }
        require(input.amount.signum() > 0 && input.exchangeRateSnapshot.signum() > 0) { "Compensation amount and exchange rate must be positive" }
        val currency = LogisticsCurrencyPolicy.normalizeIso4217(input.currency)
        LogisticsCurrencyPolicy.requireRate(currency, input.exchangeRateSnapshot, input.exchangeRateDate)
        return LogisticsShortageCompensation(input.amount, currency, input.exchangeRateSnapshot, input.amount.multiply(input.exchangeRateSnapshot))
    }

    private fun reduceShortage(shortage: LogisticsShortage, quantity: Int): LogisticsShortage {
        val remaining = shortage.quantity.remainingMissingQuantity - quantity
        return shortage.copy(
            quantity = LogisticsShortageQuantity(
                originalMissingQuantity = shortage.quantity.originalMissingQuantity,
                remainingMissingQuantity = remaining,
                basePurchaseUnitPriceSnapshot = shortage.quantity.basePurchaseUnitPriceSnapshot,
            ),
            status = when {
                remaining == 0 -> LogisticsShortageStatus.RECOVERED
                remaining < shortage.quantity.originalMissingQuantity -> LogisticsShortageStatus.PARTIALLY_RECOVERED
                else -> LogisticsShortageStatus.OPEN
            },
        )
    }

    private fun validateIdentity(organizationId: String, command: Command) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank() && command.shortageId.isNotBlank()) { "Shortage identity is incomplete" }
        require(command.quantity > 0) { "Settlement quantity must be positive" }
        require(command.audit.occurredAt >= 0L) { "occurredAt must be non-negative" }
        require(command.audit.requestId.isNotBlank()) { "requestId is required" }
    }
}
