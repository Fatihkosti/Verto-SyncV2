package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.*
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.reports.application.model.DataQualityIssue
import com.verto.app.feature.reports.application.model.FinancialDiagnosticsData
import com.verto.app.feature.reports.application.model.InternationalSupplierStatementRow
import com.verto.app.feature.reports.application.model.ReconciliationCheck
import com.verto.app.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

internal fun sumMinor(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)
internal fun multiplyMinor(unitMinor: Long, quantity: Int): Long = Math.multiplyExact(unitMinor, quantity.toLong())
internal fun major(minor: Long, currencyCode: String = Money.TRANSACTION_CURRENCY): Double =
    Money.ofMinor(minor, currencyCode.ifBlank { Money.TRANSACTION_CURRENCY }).toLegacyDouble()

internal fun percent(numeratorMinor: Long, denominatorMinor: Long): Float {
    if (denominatorMinor == 0L) return 0f
    return BigDecimal.valueOf(numeratorMinor)
        .multiply(BigDecimal.valueOf(100L))
        .divide(BigDecimal.valueOf(denominatorMinor), 4, RoundingMode.HALF_UP)
        .toFloat()
}

internal fun scaleMinor(amountMinor: Long, numeratorMinor: Long, denominatorMinor: Long): Long {
    if (amountMinor == 0L || numeratorMinor == 0L) return 0L
    require(denominatorMinor != 0L) { "report scale denominator must not be zero" }
    return BigDecimal.valueOf(amountMinor)
        .multiply(BigDecimal.valueOf(numeratorMinor))
        .divide(BigDecimal.valueOf(denominatorMinor), 0, RoundingMode.HALF_UP)
        .longValueExact()
}

internal fun selectFunctionalCurrency(invoices: Iterable<InvoiceEntity>): String {
    val codes = invoices.asSequence()
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID }
        .filter { it.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN }
        .map { it.functionalCurrencyCode.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
    return codes.singleOrNull().orEmpty()
}

internal fun InvoiceEntity.functionalMinorOrNull(currencyCode: String): Long? {
    if (lifecycleStatus == InvoiceLifecycleStatus.VOID || legacyCurrencyStatus != LegacyCurrencyStatus.KNOWN) return null
    if (currencyCode.isBlank() || functionalCurrencyCode.trim().uppercase() != currencyCode.trim().uppercase()) return null
    return functionalAmountAtRecognitionMinor
}

internal fun InvoiceItemEntity.functionalRevenueMinor(invoice: InvoiceEntity?): Long? {
    if (invoice == null || invoice.lifecycleStatus == InvoiceLifecycleStatus.VOID || invoice.legacyCurrencyStatus != LegacyCurrencyStatus.KNOWN) return null
    val transaction = invoice.transactionAmountMinor
    val functional = invoice.functionalAmountAtRecognitionMinor
    if (transaction == 0L) return if (lineRevenueSnapshotMinor == 0L) 0L else null
    return if (invoice.transactionCurrencyCode.equals(invoice.functionalCurrencyCode, ignoreCase = true)) {
        lineRevenueSnapshotMinor
    } else {
        scaleMinor(lineRevenueSnapshotMinor, functional, transaction)
    }
}

