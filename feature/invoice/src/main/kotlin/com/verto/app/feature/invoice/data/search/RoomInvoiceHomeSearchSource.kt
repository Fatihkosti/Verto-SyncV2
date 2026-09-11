package com.verto.app.feature.invoice.data.search

import com.verto.app.data.local.dao.InvoiceDao
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.InvoiceStatus
import com.verto.app.feature.invoice.application.search.InvoiceHomeSearchCategory
import com.verto.app.feature.invoice.application.search.InvoiceHomeSearchRecord
import com.verto.app.feature.invoice.application.search.InvoiceHomeSearchSource
import com.verto.app.feature.invoice.application.search.InvoiceHomeSearchStatus
import javax.inject.Inject

class RoomInvoiceHomeSearchSource @Inject constructor(
    private val invoiceDao: InvoiceDao,
) : InvoiceHomeSearchSource {
    override suspend fun search(
        organizationId: String,
        numberQuery: String,
        includeSales: Boolean,
        includePurchases: Boolean,
        limit: Int,
    ): List<InvoiceHomeSearchRecord> = invoiceDao
        .searchInvoiceCardsByNumberPrefix(
            organizationId = organizationId,
            numberQuery = numberQuery,
            includeSales = if (includeSales) 1 else 0,
            includePurchases = if (includePurchases) 1 else 0,
            limit = limit,
        )
        .map { row ->
            InvoiceHomeSearchRecord(
                id = row.invoice.id,
                clientId = row.invoice.clientId,
                invoiceNumber = row.invoice.invoiceNumber,
                highlightedItem = row.highlightedItem.orEmpty(),
                totalAmount = row.invoice.totalAmount,
                category = when (row.invoice.category) {
                    InvoiceCategory.SALE -> InvoiceHomeSearchCategory.SALE
                    InvoiceCategory.PURCHASE -> InvoiceHomeSearchCategory.PURCHASE
                },
                status = when (row.invoice.status) {
                    InvoiceStatus.CLOSED_CASH -> InvoiceHomeSearchStatus.CASH
                    InvoiceStatus.CLOSED_CREDIT -> InvoiceHomeSearchStatus.CREDIT
                },
                voided = row.invoice.voided,
                createdAtEpochMillis = row.invoice.createdAt,
            )
        }
}
