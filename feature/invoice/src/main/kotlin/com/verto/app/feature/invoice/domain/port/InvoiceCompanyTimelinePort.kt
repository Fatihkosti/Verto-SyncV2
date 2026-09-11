package com.verto.app.feature.invoice.domain.port

import kotlinx.coroutines.flow.Flow

/** Read-only projection owned by Invoice for company timelines. */
data class CompanyInvoiceTimelineItem(
    val organizationId: String,
    val clientId: String,
    val invoiceId: String,
    val invoiceNumber: Int,
    val category: String,
    val status: String,
    val description: String,
    val totalAmount: Double,
    val occurredAt: Long,
    val voided: Boolean,
)

fun interface InvoiceCompanyTimelinePort {
    fun observeCompanyInvoices(
        organizationId: String,
        clientId: String,
    ): Flow<List<CompanyInvoiceTimelineItem>>
}
