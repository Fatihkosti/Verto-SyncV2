package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.*
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.reports.application.model.*
import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import kotlin.math.abs

// Financial calculations in this file use persisted fixed-point minor units.
// Double exists only in the legacy presentation read-model boundary.

internal data class AgingKpiWindow(
    val periodNetFlowMinor: Long? = null,
    val periodDays: Int = 30,
    val asOf: Long = System.currentTimeMillis(),
)

internal fun buildAgedReceivables(
    clients: List<PartyClient>,
    creditInvoices: List<InvoiceEntity>,
    allocations: List<PaymentAllocationEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
    functionalCurrencyCode: String,
    window: AgingKpiWindow = AgingKpiWindow()
): AgedReceivablesData {
    val now = window.asOf
    val allocByInv = allocations.asSequence().filter { it.createdAt <= now }.groupBy { it.invoiceId }
    val returnedByInvoice = returnDocuments.asSequence()
        .filter { it.occurredAt <= now }
        .filter {
            it.documentType == "SALES_RETURN_CREDIT_NOTE" &&
                it.functionalCurrencyCode.equals(functionalCurrencyCode, ignoreCase = true)
        }
        .groupBy { it.originalInvoiceId }
        .mapValues { (_, rows) -> sumMinor(rows.map { it.functionalAmountMinor }) }
    val clientsMap = clients.associateBy { it.id }
    val eligibleCredit = creditInvoices.filter {
        it.category == InvoiceCategory.SALE && it.status == InvoiceStatus.CLOSED_CREDIT &&
            it.createdAt <= now && it.lifecycleStatus != InvoiceLifecycleStatus.VOID && !it.voided &&
            it.functionalMinorOrNull(functionalCurrencyCode) != null
    }
    val overdue = eligibleCredit.filter { it.dueDate > 0L && it.dueDate < now }
    val byClient = overdue.groupBy { it.clientId }

    var tot0 = 0L; var tot31 = 0L; var tot61 = 0L; var tot90 = 0L
    val rows = byClient.mapNotNull { (clientId, invs) ->
        val client = clientsMap[clientId] ?: return@mapNotNull null
        var b0 = 0L; var b31 = 0L; var b61 = 0L; var b90 = 0L
        var oldest = 0
        invs.forEach { inv ->
            val recognized = inv.functionalMinorOrNull(functionalCurrencyCode) ?: return@forEach
            val paidHistorical = sumMinor(allocByInv[inv.id].orEmpty().map { it.historicalFunctionalAmountMinor })
            val creditedHistorical = returnedByInvoice[inv.id].orZero()
            val settled = Math.addExact(paidHistorical, creditedHistorical)
            val remaining = Math.subtractExact(recognized, settled).coerceAtLeast(0L)
            if (remaining == 0L) return@forEach
            val days = ((now - inv.dueDate) / 86_400_000L).toInt()
            oldest = maxOf(oldest, days)
            when {
                days <= 30 -> b0 = Math.addExact(b0, remaining)
                days <= 60 -> b31 = Math.addExact(b31, remaining)
                days <= 90 -> b61 = Math.addExact(b61, remaining)
                else -> b90 = Math.addExact(b90, remaining)
            }
        }
        val total = sumMinor(listOf(b0, b31, b61, b90))
        if (total <= 0L) return@mapNotNull null
        tot0 = Math.addExact(tot0, b0); tot31 = Math.addExact(tot31, b31)
        tot61 = Math.addExact(tot61, b61); tot90 = Math.addExact(tot90, b90)
        AgedReceivable(
            clientId, client.name,
            major(b0, functionalCurrencyCode), major(b31, functionalCurrencyCode),
            major(b61, functionalCurrencyCode), major(b90, functionalCurrencyCode),
            major(total, functionalCurrencyCode), oldest,
        )
    }.sortedByDescending { it.oldestDueDays }

    val grand = sumMinor(listOf(tot0, tot31, tot61, tot90))
    val openReceivable = sumMinor(eligibleCredit.map { inv ->
        val recognized = inv.functionalMinorOrNull(functionalCurrencyCode) ?: 0L
        val paidHistorical = sumMinor(allocByInv[inv.id].orEmpty().map { it.historicalFunctionalAmountMinor })
        val creditedHistorical = returnedByInvoice[inv.id].orZero()
        Math.subtractExact(recognized, Math.addExact(paidHistorical, creditedHistorical)).coerceAtLeast(0L)
    })
    val denominator = window.periodNetFlowMinor ?: sumMinor(overdue.mapNotNull { it.functionalMinorOrNull(functionalCurrencyCode) })
    val dso = if (denominator > 0L && openReceivable > 0L) BigDecimal.valueOf(openReceivable)
        .multiply(BigDecimal.valueOf(window.periodDays.coerceAtLeast(1).toLong()))
        .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP).toFloat() else 0f

    return AgedReceivablesData(
        major(tot0, functionalCurrencyCode), major(tot31, functionalCurrencyCode),
        major(tot61, functionalCurrencyCode), major(tot90, functionalCurrencyCode),
        major(grand, functionalCurrencyCode), dso, rows, functionalCurrencyCode,
    )
}

