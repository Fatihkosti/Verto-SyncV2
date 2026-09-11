package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsRecovery
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryEconomics
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryLine
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.port.LogisticsCashActor
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingContext
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingPort
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingRequest
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** Appends a recovery to a CLOSED shipment without mutating the original landed-cost settlement. */
class RecordShipmentRecoveryUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val identities: LogisticsIdentityPort,
    private val cash: LogisticsCashPostingPort,
    private val calculateRecoveryCost: CalculateRecoveryLandedCostUseCase,
) {
    data class LineInput(val shortageId: String, val recoveredQuantity: Int)

    data class CostMoney(val amount: BigDecimal, val currency: String, val exchangeRateSnapshot: BigDecimal)
    data class CostInput(
        val type: LogisticsCostType, val money: CostMoney, val description: String,
        val note: String = "", val confirmPaid: Boolean = false,
    )
    data class RecoveryAudit(val recoveredAt: Long, val employee: LogisticsAssigneeSnapshot, val requestId: String)
    data class Command(
        val shipmentId: String, val lines: List<LineInput>, val noteOrLocation: String,
        val costs: List<CostInput>, val audit: RecoveryAudit,
    )

    data class Result(
        val recovery: LogisticsRecovery,
        val lines: List<LogisticsRecoveryLine>,
        val costs: List<LogisticsCost>,
    )

    suspend operator fun invoke(organizationId: String, command: Command): Result {
        validateCommand(organizationId, command)
        val aggregate = store.getShipment(organizationId, command.shipmentId)
            ?: error("Logistics shipment not found")
        require(aggregate.shipment.state == LogisticsShipmentState.CLOSED) {
            "Missing goods can be recovered only after shipment close"
        }

        val existingRecovery = store.findRecoveryByRequest(organizationId, command.shipmentId, command.audit.requestId)
        if (existingRecovery == null) validateNewRecoveryLines(aggregate.shortages, command.lines)
        val recovery = existingRecovery ?: store.createRecovery(
                LogisticsRecovery(
                    organizationId = organizationId,
                    id = identities.newId(),
                    shipmentId = command.shipmentId,
                    recoveredAt = command.audit.recoveredAt,
                    employee = command.audit.employee,
                    note = command.noteOrLocation.trim(),
                    requestId = command.audit.requestId,
                ).also(LogisticsValidation::validateRecovery),
            )
        require(recovery.shipmentId == command.shipmentId) { "Recovery request belongs to another shipment" }
        require(recovery.note == command.noteOrLocation.trim()) { "Recovery retry note/location differs from persisted request" }

        val costs = persistRecoveryCosts(organizationId, recovery, command)
        val persistedLines = store.getRecoveryLines(organizationId, recovery.id)
        val valuedLines = if (persistedLines.isNotEmpty()) {
            requireReplayMatches(command, persistedLines)
            persistedLines
        } else {
            val shortageById = aggregate.shortages.associateBy { it.identity.id }
            val rawLines = command.lines.map { input ->
                val shortage = shortageById[input.shortageId] ?: error("Recovery shortage does not belong to shipment")
                require(shortage.quantity.remainingMissingQuantity > 0) { "Shortage is already fully recovered" }
                require(input.recoveredQuantity <= shortage.quantity.remainingMissingQuantity) {
                    "Recovered quantity cannot exceed remaining missing quantity"
                }
                LogisticsRecoveryLine(
                    organizationId = organizationId,
                    id = identities.newId(),
                    recoveryId = recovery.id,
                    shortageId = shortage.identity.id,
                    shipmentLineId = shortage.identity.shipmentLineId,
                    recoveredQuantity = input.recoveredQuantity,
                    economics = LogisticsRecoveryEconomics(shortage.quantity.basePurchaseUnitPriceSnapshot),
                ).also(LogisticsValidation::validateRecoveryLine)
            }
            calculateRecoveryCost(recovery.id, rawLines, costs)
        }
        val storedLines = if (persistedLines.isNotEmpty()) persistedLines
        else store.createRecoveryLines(organizationId, command.shipmentId, valuedLines)

        appendRecoveryEventIfNeeded(organizationId, recovery, storedLines, command)
        payConfirmedCosts(organizationId, recovery, costs, command)
        return Result(recovery, storedLines, store.getCosts(organizationId, command.shipmentId).filter { it.recoveryId == recovery.id })
    }

    private suspend fun persistRecoveryCosts(
        organizationId: String,
        recovery: LogisticsRecovery,
        command: Command,
    ): List<LogisticsCost> = command.costs.mapIndexed { index, input ->
        val requestId = "${command.audit.requestId}:recovery-cost:$index"
        store.findCostByRequest(organizationId, command.shipmentId, requestId) ?: run {
            val baseAmount = input.money.amount.multiply(input.money.exchangeRateSnapshot)
            val cost = LogisticsCost(
                id = identities.newId(),
                organizationId = organizationId,
                shipmentId = command.shipmentId,
                type = input.type,
                amount = input.money.amount,
                currency = input.money.currency.trim().uppercase(),
                exchangeRateSnapshot = input.money.exchangeRateSnapshot,
                baseCurrencyAmount = baseAmount,
                status = LogisticsCostStatus.ACTUAL,
                description = input.description.trim(),
                note = input.note.trim(),
                recoveryId = recovery.id,
                requestId = requestId,
            )
            LogisticsValidation.validateCost(cost)
            store.saveCost(
                cost,
                LogisticsEvent(
                    id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
                    type = LogisticsEventType.COST_RECORDED, occurredAt = command.audit.recoveredAt,
                    employeeId = command.audit.employee.employeeId, employeeNameSnapshot = command.audit.employee.employeeName,
                    requestId = requestId,
                    payload = mapOf("operation" to "RECOVERY_COST_RECORDED", "recoveryId" to recovery.id, "costId" to cost.id),
                ),
            )
            cost
        }
    }

    private suspend fun payConfirmedCosts(
        organizationId: String,
        recovery: LogisticsRecovery,
        costs: List<LogisticsCost>,
        command: Command,
    ) {
        command.costs.forEachIndexed { index, input ->
            if (!input.confirmPaid) return@forEachIndexed
            val cost = costs[index]
            if (cost.paymentState == LogisticsCostPaymentState.PAID) return@forEachIndexed
            val wholeBase = cost.baseCurrencyAmount.setScale(0, RoundingMode.HALF_UP)
            require(wholeBase.signum() > 0) { "Rounded recovery cash amount must be positive" }
            val reference = "logistics:$organizationId:${command.shipmentId}:${cost.id}:expense"
            cash.postExpense(
                LogisticsCashPostingRequest(
                    context = LogisticsCashPostingContext(
                        organizationId = organizationId,
                        shipmentId = command.shipmentId,
                        costId = cost.id,
                        requestId = "${command.audit.requestId}:recovery-pay:$index",
                        description = cost.description.ifBlank { "Recovery ${recovery.id}" },
                        reference = reference,
                    ),
                    exactWholeBaseAmount = wholeBase,
                    actor = LogisticsCashActor(command.audit.employee.employeeId, command.audit.employee.employeeName, command.audit.recoveredAt),
                ),
            )
        }
    }

    private suspend fun appendRecoveryEventIfNeeded(
        organizationId: String,
        recovery: LogisticsRecovery,
        lines: List<LogisticsRecoveryLine>,
        command: Command,
    ) {
        if (store.isRequestProcessed(organizationId, command.audit.requestId)) return
        store.appendEvent(
            LogisticsEvent(
                id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
                type = LogisticsEventType.EXCEPTION_RECORDED, occurredAt = command.audit.recoveredAt,
                employeeId = command.audit.employee.employeeId, employeeNameSnapshot = command.audit.employee.employeeName,
                requestId = command.audit.requestId,
                payload = mapOf(
                    "kind" to "MISSING_GOODS_RECOVERED",
                    "recoveryId" to recovery.id,
                    "lineCount" to lines.size.toString(),
                    "recoveredQuantity" to lines.sumOf { it.recoveredQuantity }.toString(),
                    "noteOrLocation" to command.noteOrLocation.trim(),
                ),
            ),
        )
    }

    private fun requireReplayMatches(command: Command, persisted: List<LogisticsRecoveryLine>) {
        val expected = command.lines.associate { it.shortageId to it.recoveredQuantity }
        val actual = persisted.associate { it.shortageId to it.recoveredQuantity }
        require(expected == actual) { "Recovery retry payload differs from the persisted request" }
    }

    private fun validateNewRecoveryLines(shortages: List<com.verto.app.feature.shipment.domain.model.LogisticsShortage>, lines: List<LineInput>) {
        val shortageById = shortages.associateBy { it.identity.id }
        lines.forEach { input ->
            val shortage = shortageById[input.shortageId] ?: error("Recovery shortage does not belong to shipment")
            require(shortage.quantity.remainingMissingQuantity > 0) { "Shortage is already fully recovered" }
            require(input.recoveredQuantity <= shortage.quantity.remainingMissingQuantity) {
                "Recovered quantity cannot exceed remaining missing quantity"
            }
        }
    }

    private fun validateCommand(organizationId: String, command: Command) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank()) { "shipmentId is required" }
        require(command.audit.requestId.isNotBlank()) { "requestId is required" }
        require(command.audit.recoveredAt >= 0L) { "recoveredAt must be non-negative" }
        require(command.noteOrLocation.isNotBlank()) { "Recovery note/location is required" }
        require(command.lines.isNotEmpty()) { "At least one shortage line must be recovered" }
        require(command.lines.map { it.shortageId }.toSet().size == command.lines.size) { "Duplicate recovery shortage line" }
        command.lines.forEach {
            require(it.shortageId.isNotBlank()) { "shortageId is required" }
            require(it.recoveredQuantity > 0) { "recoveredQuantity must be positive" }
        }
        command.costs.forEach {
            require(it.money.amount.signum() > 0) { "Recovery cost amount must be positive" }
            require(it.money.exchangeRateSnapshot.signum() > 0) { "Recovery exchange rate must be positive" }
            require(it.money.currency.isNotBlank()) { "Recovery cost currency is required" }
            require(it.description.isNotBlank()) { "Recovery cost description is required" }
        }
    }
}
