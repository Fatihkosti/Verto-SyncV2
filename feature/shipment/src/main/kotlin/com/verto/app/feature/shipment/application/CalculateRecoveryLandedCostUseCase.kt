package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCost
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsRecoveryLine
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/** Values only the late-found goods; settled shipment costs never enter this calculation. */
class CalculateRecoveryLandedCostUseCase @Inject constructor() {
    operator fun invoke(
        recoveryId: String,
        lines: List<LogisticsRecoveryLine>,
        recoveryCosts: List<LogisticsCost>,
    ): List<LogisticsRecoveryLine> {
        require(recoveryId.isNotBlank()) { "recoveryId is required" }
        require(lines.isNotEmpty()) { "At least one recovered line is required" }
        require(lines.all { it.recoveryId == recoveryId }) { "Recovery line belongs to another recovery" }
        require(lines.map { it.shipmentLineId }.toSet().size == lines.size) { "Duplicate recovered shipment line" }
        require(recoveryCosts.all { it.recoveryId == recoveryId }) { "Recovery cost belongs to another recovery" }
        require(recoveryCosts.all { it.status == LogisticsCostStatus.ACTUAL }) { "Recovery allocation accepts ACTUAL costs only" }

        val totalRecoveryCost = recoveryCosts.fold(BigDecimal.ZERO) { acc, cost ->
            require(cost.baseCurrencyAmount.signum() > 0) { "Recovery cost base amount must be positive" }
            acc.add(cost.baseCurrencyAmount)
        }.setScale(0, RoundingMode.UNNECESSARY)

        if (totalRecoveryCost.signum() == 0) {
            return lines.map { it.copy(economics = it.economics.copy(allocatedRecoveryCost = BigDecimal.ZERO)) }
        }

        val bases = lines.map { line ->
            val purchaseValue = line.economics.basePurchaseUnitPriceSnapshot
                .multiply(BigDecimal(line.recoveredQuantity))
            require(purchaseValue.signum() > 0) {
                "Positive recovery cost requires a positive recovered purchase-value basis"
            }
            LargestRemainderCostAllocator.Basis(line.shipmentLineId, purchaseValue)
        }
        val allocations = LargestRemainderCostAllocator.allocate(totalRecoveryCost, bases)
            .associateBy { it.shipmentLineId }

        return lines.map { line ->
            line.copy(
                economics = line.economics.copy(
                    allocatedRecoveryCost = requireNotNull(allocations[line.shipmentLineId]).amount,
                ),
            )
        }.also { valued ->
            val allocated = valued.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.economics.allocatedRecoveryCost) }
            require(allocated.compareTo(totalRecoveryCost) == 0) { "Recovery allocations must equal recovery costs exactly" }
        }
    }

    fun landedUnitPrice(line: LogisticsRecoveryLine): BigDecimal {
        val quantity = BigDecimal(line.recoveredQuantity)
        val purchaseValue = line.economics.basePurchaseUnitPriceSnapshot.multiply(quantity)
        return purchaseValue.add(line.economics.allocatedRecoveryCost)
            .divide(quantity, UNIT_PRICE_SCALE, RoundingMode.HALF_UP)
    }

    companion object {
        const val UNIT_PRICE_SCALE: Int = 8
    }
}
