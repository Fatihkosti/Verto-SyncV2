package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.LegacyCurrencyStatus
import com.verto.app.data.local.entity.LogisticsCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsCostEntity
import com.verto.app.data.local.entity.LogisticsLateCostAllocationEntity
import com.verto.app.data.local.entity.LogisticsReceivingLineEntity
import com.verto.app.data.local.entity.LogisticsShipmentEntity
import com.verto.app.data.local.entity.LogisticsShipmentLineEntity
import com.verto.app.data.local.entity.PaymentAllocationEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseOrderEntity
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.reports.application.model.*
import kotlin.math.abs

private data class PayableSettlementFacts(
    val allocationsByInvoice: Map<String, List<PaymentAllocationEntity>>,
    val returnsByInvoice: Map<String, Long>,
    val functionalCurrencyCode: String,
    val asOf: Long,
)

internal fun buildAgedPayables(
    suppliers: List<PartyClient>,
    creditPurchases: List<InvoiceEntity>,
    allocations: List<PaymentAllocationEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    functionalCurrencyCode: String,
    window: AgingKpiWindow = AgingKpiWindow()
): AgedPayablesData {
    if (functionalCurrencyCode.isBlank()) return AgedPayablesData()
    val facts = PayableSettlementFacts(
        allocationsByInvoice = allocations.asSequence().filter { it.createdAt <= window.asOf }.groupBy { it.invoiceId },
        returnsByInvoice = returnDocuments.asSequence()
            .filter { it.occurredAt <= window.asOf && it.documentType == "PURCHASE_RETURN_DEBIT_NOTE" }
            .filter { it.functionalCurrencyCode.equals(functionalCurrencyCode, ignoreCase = true) }
            .groupBy { it.originalInvoiceId }
            .mapValues { (_, rows) -> sumMinor(rows.map { it.functionalAmountMinor }) },
        functionalCurrencyCode = functionalCurrencyCode,
        asOf = window.asOf,
    )
    val eligibleCredit = creditPurchases.filter { invoice ->
        invoice.category == InvoiceCategory.PURCHASE && invoice.status == InvoiceStatus.CLOSED_CREDIT &&
            invoice.createdAt <= window.asOf && invoice.lifecycleStatus != InvoiceLifecycleStatus.VOID && !invoice.voided &&
            invoice.functionalMinorOrNull(functionalCurrencyCode) != null
    }
    val supplierNames = suppliers.associateBy({ it.id }, { it.name })
    val rows = eligibleCredit.asSequence()
        .filter { it.dueDate > 0L && it.dueDate < window.asOf }
        .groupBy { it.clientId }
        .mapNotNull { (supplierId, invoices) ->
            buildAgedPayableRow(supplierId, supplierNames[supplierId] ?: supplierId, invoices, facts)
        }
        .sortedByDescending { it.oldestDueDays }
    val buckets = sumAgingBuckets(rows)
    val openPayable = sumMinor(eligibleCredit.mapNotNull { remainingPayableMinor(it, facts) })
    return AgedPayablesData(
        buckets = buckets,
        dpoDays = ratioDays(openPayable, window.periodNetFlowMinor ?: 0L, window.periodDays),
        suppliers = rows,
        currencyCode = functionalCurrencyCode,
    )
}

private fun remainingPayableMinor(invoice: InvoiceEntity, facts: PayableSettlementFacts): Long? {
    val recognized = invoice.functionalMinorOrNull(facts.functionalCurrencyCode) ?: return null
    val paid = sumMinor(facts.allocationsByInvoice[invoice.id].orEmpty().map { it.historicalFunctionalAmountMinor })
    val returned = facts.returnsByInvoice[invoice.id] ?: 0L
    return Math.subtractExact(recognized, Math.addExact(paid, returned)).coerceAtLeast(0L)
}

private fun buildAgedPayableRow(
    supplierId: String,
    supplierName: String,
    invoices: List<InvoiceEntity>,
    facts: PayableSettlementFacts,
): AgedPayable? {
    var b0 = 0L; var b31 = 0L; var b61 = 0L; var b90 = 0L; var oldest = 0
    invoices.forEach { invoice ->
        val remaining = remainingPayableMinor(invoice, facts) ?: return@forEach
        if (remaining == 0L) return@forEach
        val days = ((facts.asOf - invoice.dueDate) / DAY_MS_F255).toInt().coerceAtLeast(0)
        oldest = maxOf(oldest, days)
        when {
            days <= 30 -> b0 = Math.addExact(b0, remaining)
            days <= 60 -> b31 = Math.addExact(b31, remaining)
            days <= 90 -> b61 = Math.addExact(b61, remaining)
            else -> b90 = Math.addExact(b90, remaining)
        }
    }
    val buckets = AgingBucketsMinor(b0, b31, b61, b90)
    if (buckets.total <= 0L) return null
    return AgedPayable(supplierId, supplierName, buckets, oldest, facts.functionalCurrencyCode)
}

