package com.verto.app.feature.shipment.application

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Deterministically allocates an integral currency total by positive purchase-value bases.
 *
 * Floors every exact proportional share, then gives each residual unit to the greatest exact
 * remainder. Equal remainders are resolved by shipmentLineId ascending, so input order cannot
 * change the result.
 */
object LargestRemainderCostAllocator {
    data class Basis(
        val shipmentLineId: String,
        val purchaseBasis: BigDecimal,
    )

    data class Allocation(
        val shipmentLineId: String,
        val amount: BigDecimal,
    )

    fun allocate(totalCost: BigDecimal, bases: List<Basis>): List<Allocation> {
        require(totalCost.signum() >= 0) { "Allocatable cost must be non-negative" }
        require(totalCost.stripTrailingZeros().scale() <= 0) { "Allocatable cost must be an integral currency amount" }
        val totalUnits = totalCost.setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact()

        if (bases.isEmpty()) {
            require(totalUnits == BigInteger.ZERO) {
                "Positive logistics cost requires a positive accepted purchase-value basis"
            }
            return emptyList()
        }

        require(bases.map { it.shipmentLineId }.toSet().size == bases.size) {
            "Landed-cost allocation requires unique shipment line ids"
        }
        require(bases.all { it.shipmentLineId.isNotBlank() }) {
            "shipmentLineId is required for landed-cost allocation"
        }
        require(bases.all { it.purchaseBasis.signum() > 0 }) {
            "Landed-cost purchase bases must be positive"
        }

        val weights = exactIntegerWeights(bases)
        val totalWeight = weights.fold(BigInteger.ZERO) { acc, item -> acc.add(item.weight) }
        require(totalWeight.signum() > 0) { "Landed-cost allocation basis must be positive" }

        val rows = weights.map { item ->
            val quotientAndRemainder = totalUnits.multiply(item.weight).divideAndRemainder(totalWeight)
            RemainderRow(
                shipmentLineId = item.shipmentLineId,
                floor = quotientAndRemainder[0],
                remainderNumerator = quotientAndRemainder[1],
            )
        }

        val bonusIds = selectBonusIds(rows, totalUnits)
        return rows
            .sortedBy { it.shipmentLineId }
            .map { row ->
                val amount = row.floor.add(if (row.shipmentLineId in bonusIds) BigInteger.ONE else BigInteger.ZERO)
                Allocation(row.shipmentLineId, BigDecimal(amount))
            }
            .also { allocations ->
                val sum = allocations.fold(BigDecimal.ZERO) { acc, allocation -> acc.add(allocation.amount) }
                require(sum.compareTo(BigDecimal(totalUnits)) == 0) {
                    "Largest-remainder allocations must equal the allocatable cost exactly"
                }
            }
    }


    private fun selectBonusIds(rows: List<RemainderRow>, totalUnits: BigInteger): Set<String> {
        val floorTotal = rows.fold(BigInteger.ZERO) { acc, row -> acc.add(row.floor) }
        val residual = totalUnits.subtract(floorTotal)
        require(residual.signum() >= 0 && residual <= BigInteger.valueOf(rows.size.toLong())) {
            "Largest-remainder residual is outside the valid allocation range"
        }
        return rows
            .sortedWith(
                compareByDescending<RemainderRow> { it.remainderNumerator }
                    .thenBy { it.shipmentLineId },
            )
            .take(residual.intValueExact())
            .mapTo(mutableSetOf()) { it.shipmentLineId }
    }

    private fun exactIntegerWeights(bases: List<Basis>): List<IntegerWeight> {
        val normalized = bases.map { it.copy(purchaseBasis = it.purchaseBasis.stripTrailingZeros()) }
        val commonScale = normalized.maxOf { maxOf(it.purchaseBasis.scale(), 0) }
        return normalized.map { basis ->
            IntegerWeight(
                shipmentLineId = basis.shipmentLineId,
                weight = basis.purchaseBasis.movePointRight(commonScale).toBigIntegerExact(),
            )
        }
    }

    private data class IntegerWeight(val shipmentLineId: String, val weight: BigInteger)
    private data class RemainderRow(
        val shipmentLineId: String,
        val floor: BigInteger,
        val remainderNumerator: BigInteger,
    )
}
