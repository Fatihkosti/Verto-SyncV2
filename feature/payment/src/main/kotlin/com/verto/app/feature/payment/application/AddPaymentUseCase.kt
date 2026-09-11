package com.verto.app.feature.payment.application

import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.error.OperationOutcome
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.feature.payment.domain.model.PaymentMethod
import com.verto.app.feature.payment.domain.model.PaymentOperationResult
import com.verto.app.feature.payment.domain.model.RecordPaymentCommand
import com.verto.app.utils.UserErrorFactory
import java.util.UUID
import javax.inject.Inject

/** Compatibility entry point for existing screens. Business rules live in [RecordPaymentCoordinator]. */
class AddPaymentUseCase @Inject constructor(
    private val coordinator: RecordPaymentCoordinator
) {
    sealed class Result {
        data class Success(
            val paymentId: String,
            val outcome: OperationOutcome,
        ) : Result()

        data class Error(val presentation: UserErrorPresentation) : Result()
    }

    suspend operator fun invoke(
        invoiceId: String,
        clientId: String,
        amount: Double,
        paymentMethod: PaymentMethod,
        note: String = "",
        remainingAmount: Double? = null,
        clientName: String = "",
        invoiceNumber: Int = 0,
        paidAt: Long = System.currentTimeMillis(),
        cashAmount: Double? = null,
        paymentCurrencyCode: String = "",
        paymentExchangeRate: Double? = null,
        exchangeRateTimestamp: Long = paidAt,
        exchangeRateSource: String = "USER_INPUT",
        requireSupplierPermission: Boolean = false,
        unreceivedOverrideReason: String? = null,
        requestId: String = UUID.randomUUID().toString()
    ): Result = when (
        val result = coordinator.record(
            RecordPaymentCommand(
                invoiceId = invoiceId,
                clientId = clientId,
                amount = amount,
                paymentMethod = paymentMethod,
                note = note,
                remainingAmount = remainingAmount,
                clientName = clientName,
                invoiceNumber = invoiceNumber,
                paidAt = paidAt,
                cashAmount = cashAmount,
                paymentCurrencyCode = paymentCurrencyCode,
                paymentExchangeRate = paymentExchangeRate,
                exchangeRateTimestamp = exchangeRateTimestamp,
                exchangeRateSource = exchangeRateSource,
                requireSupplierPermission = requireSupplierPermission,
                unreceivedOverrideReason = unreceivedOverrideReason,
                requestId = requestId
            )
        )
    ) {
        is PaymentOperationResult.Success -> Result.Success(result.paymentId, result.outcome)
        is PaymentOperationResult.Error -> Result.Error(
            presentation = result.cause?.let { cause ->
                UserErrorFactory.from(
                    throwable = cause,
                    context = ErrorPresentationContext.FORM,
                    operation = "record_payment",
                    outcome = result.outcome,
                )
            } ?: UserErrorFactory.from(
                failure = result.failure,
                context = ErrorPresentationContext.FORM,
                operation = "record_payment",
                outcome = result.outcome,
            )
        )
    }
}