internal fun buildInternationalSupplierStatement(
    purchases: List<InvoiceEntity>,
    clients: List<PartyClient>,
    payments: List<PaymentEntity>,
    allocations: List<PaymentAllocationEntity>,
    returnDocuments: List<InvoiceReturnDocumentEntity>,
): List<InternationalSupplierStatementRow> {
    val clientsById = clients.associateBy { it.id }
    val paymentsById = payments.associateBy { it.id }
    val allocationsByInvoice = allocations.groupBy { it.invoiceId }
    val allocationsByPayment = allocations.groupBy { it.paymentId }
    val purchaseReturnsByInvoice = returnDocuments.asSequence()
        .filter { it.documentType == "PURCHASE_RETURN_DEBIT_NOTE" }
        .groupBy { it.originalInvoiceId }

    return purchases.asSequence()
        .filter { it.purchaseScope == PurchaseScope.INTERNATIONAL }
        .filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID }
        .map { invoice ->
            val invoiceAllocations = allocationsByInvoice[invoice.id].orEmpty()
            val paidTxn = sumMinor(invoiceAllocations.map { it.allocatedTransactionAmountMinor })
            val returnedTxn = sumMinor(
                purchaseReturnsByInvoice[invoice.id].orEmpty()
                    .filter { it.transactionCurrencyCode.equals(invoice.transactionCurrencyCode, ignoreCase = true) }
                    .map { it.transactionAmountMinor }
            )
            val adjustedObligation = Math.subtractExact(invoice.transactionAmountMinor, returnedTxn).coerceAtLeast(0L)
            val remaining = Math.subtractExact(adjustedObligation, paidTxn).coerceAtLeast(0L)
            val functionalCash = sumMinor(invoiceAllocations.map { allocation ->
                val payment = paymentsById[allocation.paymentId]
                if (payment != null && payment.functionalCashAmountMinor != 0L) {
                    val paymentAllocations = allocationsByPayment[payment.id].orEmpty()
                    if (paymentAllocations.size == 1) payment.functionalCashAmountMinor
                    else Math.addExact(allocation.historicalFunctionalAmountMinor, allocation.realizedFxDifferenceMinor)
                } else {
                    Math.addExact(allocation.historicalFunctionalAmountMinor, allocation.realizedFxDifferenceMinor)
                }
            })
            InternationalSupplierStatementRow(
                invoiceId = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                supplierName = clientsById[invoice.clientId]?.name ?: invoice.clientId,
                transactionCurrencyCode = invoice.transactionCurrencyCode,
                originalAmountMinor = invoice.transactionAmountMinor,
                paidTransactionAmountMinor = paidTxn,
                remainingTransactionAmountMinor = remaining,
                functionalCurrencyCode = invoice.functionalCurrencyCode,
                functionalCashPaidMinor = functionalCash,
                realizedFxDifferenceMinor = sumMinor(invoiceAllocations.map { it.realizedFxDifferenceMinor }),
                invoiceExchangeRateSnapshot = invoice.invoiceExchangeRateSnapshot,
            )
        }
        .sortedByDescending { row -> purchases.firstOrNull { it.id == row.invoiceId }?.createdAt ?: 0L }
        .toList()
}

