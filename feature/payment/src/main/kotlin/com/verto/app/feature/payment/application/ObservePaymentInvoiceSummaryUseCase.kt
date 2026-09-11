package com.verto.app.feature.payment.application

import com.verto.app.feature.payment.application.port.PaymentInvoiceSummaryPort
import javax.inject.Inject

class ObservePaymentInvoiceSummaryUseCase @Inject constructor(
    private val port: PaymentInvoiceSummaryPort,
) {
    operator fun invoke(invoiceId: String) = port.observeInvoiceSummary(invoiceId)
}