internal fun buildHeatmap(
    invoices: List<InvoiceEntity>,
    functionalCurrencyCode: String,
    recognizedRevenueByInvoice: Map<String, Long> = emptyMap(),
): List<HeatmapCell> {
    val grouped = invoices.groupBy { inv ->
        val cal = Calendar.getInstance().apply { timeInMillis = inv.createdAt }
        (cal.get(Calendar.DAY_OF_WEEK) - 1) to cal.get(Calendar.HOUR_OF_DAY)
    }
    return (0..6).flatMap { day ->
        (6..23).map { hour ->
            val invs = grouped[day to hour].orEmpty()
            val salesMinor = sumMinor(invs.map { invoice ->
                recognizedRevenueByInvoice[invoice.id] ?: invoice.functionalMinorOrNull(functionalCurrencyCode) ?: 0L
            })
            HeatmapCell(day, hour, major(salesMinor, functionalCurrencyCode), invs.size)
        }
    }
}

internal fun buildTopItems(
    items: List<InvoiceItemEntity>,
    invoicesById: Map<String, InvoiceEntity>,
    totalSalesMinor: Long,
    functionalCurrencyCode: String,
    recognizedRevenueByLine: Map<String, Long> = emptyMap(),
): List<TopItem> = items.groupBy { it.itemName }.mapNotNull { (name, its) ->
    val revenueMinor = sumMinor(its.map { line ->
        recognizedRevenueByLine[line.id] ?: line.functionalRevenueMinor(invoicesById[line.invoiceId]) ?: 0L
    })
    if (revenueMinor <= 0L) return@mapNotNull null
    val profitMinor = sumMinor(its.filter { it.costSnapshotStatus != "LEGACY_UNKNOWN" }.map { line ->
        val functionalRevenue = recognizedRevenueByLine[line.id] ?: line.functionalRevenueMinor(invoicesById[line.invoiceId]) ?: 0L
        Math.subtractExact(functionalRevenue, line.lineCostSnapshotMinor)
    })
    val qty = its.sumOf { it.quantity }
    TopItem(
        name = name,
        category = its.firstOrNull()?.itemCategory.orEmpty(),
        qty = qty,
        revenue = major(revenueMinor, functionalCurrencyCode),
        profit = major(profitMinor, functionalCurrencyCode),
        margin = percent(profitMinor, revenueMinor),
        pct = percent(revenueMinor, totalSalesMinor),
        revenueMinor = revenueMinor,
        profitMinor = profitMinor,
        currencyCode = functionalCurrencyCode,
    )
}.sortedByDescending { it.revenueMinor }.take(10)

internal fun buildCategories(
    items: List<InvoiceItemEntity>,
    invoicesById: Map<String, InvoiceEntity>,
    totalSalesMinor: Long,
    functionalCurrencyCode: String,
    recognizedRevenueByLine: Map<String, Long> = emptyMap(),
): List<CategorySummary> = items.groupBy { it.itemCategory.ifBlank { "غير مصنف" } }.mapNotNull { (cat, its) ->
    val revenueMinor = sumMinor(its.map { line ->
        recognizedRevenueByLine[line.id] ?: line.functionalRevenueMinor(invoicesById[line.invoiceId]) ?: 0L
    })
    if (revenueMinor <= 0L) return@mapNotNull null
    val profitMinor = sumMinor(its.filter { it.costSnapshotStatus != "LEGACY_UNKNOWN" }.map { line ->
        val functionalRevenue = recognizedRevenueByLine[line.id] ?: line.functionalRevenueMinor(invoicesById[line.invoiceId]) ?: 0L
        Math.subtractExact(functionalRevenue, line.lineCostSnapshotMinor)
    })
    CategorySummary(
        category = cat,
        distinctItems = its.map { it.itemName }.distinct().size,
        totalQty = its.sumOf { it.quantity },
        revenue = major(revenueMinor, functionalCurrencyCode),
        profit = major(profitMinor, functionalCurrencyCode),
        margin = percent(profitMinor, revenueMinor),
        pct = percent(revenueMinor, totalSalesMinor),
        revenueMinor = revenueMinor,
        profitMinor = profitMinor,
        currencyCode = functionalCurrencyCode,
    )
}.sortedByDescending { it.revenueMinor }

