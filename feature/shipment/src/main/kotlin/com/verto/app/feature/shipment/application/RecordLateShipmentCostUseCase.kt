package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsAssigneeSnapshot
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsCostType
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAdjustment
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAdjustmentIdentity
import com.verto.app.feature.shipment.domain.model.LogisticsLateCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentState
import com.verto.app.feature.shipment.domain.policy.LogisticsCurrencyPolicy
import com.verto.app.feature.shipment.domain.port.LogisticsIdentityPort
import com.verto.app.feature.shipment.domain.port.LogisticsInventoryCostPort
import com.verto.app.feature.shipment.domain.port.LogisticsShipmentStorePort
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** Adds a post-close logistics cost without reopening the shipment and reapplies receipt cost through Inventory. */
class RecordLateShipmentCostUseCase @Inject constructor(
    private val store: LogisticsShipmentStorePort,
    private val inventoryCost: LogisticsInventoryCostPort,
    private val identities: LogisticsIdentityPort,
) {
    data class MoneyInput(
        val amount: BigDecimal,
        val currency: String,
        val exchangeRateSnapshot: BigDecimal,
        val exchangeRateDate: Long? = null,
    )

    data class AuditInput(
        val recordedAt: Long,
        val employee: LogisticsAssigneeSnapshot?,
        val requestId: String,
    )

    data class Command(
        val shipmentId: String,
        val type: LogisticsCostType,
        val money: MoneyInput,
        val reference: String? = null,
        val note: String = "",
        val audit: AuditInput,
    )

    suspend operator fun invoke(organizationId: String, command: Command): LogisticsLateCostAdjustment {
        validateCommand(organizationId, command)
        store.findLateCostAdjustmentByRequest(organizationId, command.shipmentId, command.audit.requestId)?.let { existing ->
            applyFinalReceiptCosts(requireNotNull(store.getShipment(organizationId, command.shipmentId)))
            return existing
        }
        val before = requireNotNull(store.getShipment(organizationId, command.shipmentId)) { "Logistics shipment not found" }
        requireLateCostState(before)
        val currency = LogisticsCurrencyPolicy.normalizeIso4217(command.money.currency)
        LogisticsCurrencyPolicy.requireRate(currency, command.money.exchangeRateSnapshot, command.money.exchangeRateDate)
        validateCustomsCost(command, currency)
        val baseAmount = command.money.amount.multiply(command.money.exchangeRateSnapshot)
        val wholeBase = baseAmount.setScale(0, RoundingMode.UNNECESSARY)
        require(wholeBase.signum() > 0) { "Late cost base amount must be a positive whole SDG unit" }

        val cost = getOrCreateCost(organizationId, command, currency, baseAmount)
        val saved = saveAdjustment(organizationId, command, before, cost, wholeBase)
        applyFinalReceiptCosts(requireNotNull(store.getShipment(organizationId, command.shipmentId)))
        return saved
    }

    private fun requireLateCostState(aggregate: LogisticsShipmentAggregate) {
        require(aggregate.shipment.state == LogisticsShipmentState.CLOSED) { "Late cost requires a CLOSED shipment" }
        require(aggregate.receivingBatches.flatMap { it.lines }.any { it.acceptedQuantity > 0 }) {
            "Late cost requires accepted received goods"
        }
    }

    private fun validateCustomsCost(command: Command, currency: String) {
        if (command.type in setOf(LogisticsCostType.CUSTOMS_DUTY, LogisticsCostType.CLEARANCE)) {
            require(currency == LogisticsCurrencyPolicy.BASE_CURRENCY) { "Customs costs must be recorded in SDG" }
            require(command.note.isNotBlank()) { "Customs cost description is required" }
        }
    }

    private suspend fun getOrCreateCost(
        organizationId: String,
        command: Command,
        currency: String,
        baseAmount: BigDecimal,
    ): LogisticsCost {
        val requestId = "${command.audit.requestId}:cost"
        store.findCostByRequest(organizationId, command.shipmentId, requestId)?.let { return it }
        val cost = LogisticsCost(
            id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId, type = command.type,
            amount = command.money.amount, currency = currency, exchangeRateSnapshot = command.money.exchangeRateSnapshot,
            exchangeRateDate = command.money.exchangeRateDate, baseCurrencyAmount = baseAmount, status = LogisticsCostStatus.ACTUAL,
            reference = command.reference?.trim()?.takeIf(String::isNotBlank), note = command.note.trim(),
            description = command.note.trim().ifBlank { command.reference?.trim().orEmpty() }.ifBlank { command.type.name },
            paymentState = LogisticsCostPaymentState.UNPAID, requestId = requestId,
        )
        store.saveCost(
            cost,
            LogisticsEvent(
                id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
                type = LogisticsEventType.COST_RECORDED, occurredAt = command.audit.recordedAt,
                employeeId = command.audit.employee?.employeeId, employeeNameSnapshot = command.audit.employee?.employeeName,
                requestId = requestId, payload = mapOf("costId" to cost.id, "kind" to "LATE_COST", "paymentState" to "UNPAID"),
            ),
        )
        return cost
    }

    private suspend fun saveAdjustment(
        organizationId: String,
        command: Command,
        before: LogisticsShipmentAggregate,
        cost: LogisticsCost,
        wholeBase: BigDecimal,
    ): LogisticsLateCostAdjustment {
        val adjustmentId = identities.newId()
        val adjustment = LogisticsLateCostAdjustment(
            identity = LogisticsLateCostAdjustmentIdentity(adjustmentId, organizationId, command.shipmentId, cost.id),
            recordedAt = command.audit.recordedAt,
            employee = command.audit.employee,
            requestId = command.audit.requestId,
        )
        return store.saveLateCostAdjustment(
            adjustment = adjustment,
            allocations = allocateLateCost(before, adjustmentId, wholeBase),
            event = LogisticsEvent(
                id = identities.newId(), organizationId = organizationId, shipmentId = command.shipmentId,
                type = LogisticsEventType.COST_SETTLED, occurredAt = command.audit.recordedAt,
                employeeId = command.audit.employee?.employeeId, employeeNameSnapshot = command.audit.employee?.employeeName,
                requestId = command.audit.requestId,
                payload = mapOf(
                    "kind" to "LATE_COST_ADJUSTMENT", "costId" to cost.id,
                    "baseAmount" to wholeBase.toPlainString(), "paymentState" to "UNPAID",
                ),
            ),
        )
    }

    internal fun allocateLateCost(
        aggregate: LogisticsShipmentAggregate,
        adjustmentId: String,
        amount: BigDecimal,
    ): List<LogisticsLateCostAllocation> {
        val acceptedByLine = aggregate.receivingBatches.flatMap { it.lines }
            .groupBy { it.shipmentLineId }
            .mapValues { (_, rows) -> rows.sumOf { it.acceptedQuantity.toLong() } }
        val bases = aggregate.lines.mapNotNull { line ->
            val quantity = acceptedByLine[line.id] ?: 0L
            if (quantity <= 0L) return@mapNotNull null
            val basis = line.basePurchaseUnitPrice.multiply(BigDecimal(quantity))
            if (basis.signum() <= 0) return@mapNotNull null
            LargestRemainderCostAllocator.Basis(line.id, basis)
        }
        require(bases.isNotEmpty()) { "Late cost requires a positive received purchase-value basis" }
        return LargestRemainderCostAllocator.allocate(amount, bases).map { row ->
            LogisticsLateCostAllocation(
                id = "late:$adjustmentId:${row.shipmentLineId}", organizationId = aggregate.shipment.organizationId,
                adjustmentId = adjustmentId, shipmentId = aggregate.shipment.id, shipmentLineId = row.shipmentLineId, amount = row.amount,
            )
        }.also { rows ->
            require(rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) }.compareTo(amount) == 0) {
                "Late-cost allocations must equal the late cost exactly"
            }
        }
    }

    internal suspend fun applyFinalReceiptCosts(aggregate: LogisticsShipmentAggregate) {
        val lineById = aggregate.lines.associateBy { it.id }
        val closingByLine = aggregate.costAllocations.associate { it.shipmentLineId to it.amount }
        val lateByLine = aggregate.lateCostAllocations.groupBy { it.shipmentLineId }
            .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.amount) } }
        val acceptedRows = aggregate.receivingBatches.flatMap { batch -> batch.lines.map { batch.id to it } }
            .filter { (_, line) -> line.acceptedQuantity > 0 }
            .groupBy { (_, line) -> line.shipmentLineId }
        acceptedRows.forEach { (shipmentLineId, rows) ->
            val shipmentLine = lineById[shipmentLineId] ?: error("Receiving line references unknown shipment line")
            val quantity = rows.sumOf { (_, line) -> line.acceptedQuantity.toLong() }
            require(quantity > 0L) { "Accepted quantity must be positive" }
            val total = shipmentLine.basePurchaseUnitPrice.multiply(BigDecimal(quantity))
                .add(closingByLine[shipmentLineId] ?: BigDecimal.ZERO)
                .add(lateByLine[shipmentLineId] ?: BigDecimal.ZERO)
            val unitPrice = total.divide(BigDecimal(quantity), CalculateShipmentLandedCostUseCase.UNIT_PRICE_SCALE, RoundingMode.HALF_UP)
            rows.forEach { (batchId, line) ->
                inventoryCost.applyReceivingPostingUnitPrice(
                    postingId = "logistics-receipt:$batchId:${line.id}", shipmentId = aggregate.shipment.id, unitPrice = unitPrice,
                ).getOrThrow()
            }
        }
    }

    private fun validateCommand(organizationId: String, command: Command) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(command.shipmentId.isNotBlank() && command.audit.requestId.isNotBlank()) { "Late-cost identity is incomplete" }
        require(command.money.amount.signum() > 0 && command.money.exchangeRateSnapshot.signum() > 0) { "Late cost amount and rate must be positive" }
        require(command.money.currency.isNotBlank()) { "currency is required" }
        require(command.audit.recordedAt >= 0L) { "recordedAt must be non-negative" }
    }
}
