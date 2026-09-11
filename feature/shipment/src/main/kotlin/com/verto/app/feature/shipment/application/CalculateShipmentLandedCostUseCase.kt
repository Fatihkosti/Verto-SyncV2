package com.verto.app.feature.shipment.application

import com.verto.app.feature.shipment.domain.model.LogisticsCostAllocation
import com.verto.app.feature.shipment.domain.model.LogisticsCostStatus
import com.verto.app.feature.shipment.domain.model.LogisticsShipmentAggregate
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Calculates Logistics V2 landed-cost allocation from the value of goods actually accepted.
 * Missing/damaged/rejected/quarantined quantities never enter the purchase-value basis.
 */
class CalculateShipmentLandedCostUseCase @Inject constructor() {
    operator fun invoke(aggregate: LogisticsShipmentAggregate): List<LogisticsCostAllocation> {
        val total = totalActualCost(aggregate)
        if (total.signum() == 0) return emptyList()

        val wholeTotal = total.setScale(0, RoundingMode.UNNECESSARY)
        val acceptedByLine = aggregate.receivingBatches
            .flatMap { it.lines }
            .groupBy { it.shipmentLineId }
            .mapValues { (_, lines) -> lines.sumOf { it.acceptedQuantity.toLong() } }

        val bases = aggregate.lines
            .asSequence()
            .mapNotNull { line ->
                val acceptedQuantity = acceptedByLine[line.id] ?: 0L
                if (acceptedQuantity <= 0L) return@mapNotNull null
                val purchaseBasis = line.basePurchaseUnitPrice.multiply(BigDecimal(acceptedQuantity))
                if (purchaseBasis.signum() <= 0) return@mapNotNull null
                LargestRemainderCostAllocator.Basis(line.id, purchaseBasis)
            }
            .toList()

        require(bases.isNotEmpty()) {
            "Actual logistics costs require a positive accepted purchase-value basis"
        }

        val exact = LargestRemainderCostAllocator.allocate(wholeTotal, bases)
        return exact.map { allocation ->
            LogisticsCostAllocation(
                id = "landed:${aggregate.shipment.id}:${allocation.shipmentLineId}",
                shipmentId = aggregate.shipment.id,
                shipmentLineId = allocation.shipmentLineId,
                amount = allocation.amount,
            )
        }.also { allocations ->
            require(allocations.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.amount) }.compareTo(wholeTotal) == 0) {
                "Landed-cost allocations must equal actual logistics costs exactly"
            }
        }
    }

    fun totalActualCost(aggregate: LogisticsShipmentAggregate): BigDecimal {
        return aggregate.costs
            .asSequence()
            .filter { it.status == LogisticsCostStatus.ACTUAL }
            .fold(BigDecimal.ZERO) { acc, cost ->
                require(cost.baseCurrencyAmount.signum() > 0) { "ACTUAL baseCurrencyAmount must be positive" }
                acc.add(cost.baseCurrencyAmount)
            }
    }

    companion object {
        const val UNIT_PRICE_SCALE: Int = 8
    }
}