internal fun buildCashFlow(movements: List<CashRegisterMovementEntity>, currencyCode: String): CashFlowData {
    if (movements.isEmpty()) return CashFlowData(currencyCode = currencyCode)
    val ordered = movements.sortedBy { it.createdAt }
    val opening = ordered.first().balanceBeforeMinor
    val closing = ordered.last().balanceAfterMinor
    fun amount(type: CashMovementType, absolute: Boolean = false): Long = sumMinor(
        movements.filter { it.movementType == type }.map { if (absolute) abs(it.amountMinor) else it.amountMinor }
    )
    val cashSales = amount(CashMovementType.SALE_CASH)
    val debt = amount(CashMovementType.PAYMENT_RECEIVED)
    val manualAdds = amount(CashMovementType.MANUAL_ADD)
    val purchases = amount(CashMovementType.PURCHASE_CASH, true)
    val supplier = amount(CashMovementType.PAYMENT_MADE, true)
    val expenses = amount(CashMovementType.EXPENSE, true)
    val manualDeducts = amount(CashMovementType.MANUAL_DEDUCT, true)
    val totalIn = sumMinor(listOf(cashSales, debt, manualAdds))
    val totalOut = sumMinor(listOf(purchases, supplier, expenses, manualDeducts))
    val net = Math.subtractExact(totalIn, totalOut)
    return CashFlowData(
        openingBalance = major(opening, currencyCode), totalIn = major(totalIn, currencyCode),
        cashSales = major(cashSales, currencyCode), debtCollections = major(debt, currencyCode),
        manualAdds = major(manualAdds, currencyCode), totalOut = major(totalOut, currencyCode),
        cashPurchases = major(purchases, currencyCode), supplierPayments = major(supplier, currencyCode),
        expenses = major(expenses, currencyCode), manualDeductions = major(manualDeducts, currencyCode),
        netFlow = major(net, currencyCode), closingBalance = major(closing, currencyCode), currencyCode = currencyCode,
    )
}

internal fun buildInventoryHealth(
    items: List<InventoryItemEntity>,
    movements: List<InventoryMovementEntity>,
    currencyCode: String,
): InventoryHealthData {
    val now = System.currentTimeMillis(); val ninetyDaysAgo = now - 90L * 24 * 60 * 60 * 1000
    val lastMovByItem = movements.groupBy { it.itemId }.mapValues { (_, rows) -> rows.maxOf { it.createdAt } }
    val outCountByItem = movements.filter { it.movementType == MovementType.OUT }.groupBy { it.itemId }.mapValues { it.value.size }
    fun summary(item: InventoryItemEntity) = InventoryItemSummary(
        item.id, item.name, item.quantity, item.minQuantity, major(item.buyPriceMinor, currencyCode),
        lastMovByItem[item.id] ?: 0L, outCountByItem[item.id] ?: 0,
    )
    val deadItems = items.filter { (lastMovByItem[it.id] ?: it.updatedAt) < ninetyDaysAgo && it.quantity > 0 }
        .sortedByDescending { multiplyMinor(it.buyPriceMinor, it.quantity) }
    val dead = deadItems.map(::summary)
    val fast = items.map(::summary).sortedByDescending { it.turnoverCount }.take(10)
    val risk = items.filter { it.quantity <= it.minQuantity }.map(::summary).sortedBy { it.quantity }
    val totalValuationMinor = sumMinor(items.map { multiplyMinor(it.buyPriceMinor, it.quantity) })
    val deadIds = dead.map { it.id }.toSet()
    val deadValueMinor = sumMinor(items.filter { it.id in deadIds }.map { multiplyMinor(it.buyPriceMinor, it.quantity) })
    return InventoryHealthData(dead, fast, risk, major(totalValuationMinor, currencyCode), major(deadValueMinor, currencyCode), currencyCode)
}

