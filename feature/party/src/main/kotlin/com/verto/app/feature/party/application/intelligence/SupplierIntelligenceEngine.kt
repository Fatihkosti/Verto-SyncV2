package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.application.port.SupplierOrderEvidence
import java.math.BigInteger
import kotlin.math.max

internal enum class SupplierRecommendedAction {
    INSUFFICIENT_HISTORY,
    PREFER,
    ACCEPTABLE,
    REVIEW_QUALITY,
    REVIEW_FILL,
    REVIEW_DELIVERY,
    REVIEW_PRICE,
    REVIEW_PERFORMANCE,
}

internal data class SupplierScorePolicy(
    val minimumOrdersForRecommendation: Int = 3,
    val minimumOrdersForComparison: Int = 2,
    val qualityReviewBelowBps: Int = 9_000,
    val returnReviewAboveBps: Int = 500,
    val fillReviewBelowBps: Int = 8_500,
    val onTimeReviewBelowBps: Int = 8_000,
    val unfavorablePriceVarianceAboveBps: Int = 500,
    val preferredScoreAtLeastBps: Int = 8_500,
)

internal data class SupplierPerformanceSnapshot(
    val supplierId: String,
    val asOf: Long,
    val dataComplete: Boolean,
    val currencies: Set<String>,
    val orderCount: Int,
    val matureFillOrderCount: Int,
    val completedOrderCount: Int,
    val receivedQuantity: Long,
    val acceptedQuantity: Long,
    val rejectedQuantity: Long,
    val purchaseReturnQuantity: Long,
    val qualityRateBps: Int?,
    val purchaseReturnRateBps: Int?,
    val acceptedFillRateBps: Int?,
    val rejectionRateBps: Int?,
    val priceVarianceBps: Int?,
    val averageLeadTimeDays: Int?,
    val leadTimeVariabilityDays: Int?,
    val promisedDeliveryCoverageBps: Int?,
    val onTimeDeliveryRateBps: Int?,
    val grnAcceptedCostByCurrencyMinor: Map<String, Long>,
    val matchedInvoiceCostByCurrencyMinor: Map<String, Long>,
    val landedCostAvailable: Boolean,
    val scoreBps: Int?,
    val scoreReasons: List<String>,
    val recommendedAction: SupplierRecommendedAction,
)

internal data class SupplierItemCandidate(
    val supplierId: String,
    val scoreBps: Int,
    val orderCount: Int,
    val qualityRateBps: Int?,
    val purchaseReturnRateBps: Int?,
    val acceptedFillRateBps: Int?,
    val onTimeDeliveryRateBps: Int?,
    val priceVarianceBps: Int?,
    val reasons: List<String>,
)

internal data class SupplierItemRecommendation(
    val inventoryItemId: String,
    val bestSupplierId: String?,
    val candidates: List<SupplierItemCandidate>,
    val reasons: List<String>,
)

/**
 * Pure supplier-performance engine.
 *
 * Every score is derived from PO/GRN/match facts. Missing promised dates or multi-currency price
 * evidence removes that component and reweights the remaining dimensions; it never invents a KPI.
 */
internal object SupplierIntelligenceEngine {
    private const val DAY_MS = 86_400_000L
    private val BPS = BigInteger.valueOf(10_000L)

