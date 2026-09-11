package com.verto.app.feature.payment.domain.port

import kotlinx.coroutines.flow.Flow

/** Read-only projection owned by Payment for company timelines. */
data class CompanyPaymentTimelineItem(
    val organizationId: String,
    val clientId: String,
    val paymentId: String,
    val invoiceId: String,
    val amount: Double,
    val paymentMethod: String,
    val note: String,
    val occurredAt: Long,
    val isReversal: Boolean,
)

fun interface PaymentCompanyTimelinePort {
    fun observeCompanyPayments(
        organizationId: String,
        clientId: String,
    ): Flow<List<CompanyPaymentTimelineItem>>
}