private fun sumAgingBuckets(rows: List<AgedPayable>) = AgingBucketsMinor(
    days0To30 = sumMinor(rows.map { it.bucket0_30Minor }),
    days31To60 = sumMinor(rows.map { it.bucket31_60Minor }),
    days61To90 = sumMinor(rows.map { it.bucket61_90Minor }),
    daysOver90 = sumMinor(rows.map { it.bucketOver90Minor }),
)

internal fun buildPurchasePriceVariance(
    orders: List<PurchaseOrderEntity>,
    matches: List<PurchaseInvoiceMatchEntity>,
    matchLines: List<PurchaseInvoiceMatchLineEntity>,
    invoices: List<InvoiceEntity>,
    invoiceItems: List<InvoiceItemEntity>,
    period: LongRange
): PurchasePriceVarianceData {
    val ordersById = orders.associateBy { it.id }
    val invoicesById = invoices.associateBy { it.id }
    val itemsById = invoiceItems.associateBy { it.id }
    val matchById = matches.associateBy { it.id }

    val rows = matchLines.mapNotNull { line ->
        val match = matchById[line.matchId] ?: return@mapNotNull null
        if (match.matchedAt !in period) return@mapNotNull null
        val invoice = invoicesById[match.invoiceId] ?: return@mapNotNull null
        if (invoice.lifecycleStatus == InvoiceLifecycleStatus.VOID || invoice.voided) return@mapNotNull null
        val order = ordersById[match.purchaseOrderId] ?: return@mapNotNull null
        val currency = order.currencyCode.trim().uppercase()
        if (currency.isBlank() || !invoice.transactionCurrencyCode.equals(currency, ignoreCase = true)) return@mapNotNull null
        val delta = Math.subtractExact(line.invoiceUnitPriceMinor, line.poUnitPriceMinor)
        val variance = Math.multiplyExact(delta, line.invoicedQuantity.toLong())
        val invoiceItem = itemsById[line.invoiceItemId]
        PurchasePriceVarianceRow(
            itemId = invoiceItem?.inventoryItemId.orEmpty().ifBlank { line.invoiceItemId },
            itemName = invoiceItem?.itemName.orEmpty().ifBlank { line.invoiceItemId },
            price = PurchasePriceSnapshot(line.invoicedQuantity, line.poUnitPriceMinor, line.invoiceUnitPriceMinor),
            varianceMinor = variance,
            currencyCode = currency,
        )
    }

    return PurchasePriceVarianceData(
        groups = rows.groupBy { it.currencyCode }.map { (currency, currencyRows) ->
            val favorable = sumMinor(currencyRows.filter { it.varianceMinor < 0L }.map { absExactMinor(it.varianceMinor) })
            val unfavorable = sumMinor(currencyRows.filter { it.varianceMinor > 0L }.map { it.varianceMinor })
            val net = sumMinor(currencyRows.map { it.varianceMinor })
            PurchasePriceVarianceGroup(
                currencyCode = currency,
                favorableMinor = favorable,
                unfavorableMinor = unfavorable,
                netVarianceMinor = net,
                rows = currencyRows.sortedByDescending { absExactMinor(it.varianceMinor) },
            )
        }.sortedBy { it.currencyCode },
    )
}

private data class LandedCostFacts(
    val costsByShipment: Map<String, List<com.verto.app.feature.reports.application.port.ReportsShipmentCostSnapshot>>,
    val linesByShipment: Map<String, List<LogisticsShipmentLineEntity>>,
    val receivingByShipmentLine: Map<String, List<LogisticsReceivingLineEntity>>,
    val allocationsByShipment: Map<String, List<LogisticsCostAllocationEntity>>,
    val lateByShipment: Map<String, List<LogisticsLateCostAllocationEntity>>,
)

private data class LandedVarianceProjection(
    val shipment: LandedCostShipmentVariance,
    val items: List<LandedCostItemVariance>,
)

