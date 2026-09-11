package com.verto.app.feature.payment.application

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.feature.payment.domain.model.PaymentInvoice
import com.verto.app.feature.payment.domain.model.PaymentInvoiceCategory
import com.verto.app.feature.payment.domain.model.RecordPaymentCommand
import com.verto.app.money.ExchangeRate
import com.verto.app.money.Money
import kotlin.math.absoluteValue

/** F246: immutable currency facts for one payment allocation. */
internal data class PaymentCurrencyFacts(
    val paymentCurrencyCode: String,
    val functionalCurrencyCode: String,
    val supplierAmountMinor: Long,
    val paymentExchangeRate: String,
    val paymentExchangeRateTimestamp: Long,
    val paymentExchangeRateSource: String,
    val functionalCashAmountMinor: Long,
    val historicalFunctionalAmountMinor: Long,
    val realizedFxDifferenceMinor: Long,
    val realizedFxResult: String,
    val legacyCurrencyStatus: String,
) {
    fun functionalCashAsDouble(): Double = Money.ofMinor(
        functionalCashAmountMinor,
        functionalCurrencyCode.ifBlank { Money.TRANSACTION_CURRENCY },
    ).toLegacyDouble()
}

internal object PaymentCurrencyCalculator {
    fun calculate(invoice: PaymentInvoice, command: RecordPaymentCommand): PaymentCurrencyFacts {
        if (!command.amount.isFinite() || command.amount <= 0.0) {
            throw business(PaymentFailureCodes.AMOUNT_INVALID, "amount")
        }
        if (invoice.legacyCurrencyStatus == "UNKNOWN") {
            throw business(PaymentFailureCodes.INVOICE_CURRENCY_UNKNOWN, "invoiceCurrency")
        }

        if (invoice.legacyCurrencyStatus != "KNOWN") {
            val amount = Money.fromLegacyDouble(command.amount)
            val cash = command.cashAmount?.let(Money::fromLegacyDouble) ?: amount
            return PaymentCurrencyFacts(
                paymentCurrencyCode = command.paymentCurrencyCode.trim().uppercase(),
                functionalCurrencyCode = "",
                supplierAmountMinor = amount.amountMinor,
                paymentExchangeRate = "",
                paymentExchangeRateTimestamp = 0L,
                paymentExchangeRateSource = "LEGACY_REVIEW_REQUIRED",
                functionalCashAmountMinor = cash.amountMinor,
                historicalFunctionalAmountMinor = 0L,
                realizedFxDifferenceMinor = 0L,
                realizedFxResult = "NONE",
                legacyCurrencyStatus = "REVIEW_REQUIRED",
            )
        }

        val transactionCurrency = invoice.transactionCurrencyCode.trim().uppercase()
        val functionalCurrency = invoice.functionalCurrencyCode.trim().uppercase()
        if (transactionCurrency.isEmpty() || functionalCurrency.isEmpty()) {
            throw business(PaymentFailureCodes.INVOICE_CURRENCY_INCOMPLETE, "invoiceCurrency")
        }
        val paymentCurrency = command.paymentCurrencyCode.trim().uppercase().ifBlank { transactionCurrency }
        if (paymentCurrency != transactionCurrency) {
            throw business(PaymentFailureCodes.PAYMENT_CURRENCY_UNSUPPORTED, "paymentCurrency")
        }

        val supplierAmount = Money.fromLegacyDouble(command.amount, transactionCurrency)
        val recognitionRateRaw = invoice.invoiceExchangeRateSnapshot.ifBlank {
            if (transactionCurrency == functionalCurrency) {
                "1"
            } else {
                throw business(PaymentFailureCodes.INVOICE_EXCHANGE_RATE_REQUIRED, "invoiceExchangeRate")
            }
        }
        val recognitionRate = runCatching {
            ExchangeRate.parse(recognitionRateRaw, transactionCurrency, functionalCurrency)
        }.getOrElse { cause ->
            throw business(PaymentFailureCodes.INVOICE_EXCHANGE_RATE_INVALID, "invoiceExchangeRate", cause)
        }

        val paymentRate = if (transactionCurrency == functionalCurrency) {
            ExchangeRate.one(transactionCurrency, functionalCurrency)
        } else {
            val raw = command.paymentExchangeRate
                ?: throw business(PaymentFailureCodes.EXCHANGE_RATE_REQUIRED, "paymentExchangeRate")
            runCatching {
                ExchangeRate.fromLegacyDouble(raw, transactionCurrency, functionalCurrency)
            }.getOrElse { cause ->
                throw business(PaymentFailureCodes.EXCHANGE_RATE_INVALID, "paymentExchangeRate", cause)
            }
        }
        val functionalCash = paymentRate.convert(supplierAmount)
        val historical = recognitionRate.convert(supplierAmount)
        command.cashAmount?.let { suppliedCash ->
            val supplied = Money.fromLegacyDouble(suppliedCash, functionalCurrency)
            if (supplied.amountMinor != functionalCash.amountMinor) {
                throw business(PaymentFailureCodes.CASH_AMOUNT_RATE_MISMATCH, "cashAmount")
            }
        }

        val signedDifference = Math.subtractExact(
            functionalCash.amountMinor,
            historical.amountMinor,
        )
        val difference = signedDifference.absoluteValue
        val result = when {
            difference == 0L -> "NONE"
            invoice.category == PaymentInvoiceCategory.PURCHASE && signedDifference > 0L -> "LOSS"
            invoice.category == PaymentInvoiceCategory.PURCHASE -> "GAIN"
            signedDifference > 0L -> "GAIN"
            else -> "LOSS"
        }
        return PaymentCurrencyFacts(
            paymentCurrencyCode = transactionCurrency,
            functionalCurrencyCode = functionalCurrency,
            supplierAmountMinor = supplierAmount.amountMinor,
            paymentExchangeRate = paymentRate.asDecimal().toPlainString(),
            paymentExchangeRateTimestamp = command.exchangeRateTimestamp.takeIf { it > 0L } ?: command.paidAt,
            paymentExchangeRateSource = if (transactionCurrency == functionalCurrency) {
                "FUNCTIONAL_CURRENCY"
            } else {
                command.exchangeRateSource.trim().ifBlank { "USER_INPUT" }
            },
            functionalCashAmountMinor = functionalCash.amountMinor,
            historicalFunctionalAmountMinor = historical.amountMinor,
            realizedFxDifferenceMinor = difference,
            realizedFxResult = result,
            legacyCurrencyStatus = "KNOWN",
        )
    }

    private fun business(code: String, target: String, cause: Throwable? = null): BusinessRuleFailureException =
        BusinessRuleFailureException(code = code, target = target, cause = cause)
}
