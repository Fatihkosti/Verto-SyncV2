package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.domain.model.InvoiceCashMovementType
import com.verto.app.feature.invoice.domain.model.InvoicePayment
import com.verto.app.feature.invoice.domain.model.InvoicePaymentAllocation
import com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMethod
import com.verto.app.feature.invoice.domain.model.InvoicePaymentMode
import com.verto.app.feature.invoice.domain.model.InvoiceRecord
import com.verto.app.feature.invoice.domain.model.InvoiceStatus
import com.verto.app.feature.invoice.domain.model.SaveInvoiceCommand
import com.verto.app.feature.invoice.domain.port.InvoiceCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceStorePort
import com.verto.app.money.ExchangeRate
import com.verto.app.money.Money
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

internal data class InvoiceCreatePaymentRequest(
    val command: SaveInvoiceCommand,
    val invoice: InvoiceRecord,
    val actor: InvoiceActor,
    val invoiceId: String,
)

internal data class InvoiceEditPaymentRequest(
    val command: SaveInvoiceCommand,
    val invoice: InvoiceRecord,
    val actor: InvoiceActor,
    val invoiceId: String,
    val oldInvoice: InvoiceRecord?,
)

internal class InvoicePaymentWriter @Inject constructor(
    private val store: InvoiceStorePort,
    private val cash: InvoiceCashPort,
) {

    suspend fun writeForCreate(request: InvoiceCreatePaymentRequest) {
        when (request.command.paymentMode) {
            InvoicePaymentMode.CASH -> writeCreateCash(request)
            InvoicePaymentMode.CREDIT -> writeCreateCredit(request)
        }
    }

    suspend fun writeForEdit(request: InvoiceEditPaymentRequest) {
        writeEditCashMovement(request)
        writeEditPaymentRecords(request)
    }

    private suspend fun writeCreateCash(request: InvoiceCreatePaymentRequest) {
        val amount = Money.ofMinor(request.invoice.totalAmountMinor, request.invoice.transactionCurrencyCode)
        val payment = payment(request, amount, "سداد كاش فوري")
        store.addPayment(payment)
        store.addPaymentAllocation(allocation(request.invoice, payment, request.command.writeId))

        val rate = exchangeRate(request.command)
        val cashAmount = Money.ofMinor(payment.functionalCashAmountMinor, request.invoice.functionalCurrencyCode)
        val note = exchangeNote(amount, rate)
        if (request.command.isSale) {
            cash.onSaleCash(cashAmount.toLegacyDouble(), request.invoiceId, request.command.writeId)
        } else {
            cash.recordMovement(
                InvoiceCashMovementType.PURCHASE_CASH,
                cashAmount.toLegacyDouble(),
                request.invoiceId,
                note,
                request.command.writeId,
            )
        }
    }

    private suspend fun writeCreateCredit(request: InvoiceCreatePaymentRequest) {
        val initialPayment = Money.ofMinor(request.command.initialPayment.amountMinor, request.invoice.transactionCurrencyCode)
        if (!initialPayment.isPositive()) return
        val payment = payment(request, initialPayment, "دفعة مقدمة عند الإنشاء")
        store.addPayment(payment)
        store.addPaymentAllocation(allocation(request.invoice, payment, request.command.writeId))

        val rate = exchangeRate(request.command)
        val localAmount = Money.ofMinor(payment.functionalCashAmountMinor, request.invoice.functionalCurrencyCode)
        val note = exchangeNote(initialPayment, rate)
        if (request.command.isSale) {
            cash.onPaymentReceived(localAmount.toLegacyDouble(), request.invoiceId, request.command.writeId)
        } else {
            cash.recordMovement(
                InvoiceCashMovementType.PAYMENT_MADE,
                localAmount.toLegacyDouble(),
                request.invoiceId,
                note,
                request.command.writeId,
            )
        }
    }

    private suspend fun writeEditCashMovement(request: InvoiceEditPaymentRequest) {
        val wasCash = request.oldInvoice?.status == InvoiceStatus.CLOSED_CASH
        val isCash = request.invoice.status == InvoiceStatus.CLOSED_CASH
        val oldCurrency = request.oldInvoice?.functionalCurrencyCode?.takeIf { it.isNotBlank() }
            ?: request.invoice.functionalCurrencyCode
        val oldAmount = Money.ofMinor(
            request.oldInvoice?.functionalAmountAtRecognitionMinor ?: 0L, oldCurrency,
        )
        val newAmount = Money.ofMinor(
            request.invoice.functionalAmountAtRecognitionMinor, request.invoice.functionalCurrencyCode,
        )
        val note = "تعديل فاتورة #${request.invoice.invoiceNumber}"
        when {
            wasCash && isCash -> reconcileCashDifference(request, newAmount - oldAmount, note)
            wasCash && !isCash -> reverseOldCash(request, oldAmount, note)
            !wasCash && isCash -> addNewCash(request, newAmount)
            else -> Unit
        }
    }

    private suspend fun reconcileCashDifference(
        request: InvoiceEditPaymentRequest,
        difference: Money,
        note: String,
    ) {
        when {
            difference.isPositive() -> addNewCash(request, difference)
            difference.isNegative() -> reverseOldCash(request, difference.negate(), note)
        }
    }

    private suspend fun addNewCash(request: InvoiceEditPaymentRequest, amount: Money) {
        val legacy = amount.toLegacyDouble()
        if (request.command.isSale) cash.onSaleCash(legacy, request.invoiceId, request.command.writeId)
        else cash.onPurchaseCash(legacy, request.invoiceId, request.command.writeId)
    }

    private suspend fun reverseOldCash(
        request: InvoiceEditPaymentRequest,
        amount: Money,
        note: String,
    ) {
        val type = if (request.command.isSale) {
            InvoiceCashMovementType.SALE_CASH
        } else {
            InvoiceCashMovementType.PURCHASE_CASH
        }
        cash.reverseMovement(type, amount.toLegacyDouble(), request.invoiceId, note, request.command.writeId)
    }

    private suspend fun writeEditPaymentRecords(request: InvoiceEditPaymentRequest) {
        val wasCash = request.oldInvoice?.status == InvoiceStatus.CLOSED_CASH
        val isCash = request.invoice.status == InvoiceStatus.CLOSED_CASH
        val initialPayment = request.command.initialPayment
        val invoiceAmount = Money.ofMinor(request.invoice.totalAmountMinor)
        when {
            wasCash && !isCash -> {
                store.deletePayments(request.invoiceId)
                if (initialPayment.isPositive()) {
                    persistEditPayment(request, Money.ofMinor(initialPayment.amountMinor, request.invoice.transactionCurrencyCode), "دفعة مقدمة عند التعديل لآجل")
                }
            }
            !wasCash && isCash -> {
                store.deletePayments(request.invoiceId)
                persistEditPayment(request, Money.ofMinor(invoiceAmount.amountMinor, request.invoice.transactionCurrencyCode), "سداد كاش (بعد التعديل)")
            }
            wasCash && isCash -> {
                store.deletePayments(request.invoiceId)
                persistEditPayment(request, Money.ofMinor(invoiceAmount.amountMinor, request.invoice.transactionCurrencyCode), "سداد كاش (مُعدل)")
            }
            else -> Unit
        }
    }

    private fun payment(
        request: InvoiceCreatePaymentRequest,
        amount: Money,
        note: String,
    ): InvoicePayment = InvoicePayment(
        id = UUID.nameUUIDFromBytes(
            "invoice-create-payment|${request.invoiceId}|${request.command.writeId}".toByteArray(StandardCharsets.UTF_8)
        ).toString(),
        invoiceId = request.invoiceId,
        clientId = request.command.clientId,
        employeeId = request.actor.id,
        employeeName = request.actor.name,
        sourceType = "INVOICE",
        sourceId = request.invoiceId,
        sourceVersion = 1,
        writeId = request.command.writeId,
        amount = amount.toLegacyDouble(),
        amountMinor = amount.amountMinor,
        paymentCurrencyCode = request.invoice.transactionCurrencyCode,
        supplierAmountMinor = amount.amountMinor,
        paymentExchangeRate = request.command.exchangeRate.asDecimal().toPlainString(),
        paymentExchangeRateTimestamp = request.command.exchangeRateTimestamp,
        paymentExchangeRateSource = request.command.exchangeRateSource,
        functionalCashAmountMinor = request.command.exchangeRate.convert(amount).amountMinor,
        historicalFunctionalAmountMinor = request.command.exchangeRate.convert(amount).amountMinor,
        realizedFxDifferenceMinor = 0L,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        paymentMethod = InvoicePaymentMethod.CASH,
        note = note,
    )

    private fun editPayment(
        request: InvoiceEditPaymentRequest,
        amount: Money,
        note: String,
    ): InvoicePayment = InvoicePayment(
        invoiceId = request.invoiceId,
        clientId = request.command.clientId,
        employeeId = request.actor.id,
        employeeName = request.actor.name,
        sourceType = "INVOICE",
        sourceId = request.invoiceId,
        sourceVersion = 1,
        writeId = request.command.writeId,
        amount = amount.toLegacyDouble(),
        amountMinor = amount.amountMinor,
        paymentCurrencyCode = request.invoice.transactionCurrencyCode,
        supplierAmountMinor = amount.amountMinor,
        paymentExchangeRate = request.command.exchangeRate.asDecimal().toPlainString(),
        paymentExchangeRateTimestamp = request.command.exchangeRateTimestamp,
        paymentExchangeRateSource = request.command.exchangeRateSource,
        functionalCashAmountMinor = request.command.exchangeRate.convert(amount).amountMinor,
        historicalFunctionalAmountMinor = request.command.exchangeRate.convert(amount).amountMinor,
        realizedFxDifferenceMinor = 0L,
        legacyCurrencyStatus = LegacyCurrencyStatus.KNOWN,
        paymentMethod = InvoicePaymentMethod.CASH,
        note = note,
    )

    private suspend fun persistEditPayment(
        request: InvoiceEditPaymentRequest,
        amount: Money,
        note: String,
    ) {
        val payment = editPayment(request, amount, note)
        store.addPayment(payment)
        store.addPaymentAllocation(allocation(request.invoice, payment, request.command.writeId))
    }

    private fun allocation(
        invoice: InvoiceRecord,
        payment: InvoicePayment,
        writeId: String,
    ) = InvoicePaymentAllocation(
        paymentId = payment.id,
        invoiceId = invoice.id,
        allocatedTransactionAmountMinor = payment.supplierAmountMinor,
        historicalFunctionalAmountMinor = payment.historicalFunctionalAmountMinor,
        realizedFxDifferenceMinor = payment.realizedFxDifferenceMinor,
        createdAt = payment.paidAt,
        sourceId = invoice.id,
        writeId = writeId,
    )

    private fun exchangeRate(command: SaveInvoiceCommand): ExchangeRate = command.exchangeRate

    private fun exchangeNote(amount: Money, exchangeRate: ExchangeRate): String =
        if (exchangeRate.asDecimal().compareTo(java.math.BigDecimal.ONE) != 0) {
            "${amount.toPlainString()} × ${exchangeRate.asDecimal().toPlainString()}"
        } else ""
}