internal fun buildLandedCostVariance(
    shipments: List<com.verto.app.feature.reports.application.port.ReportsShipmentSnapshot>,
    costs: List<com.verto.app.feature.reports.application.port.ReportsShipmentCostSnapshot>,
    shipmentLines: List<LogisticsShipmentLineEntity>,
    receivingLines: List<LogisticsReceivingLineEntity>,
    allocations: List<LogisticsCostAllocationEntity>,
    lateAllocations: List<LogisticsLateCostAllocationEntity>
): LandedCostVarianceData {
    val facts = LandedCostFacts(
        costs.groupBy { it.shipmentId }, shipmentLines.groupBy { it.shipmentId },
        receivingLines.groupBy { it.shipmentLineId }, allocations.groupBy { it.shipmentId },
        lateAllocations.groupBy { it.shipmentId },
    )
    val projections = shipments.asSequence()
        .filterNot { it.state == "CANCELLED" && it.startedAt == null }
        .mapNotNull { buildLandedVarianceProjection(it, facts) }
        .toList()
    return LandedCostVarianceData(
        shipments = projections.map { it.shipment }.sortedByDescending { absExactMinor(it.varianceMinor) },
        items = projections.flatMap { it.items }.sortedByDescending { absExactMinor(it.varianceMinor) },
        currencyCode = LOGISTICS_BASE_CURRENCY_F255,
    )
}

private fun buildLandedVarianceProjection(
    shipment: com.verto.app.feature.reports.application.port.ReportsShipmentSnapshot,
    facts: LandedCostFacts,
): LandedVarianceProjection? {
    val scopedCosts = facts.costsByShipment[shipment.id].orEmpty()
    val estimated = sumMinor(scopedCosts.filter { it.status == "ESTIMATED" }.mapNotNull { parseBaseMinor(it.baseCurrencyAmount) })
    val actual = sumMinor(scopedCosts.filter { it.status == "ACTUAL" }.mapNotNull { parseBaseMinor(it.baseCurrencyAmount) })
    if (estimated == 0L && actual == 0L) return null
    val shipmentRow = LandedCostShipmentVariance(
        shipment.id, shipment.shipmentNumber, estimated, actual, Math.subtractExact(actual, estimated),
        LOGISTICS_BASE_CURRENCY_F255,
    )
    val lines = facts.linesByShipment[shipment.id].orEmpty()
    val bases = lines.mapNotNull { line ->
        val accepted = facts.receivingByShipmentLine[line.id].orEmpty().sumOf { it.acceptedQuantity.toLong() }
        val unit = parseBaseMinor(line.basePurchaseUnitPrice) ?: return@mapNotNull null
        if (accepted <= 0L || unit <= 0L) null else line.id to Math.multiplyExact(unit, accepted)
    }
    val estimatedByLine = allocateMinorByBasis(estimated, bases)
    val actualByLine = mutableMapOf<String, Long>()
    facts.allocationsByShipment[shipment.id].orEmpty().forEach { row -> addActualAllocation(actualByLine, row.shipmentLineId, row.amount) }
    facts.lateByShipment[shipment.id].orEmpty().forEach { row -> addActualAllocation(actualByLine, row.shipmentLineId, row.amount) }
    val itemRows = lines.mapNotNull { line ->
        val estimatedAllocation = estimatedByLine[line.id] ?: 0L
        val actualAllocation = actualByLine[line.id] ?: 0L
        if (estimatedAllocation == 0L && actualAllocation == 0L) return@mapNotNull null
        LandedCostItemVariance(
            shipmentId = shipment.id, shipmentNumber = shipment.shipmentNumber, inventoryItemId = line.inventoryItemId,
            itemName = line.itemNameSnapshot, amounts = CostVarianceAmounts(estimatedAllocation, actualAllocation),
            currencyCode = LOGISTICS_BASE_CURRENCY_F255,
        )
    }
    return LandedVarianceProjection(shipmentRow, itemRows)
}

private fun addActualAllocation(target: MutableMap<String, Long>, shipmentLineId: String, rawAmount: String) {
    val amount = parseBaseMinor(rawAmount) ?: return
    target[shipmentLineId] = Math.addExact(target[shipmentLineId] ?: 0L, amount)
}

