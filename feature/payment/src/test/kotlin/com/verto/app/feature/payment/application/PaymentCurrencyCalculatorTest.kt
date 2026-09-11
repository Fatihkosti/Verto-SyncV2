package com.verto.app.feature.payment.application

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.feature.payment.domain.model.PaymentInvoice
import com.verto.app.feature.payment.domain.model.PaymentInvoiceCategory
import com.verto.app.feature.payment.domain.model.PaymentMethod
import com.verto.app.feature.payment.domain.model.RecordPaymentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCurrencyCalculatorTest {
    private val invoice = PaymentInvoice(
        id = "inv",
        invoiceNumber = 1,
        clientId = "supplier",
        category = PaymentInvoiceCategory.PURCHASE,
        totalAmount = 100.0,
        totalAmountMinor = 10_000L,
        transactionCurrencyCode = "USD",
        functionalCurrencyCode = "SDG",
        invoiceExchangeRateSnapshot = "2500",
        functionalAmountAtRecognitionMinor = 25_000_000L,
        legacyCurrencyStatus = "KNOWN",
    )

    @Test
    fun `two foreign payments close transaction balance and realize fx loss`() {
        val first = PaymentCurrencyCalculator.calculate(invoice, command(40.0, 2500.0, "p1"))
        val second = PaymentCurrencyCalculator.calculate(invoice, command(60.0, 2600.0, "p2"))

        assertEquals(10_000L, first.supplierAmountMinor + second.supplierAmountMinor)
        assertEquals(10_000_000L, first.functionalCashAmountMinor)
        assertEquals(15_600_000L, second.functionalCashAmountMinor)
        assertEquals(600_000L, second.realizedFxDifferenceMinor)
        assertEquals("LOSS", second.realizedFxResult)
    }

    @Test
    fun `third currency has stable business code`() {
        val error = runCatching {
            PaymentCurrencyCalculator.calculate(
                invoice,
                command(10.0, 3000.0, "p3").copy(paymentCurrencyCode = "EUR"),
            )
        }.exceptionOrNull()

        assertTrue(error is BusinessRuleFailureException)
        assertEquals(
            PaymentFailureCodes.PAYMENT_CURRENCY_UNSUPPORTED,
            (error as BusinessRuleFailureException).code,
        )
    }

    @Test
    fun `unknown legacy international currency has stable business code`() {
        val error = runCatching {
            PaymentCurrencyCalculator.calculate(
                invoice.copy(legacyCurrencyStatus = "UNKNOWN"),
                command(10.0, 2500.0, "p4"),
            )
        }.exceptionOrNull()

        assertTrue(error is BusinessRuleFailureException)
        assertEquals(
            PaymentFailureCodes.INVOICE_CURRENCY_UNKNOWN,
            (error as BusinessRuleFailureException).code,
        )
    }

    @Test
    fun `missing payment exchange rate has stable business code`() {
        val error = runCatching {
            PaymentCurrencyCalculator.calculate(
                invoice,
                command(10.0, 2500.0, "p5").copy(paymentExchangeRate = null),
            )
        }.exceptionOrNull()

        assertTrue(error is BusinessRuleFailureException)
        assertEquals(
            PaymentFailureCodes.EXCHANGE_RATE_REQUIRED,
            (error as BusinessRuleFailureException).code,
        )
    }

    private fun command(amount: Double, rate: Double, id: String) = RecordPaymentCommand(
        invoiceId = invoice.id,
        clientId = invoice.clientId,
        amount = amount,
        paymentMethod = PaymentMethod.CASH,
        paymentCurrencyCode = "USD",
        paymentExchangeRate = rate,
        exchangeRateTimestamp = 123L,
        requestId = id,
    )
}