    fun evaluate(
        orders: List<SupplierOrderEvidence>,
        now: Long,
        policy: SupplierScorePolicy = SupplierScorePolicy(),
    ): SupplierPerformanceSnapshot {
        require(now >= 0L)
        val active = orders.filterNot { it.status.equals("CANCELLED", ignoreCase = true) }
        val supplierIds = active.map { it.supplierId }.filter { it.isNotBlank() }.toSet()
        val supplierId = supplierIds.singleOrNull().orEmpty()
        val reasons = mutableListOf<String>()

        var dataComplete = supplierIds.size <= 1
        if (supplierIds.size > 1) reasons += "MIXED_SUPPLIER_EVIDENCE"

        val validOrders = active.filter { order ->
            val valid = order.orderId.isNotBlank() &&
                order.createdAt > 0L &&
                order.currencyCode.isNotBlank() &&
                order.lines.isNotEmpty() &&
                order.lines.all { it.id.isNotBlank() && it.orderedQuantity > 0 && it.poUnitPriceMinor >= 0L } &&
                order.receipts.all {
                    it.orderLineId.isNotBlank() &&
                        it.receivedAt > 0L &&
                        it.receivedQuantity > 0 &&
                        it.acceptedQuantity >= 0 &&
                        it.rejectedQuantity >= 0 &&
                        it.acceptedQuantity + it.rejectedQuantity == it.receivedQuantity &&
                        it.unitCostMinor >= 0L
                } &&
                order.priceMatches.all {
                    it.orderLineId.isNotBlank() &&
                        it.invoicedQuantity > 0 &&
                        it.poUnitPriceMinor >= 0L &&
                        it.invoiceUnitPriceMinor >= 0L
                } &&
                order.returns.all {
                    it.id.isNotBlank() && it.inventoryItemId.isNotBlank() && it.quantity > 0 && it.occurredAt > 0L
                }
            if (!valid) dataComplete = false
            valid
        }
        if (validOrders.size != active.size) reasons += "INVALID_PURCHASE_EVIDENCE_EXCLUDED"

        val currencies = validOrders.map { it.currencyCode.trim().uppercase() }.toSet()
        val allReceipts = validOrders.flatMap { it.receipts }
        val receivedQuantity = allReceipts.sumOfLong { it.receivedQuantity.toLong() }
        val acceptedQuantity = allReceipts.sumOfLong { it.acceptedQuantity.toLong() }
        val rejectedQuantity = allReceipts.sumOfLong { it.rejectedQuantity.toLong() }

        val qualityRate = ratioBps(acceptedQuantity, receivedQuantity)
        val rejectionRate = ratioBps(rejectedQuantity, receivedQuantity)
        val purchaseReturnQuantity = validOrders.flatMap { it.returns }.sumOfLong { it.quantity.toLong() }
        val purchaseReturnRate = ratioBps(purchaseReturnQuantity, acceptedQuantity)
        if (purchaseReturnRate != null && purchaseReturnRate > 10_000) {
            dataComplete = false
            reasons += "PURCHASE_RETURNS_EXCEED_ACCEPTED_QUANTITY"
        }

        val matureOrders = validOrders.filter { isMatureForFill(it, now) }
        val fillLineRates = matureOrders.flatMap { order ->
            val acceptedByLine = order.receipts.groupBy { it.orderLineId }
                .mapValues { (_, rows) -> rows.sumOfLong { it.acceptedQuantity.toLong() } }
            order.lines.map { line ->
                val accepted = acceptedByLine[line.id] ?: 0L
                ratioBps(minOf(accepted, line.orderedQuantity.toLong()), line.orderedQuantity.toLong()) ?: 0
            }
        }
        val fillRate = averageInt(fillLineRates)

        val completions = validOrders.mapNotNull { order ->
            completionAt(order)?.let { order to it }
        }
        val leadDays = completions.map { (order, completionAt) ->
            daysBetween(order.createdAt, completionAt).coerceAtLeast(0)
        }
        val averageLead = averageInt(leadDays)
        val leadVariability = medianAbsoluteDeviation(leadDays)

        val promisedCount = validOrders.count { it.promisedDeliveryAt != null }
        val promisedCoverage = ratioBps(promisedCount.toLong(), validOrders.size.toLong())
        if (validOrders.isNotEmpty() && promisedCount < validOrders.size) reasons += "PROMISED_DELIVERY_DATA_INCOMPLETE"

        var onTimeEligible = 0
        var onTime = 0
        validOrders.forEach { order ->
            val promise = order.promisedDeliveryAt ?: return@forEach
            val completedAt = completionAt(order)
            when {
                completedAt != null -> {
                    onTimeEligible += 1
                    if (completedAt <= promise) onTime += 1
                }
                now > promise -> onTimeEligible += 1
            }
        }
        val onTimeRate = ratioBps(onTime.toLong(), onTimeEligible.toLong())

        val priceVariance = when {
            validOrders.flatMap { it.priceMatches }.isEmpty() -> null
            currencies.size != 1 -> {
                reasons += "MULTI_CURRENCY_PRICE_VARIANCE_UNAVAILABLE"
                null
            }
            else -> signedPriceVarianceBps(validOrders)
        }

        val grnAcceptedCost = acceptedCostByCurrency(validOrders)
        val matchedInvoiceCost = matchedInvoiceCostByCurrency(validOrders)
        if (grnAcceptedCost == null || matchedInvoiceCost == null) {
            dataComplete = false
            reasons += "ACTUAL_COST_OVERFLOW"
        }
        // Current landed-cost revisions are shipment/item facts and are not always uniquely attributable
        // to a supplier/PO. 346 intentionally refuses to claim supplier landed cost until that link is exact.
        reasons += "LANDED_COST_NOT_ATTRIBUTABLE_TO_SUPPLIER"

        if (qualityRate != null && qualityRate < policy.qualityReviewBelowBps) reasons += "QUALITY_BELOW_POLICY"
        if (purchaseReturnRate != null && purchaseReturnRate > policy.returnReviewAboveBps) reasons += "PURCHASE_RETURN_RATE_ABOVE_POLICY"
        if (fillRate != null && fillRate < policy.fillReviewBelowBps) reasons += "FILL_BELOW_POLICY"
        if (onTimeRate != null && onTimeRate < policy.onTimeReviewBelowBps) reasons += "ON_TIME_BELOW_POLICY"
        if (priceVariance != null && priceVariance > policy.unfavorablePriceVarianceAboveBps) reasons += "PRICE_VARIANCE_ABOVE_POLICY"

        val effectiveQualityRate = listOfNotNull(
            qualityRate,
            purchaseReturnRate?.let { 10_000 - it.coerceIn(0, 10_000) },
        ).minOrNull()
        val score = score(
            qualityRate = effectiveQualityRate,
            fillRate = fillRate,
            onTimeRate = onTimeRate,
            priceVarianceBps = priceVariance,
            leadDays = leadDays,
            leadVariabilityDays = leadVariability,
        )

        val action = when {
            validOrders.size < policy.minimumOrdersForRecommendation -> SupplierRecommendedAction.INSUFFICIENT_HISTORY
            qualityRate != null && qualityRate < policy.qualityReviewBelowBps -> SupplierRecommendedAction.REVIEW_QUALITY
            purchaseReturnRate != null && purchaseReturnRate > policy.returnReviewAboveBps -> SupplierRecommendedAction.REVIEW_QUALITY
            fillRate != null && fillRate < policy.fillReviewBelowBps -> SupplierRecommendedAction.REVIEW_FILL
            onTimeRate != null && onTimeRate < policy.onTimeReviewBelowBps -> SupplierRecommendedAction.REVIEW_DELIVERY
            priceVariance != null && priceVariance > policy.unfavorablePriceVarianceAboveBps -> SupplierRecommendedAction.REVIEW_PRICE
            score == null -> SupplierRecommendedAction.REVIEW_PERFORMANCE
            score >= policy.preferredScoreAtLeastBps -> SupplierRecommendedAction.PREFER
            else -> SupplierRecommendedAction.ACCEPTABLE
        }

        return SupplierPerformanceSnapshot(
            supplierId = supplierId,
            asOf = now,
            dataComplete = dataComplete,
            currencies = currencies,
            orderCount = validOrders.size,
            matureFillOrderCount = matureOrders.size,
            completedOrderCount = completions.size,
            receivedQuantity = receivedQuantity,
            acceptedQuantity = acceptedQuantity,
            rejectedQuantity = rejectedQuantity,
            purchaseReturnQuantity = purchaseReturnQuantity,
            qualityRateBps = qualityRate,
            purchaseReturnRateBps = purchaseReturnRate,
            acceptedFillRateBps = fillRate,
            rejectionRateBps = rejectionRate,
            priceVarianceBps = priceVariance,
            averageLeadTimeDays = averageLead,
            leadTimeVariabilityDays = leadVariability,
            promisedDeliveryCoverageBps = promisedCoverage,
            onTimeDeliveryRateBps = onTimeRate,
            grnAcceptedCostByCurrencyMinor = grnAcceptedCost.orEmpty(),
            matchedInvoiceCostByCurrencyMinor = matchedInvoiceCost.orEmpty(),
            landedCostAvailable = false,
            scoreBps = score,
            scoreReasons = reasons.distinct(),
            recommendedAction = action,
        )
    }