internal fun buildFinancialDiagnostics(
    organizationId: String,
    invoices: List<InvoiceEntity>,
    invoiceItems: List<InvoiceItemEntity>,
    payments: List<PaymentEntity>,
    allocations: List<PaymentAllocationEntity>,
    cashRegister: CashRegisterEntity?,
    cashMovements: List<CashRegisterMovementEntity>,
    inventoryItems: List<InventoryItemEntity>,
    inventoryMovements: List<InventoryMovementEntity>,
    outbox: List<FinancialOutboxEntity>,
    writeGuards: List<InvoiceWriteGuardEntity>,
): FinancialDiagnosticsData {
    val wrongOrganization = if (organizationId.isBlank()) 0 else invoices.count {
        it.organizationId.isNotBlank() && it.organizationId != organizationId
    }
    val scopedInvoices = if (organizationId.isBlank()) invoices else invoices.filter {
        it.organizationId.isBlank() || it.organizationId == organizationId
    }
    val activeInvoices = scopedInvoices.filter { it.lifecycleStatus != InvoiceLifecycleStatus.VOID }
    val scopedInvoiceIds = scopedInvoices.map { it.id }.toSet()
    val scopedPayments = payments.filter { it.invoiceId in scopedInvoiceIds }
    val scopedPaymentIds = scopedPayments.map { it.id }.toSet()
    val scopedAllocations = allocations.filter { it.invoiceId in scopedInvoiceIds && it.paymentId in scopedPaymentIds }
    val allocationsByInvoice = scopedAllocations.groupBy { it.invoiceId }
    val allocationsByPayment = scopedAllocations.groupBy { it.paymentId }

    var invoiceOverAllocatedMinor = 0L
    var invoiceOverAllocatedCount = 0
    activeInvoices.forEach { invoice ->
        val allocated = sumMinor(allocationsByInvoice[invoice.id].orEmpty().map { it.allocatedTransactionAmountMinor })
        val over = Math.subtractExact(allocated, invoice.transactionAmountMinor).coerceAtLeast(0L)
        if (over > 0L) {
            invoiceOverAllocatedMinor = Math.addExact(invoiceOverAllocatedMinor, over)
            invoiceOverAllocatedCount++
        }
    }

    var paymentOverAllocatedMinor = 0L
    var paymentOverAllocatedCount = 0
    scopedPayments.filter { it.reversedPaymentId == null && it.supplierAmountMinor >= 0L }.forEach { payment ->
        val allocated = sumMinor(allocationsByPayment[payment.id].orEmpty().map { it.allocatedTransactionAmountMinor })
        val over = Math.subtractExact(allocated, payment.supplierAmountMinor).coerceAtLeast(0L)
        if (over > 0L) {
            paymentOverAllocatedMinor = Math.addExact(paymentOverAllocatedMinor, over)
            paymentOverAllocatedCount++
        }
    }

    val orderedCash = cashMovements.sortedBy { it.createdAt }
    val cashOpening = orderedCash.firstOrNull()?.balanceBeforeMinor ?: 0L
    val cashComputed = Math.addExact(cashOpening, sumMinor(orderedCash.map { it.amountMinor }))
    val cashActual = cashRegister?.balanceMinor ?: 0L
    val cashDifference = Math.subtractExact(cashActual, cashComputed)

    val movesByItem = inventoryMovements.groupBy { it.itemId }
    var inventoryMismatchCount = 0
    inventoryItems.forEach { item ->
        val movements = movesByItem[item.id].orEmpty().sortedBy { it.createdAt }
        if (movements.isNotEmpty()) {
            val opening = movements.first().quantityBefore.toLong()
            val delta = movements.fold(0L) { acc, movement ->
                Math.addExact(acc, movement.quantityAfter.toLong() - movement.quantityBefore.toLong())
            }
            if (Math.addExact(opening, delta) != item.quantity.toLong()) inventoryMismatchCount++
        }
    }

    // F249 did not backfill historical local write guards. Only guards at/after the first
    // durable outbox event are eligible for strict coverage; older guards are migration-era history.
    val firstOutboxCreatedAt = outbox.minOfOrNull { it.createdAt }
    val outboxIdentity = outbox.map { it.organizationId to it.writeId }.toSet()
    val uncoveredWriteGuards = if (firstOutboxCreatedAt == null) 0 else writeGuards.count { guard ->
        guard.createdAt >= firstOutboxCreatedAt && (guard.organizationId to guard.writeId) !in outboxIdentity
    }

    val functionalCurrency = selectFunctionalCurrency(activeInvoices)
    val inventoryValueMinor = sumMinor(inventoryItems.map { multiplyMinor(it.buyPriceMinor, it.quantity) })
    val unknownCurrencyInvoices = activeInvoices.count {
        it.legacyCurrencyStatus != LegacyCurrencyStatus.KNOWN ||
            it.transactionCurrencyCode.isBlank() || it.functionalCurrencyCode.isBlank()
    }
    val unknownCurrencyPayments = scopedPayments.count {
        it.legacyCurrencyStatus != LegacyCurrencyStatus.KNOWN || it.paymentCurrencyCode.isBlank()
    }
    val activeSaleIds = activeInvoices.filter { it.category == InvoiceCategory.SALE }.map { it.id }.toSet()
    val unknownSaleCostLines = invoiceItems.count {
        it.invoiceId in activeSaleIds && it.costSnapshotStatus == "LEGACY_UNKNOWN"
    }
    val missingIdentity = activeInvoices.count { it.organizationId.isBlank() || it.clientId.isBlank() }
    val functionalCodes = activeInvoices.asSequence()
        .filter { it.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN }
        .map { it.functionalCurrencyCode.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .distinct().toList()
    val mixedFunctionalCurrency = if (functionalCodes.size > 1) functionalCodes.size else 0

    return FinancialDiagnosticsData(
        functionalCurrencyCode = functionalCurrency,
        checks = listOf(
            ReconciliationCheck("INVOICE_ALLOCATION", "الفاتورة = المخصص + المتبقي", invoiceOverAllocatedMinor, invoiceOverAllocatedCount),
            ReconciliationCheck("PAYMENT_ALLOCATION", "الدفعة = المخصص + غير المخصص", paymentOverAllocatedMinor, paymentOverAllocatedCount),
            ReconciliationCheck("CASH_LEDGER", "الصندوق = الافتتاح + الحركات", cashDifference, if (cashDifference == 0L) 0 else 1),
            ReconciliationCheck("INVENTORY_LEDGER", "الكمية = الافتتاح + الحركات", 0L, inventoryMismatchCount),
            ReconciliationCheck("FINANCIAL_OUTBOX", "كل كتابة مالية لها Outbox", 0L, uncoveredWriteGuards),
        ),
        dataQualityIssues = listOf(
            DataQualityIssue("UNKNOWN_INVOICE_CURRENCY", "فواتير تحتاج مراجعة العملة", unknownCurrencyInvoices),
            DataQualityIssue("UNKNOWN_PAYMENT_CURRENCY", "دفعات تحتاج مراجعة العملة", unknownCurrencyPayments),
            DataQualityIssue("UNKNOWN_HISTORICAL_COST", "بنود بيع بلا تكلفة تاريخية موثوقة", unknownSaleCostLines),
            DataQualityIssue("MISSING_FINANCIAL_IDENTITY", "سجلات مالية بهوية ناقصة", missingIdentity),
            DataQualityIssue("MIXED_FUNCTIONAL_CURRENCY", "أكثر من عملة وظيفية داخل النطاق", mixedFunctionalCurrency),
            DataQualityIssue("WRONG_ORGANIZATION", "سجلات من منشأة مختلفة", wrongOrganization),
        ),
        inventoryOperationalValueMinor = inventoryValueMinor,
        outboxRequiresReviewCount = outbox.count { it.syncState == "REQUIRES_REVIEW" },
    )
}
