package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.PaymentAllocationEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceReceiptAllocationEntity
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.reports.application.model.OperationalAlertAmount
import com.verto.app.feature.reports.application.model.OperationalAlertSeverity
import com.verto.app.feature.reports.application.model.OperationalAlertType
import com.verto.app.feature.reports.application.model.OperationalAnalyticsAlert
import com.verto.app.feature.reports.application.model.PurchasePriceVarianceData
import com.verto.app.feature.reports.application.model.SupplierFxVarianceData
import java.math.BigDecimal
import java.math.RoundingMode

private const val DAY_MS_ALERTS_F255 = 86_400_000L

internal data class ReceiptMatchAlertFacts(
    val matches: List<PurchaseInvoiceMatchEntity>,
    val matchLines: List<PurchaseInvoiceMatchLineEntity>,
    val receiptAllocations: List<PurchaseInvoiceReceiptAllocationEntity>,
)

internal data class OperationalAlertFacts(
    val invoices: List<InvoiceEntity>,
    val clients: List<PartyClient>,
    val allocations: List<PaymentAllocationEntity>,
    val returnDocuments: List<InvoiceReturnDocumentEntity>,
    val receiptMatching: ReceiptMatchAlertFacts,
)

internal fun buildOperationalAnalyticsAlerts(
    facts: OperationalAlertFacts,
    ppv: PurchasePriceVarianceData,
    fx: SupplierFxVarianceData,
    outbox: List<FinancialOutboxEntity>,
    resolveString: (Int) -> String,
    now: Long = System.currentTimeMillis(),
): List<OperationalAnalyticsAlert> {
    val alerts = buildList {
        addAll(buildDueAlerts(facts, now, resolveString))
        addAll(buildPriceAlerts(ppv))
        addAll(buildFxAlerts(fx))
        addAll(buildReceiptMatchAlerts(facts.invoices, facts.receiptMatching))
        buildConflictReviewAlert(outbox)?.let(::add)
    }
    return alerts.distinctBy { it.id }.sortedWith(
        compareByDescending<OperationalAnalyticsAlert> { severityRank(it.severity) }.thenBy { it.id }
    )
}

private fun buildDueAlerts(facts: OperationalAlertFacts, now: Long, resolveString: (Int) -> String): List<OperationalAnalyticsAlert> {
    val names = facts.clients.associateBy({ it.id }, { it.name })
    val allocations = facts.allocations.asSequence().filter { it.createdAt <= now }.groupBy { it.invoiceId }
    val returns = facts.returnDocuments.asSequence().filter { it.occurredAt <= now }.groupBy { it.originalInvoiceId }
    return facts.invoices.asSequence()
        .filter { it.createdAt <= now && it.status == InvoiceStatus.CLOSED_CREDIT }
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID && !it.voided }
        .filter { it.dueDate > 0L && it.dueDate <= now + 7L * DAY_MS_ALERTS_F255 }
        .mapNotNull { invoice ->
            val paid = sumMinor(allocations[invoice.id].orEmpty().map { it.allocatedTransactionAmountMinor })
            val returnType = if (invoice.category == InvoiceCategory.SALE) "SALES_RETURN_CREDIT_NOTE" else "PURCHASE_RETURN_DEBIT_NOTE"
            val returned = sumMinor(
                returns[invoice.id].orEmpty()
                    .filter { it.documentType == returnType && it.transactionCurrencyCode.equals(invoice.transactionCurrencyCode, true) }
                    .map { it.transactionAmountMinor }
            )
            val remaining = Math.subtractExact(invoice.transactionAmountMinor, Math.addExact(paid, returned)).coerceAtLeast(0L)
            if (remaining <= 0L) return@mapNotNull null
            dueAlert(invoice, names[invoice.clientId] ?: invoice.clientId, remaining, now, resolveString)
        }.toList()
}

private fun dueAlert(invoice: InvoiceEntity, partyName: String, remaining: Long, now: Long, resolveString: (Int) -> String): OperationalAnalyticsAlert {
    val overdueDays = ((now - invoice.dueDate) / DAY_MS_ALERTS_F255).toInt()
    val severity = when {
        overdueDays > 30 -> OperationalAlertSeverity.CRITICAL
        overdueDays >= 0 -> OperationalAlertSeverity.WARNING
        else -> OperationalAlertSeverity.INFO
    }
    val label = if (invoice.category == InvoiceCategory.SALE) "عميل" else "مورد"
    return OperationalAnalyticsAlert(
        id = "due:${invoice.id}", type = OperationalAlertType.DUE, severity = severity,
        title = if (overdueDays >= 0) resolveString(com.verto.data.operations.R.string.dataops_v298_3ed5a965178f_2) else resolveString(com.verto.data.operations.R.string.dataops_v298_3ed5a965178f),
        message = "$label $partyName · فاتورة ${invoice.invoiceNumber}", relatedId = invoice.id,
        amount = OperationalAlertAmount(remaining, invoice.transactionCurrencyCode),
    )
}