internal fun buildReturnsData(
    returnLines: List<InvoiceReturnLineEntity>,
    returnDocumentCount: Int,
    grossSalesMinor: Long,
    currencyCode: String,
): ReturnsData {
    val totalMinor = sumMinor(returnLines.map { it.functionalAmountMinor })
    val top = returnLines.groupBy { it.inventoryItemId }.map { (_, rows) ->
        val value = sumMinor(rows.map { it.functionalAmountMinor })
        value to ReturnedItem(
            rows.firstOrNull()?.itemNameSnapshot.orEmpty(),
            rows.sumOf { it.quantity },
            major(value, currencyCode),
        )
    }.sortedByDescending { it.first }.take(5).map { it.second }
    return ReturnsData(percent(totalMinor, grossSalesMinor), major(totalMinor, currencyCode), returnDocumentCount, top)
}

internal fun buildEmployeePerformance(payments: List<PaymentEntity>, currencyCode: String): EmployeePerformanceData {
    val rows = payments.filter { it.employeeName.isNotBlank() }.groupBy { it.employeeName }.map { (name, pays) ->
        val totalMinor = sumMinor(pays.map { it.functionalCashAmountMinor })
        val avgMinor = if (pays.isEmpty()) 0L else BigDecimal.valueOf(totalMinor)
            .divide(BigDecimal.valueOf(pays.size.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
        totalMinor to EmployeeSummary(name, major(totalMinor, currencyCode), pays.size, major(avgMinor, currencyCode))
    }.sortedByDescending { it.first }.map { it.second }
    return EmployeePerformanceData(rows)
}

internal fun buildTopSuppliers(
    purchases: List<InvoiceEntity>,
    clients: List<PartyClient>,
    currencyCode: String,
): List<SupplierSummary> {
    val names = clients.associateBy { it.id }
    return purchases.groupBy { it.clientId }.mapNotNull { (clientId, invs) ->
        val totalMinor = sumMinor(invs.mapNotNull { it.functionalMinorOrNull(currencyCode) })
        if (totalMinor == 0L && invs.any { it.functionalMinorOrNull(currencyCode) == null }) return@mapNotNull null
        totalMinor to SupplierSummary(names[clientId]?.name ?: clientId, major(totalMinor, currencyCode), invs.size, currencyCode)
    }.sortedByDescending { it.first }.take(10).map { it.second }
}

internal fun buildShipmentsSummary(shipments: List<com.verto.app.feature.reports.application.port.ReportsShipmentSnapshot>, costs: List<com.verto.app.feature.reports.application.port.ReportsShipmentCostSnapshot>): ShipmentsSummaryData {
    val totalMinor = sumMinor(costs.asSequence().filter { it.status == "ACTUAL" }
        .mapNotNull { Money.parseOrNull(it.baseCurrencyAmount)?.amountMinor }.toList())
    val inTransit = setOf("WAITING_DEPARTURE", "IN_TRANSIT", "AT_STATION", "CUSTOMS")
    val pending = setOf("DRAFT", "READY", "RECEIVING", "ARRIVED", "PARTIAL", "RECEIVED")
    return ShipmentsSummaryData(shipments.size, shipments.count { it.state in inTransit }, shipments.count { it.state == "CLOSED" }, shipments.count { it.state in pending }, major(totalMinor))
}

internal fun buildShrinkage(
    movements: List<InventoryMovementEntity>, items: List<InventoryItemEntity>, from: Long, to: Long, currencyCode: String,
): ShrinkageData {
    val currentCost = items.associateBy({ it.id }, { it.buyPriceMinor })
    val adjustments = movements.filter { it.movementType == MovementType.ADJUST && it.createdAt in from..to && it.quantityAfter < it.quantityBefore }
    val totalMinor = sumMinor(adjustments.map { row ->
        multiplyMinor(currentCost[row.itemId] ?: row.unitPriceMinor, row.quantityBefore - row.quantityAfter)
    })
    return ShrinkageData(major(totalMinor, currencyCode), adjustments.size)
}

internal fun calcChangeMinor(currentMinor: Long, previousMinor: Long): Float =
    if (previousMinor != 0L) percent(Math.subtractExact(currentMinor, previousMinor), previousMinor) else 0f

private fun Long?.orZero(): Long = this ?: 0L
