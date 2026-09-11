package com.verto.app.feature.invoice.bridge

import com.verto.app.data.local.dao.InvoiceDao
import com.verto.app.feature.invoice.domain.port.CompanyInvoiceTimelineItem
import com.verto.app.feature.invoice.domain.port.InvoiceCompanyTimelinePort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInvoiceCompanyTimelineAdapter @Inject constructor(
    private val invoiceDao: InvoiceDao,
) : InvoiceCompanyTimelinePort {
    override fun observeCompanyInvoices(
        organizationId: String,
        clientId: String,
    ): Flow<List<CompanyInvoiceTimelineItem>> = invoiceDao.getInvoicesForClient(clientId)
        .map { invoices ->
            invoices.map { invoice ->
                CompanyInvoiceTimelineItem(
                    organizationId = organizationId,
                    clientId = invoice.clientId,
                    invoiceId = invoice.id,
                    invoiceNumber = invoice.invoiceNumber,
                    category = invoice.category.name,
                    status = invoice.status.name,
                    description = invoice.description,
                    totalAmount = invoice.totalAmount,
                    occurredAt = invoice.createdAt,
                    voided = invoice.voided,
                )
            }
        }
}
