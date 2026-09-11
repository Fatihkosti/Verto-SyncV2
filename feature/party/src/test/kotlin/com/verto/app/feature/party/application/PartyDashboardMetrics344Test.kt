package com.verto.app.feature.party.application

import com.verto.app.feature.party.application.ledger.PartyLedgerEngine
import com.verto.app.feature.party.domain.ledger.LedgerEvent
import com.verto.app.feature.party.domain.ledger.LedgerEventKey
import com.verto.app.feature.party.domain.ledger.LedgerEventType
import com.verto.app.feature.party.domain.ledger.LedgerSide
import com.verto.app.feature.party.domain.model.PartyInvoice
import com.verto.app.feature.party.domain.model.PartyInvoiceCategory
import com.verto.app.feature.party.domain.model.PartyInvoiceFinancialState
import com.verto.app.feature.party.domain.model.PartyInvoiceItem
import com.verto.app.feature.party.domain.model.PartyInvoiceStatus
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import com.verto.app.feature.party.domain.model.PartyInvoiceType
import com.verto.app.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyDashboardMetrics344Test {
    @Test
    fun `ledger dashboard never collapses different currencies`() {
        val engine = PartyLedgerEngine()
        val ledger = engine.build(
            partyId = "p",
            side = LedgerSide.CUSTOMER,
            events = listOf(
                event("usd-invoice", 10_000, "USD", LedgerEventType.INVOICE),
                event("usd-payment", -4_000, "USD", LedgerEventType.PAYMENT),
                event("sdg-invoice", 20_000, "SDG", LedgerEventType.INVOICE),
            ),
            fromInclusive = 0,
            toExclusive = 100,
        )

        val result = PartyDashboardMetricsCalculator.ledger(ledger)

        assertEquals(listOf("SDG", "USD"), result.closingByCurrency.map { it.currencyCode })
        assertEquals(listOf(20_000L, 6_000L), result.closingByCurrency.map { it.amountMinor })
        assertEquals(listOf(20_000L, 10_000L), result.invoicedByCurrency.map { it.amountMinor })
        assertEquals(listOf("USD"), result.paidByCurrency.map { it.currencyCode })
        assertEquals(4_000L, result.paidByCurrency.single().amountMinor)
        assertEquals(PartyBalanceDirection.RECEIVABLE, result.direction)
    }

    @Test
    fun `customer turnover stays separated and profit fails closed for unknown cost history`() {
        val usd = summary("usd", "USD", 10_000, 500)
        val sdg = summary("sdg", "SDG", 20_000, 0)
        val items = listOf(
            line("usd", revenue = 10_000, cost = 6_000, status = "KNOWN"),
            line("sdg", revenue = 20_000, cost = 12_000, status = "LEGACY_UNKNOWN"),
        )

        val result = PartyDashboardMetricsCalculator.customerCommercial(listOf(usd, sdg), items)

        assertEquals(listOf("SDG", "USD"), result.turnoverByCurrency.map { it.currencyCode })
        assertEquals(listOf(20_000L, 9_500L), result.turnoverByCurrency.map { it.amountMinor })
        assertFalse(result.profitComplete)
        assertTrue(result.profitByCurrency.isEmpty())
    }

    private fun event(id: String, minor: Long, currency: String, type: LedgerEventType) = LedgerEvent(
        key = LedgerEventKey(type.name, id, "MAIN"),
        partyId = "p",
        side = LedgerSide.CUSTOMER,
        type = type,
        delta = Money.ofMinor(minor, currency),
        occurredAt = 10,
        recordedAt = 10,
        eventId = id,
    )

    private fun summary(id: String, currency: String, amount: Long, commission: Long) = PartyInvoiceSummary(
        invoice = PartyInvoice(
            id = id,
            invoiceNumber = 1,
            clientId = "p",
            type = PartyInvoiceType.GOODS,
            category = PartyInvoiceCategory.SALE,
            description = "",
            totalAmount = amount / 100.0,
            createdAt = 1,
            dueDate = 0,
            status = PartyInvoiceStatus.CLOSED_CASH,
            commission = commission / 100.0,
            transactionCurrencyCode = currency,
            transactionAmountMinor = amount,
            commissionMinor = commission,
            functionalCurrencyCode = currency,
            functionalAmountAtRecognitionMinor = amount,
            legacyCurrencyKnown = true,
        ),
        totalPaid = 0.0,
        financial = PartyInvoiceFinancialState(0.0, false, true, 1f, false),
    )

    private fun line(invoiceId: String, revenue: Long, cost: Long, status: String) = PartyInvoiceItem(
        id = "$invoiceId-line",
        invoiceId = invoiceId,
        itemName = "x",
        lineRevenueSnapshotMinor = revenue,
        lineCostSnapshotMinor = cost,
        grossProfitSnapshotMinor = revenue - cost,
        costSnapshotStatus = status,
    )
}