    fun recommendForItem(
        inventoryItemId: String,
        orders: List<SupplierOrderEvidence>,
        now: Long,
        policy: SupplierScorePolicy = SupplierScorePolicy(),
    ): SupplierItemRecommendation {
        val normalizedItem = inventoryItemId.trim()
        require(normalizedItem.isNotEmpty())
        val relevant = orders.mapNotNull { order ->
            val itemLineIds = order.lines.filter { it.inventoryItemId == normalizedItem }.mapTo(hashSetOf()) { it.id }
            if (itemLineIds.isEmpty()) return@mapNotNull null
            order.copy(
                lines = order.lines.filter { it.id in itemLineIds },
                receipts = order.receipts.filter { it.orderLineId in itemLineIds },
                priceMatches = order.priceMatches.filter { it.orderLineId in itemLineIds },
                returns = order.returns.filter { it.inventoryItemId == normalizedItem },
            )
        }

        val snapshots = relevant.groupBy { it.supplierId }.mapValues { (_, supplierOrders) ->
            evaluate(supplierOrders, now, policy)
        }
        val candidates = snapshots.values.mapNotNull { snapshot ->
            val score = snapshot.scoreBps ?: return@mapNotNull null
            if (snapshot.orderCount < policy.minimumOrdersForComparison) return@mapNotNull null
            SupplierItemCandidate(
                supplierId = snapshot.supplierId,
                scoreBps = score,
                orderCount = snapshot.orderCount,
                qualityRateBps = snapshot.qualityRateBps,
                purchaseReturnRateBps = snapshot.purchaseReturnRateBps,
                acceptedFillRateBps = snapshot.acceptedFillRateBps,
                onTimeDeliveryRateBps = snapshot.onTimeDeliveryRateBps,
                priceVarianceBps = snapshot.priceVarianceBps,
                reasons = snapshot.scoreReasons,
            )
        }.sortedWith(
            compareByDescending<SupplierItemCandidate> { it.scoreBps }
                .thenByDescending { it.qualityRateBps ?: -1 }
                .thenByDescending { it.acceptedFillRateBps ?: -1 }
                .thenBy { it.supplierId }
        )

        val recommendationReasons = mutableListOf<String>()
        if (relevant.isEmpty()) recommendationReasons += "NO_ITEM_PURCHASE_HISTORY"
        if (relevant.isNotEmpty() && candidates.isEmpty()) recommendationReasons += "INSUFFICIENT_COMPARABLE_SUPPLIER_HISTORY"
        if (candidates.size == 1) recommendationReasons += "ONLY_ONE_ELIGIBLE_SUPPLIER"
        if (candidates.size > 1 && candidates[0].scoreBps == candidates[1].scoreBps) recommendationReasons += "TOP_SCORE_TIE_RESOLVED_BY_SECONDARY_METRICS"

        return SupplierItemRecommendation(
            inventoryItemId = normalizedItem,
            bestSupplierId = candidates.firstOrNull()?.supplierId,
            candidates = candidates,
            reasons = recommendationReasons,
        )
    }

