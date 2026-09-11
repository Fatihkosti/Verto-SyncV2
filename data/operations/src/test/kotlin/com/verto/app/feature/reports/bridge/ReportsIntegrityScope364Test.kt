package com.verto.app.feature.reports.bridge

import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceLifecycleStatus
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.data.local.entity.InvoiceType
import com.verto.app.data.local.entity.LegacyCurrencyStatus
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.local.entity.PaymentMethod
import com.verto.app.feature.reports.application.model.ReportsFilters
import com.verto.app.utils.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsIntegrityScope364Test {

    @Test
    fun lineAllocation_reconcilesInvoiceRecognitionAfterDiscount() {
        val invoice = invoice(id = "inv", transactionMinor = 9_000L, functionalMinor = 9_000L)
        val lines = listOf(
            line("a", invoice.id, 6_000L, "A"),
            line("b", invoice.id, 4_000L, "B"),
        )

        val allocated = allocateRecognizedRevenueByLine(lines, mapOf(invoice.id to invoice), "SDG")

        assertEquals(5_400L, allocated.getValue("a"))
        assertEquals(3_600L, allocated.getValue("b"))
        assertEquals(invoice.functionalAmountAtRecognitionMinor, sumMinor(allocated.values))
    }

    @Test
    fun categoryRevenue_usesAllocatedRecognition_notGrossLineSnapshot() {
        val invoice = invoice(id = "inv", transactionMinor = 9_000L, functionalMinor = 9_000L)
        val lines = listOf(
            line("a", invoice.id, 6_000L, "A"),
            line("b", invoice.id, 4_000L, "B"),
        )
        val allocated = allocateRecognizedRevenueByLine(lines, mapOf(invoice.id to invoice), "SDG")
        val selected = lines.filter { matchesReportCategory(it.itemCategory, "A") }

        assertEquals(5_400L, sumMinor(selected.map { allocated.getValue(it.id) }))
    }

    @Test
    fun cashierScope_usesStableEmployeeId_andLegacyPaymentFallback() {
        val modern = invoice(id = "modern", createdBy = "u1")
        val other = invoice(id = "other", createdBy = "u2")
        val collectedByOther = invoice(id = "collected-by-other", createdBy = "u2")
        val legacy = invoice(id = "legacy", createdBy = "")
        val payments = listOf(
            payment("p1", modern.id, "u1", "أحمد"),
            payment("p2", legacy.id, "", "أحمد"),
            payment("p3", other.id, "u2", "سارة"),
            payment("p4", collectedByOther.id, "u1", "أحمد"),
        )

        val scoped = scopeSalesInvoices(
            listOf(modern, other, collectedByOther, legacy), payments, ReportsFilters(cashierName = "أحمد"),
        )

        assertEquals(setOf("modern", "legacy"), scoped.map { it.id }.toSet())
    }

    @Test
    fun commission_isConvertedToFunctionalCurrencyFromImmutableRecognition() {
        val invoice = invoice(
            id = "usd", transactionMinor = 10_000L, functionalMinor = 25_000_000L,
            transactionCurrency = "USD", commissionMinor = 1_000L,
        )

        assertEquals(2_500_000L, functionalCommissionMinor(invoice, "SDG"))
    }

    @Test
    fun customComparison_isImmediatelyPrecedingEqualDuration() {
        val current = 1_000_000L to 1_599_999L
        val previous = previousComparisonRange(ReportPeriod.CUSTOM, current.first, current.second)

        assertEquals(400_000L, previous.first)
        assertEquals(999_999L, previous.second)
        assertEquals(current.second - current.first, previous.second - previous.first)
    }


    @Test
    fun segmentedReport_neverClaimsNetProfitWhenSharedExpensesAreUnallocated() {
        val issues = reportNetProfitReliabilityIssues(
            segmentFilterActive = true,
            historicalCostComplete = true,
            salesCurrencyComplete = true,
            returnsCurrencyComplete = true,
        )
        assertTrue(com.verto.app.feature.reports.application.model.NetProfitReliabilityIssue.SEGMENT_FILTER_WITH_UNALLOCATED_EXPENSES in issues)
    }

    @Test
    fun incompleteCurrency_marksNetProfitUnreliable() {
        val issues = reportNetProfitReliabilityIssues(
            segmentFilterActive = false,
            historicalCostComplete = true,
            salesCurrencyComplete = false,
            returnsCurrencyComplete = true,
        )
        assertTrue(com.verto.app.feature.reports.application.model.NetProfitReliabilityIssue.UNKNOWN_OR_MIXED_CURRENCY in issues)
    }

    @Test
    fun unclassifiedCategory_matchesBlankOnly() {
        assertTrue(matchesReportCategory("", "غير مصنف"))
        assertTrue(!matchesReportCategory("فرامل", "غير مصنف"))
    }

    private fun invoice(
        id: String,
        transactionMinor: Long = 10_000L,
        functionalMinor: Long = transactionMinor,
        transactionCurrency: String = "SDG",
        createdBy: String = "",
        commissionMinor: Long = 0L,
    ) = InvoiceEntity(
        id = id,
        invoiceNumber = id.hashCode(),
        clientId = "client",
        organizationId = "org",
        type = InvoiceType.GOODS,
        category = InvoiceCategory.SALE,
        description = "",
        totalAmount = transactionMinor / 100.0,
        totalAmountMinor = transactionMinor,
        transactionCurrencyCode = transactionCurrency,
        functionalCurrencyCode = "SDG",
        transactionAmountMinor = transactionMinor,
        functionalAmountAtRecognitionMinor = functionalMinor,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        dueDate = 0L,
        status = InvoiceStatus.CLOSED_CASH,
        createdBy = createdBy,
        commission = commissionMinor / 100.0,
        commissionMinor = commissionMinor,
        lifecycleStatus = InvoiceLifecycleStatus.POSTED,
    )

    private fun line(id: String, invoiceId: String, revenueMinor: Long, category: String) = InvoiceItemEntity(
        id = id,
        invoiceId = invoiceId,
        itemName = id,
        itemCategory = category,
        quantity = 1,
        sellPrice = revenueMinor / 100.0,
        sellPriceMinor = revenueMinor,
        totalPrice = revenueMinor / 100.0,
        totalPriceMinor = revenueMinor,
        lineRevenueSnapshot = revenueMinor / 100.0,
        lineRevenueSnapshotMinor = revenueMinor,
        lineCostSnapshotMinor = revenueMinor / 2,
        costSnapshotStatus = "KNOWN",
    )

    private fun payment(id: String, invoiceId: String, employeeId: String, employeeName: String) = PaymentEntity(
        id = id,
        invoiceId = invoiceId,
        clientId = "client",
        amount = 100.0,
        amountMinor = 10_000L,
        paymentCurrencyCode = "SDG",
        supplierAmountMinor = 10_000L,
        functionalCashAmountMinor = 10_000L,
        historicalFunctionalAmountMinor = 10_000L,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        paymentMethod = PaymentMethod.CASH,
        employeeId = employeeId,
        employeeName = employeeName,
    )
}
