package com.verto.app.feature.payment.domain.model

import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.OperationOutcome
import java.math.BigDecimal
import java.math.RoundingMode
import com.verto.app.money.Money

enum class PaymentInvoiceCategory { SALE, PURCHASE }
enum class PaymentMethod { CASH, TRANSFER, CHECK }

data class PaymentInvoice(
    val id: String,
    val invoiceNumber: Int,
    val clientId: String,
    val category: PaymentInvoiceCategory,
    val totalAmount: Double,
    val totalAmountMinor: Long = Money.fromLegacyDouble(totalAmount).amountMinor,
    val transactionCurrencyCode: String = "",
    val functionalCurrencyCode: String = "",
    val invoiceExchangeRateSnapshot: String = "",
    val functionalAmountAtRecognitionMinor: Long = 0L,
    val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
    val voided: Boolean = false,
)

data class PaymentRecord(
    val id: String,
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor,
    val paymentCurrencyCode: String = "",
    val supplierAmountMinor: Long = amountMinor,
    val paymentExchangeRate: String = "",
    val paymentExchangeRateTimestamp: Long = 0L,
    val paymentExchangeRateSource: String = "",
    val functionalCashAmountMinor: Long = 0L,
    val historicalFunctionalAmountMinor: Long = 0L,
    val realizedFxDifferenceMinor: Long = 0L,
    val legacyCurrencyStatus: String = "REVIEW_REQUIRED",
    val paymentMethod: PaymentMethod,
    val note: String = "",
    val paidAt: Long = System.currentTimeMillis(),
    val employeeId: String = "",
    val employeeName: String = "",
    val reversedPaymentId: String? = null,
    val isDirty: Boolean = true
)

data class RecordPaymentCommand(
    val invoiceId: String,
    val clientId: String,
    val amount: Double,
    val paymentMethod: PaymentMethod,
    val note: String = "",
    val remainingAmount: Double? = null,
    val clientName: String = "",
    val invoiceNumber: Int = 0,
    val paidAt: Long = System.currentTimeMillis(),
    /** Compatibility only for legacy rows; currency-aware invoices derive cash from paymentExchangeRate. */
    val cashAmount: Double? = null,
    val paymentCurrencyCode: String = "",
    val paymentExchangeRate: Double? = null,
    val exchangeRateTimestamp: Long = paidAt,
    val exchangeRateSource: String = "USER_INPUT",
    val requireSupplierPermission: Boolean = false,
    val unreceivedOverrideReason: String? = null,
    val requestId: String
)

data class PaymentAllocationRecord(
    val id: String,
    val paymentId: String,
    val invoiceId: String,
    val allocatedTransactionAmountMinor: Long,
    val historicalFunctionalAmountMinor: Long,
    val realizedFxDifferenceMinor: Long,
    val createdAt: Long,
    val writeId: String,
)

data class RealizedFxEventRecord(
    val id: String,
    val paymentId: String,
    val invoiceId: String,
    val functionalCurrencyCode: String,
    val historicalFunctionalAmountMinor: Long,
    val functionalCashAmountMinor: Long,
    val differenceMinor: Long,
    val result: String,
    val occurredAt: Long,
    val writeId: String,
)

data class ReversePaymentCommand(
    val paymentId: String,
    val requestId: String
)

/** Durable invoice-aggregate event emitted from the existing Verto payment transaction. */
enum class PaymentIntegrationEventKind {
    RECORDED,
    REVERSED,
}

data class PersistPaymentIntegrationCommand(
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val payment: PaymentRecord,
    val eventKind: PaymentIntegrationEventKind,
    val originalPayment: PaymentRecord? = null,
    val occurredAt: Long,
)

data class BulkPaymentTarget(
    val invoiceId: String,
    val invoiceNumber: Int,
    val remaining: Double
)

data class BulkPaymentCommand(
    val clientId: String,
    val clientName: String,
    val amount: Double,
    val targets: List<BulkPaymentTarget>,
    val note: String,
    val moneyIn: Boolean,
    val rate: Double = 1.0,
    val requireSupplierPermission: Boolean = false,
    val requestId: String
)

data class AdvanceCreditRecord(
    val id: String,
    val clientId: String,
    /** Compatibility projection; [amountMinor] is the domain financial source of truth. */
    val amount: Double,
    val amountMinor: Long = Money.fromLegacyDouble(amount).amountMinor,
    val note: String,
    val createdAt: Long,
    val employeeId: String,
    val employeeName: String,
    val sourcePaymentId: String,
)

sealed interface PaymentOperationResult {
    data class Success(
        val paymentId: String,
        val outcome: OperationOutcome = OperationOutcome.SAVED_LOCALLY_PENDING_SYNC,
    ) : PaymentOperationResult

    data class Error(
        val failure: AppFailure,
        val cause: Throwable? = null,
        val outcome: OperationOutcome = OperationOutcome.NOT_APPLIED,
    ) : PaymentOperationResult
}

sealed interface BulkPaymentResult {
    data class Success(val applied: Double, val surplusCredit: Double) : BulkPaymentResult
    data class Error(val message: String) : BulkPaymentResult
}

object PaymentMoney {
    private const val SCALE = 2
    private val ROUNDING = RoundingMode.HALF_UP

    private fun Double.moneyValue(): BigDecimal {
        require(isFinite()) { "Money value must be finite" }
        return BigDecimal(toString()).setScale(SCALE, ROUNDING)
    }

    fun isGreaterThan(left: Double, right: Double): Boolean =
        left.moneyValue().compareTo(right.moneyValue()) > 0

    fun format(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)
}