    private fun isMatureForFill(order: SupplierOrderEvidence, now: Long): Boolean {
        if (order.status.equals("RECEIVED", true) || order.status.equals("CLOSED", true)) return true
        val promise = order.promisedDeliveryAt
        return promise != null && now > promise
    }

    private fun completionAt(order: SupplierOrderEvidence): Long? {
        if (order.lines.isEmpty()) return null
        val receiptsByLine = order.receipts.groupBy { it.orderLineId }
        val lineCompletionTimes = order.lines.map { line ->
            var accepted = 0L
            var completedAt: Long? = null
            receiptsByLine[line.id].orEmpty().sortedWith(compareBy({ it.receivedAt }, { it.id })).forEach { receipt ->
                if (completedAt == null) {
                    accepted = safeAdd(accepted, receipt.acceptedQuantity.toLong()) ?: return null
                    if (accepted >= line.orderedQuantity.toLong()) completedAt = receipt.receivedAt
                }
            }
            completedAt ?: return null
        }
        return lineCompletionTimes.maxOrNull()
    }

    private fun score(
        qualityRate: Int?,
        fillRate: Int?,
        onTimeRate: Int?,
        priceVarianceBps: Int?,
        leadDays: List<Int>,
        leadVariabilityDays: Int?,
    ): Int? {
        val components = mutableListOf<Pair<Int, Int>>()
        qualityRate?.let { components += it.coerceIn(0, 10_000) to 30 }
        fillRate?.let { components += it.coerceIn(0, 10_000) to 25 }
        onTimeRate?.let { components += it.coerceIn(0, 10_000) to 20 }
        priceVarianceBps?.let {
            val unfavorable = max(0, it)
            components += (10_000 - unfavorable.coerceAtMost(10_000)) to 15
        }
        if (leadDays.size >= 2 && leadVariabilityDays != null) {
            val typical = median(leadDays) ?: 0
            val penalty = ratioBps(leadVariabilityDays.toLong(), max(1, typical).toLong()) ?: 10_000
            components += (10_000 - penalty.coerceAtMost(10_000)) to 10
        }
        if (components.isEmpty()) return null
        val weighted = components.fold(BigInteger.ZERO) { acc, (value, weight) ->
            acc + BigInteger.valueOf(value.toLong()).multiply(BigInteger.valueOf(weight.toLong()))
        }
        val totalWeight = components.sumOf { it.second }.toLong()
        return weighted.divide(BigInteger.valueOf(totalWeight)).toInt()
    }