private fun buildPriceAlerts(ppv: PurchasePriceVarianceData): List<OperationalAnalyticsAlert> =
    ppv.groups.flatMap { it.rows }.asSequence()
        .filter { it.varianceMinor > 0L && it.poUnitPriceMinor > 0L }
        .filter { percentageAtLeast(Math.subtractExact(it.invoiceUnitPriceMinor, it.poUnitPriceMinor), it.poUnitPriceMinor, 5L) }
        .take(10)
        .map { row ->
            OperationalAnalyticsAlert(
                id = "price:${row.itemId}:${row.invoiceUnitPriceMinor}", type = OperationalAlertType.PRICE_INCREASE,
                severity = OperationalAlertSeverity.WARNING, title = "ارتفاع سعر شراء", message = row.itemName,
                relatedId = row.itemId, amount = OperationalAlertAmount(row.varianceMinor, row.currencyCode),
            )
        }.toList()

private fun buildFxAlerts(fx: SupplierFxVarianceData): List<OperationalAnalyticsAlert> =
    fx.groups.flatMap { it.suppliers }.asSequence()
        .filter { it.historicalFunctionalMinor != 0L && it.realizedGainLossMinor != 0L }
        .filter { percentageAtLeast(absExactMinor(it.realizedGainLossMinor), absExactMinor(it.historicalFunctionalMinor), 2L) }
        .take(10)
        .map { row ->
            OperationalAnalyticsAlert(
                id = "fx:${row.supplierId}:${row.currencyCode}", type = OperationalAlertType.FX_VARIANCE,
                severity = OperationalAlertSeverity.WARNING, title = "فرق صرف ملحوظ", message = row.supplierName,
                relatedId = row.supplierId, amount = OperationalAlertAmount(row.realizedGainLossMinor, row.currencyCode),
            )
        }.toList()

private fun buildReceiptMatchAlerts(
    invoices: List<InvoiceEntity>,
    facts: ReceiptMatchAlertFacts,
): List<OperationalAnalyticsAlert> {
    val matchByInvoice = facts.matches.associateBy { it.invoiceId }
    val linesByMatch = facts.matchLines.groupBy { it.matchId }
    val allocatedQtyByLine = facts.receiptAllocations.groupBy { it.matchLineId }
        .mapValues { (_, rows) -> rows.sumOf { it.allocatedQuantity } }
    return invoices.asSequence()
        .filter { it.category == InvoiceCategory.PURCHASE && it.purchaseOrderId != null }
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID && !it.voided }
        .filter { invoice ->
            val match = matchByInvoice[invoice.id] ?: return@filter true
            linesByMatch[match.id].orEmpty().any { line ->
                val expectedBackedQty = minOf(line.invoicedQuantity, line.acceptedQuantity)
                (allocatedQtyByLine[line.id] ?: 0) < expectedBackedQty || line.quantityVarianceUnits > 0
            }
        }
        .map { invoice ->
            OperationalAnalyticsAlert(
                id = "receipt-match:${invoice.id}", type = OperationalAlertType.PURCHASE_WITHOUT_MATCHED_RECEIPT,
                severity = OperationalAlertSeverity.CRITICAL, title = "فاتورة شراء بلا استلام مطابق",
                message = "فاتورة ${invoice.invoiceNumber}", relatedId = invoice.id,
            )
        }.toList()
}

private fun buildConflictReviewAlert(outbox: List<FinancialOutboxEntity>): OperationalAnalyticsAlert? {
    val count = outbox.count { it.syncState == "REQUIRES_REVIEW" }
    if (count == 0) return null
    return OperationalAnalyticsAlert(
        id = "conflict-review", type = OperationalAlertType.CONFLICT_REVIEW,
        severity = OperationalAlertSeverity.CRITICAL, title = "تعارضات مالية تحتاج مراجعة",
        message = "$count عملية متوقفة للمراجعة",
    )
}

private fun percentageAtLeast(numerator: Long, denominator: Long, threshold: Long): Boolean {
    if (denominator <= 0L) return false
    return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP) >= BigDecimal.valueOf(threshold)
}

private fun severityRank(severity: OperationalAlertSeverity): Int = when (severity) {
    OperationalAlertSeverity.CRITICAL -> 3
    OperationalAlertSeverity.WARNING -> 2
    OperationalAlertSeverity.INFO -> 1
}
