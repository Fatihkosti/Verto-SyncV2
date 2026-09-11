package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceReturnLineEntity
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.InvoiceType
import com.verto.app.data.local.entity.LegacyCurrencyStatus
import com.verto.app.data.local.entity.PurchaseScope
import com.verto.app.feature.party.domain.model.PartyClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsReturnsF252Test {

    @Test
    fun salesCreditNote_reducesAgedReceivableWithoutRewritingInvoice() {
        val original = invoice(
            id = "sale",
            category = InvoiceCategory.SALE,
            transactionMinor = 10_000L,
            dueDate = System.currentTimeMillis() - 40L * 86_400_000L,
            status = InvoiceStatus.CLOSED_CREDIT,
        )
        val creditNote = returnDocument(
            id = "return-sale",
            invoice = original,
            documentType = "SALES_RETURN_CREDIT_NOTE",
            transactionMinor = 3_000L,
            functionalMinor = 3_000L,
        )

        val result = buildAgedReceivables(
            clients = listOf(PartyClient(id = "client", name = "عميل", phone = "")),
            creditInvoices = listOf(original),
            allocations = emptyList(),
            returnDocuments = listOf(creditNote),
            functionalCurrencyCode = "SDG",
        )

        assertEquals(70.0, result.grandTotal, 0.001)
        assertEquals(10_000L, original.transactionAmountMinor)
    }

    @Test
    fun purchaseDebitNote_reducesInternationalSupplierOutstanding() {
        val original = invoice(
            id = "purchase",
            category = InvoiceCategory.PURCHASE,
            transactionMinor = 10_000L,
            purchaseScope = PurchaseScope.INTERNATIONAL,
        )
        val debitNote = returnDocument(
            id = "return-purchase",
            invoice = original,
            documentType = "PURCHASE_RETURN_DEBIT_NOTE",
            transactionMinor = 3_000L,
            functionalMinor = 7_500_000L,
        )

        val row = buildInternationalSupplierStatement(
            purchases = listOf(original),
            clients = listOf(PartyClient(id = "client", name = "مورد", phone = "")),
            payments = emptyList(),
            allocations = emptyList(),
            returnDocuments = listOf(debitNote),
        ).single()

        assertEquals(10_000L, row.originalAmountMinor)
        assertEquals(7_000L, row.remainingTransactionAmountMinor)
    }

    @Test
    fun returnsCard_usesReturnRevenueNotInventoryCost() {
        val lines = listOf(
            InvoiceReturnLineEntity(
                id = "line-1",
                returnId = "return-sale",
                originalInvoiceItemId = "original-line",
                inventoryItemId = "item",
                itemNameSnapshot = "قطعة",
                quantity = 2,
                unitTransactionAmountMinor = 2_000L,
                transactionAmountMinor = 4_000L,
                unitFunctionalAmountMinor = 2_000L,
                functionalAmountMinor = 4_000L,
                unitCostAtSaleMinor = 1_200L,
                historicalCostAmountMinor = 2_400L,
                originalPurchaseUnitCostMinor = 0L,
            )
        )

        val result = buildReturnsData(lines, returnDocumentCount = 1, grossSalesMinor = 20_000L, currencyCode = "SDG")

        assertEquals(40.0, result.totalReturnValue, 0.001)
        assertEquals(20f, result.returnRate, 0.001f)
        assertEquals(1, result.returnCount)
    }

    private fun invoice(
        id: String,
        category: InvoiceCategory,
        transactionMinor: Long,
        dueDate: Long = 0L,
        status: InvoiceStatus = InvoiceStatus.CLOSED_CASH,
        purchaseScope: PurchaseScope = PurchaseScope.LOCAL,
    ) = InvoiceEntity(
        id = id,
        invoiceNumber = 1,
        clientId = "client",
        organizationId = "org",
        type = InvoiceType.GOODS,
        category = category,
        description = "",
        totalAmount = transactionMinor / 100.0,
        totalAmountMinor = transactionMinor,
        transactionCurrencyCode = if (purchaseScope == PurchaseScope.INTERNATIONAL) "USD" else "SDG",
        functionalCurrencyCode = "SDG",
        transactionAmountMinor = transactionMinor,
        functionalAmountAtRecognitionMinor = if (purchaseScope == PurchaseScope.INTERNATIONAL) 25_000_000L else transactionMinor,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        dueDate = dueDate,
        status = status,
        purchaseScope = purchaseScope,
        lifecycleStatus = InvoiceLifecycleStatus.POSTED,
    )

    private fun returnDocument(
        id: String,
        invoice: InvoiceEntity,
        documentType: String,
        transactionMinor: Long,
        functionalMinor: Long,
    ) = InvoiceReturnDocumentEntity(
        id = id,
        organizationId = invoice.organizationId,
        originalInvoiceId = invoice.id,
        clientId = invoice.clientId,
        documentType = documentType,
        settlementMode = "CREDIT_BALANCE",
        transactionCurrencyCode = invoice.transactionCurrencyCode,
        functionalCurrencyCode = invoice.functionalCurrencyCode,
        transactionAmountMinor = transactionMinor,
        functionalAmountMinor = functionalMinor,
        reason = "test",
        occurredAt = 1L,
        recordedAt = 1L,
        createdBy = "tester",
        createdByName = "Tester",
        writeId = "write-$id",
    )
}
