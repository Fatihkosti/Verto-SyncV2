package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.*
import com.verto.app.feature.party.domain.model.PartyClient
import com.verto.app.feature.reports.application.analytics.CostAllocationEngine
import com.verto.app.feature.reports.application.model.OperationalAlertType
import com.verto.app.feature.reports.application.model.PurchasePriceVarianceData
import com.verto.app.feature.reports.application.model.ReportInvoiceLine
import com.verto.app.feature.reports.application.model.SupplierFxVarianceData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsInvoiceAnalyticsF255Test {

    @Test
    fun realizedMargin_doesNotChangeWhenLaterReplacementPriceChanges() {
        val engine = CostAllocationEngine()
        fun margin(replacementMinor: Long) = engine.computeRealMargins(
            listOf(
                ReportInvoiceLine(
                    inventoryItemId = "item", itemName = "قطعة", revenueMinor = 10_000L,
                    costAtSaleMinor = 6_000L, currentReplacementUnitCostMinor = replacementMinor,
                    quantity = 1, costSnapshotKnown = true, currencyCode = "SDG",
                )
            )
        ).single()

        val before = margin(7_000L)
        val after = margin(9_000L)

        assertEquals(40f, before.realizedMarginPct, 0.001f)
        assertEquals(before.realizedMarginPct, after.realizedMarginPct, 0.001f)
        assertEquals(30f, before.replacementMarginPct, 0.001f)
        assertEquals(10f, after.replacementMarginPct, 0.001f)
    }

    @Test
    fun ppv_neverSumsDifferentTransactionCurrencies() {
        val orders = listOf(order("po-usd", "USD"), order("po-eur", "EUR"))
        val invoices = listOf(
            purchaseInvoice("inv-usd", "USD", "po-usd"),
            purchaseInvoice("inv-eur", "EUR", "po-eur"),
        )
        val items = listOf(invoiceItem("line-usd", "inv-usd"), invoiceItem("line-eur", "inv-eur"))
        val matches = listOf(
            match("m-usd", "inv-usd", "po-usd", 10L),
            match("m-eur", "inv-eur", "po-eur", 10L),
        )
        val lines = listOf(
            matchLine("ml-usd", "m-usd", "line-usd", po = 1_000L, invoice = 1_100L),
            matchLine("ml-eur", "m-eur", "line-eur", po = 2_000L, invoice = 1_900L),
        )

        val result = buildPurchasePriceVariance(orders, matches, lines, invoices, items, 0L..20L)

        assertEquals(listOf("EUR", "USD"), result.groups.map { it.currencyCode })
        assertEquals(-100L, result.groups.first { it.currencyCode == "EUR" }.netVarianceMinor)
        assertEquals(100L, result.groups.first { it.currencyCode == "USD" }.netVarianceMinor)
    }

    @Test
    fun apAging_appliesDebitNotesAndExcludesVoid() {
        val now = 100L * 86_400_000L
        val open = purchaseInvoice("open", "SDG", null, dueDate = now - 40L * 86_400_000L, amountMinor = 10_000L)
        val voided = purchaseInvoice("void", "SDG", null, dueDate = now - 40L * 86_400_000L, amountMinor = 90_000L).copy(
            lifecycleStatus = InvoiceLifecycleStatus.VOID,
            voided = true,
        )
        val debit = returnDocument(open, 3_000L)

        val result = buildAgedPayables(
            suppliers = listOf(PartyClient("supplier", "مورد", "")),
            creditPurchases = listOf(open, voided),
            allocations = emptyList(),
            returnDocuments = listOf(debit),
            functionalCurrencyCode = "SDG",
            window = AgingKpiWindow(periodNetFlowMinor = 14_000L, periodDays = 30, asOf = now),
        )

        assertEquals(7_000L, result.grandTotalMinor)
        assertEquals(15f, result.dpoDays, 0.001f)
        assertEquals(1, result.suppliers.size)
    }


    @Test
    fun apAging_ignoresFuturePaymentsAndReturnsAtHistoricalCutoff() {
        val day = 86_400_000L
        val asOf = 100L * day
        val invoice = purchaseInvoice("historical-open", "SDG", null, dueDate = asOf - 40L * day, amountMinor = 10_000L)
            .copy(createdAt = asOf - 60L * day)
        val futurePayment = PaymentAllocationEntity(
            paymentId = "p-future", invoiceId = invoice.id, allocatedTransactionAmountMinor = 10_000L,
            historicalFunctionalAmountMinor = 10_000L, realizedFxDifferenceMinor = 0L, createdAt = asOf + day,
        )
        val futureReturn = returnDocument(invoice, 10_000L).copy(occurredAt = asOf + 2L * day)

        val result = buildAgedPayables(
            listOf(PartyClient("supplier", "مورد", "")), listOf(invoice), listOf(futurePayment), listOf(futureReturn), "SDG",
            AgingKpiWindow(periodNetFlowMinor = 10_000L, periodDays = 30, asOf = asOf),
        )

        assertEquals(10_000L, result.grandTotalMinor)
        assertEquals(30f, result.dpoDays, 0.001f)
    }

    @Test
    fun supplierPaymentTiming_doesNotBackdateLaterReturn() {
        val day = 86_400_000L
        val invoice = purchaseInvoice("timing", "SDG", null, amountMinor = 10_000L).copy(createdAt = 0L)
        val payment = PaymentAllocationEntity(
            paymentId = "p", invoiceId = invoice.id, allocatedTransactionAmountMinor = 6_000L,
            historicalFunctionalAmountMinor = 6_000L, realizedFxDifferenceMinor = 0L, createdAt = 5L * day,
        )
        val laterReturn = returnDocument(invoice, 4_000L).copy(occurredAt = 20L * day)

        val result = buildSupplierPaymentTiming(
            listOf(invoice), listOf(PartyClient("supplier", "مورد", "")), listOf(payment), listOf(laterReturn), 0L, 30L * day,
        )

        assertEquals(20f, result.portfolioAverageDays, 0.001f)
    }

    @Test
    fun supplierPaymentTiming_excludesFullyReturnedInvoice() {
        val day = 86_400_000L
        val invoice = purchaseInvoice("fully-returned", "SDG", null, amountMinor = 10_000L).copy(createdAt = 0L)
        val fullReturn = returnDocument(invoice, 10_000L).copy(occurredAt = 10L * day)

        val result = buildSupplierPaymentTiming(
            listOf(invoice), listOf(PartyClient("supplier", "مورد", "")), emptyList(), listOf(fullReturn), 0L, 30L * day,
        )

        assertTrue(result.suppliers.isEmpty())
        assertEquals(0f, result.portfolioAverageDays, 0.001f)
    }

    @Test
    fun dueAlert_ignoresFutureSettlementFacts() {
        val day = 86_400_000L
        val asOf = 100L * day
        val invoice = purchaseInvoice("due", "SDG", null, dueDate = asOf - day, amountMinor = 10_000L)
            .copy(createdAt = asOf - 10L * day)
        val futurePayment = PaymentAllocationEntity(
            paymentId = "p-future-alert", invoiceId = invoice.id, allocatedTransactionAmountMinor = 10_000L,
            historicalFunctionalAmountMinor = 10_000L, realizedFxDifferenceMinor = 0L, createdAt = asOf + day,
        )
        val alerts = buildOperationalAnalyticsAlerts(
            facts = OperationalAlertFacts(
                invoices = listOf(invoice), clients = listOf(PartyClient("supplier", "مورد", "")),
                allocations = listOf(futurePayment), returnDocuments = emptyList(),
                receiptMatching = ReceiptMatchAlertFacts(emptyList(), emptyList(), emptyList()),
            ),
            ppv = PurchasePriceVarianceData(), fx = SupplierFxVarianceData(), outbox = emptyList(),
            resolveString = { id ->
                when (id) {
                    com.verto.data.operations.R.string.dataops_v298_3ed5a965178f -> "استحقاق قريب"
                    com.verto.data.operations.R.string.dataops_v298_3ed5a965178f_2 -> "استحقاق متأخر"
                    else -> error("Unexpected string resource: $id")
                }
            },
            now = asOf,
        )

        assertTrue(alerts.any { it.type == OperationalAlertType.DUE && it.relatedId == invoice.id })
    }

    @Test
    fun kpiCatalog_documentsCurrencyVoidAndReturnsPolicy() {
        val definitions = com.verto.app.feature.reports.application.model.InvoiceAnalyticsKpiCatalog.definitions
        assertTrue(definitions.size >= 8)
        assertTrue(definitions.all { it.currencyPolicy.isNotBlank() && it.voidPolicy.isNotBlank() && it.returnsPolicy.isNotBlank() })
        assertTrue(definitions.any { it.code == "CURRENT_REPLACEMENT_MARGIN" && it.label.contains("Replacement") })
        assertTrue(definitions.any { it.code == "REALIZED_GROSS_MARGIN" })
    }

    private fun order(id: String, currency: String) = PurchaseOrderEntity(
        id = id, organizationId = "org", orderNumber = id, supplierId = "supplier",
        purchaseScope = "LOCAL", currencyCode = currency, status = "OPEN", createdAt = 1L,
        createdBy = "u", createdByName = "U", writeId = "w-$id",
    )

    private fun match(id: String, invoiceId: String, orderId: String, at: Long) = PurchaseInvoiceMatchEntity(
        id = id, organizationId = "org", invoiceId = invoiceId, purchaseOrderId = orderId,
        status = "MATCHED", quantityVarianceUnits = 0, priceVarianceMinor = 0L,
        quantityToleranceUnits = 0, priceToleranceMinor = 0L, invoiceAmountMinor = 0L,
        payableAmountMinor = 0L, matchedAt = at, writeId = "w-$id",
    )

    private fun matchLine(id: String, matchId: String, invoiceItemId: String, po: Long, invoice: Long) = PurchaseInvoiceMatchLineEntity(
        id = id, matchId = matchId, invoiceItemId = invoiceItemId, purchaseOrderLineId = "po-line-$id",
        orderedQuantity = 1, acceptedQuantity = 1, invoicedQuantity = 1,
        poUnitPriceMinor = po, invoiceUnitPriceMinor = invoice,
        quantityVarianceUnits = 0, priceVarianceMinor = invoice - po, payableAmountMinor = invoice,
    )

    private fun invoiceItem(id: String, invoiceId: String) = InvoiceItemEntity(
        id = id, invoiceId = invoiceId, itemName = id, inventoryItemId = "inventory-$id",
    )

    private fun purchaseInvoice(
        id: String,
        currency: String,
        orderId: String?,
        dueDate: Long = 0L,
        amountMinor: Long = 10_000L,
        createdAt: Long = 1L,
    ) = InvoiceEntity(
        id = id, invoiceNumber = id.hashCode(), clientId = "supplier", organizationId = "org",
        type = InvoiceType.GOODS, category = InvoiceCategory.PURCHASE, description = "",
        totalAmount = amountMinor / 100.0, totalAmountMinor = amountMinor,
        transactionCurrencyCode = currency, functionalCurrencyCode = "SDG",
        transactionAmountMinor = amountMinor, functionalAmountAtRecognitionMinor = amountMinor,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN, dueDate = dueDate,
        status = InvoiceStatus.CLOSED_CREDIT, purchaseOrderId = orderId,
        lifecycleStatus = InvoiceLifecycleStatus.POSTED,
        createdAt = createdAt,
    )

    private fun returnDocument(invoice: InvoiceEntity, amountMinor: Long) = InvoiceReturnDocumentEntity(
        id = "ret-${invoice.id}", organizationId = "org", originalInvoiceId = invoice.id,
        clientId = invoice.clientId, documentType = "PURCHASE_RETURN_DEBIT_NOTE", settlementMode = "CREDIT_BALANCE",
        transactionCurrencyCode = invoice.transactionCurrencyCode, functionalCurrencyCode = invoice.functionalCurrencyCode,
        transactionAmountMinor = amountMinor, functionalAmountMinor = amountMinor, reason = "test",
        occurredAt = 1L, recordedAt = 1L, createdBy = "u", createdByName = "U", writeId = "w-ret-${invoice.id}",
    )
}
