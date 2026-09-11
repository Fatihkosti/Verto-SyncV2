package com.verto.app.feature.payment.application.command

import com.verto.app.feature.payment.application.BulkPaymentCoordinator
import com.verto.app.feature.payment.domain.model.BulkPaymentCommand
import com.verto.app.feature.payment.domain.model.BulkPaymentResult
import com.verto.app.feature.payment.domain.model.BulkPaymentTarget
import javax.inject.Inject

/** Compatibility entry point for existing client and supplier payment screens. */
class BulkPaymentAllocator @Inject constructor(
    private val coordinator: BulkPaymentCoordinator
) {
    data class Target(
        val invoiceId: String,
        val invoiceNumber: Int,
        val remaining: Double
    )

    sealed class Result {
        data class Success(val applied: Double, val surplusCredit: Double) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(
        clientId: String,
        clientName: String,
        amount: Double,
        targets: List<Target>,
        note: String,
        moneyIn: Boolean,
        rate: Double = 1.0,
        requireSupplierPermission: Boolean = false,
        requestId: String,
    ): Result = when (
        val result = coordinator.allocate(
            BulkPaymentCommand(
                clientId = clientId,
                clientName = clientName,
                amount = amount,
                targets = targets.map {
                    BulkPaymentTarget(it.invoiceId, it.invoiceNumber, it.remaining)
                },
                note = note,
                moneyIn = moneyIn,
                rate = rate,
                requireSupplierPermission = requireSupplierPermission,
                requestId = requestId,
            )
        )
    ) {
        is BulkPaymentResult.Success -> Result.Success(result.applied, result.surplusCredit)
        is BulkPaymentResult.Error -> Result.Error(result.message)
    }
}
