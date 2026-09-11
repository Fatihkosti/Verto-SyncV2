package com.verto.app.feature.payment.application

import com.verto.app.core.error.ErrorPresentationContext
import com.verto.app.core.error.UserErrorPresentation
import com.verto.app.feature.payment.domain.model.PaymentOperationResult
import com.verto.app.feature.payment.domain.model.ReversePaymentCommand
import com.verto.app.feature.payment.domain.port.PaymentReversalPort
import com.verto.app.utils.UserErrorFactory
import java.util.UUID
import javax.inject.Inject

/** Compatibility entry point for existing screens. Business rules live in [ReversePaymentCoordinator]. */
class ReversePaymentUseCase @Inject constructor(
    private val coordinator: ReversePaymentCoordinator
) : PaymentReversalPort {
    sealed class Result {
        data class Success(val reversalId: String) : Result()
        data class Error(val presentation: UserErrorPresentation) : Result()
    }

    override suspend fun reverse(paymentId: String): PaymentOperationResult =
        coordinator.reverse(
            ReversePaymentCommand(
                paymentId = paymentId,
                requestId = UUID.nameUUIDFromBytes("payment-reversal:$paymentId".toByteArray()).toString()
            )
        )

    suspend operator fun invoke(
        paymentId: String,
        requestId: String = UUID.nameUUIDFromBytes("payment-reversal:$paymentId".toByteArray()).toString()
    ): Result = when (
        val result = coordinator.reverse(ReversePaymentCommand(paymentId, requestId))
    ) {
        is PaymentOperationResult.Success -> Result.Success(result.paymentId)
        is PaymentOperationResult.Error -> Result.Error(
            presentation = result.cause?.let { cause ->
                UserErrorFactory.from(
                    throwable = cause,
                    context = ErrorPresentationContext.TRANSIENT_ACTION,
                    operation = "reverse_payment",
                    outcome = result.outcome,
                )
            } ?: UserErrorFactory.from(
                failure = result.failure,
                context = ErrorPresentationContext.TRANSIENT_ACTION,
                operation = "reverse_payment",
                outcome = result.outcome,
            )
        )
    }
}
