package com.verto.app.feature.payment.domain.port

import com.verto.app.feature.payment.domain.model.PaymentOperationResult

interface PaymentReversalPort {
    suspend fun reverse(paymentId: String): PaymentOperationResult
}
