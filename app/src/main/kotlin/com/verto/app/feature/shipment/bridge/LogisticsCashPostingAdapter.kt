package com.verto.app.feature.shipment.bridge

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostPaymentState
import com.verto.app.feature.shipment.domain.model.LogisticsEvent
import com.verto.app.feature.shipment.domain.model.LogisticsEventType
import com.verto.app.feature.shipment.domain.port.LogisticsCashAdjustmentRequest
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingContext
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingPort
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingRecord
import com.verto.app.feature.shipment.domain.port.LogisticsCashPostingRequest
import com.verto.app.feature.shipment.domain.validation.LogisticsValidation
import com.verto.app.utils.CashRegisterManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * App-owned bridge between Shipment and the existing cash register.
 * The two-phase state is deliberate: POSTING/ADJUSTING survives process death,
 * while cash movement + terminal PAID reconciliation is one Room transaction.
 */
class LogisticsCashPostingAdapter @Inject constructor(
    private val database: AppDatabase,
    private val cashRegister: CashRegisterManager,
    private val outbox: UnifiedOutboxWriter,
) : LogisticsCashPostingPort {
    private val logisticsDao get() = database.logisticsDao()
    private val cashDao get() = database.cashRegisterDao()
    private val json = Json { encodeDefaults = true }

    override suspend fun postExpense(request: LogisticsCashPostingRequest): LogisticsCost {
        validateExpenseRequest(request)
        val staged = database.withTransaction {
            val current = loadCost(request.context.organizationId, request.context.shipmentId, request.context.costId)
            val expected = roundedWhole(current.baseCurrencyAmount)
            require(expected.compareTo(request.exactWholeBaseAmount) == 0) {
                "Cash amount must equal the HALF_UP whole-unit value of the persisted cost"
            }
            val result = when (current.paymentState) {
                LogisticsCostPaymentState.PAID -> {
                    require(current.cashPostedBaseAmount?.compareTo(request.exactWholeBaseAmount) == 0) {
                        "Paid cost cash amount differs from requested confirmation"
                    }
                    current
                }
                LogisticsCostPaymentState.UNPAID -> {
                    val next = current.copy(
                        paymentState = LogisticsCostPaymentState.POSTING,
                        cashReference = request.context.reference,
                    )
                    LogisticsValidation.validateCost(next)
                    require(logisticsDao.updateCost(next.toEntityV2()) == 1) { "Failed to stage logistics cost payment" }
                    next
                }
                LogisticsCostPaymentState.POSTING -> {
                    require(current.cashReference == request.context.reference) {
                        "Interrupted payment has a different deterministic cash reference"
                    }
                    current
                }
                else -> error("Cost is not payable from ${current.paymentState}")
            }
            if (result.paymentState == LogisticsCostPaymentState.POSTING) {
                enqueueShipmentCommand(
                    request.context.organizationId, request.context.shipmentId,
                    "COST_PAYMENT_STAGE", request.context.reference,
                    mapOf("costId" to request.context.costId, "state" to result.paymentState.name),
                )
            }
            result
        }
        if (staged.paymentState == LogisticsCostPaymentState.PAID) return staged
        return completeExpense(request, staged.cashReference ?: request.context.reference)
    }

    override suspend fun postAdjustment(request: LogisticsCashAdjustmentRequest): LogisticsCost {
        validateAdjustmentRequest(request)
        val staged = database.withTransaction {
            val current = loadCost(request.context.organizationId, request.context.shipmentId, request.context.costId)
            val result = when (current.paymentState) {
                LogisticsCostPaymentState.PAID -> stageAdjustment(current, request)
                LogisticsCostPaymentState.ADJUSTING -> {
                    require(!current.cashReference.isNullOrBlank()) { "Interrupted adjustment is missing cash reference" }
                    require(roundedWhole(current.baseCurrencyAmount).compareTo(request.exactWholeBaseAmount) == 0) {
                        "Interrupted adjustment target differs from persisted target"
                    }
                    current
                }
                else -> error("Cost is not adjustable from ${current.paymentState}")
            }
            if (result.paymentState == LogisticsCostPaymentState.ADJUSTING) {
                enqueueShipmentCommand(
                    request.context.organizationId, request.context.shipmentId,
                    "COST_ADJUSTMENT_STAGE", request.context.reference,
                    mapOf("costId" to request.context.costId, "state" to result.paymentState.name),
                )
            }
            result
        }
        if (staged.paymentState == LogisticsCostPaymentState.PAID) return staged
        return completeAdjustment(request, staged)
    }

    override suspend fun reverseShipmentPayments(
        organizationId: String,
        shipmentId: String,
        requestId: String,
        actor: com.verto.app.feature.shipment.domain.port.LogisticsCashActor,
    ) {
        require(organizationId.isNotBlank() && shipmentId.isNotBlank() && requestId.isNotBlank()) {
            "Permanent-delete cash reversal identity is incomplete"
        }
        database.withTransaction {
            val costs = logisticsDao.getCosts(organizationId, shipmentId).map { it.toDomainV2() }
            require(costs.none { it.paymentState == LogisticsCostPaymentState.POSTING || it.paymentState == LogisticsCostPaymentState.ADJUSTING }) {
                "Resolve interrupted logistics cash operations before permanent deletion"
            }
            costs.filter { it.paymentState == LogisticsCostPaymentState.PAID }.forEach { cost ->
                val posted = requireNotNull(cost.cashPostedBaseAmount) { "Paid logistics cost is missing posted cash amount" }
                require(posted.signum() > 0 && posted.isWhole()) { "Paid logistics cash amount is invalid" }
                val reversalReference = "logistics:$organizationId:$shipmentId:${cost.id}:delete-reversal"
                val existing = movementByReference(reversalReference)
                if (existing == null) {
                    cashRegister.recordMovement(
                        CashMovementType.MANUAL_ADD,
                        posted.toExactCashDouble(),
                        reversalReference,
                        "عكس دفع تكلفة شحنة محذوفة",
                    )
                } else {
                    require(existing.movementType == CashMovementType.MANUAL_ADD) {
                        "Permanent-delete reversal reference belongs to an unexpected cash movement"
                    }
                    require(BigDecimal.valueOf(existing.amount).compareTo(posted) == 0) {
                        "Existing permanent-delete cash reversal amount does not match the paid cost"
                    }
                }
            }
        }
    }

    override suspend fun findByReference(
        organizationId: String,
        reference: String,
    ): LogisticsCashPostingRecord? {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(reference.startsWith("logistics:$organizationId:")) { "Cash reference does not belong to organization" }
        val movement = cashDao.getAllMovementsSync().firstOrNull { it.referenceId == reference } ?: return null
        return LogisticsCashPostingRecord(
            reference = movement.referenceId,
            signedBaseAmount = BigDecimal.valueOf(movement.amount),
            occurredAt = movement.createdAt,
        )
    }

    private suspend fun completeExpense(
        request: LogisticsCashPostingRequest,
        reference: String,
    ): LogisticsCost = database.withTransaction {
        val current = loadCost(request.context.organizationId, request.context.shipmentId, request.context.costId)
        if (current.paymentState == LogisticsCostPaymentState.PAID) return@withTransaction current
        require(current.paymentState == LogisticsCostPaymentState.POSTING) { "Cost is not staged for payment" }
        require(current.cashReference == reference) { "Staged payment reference changed" }

        val existing = movementByReference(reference)
        if (existing == null) {
            cashRegister.onExpense(request.exactWholeBaseAmount.toExactCashDouble(), reference)
        } else {
            require(existing.movementType == CashMovementType.EXPENSE) { "Cash reference belongs to a non-expense movement" }
            require(BigDecimal.valueOf(existing.amount).compareTo(request.exactWholeBaseAmount.negate()) == 0) {
                "Existing expense movement amount does not match logistics cost"
            }
        }

        val paid = current.copy(
            paymentState = LogisticsCostPaymentState.PAID,
            cashReference = reference,
            cashPostedBaseAmount = request.exactWholeBaseAmount,
            cashPostedAt = request.actor.occurredAt,
        )
        LogisticsValidation.validateCost(paid)
        require(logisticsDao.updateCost(paid.toEntityV2()) == 1) { "Failed to reconcile paid logistics cost" }
        insertAuditIfMissing(
            request.context, reference, LogisticsEventType.COST_RECORDED, request.actor,
            mapOf(
                "operation" to "COST_PAYMENT_CONFIRMED",
                "costId" to request.context.costId,
                "cashReference" to reference,
                "wholeBaseAmount" to request.exactWholeBaseAmount.toPlainString(),
                "description" to request.context.description,
            ),
        )
        enqueueShipmentCommand(
            request.context.organizationId, request.context.shipmentId,
            "COST_PAYMENT_CONFIRMED", reference,
            mapOf("costId" to request.context.costId, "state" to paid.paymentState.name, "cashReference" to reference),
        )
        paid
    }

    private suspend fun stageAdjustment(
        current: LogisticsCost,
        request: LogisticsCashAdjustmentRequest,
    ): LogisticsCost {
        val currentPosted = requireNotNull(current.cashPostedBaseAmount) { "Paid cost is missing posted amount" }
        require(currentPosted.compareTo(request.previousWholeBaseAmount) == 0) { "Paid amount changed; refresh cost before editing" }
        require(request.updatedCost.organizationId == current.organizationId && request.updatedCost.shipmentId == current.shipmentId && request.updatedCost.id == current.id) {
            "Adjusted cost identity cannot change"
        }
        val targetWhole = roundedWhole(request.updatedCost.baseCurrencyAmount)
        require(targetWhole.compareTo(request.exactWholeBaseAmount) == 0) { "Adjustment whole-unit amount mismatch" }

        if (sameCurrentFacts(current, request.updatedCost) && currentPosted.compareTo(targetWhole) == 0) return current

        val staged = request.updatedCost.copy(
            paymentState = LogisticsCostPaymentState.ADJUSTING,
            cashReference = request.context.reference,
            cashPostedBaseAmount = currentPosted,
            cashPostedAt = current.cashPostedAt,
            requestId = current.requestId,
        )
        LogisticsValidation.validateCost(staged)
        require(logisticsDao.updateCost(staged.toEntityV2()) == 1) { "Failed to stage logistics cost adjustment" }
        insertAuditIfMissing(
            request.context, "adjust-stage:${request.context.reference}", LogisticsEventType.CORRECTION_RECORDED, request.actor,
            mapOf(
                "operation" to "PAID_COST_ADJUSTMENT",
                "costId" to current.id,
                "cashReference" to request.context.reference,
                "oldAmount" to current.amount.toPlainString(),
                "oldCurrency" to current.currency,
                "oldRate" to current.exchangeRateSnapshot.toPlainString(),
                "oldBaseAmount" to current.baseCurrencyAmount.toPlainString(),
                "newAmount" to staged.amount.toPlainString(),
                "newCurrency" to staged.currency,
                "newRate" to staged.exchangeRateSnapshot.toPlainString(),
                "newBaseAmount" to staged.baseCurrencyAmount.toPlainString(),
                "oldPostedWhole" to currentPosted.toPlainString(),
                "newPostedWhole" to targetWhole.toPlainString(),
            ),
        )
        return staged
    }

    private suspend fun completeAdjustment(
        request: LogisticsCashAdjustmentRequest,
        staged: LogisticsCost,
    ): LogisticsCost = database.withTransaction {
        val current = loadCost(request.context.organizationId, request.context.shipmentId, request.context.costId)
        if (current.paymentState == LogisticsCostPaymentState.PAID) return@withTransaction current
        require(current.paymentState == LogisticsCostPaymentState.ADJUSTING) { "Cost is not staged for adjustment" }
        val reference = requireNotNull(current.cashReference) { "Adjustment cash reference is missing" }
        val oldWhole = requireNotNull(current.cashPostedBaseAmount) { "Adjustment previous cash amount is missing" }
        val newWhole = roundedWhole(current.baseCurrencyAmount)
        val delta = newWhole.subtract(oldWhole)

        if (delta.signum() != 0) {
            val existing = movementByReference(reference)
            val expectedSigned = if (delta.signum() > 0) delta.negate() else delta.abs()
            if (existing == null) {
                if (delta.signum() > 0) {
                    cashRegister.onExpense(delta.toExactCashDouble(), reference)
                } else {
                    cashRegister.recordMovement(CashMovementType.MANUAL_ADD, delta.abs().toExactCashDouble(), reference, "تصحيح تكلفة شحنة")
                }
            } else {
                val expectedType = if (delta.signum() > 0) CashMovementType.EXPENSE else CashMovementType.MANUAL_ADD
                require(existing.movementType == expectedType) { "Adjustment reference belongs to an unexpected cash movement" }
                require(BigDecimal.valueOf(existing.amount).compareTo(expectedSigned) == 0) {
                    "Existing adjustment movement amount does not match logistics delta"
                }
            }
        }

        val paid = current.copy(
            paymentState = LogisticsCostPaymentState.PAID,
            cashReference = reference,
            cashPostedBaseAmount = newWhole,
            cashPostedAt = request.actor.occurredAt,
        )
        LogisticsValidation.validateCost(paid)
        require(logisticsDao.updateCost(paid.toEntityV2()) == 1) { "Failed to finish logistics cost adjustment" }
        enqueueShipmentCommand(
            request.context.organizationId, request.context.shipmentId,
            "COST_ADJUSTMENT_CONFIRMED", reference,
            mapOf("costId" to request.context.costId, "state" to paid.paymentState.name, "cashReference" to reference),
        )
        paid
    }

    private suspend fun enqueueShipmentCommand(
        organizationId: String,
        shipmentId: String,
        command: String,
        semanticKey: String,
        payload: Map<String, String?>,
    ) {
        require(organizationId.isNotBlank() && shipmentId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        val mutationId = UUID.nameUUIDFromBytes(
            "v307|SHIPMENT|$organizationId|$shipmentId|$command|$semanticKey".toByteArray(Charsets.UTF_8)
        ).toString()
        outbox.enqueue(
            organizationId = organizationId,
            aggregateType = "SHIPMENT",
            aggregateId = shipmentId,
            operationType = "COMMAND",
            mutationId = mutationId,
            payload = payload + mapOf("command" to command, "shipmentId" to shipmentId),
        )
    }

    private suspend fun loadCost(organizationId: String, shipmentId: String, costId: String): LogisticsCost =
        logisticsDao.getCosts(organizationId, shipmentId)
            .singleOrNull { it.id == costId }
            ?.toDomainV2()
            ?: error("Logistics cost not found")

    private suspend fun insertAuditIfMissing(
        context: LogisticsCashPostingContext,
        stableRequestId: String,
        type: LogisticsEventType,
        actor: com.verto.app.feature.shipment.domain.port.LogisticsCashActor,
        payload: Map<String, String>,
    ) {
        if (logisticsDao.findEventByRequest(context.organizationId, context.shipmentId, stableRequestId, type.name) != null) return
        logisticsDao.insertEvent(
            LogisticsEvent(
                id = UUID.randomUUID().toString(), organizationId = context.organizationId, shipmentId = context.shipmentId,
                type = type, occurredAt = actor.occurredAt, employeeId = actor.actorId, employeeNameSnapshot = actor.actorName,
                requestId = stableRequestId, payload = payload,
            ).toEntityV2(json),
        )
    }

    private suspend fun movementByReference(reference: String) =
        cashDao.getAllMovementsSync().firstOrNull { it.referenceId == reference }

    private fun validateExpenseRequest(request: LogisticsCashPostingRequest) {
        validateCommon(request.context, request.exactWholeBaseAmount, request.actor.occurredAt)
    }

    private fun validateAdjustmentRequest(request: LogisticsCashAdjustmentRequest) {
        validateCommon(request.context, request.exactWholeBaseAmount, request.actor.occurredAt)
        require(request.previousWholeBaseAmount.signum() > 0 && request.previousWholeBaseAmount.isWhole()) { "previous cash amount must be a positive whole unit" }
    }

    private fun validateCommon(context: LogisticsCashPostingContext, amount: BigDecimal, occurredAt: Long) {
        require(context.organizationId.isNotBlank() && context.shipmentId.isNotBlank() && context.costId.isNotBlank() && context.requestId.isNotBlank()) { "Cash posting identity is incomplete" }
        require(context.description.isNotBlank()) { "description is required" }
        require(context.reference.startsWith("logistics:${context.organizationId}:${context.shipmentId}:${context.costId}:")) { "Cash reference is not deterministic for this logistics cost" }
        require(amount.signum() > 0 && amount.isWhole()) { "Cash amount must be a positive whole base-currency unit" }
        require(occurredAt >= 0L) { "occurredAt must be non-negative" }
    }

    private fun roundedWhole(value: BigDecimal): BigDecimal = value.setScale(0, RoundingMode.HALF_UP)

    private fun BigDecimal.isWhole(): Boolean = stripTrailingZeros().scale() <= 0

    private fun BigDecimal.toExactCashDouble(): Double {
        require(isWhole() && signum() > 0) { "Cash amount must be a positive whole unit" }
        val value = toDouble()
        require(value.isFinite() && BigDecimal.valueOf(value).compareTo(this) == 0) {
            "Cash amount cannot be represented exactly by the existing cash API"
        }
        return value
    }

    private fun sameCurrentFacts(left: LogisticsCost, right: LogisticsCost): Boolean =
        left.amount.compareTo(right.amount) == 0 &&
            left.currency == right.currency &&
            left.exchangeRateSnapshot.compareTo(right.exchangeRateSnapshot) == 0 &&
            left.baseCurrencyAmount.compareTo(right.baseCurrencyAmount) == 0 &&
            left.description == right.description &&
            left.note == right.note
}