internal fun buildSupplierFxVariance(
    purchases: List<InvoiceEntity>,
    suppliers: List<PartyClient>,
    allocations: List<PaymentAllocationEntity>,
    from: Long,
    to: Long,
): SupplierFxVarianceData {
    val supplierNames = suppliers.associateBy({ it.id }, { it.name })
    val purchasesById = purchases.asSequence()
        .filter { it.category == InvoiceCategory.PURCHASE }
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID && !it.voided }
        .filter { it.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN }
        .associateBy { it.id }

    data class Key(val currency: String, val supplierId: String)
    data class Totals(var fx: Long = 0L, var historical: Long = 0L)
    val totals = linkedMapOf<Key, Totals>()
    allocations.asSequence().filter { it.createdAt in from..to }.forEach { allocation ->
        val invoice = purchasesById[allocation.invoiceId] ?: return@forEach
        val currency = invoice.functionalCurrencyCode.trim().uppercase()
        if (currency.isBlank()) return@forEach
        val key = Key(currency, invoice.clientId)
        val row = totals.getOrPut(key) { Totals() }
        row.fx = Math.addExact(row.fx, allocation.realizedFxDifferenceMinor)
        row.historical = Math.addExact(row.historical, allocation.historicalFunctionalAmountMinor)
    }

    val rows = totals.map { (key, value) ->
        SupplierFxVarianceRow(
            supplierId = key.supplierId,
            supplierName = supplierNames[key.supplierId] ?: key.supplierId,
            realizedGainLossMinor = value.fx,
            historicalFunctionalMinor = value.historical,
            currencyCode = key.currency,
        )
    }
    return SupplierFxVarianceData(
        groups = rows.groupBy { it.currencyCode }.map { (currency, currencyRows) ->
            SupplierFxVarianceGroup(
                currencyCode = currency,
                totalRealizedGainLossMinor = sumMinor(currencyRows.map { it.realizedGainLossMinor }),
                suppliers = currencyRows.sortedByDescending { absExactMinor(it.realizedGainLossMinor) },
            )
        }.sortedBy { it.currencyCode },
    )
}

private data class SettlementEvent(val occurredAt: Long, val amountMinor: Long, val stableId: String)

internal fun buildSupplierPaymentTiming(
    purchases: List<InvoiceEntity>,
    suppliers: List<PartyClient>,
    allocations: List<PaymentAllocationEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    from: Long,
    to: Long
): SupplierPaymentTimingData {
    val supplierNames = suppliers.associateBy({ it.id }, { it.name })
    val allocationsByInvoice = allocations.groupBy { it.invoiceId }
    val returnsByInvoice = returnDocuments.asSequence()
        .filter { it.documentType == "PURCHASE_RETURN_DEBIT_NOTE" }
        .groupBy { it.originalInvoiceId }
    data class Settlement(val supplierId: String, val days: Float)
    val settlements = purchases.asSequence()
        .filter { it.category == InvoiceCategory.PURCHASE && it.status == InvoiceStatus.CLOSED_CREDIT }
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID && !it.voided }
        .mapNotNull { invoice ->
            val settledAt = supplierSettlementAt(
                invoice, allocationsByInvoice[invoice.id].orEmpty(), returnsByInvoice[invoice.id].orEmpty(), to,
            ) ?: return@mapNotNull null
            if (settledAt !in from..to) return@mapNotNull null
            Settlement(invoice.clientId, (settledAt - invoice.createdAt).coerceAtLeast(0L).toFloat() / DAY_MS_F255.toFloat())
        }.toList()
    if (settlements.isEmpty()) return SupplierPaymentTimingData()
    val portfolioAverage = settlements.map { it.days.toDouble() }.average().toFloat()
    val rows = settlements.groupBy { it.supplierId }.map { (supplierId, rows) ->
        val avg = rows.map { it.days.toDouble() }.average().toFloat()
        SupplierPaymentTimingRow(
            supplierId, supplierNames[supplierId] ?: supplierId, rows.size, avg, avg - portfolioAverage,
        )
    }.sortedByDescending { abs(it.varianceFromPortfolioDays) }
    return SupplierPaymentTimingData(portfolioAverage, rows)
}

private fun supplierSettlementAt(
    invoice: InvoiceEntity,
    allocations: List<PaymentAllocationEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    asOf: Long,
): Long? {
    val eligibleReturns = returnDocuments.filter {
        it.occurredAt <= asOf && it.transactionCurrencyCode.equals(invoice.transactionCurrencyCode, ignoreCase = true)
    }
    if (sumMinor(eligibleReturns.map { it.transactionAmountMinor }) >= invoice.transactionAmountMinor) return null
    val paymentEvents = allocations.asSequence().filter { it.createdAt <= asOf }
        .map { SettlementEvent(it.createdAt, it.allocatedTransactionAmountMinor, "P:${it.id}") }.toList()
    val returnEvents = eligibleReturns
        .map { SettlementEvent(it.occurredAt, it.transactionAmountMinor, "R:${it.id}") }
    var settledMinor = 0L
    return (paymentEvents + returnEvents)
        .sortedWith(compareBy<SettlementEvent> { it.occurredAt }.thenBy { it.stableId })
        .firstOrNull { event ->
            settledMinor = Math.addExact(settledMinor, event.amountMinor)
            settledMinor >= invoice.transactionAmountMinor
        }?.occurredAt
}