    private fun signedPriceVarianceBps(orders: List<SupplierOrderEvidence>): Int? {
        var expected = BigInteger.ZERO
        var actual = BigInteger.ZERO
        orders.flatMap { it.priceMatches }.forEach { row ->
            val qty = BigInteger.valueOf(row.invoicedQuantity.toLong())
            expected += BigInteger.valueOf(row.poUnitPriceMinor).multiply(qty)
            actual += BigInteger.valueOf(row.invoiceUnitPriceMinor).multiply(qty)
        }
        if (expected <= BigInteger.ZERO) return null
        val delta = actual.subtract(expected)
        return delta.multiply(BPS).divide(expected).coerceToInt()
    }

    private fun matchedInvoiceCostByCurrency(orders: List<SupplierOrderEvidence>): Map<String, Long>? {
        val totals = linkedMapOf<String, BigInteger>()
        orders.forEach { order ->
            val currency = order.currencyCode.trim().uppercase()
            order.priceMatches.forEach { match ->
                val cost = BigInteger.valueOf(match.invoiceUnitPriceMinor)
                    .multiply(BigInteger.valueOf(match.invoicedQuantity.toLong()))
                totals[currency] = totals.getOrDefault(currency, BigInteger.ZERO) + cost
            }
        }
        val result = linkedMapOf<String, Long>()
        totals.forEach { (currency, value) ->
            if (value.bitLength() > 63) return null
            result[currency] = value.toLong()
        }
        return result
    }

    private fun acceptedCostByCurrency(orders: List<SupplierOrderEvidence>): Map<String, Long>? {
        val totals = linkedMapOf<String, BigInteger>()
        orders.forEach { order ->
            val currency = order.currencyCode.trim().uppercase()
            order.receipts.forEach { receipt ->
                val cost = BigInteger.valueOf(receipt.unitCostMinor)
                    .multiply(BigInteger.valueOf(receipt.acceptedQuantity.toLong()))
                totals[currency] = totals.getOrDefault(currency, BigInteger.ZERO) + cost
            }
        }
        val result = linkedMapOf<String, Long>()
        totals.forEach { (currency, value) ->
            if (value.bitLength() > 63) return null
            result[currency] = value.toLong()
        }
        return result
    }

    private fun ratioBps(numerator: Long, denominator: Long): Int? {
        if (denominator <= 0L || numerator < 0L) return null
        return BigInteger.valueOf(numerator).multiply(BPS)
            .divide(BigInteger.valueOf(denominator)).coerceToInt()
    }

    private fun BigInteger.coerceToInt(): Int = when {
        this > BigInteger.valueOf(Int.MAX_VALUE.toLong()) -> Int.MAX_VALUE
        this < BigInteger.valueOf(Int.MIN_VALUE.toLong()) -> Int.MIN_VALUE
        else -> toInt()
    }

    private fun averageInt(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        return ((values.sumOf { it.toLong() } + values.size / 2L) / values.size.toLong()).toInt()
    }

    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else ((sorted[middle - 1].toLong() + sorted[middle]) / 2L).toInt()
    }

    private fun medianAbsoluteDeviation(values: List<Int>): Int? {
        val med = median(values) ?: return null
        return median(values.map { kotlin.math.abs(it - med) })
    }

    private fun daysBetween(from: Long, to: Long): Int = ((to - from) / DAY_MS).toInt()

    private fun safeAdd(a: Long, b: Long): Long? = try {
        Math.addExact(a, b)
    } catch (_: ArithmeticException) {
        null
    }

    private inline fun <T> Iterable<T>.sumOfLong(selector: (T) -> Long): Long {
        var total = 0L
        for (row in this) total = Math.addExact(total, selector(row))
        return total
    }
}
