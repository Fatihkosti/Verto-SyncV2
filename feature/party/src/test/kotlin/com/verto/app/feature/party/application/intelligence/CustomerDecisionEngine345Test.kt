package com.verto.app.feature.party.application.intelligence

import com.verto.app.feature.party.domain.model.PartyInvoice
import com.verto.app.feature.party.domain.model.PartyInvoiceCategory
import com.verto.app.feature.party.domain.model.PartyInvoiceFinancialState
import com.verto.app.feature.party.domain.model.PartyInvoiceStatus
import com.verto.app.feature.party.domain.model.PartyInvoiceSummary
import com.verto.app.feature.party.domain.model.PartyInvoiceType
import com.verto.app.feature.party.domain.model.PartyPayment
import com.verto.app.feature.party.domain.model.PartyPaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerDecisionEngine345Test {
    private val day = MILLIS_PER_DAY

    @Test
    fun `good settled history auto approves credit`() {
        val rows = (0 until 4).map { i ->
            val created = i * 30L * day
            paidInvoice("i$i", created, created + 20 * day, created + 20 * day)
        }
        val result = CustomerDecisionEngine.evaluate(rows, now = 150 * day)
        assertEquals(CustomerCreditDecision.ALLOW_CREDIT, result.creditDecision)
        assertEquals(listOf("GOOD_SETTLEMENT_HISTORY"), result.creditReasons)
    }

    @Test
    fun `severely overdue balance forces cash only`() {
        val row = unpaidInvoice("late", created = 0L, due = 10 * day)
        val result = CustomerDecisionEngine.evaluate(listOf(row), now = 50 * day)
        assertEquals(CustomerCreditDecision.CASH_ONLY, result.creditDecision)
        assertEquals(CustomerRecommendedAction.COLLECT_OVERDUE, result.recommendedAction)
        assertTrue(result.creditReasons.contains("SEVERELY_OVERDUE_NOW"))
        assertEquals(40, result.maxCurrentDaysOverdue)
    }

    @Test
    fun `unknown legacy currency fails closed`() {
        val base = unpaidInvoice("legacy", created = 0L, due = 10 * day)
        val legacy = base.copy(invoice = base.invoice.copy(transactionCurrencyCode = "", legacyCurrencyKnown = false))
        val result = CustomerDecisionEngine.evaluate(listOf(legacy), now = 20 * day)
        assertFalse(result.dataComplete)
        assertEquals(CustomerCreditDecision.REQUIRES_APPROVAL, result.creditDecision)
        assertTrue(result.creditReasons.contains("INCOMPLETE_FINANCIAL_HISTORY"))
    }

    @Test
    fun `repurchase prediction uses median interval not fixed ninety days`() {
        val rows = listOf(0L, 20L, 40L, 60L).mapIndexed { index, d -> cashInvoice("c$index", d * day) }
        val result = CustomerDecisionEngine.evaluate(rows, now = 82 * day)
        assertEquals(20, result.typicalRepurchaseDays)
        assertEquals(80 * day, result.predictedNextPurchaseAt)
        assertEquals(CustomerRepurchaseState.DUE, result.repurchaseState)
        assertEquals(CustomerRecommendedAction.FOLLOW_UP_REPURCHASE, result.recommendedAction)
    }

    @Test
    fun `multiple transaction currencies cannot auto approve`() {
        val rows = (0 until 4).map { i ->
            val currency = if (i == 3) "USD" else "SDG"
            paidInvoice("m$i", i * 30L * day, i * 30L * day + 20 * day, i * 30L * day + 20 * day, currency)
        }
        val result = CustomerDecisionEngine.evaluate(rows, now = 150 * day)
        assertEquals(CustomerCreditDecision.REQUIRES_APPROVAL, result.creditDecision)
        assertTrue(result.creditReasons.contains("MULTI_CURRENCY_REQUIRES_EXPLICIT_POLICY"))
    }

    private fun paidInvoice(id: String, created: Long, due: Long, paidAt: Long, currency: String = "SDG"): PartyInvoiceSummary {
        val payment = PartyPayment(
            id = "p$id", invoiceId = id, clientId = "c", amount = 100.0,
            amountMinor = 10_000L, currencyCode = currency, currencyKnown = true,
            paymentMethod = PartyPaymentMethod.CASH, paidAt = paidAt,
        )
        return summary(id, created, due, currency, listOf(payment), paid = true, credit = true)
    }

    private fun unpaidInvoice(id: String, created: Long, due: Long, currency: String = "SDG") =
        summary(id, created, due, currency, emptyList(), paid = false, credit = true)

    private fun cashInvoice(id: String, created: Long, currency: String = "SDG") =
        summary(id, created, created, currency, emptyList(), paid = true, credit = false)

    private fun summary(
        id: String,
        created: Long,
        due: Long,
        currency: String,
        payments: List<PartyPayment>,
        paid: Boolean,
        credit: Boolean,
    ): PartyInvoiceSummary {
        val paidMajor = payments.sumOf { it.amount }
        return PartyInvoiceSummary(
            invoice = PartyInvoice(
                id = id, invoiceNumber = 1, clientId = "c", type = PartyInvoiceType.GOODS,
                category = PartyInvoiceCategory.SALE, description = "", totalAmount = 100.0,
                createdAt = created, dueDate = due,
                status = if (credit) PartyInvoiceStatus.CLOSED_CREDIT else PartyInvoiceStatus.CLOSED_CASH,
                transactionCurrencyCode = currency, transactionAmountMinor = 10_000L, legacyCurrencyKnown = true,
            ),
            totalPaid = paidMajor,
            payments = payments,
            financial = PartyInvoiceFinancialState(
                remaining = if (paid) 0.0 else 100.0,
                isOverdue = false,
                isPaid = paid,
                progressPercent = if (paid) 1f else 0f,
                isCredit = credit,
            ),
        )
    }
}
