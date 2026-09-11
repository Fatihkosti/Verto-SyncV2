package com.verto.app.feature.payment.application.port

import com.verto.app.feature.payment.application.model.PaymentInvoiceSummaryViewData
import kotlinx.coroutines.flow.Flow

interface PaymentInvoiceSummaryPort {
    fun observeInvoiceSummary(invoiceId: String): Flow<PaymentInvoiceSummaryViewData?>
}
