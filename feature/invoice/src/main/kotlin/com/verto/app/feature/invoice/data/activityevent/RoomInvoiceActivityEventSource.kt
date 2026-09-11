package com.verto.app.feature.invoice.data.activityevent

import com.verto.app.data.local.dao.InvoiceDao
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.feature.invoice.application.activityevent.InvoiceActivityCategory
import com.verto.app.feature.invoice.application.activityevent.InvoiceActivityEventSource
import com.verto.app.feature.invoice.application.activityevent.InvoiceActivityRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInvoiceActivityEventSource @Inject constructor(
    private val invoiceDao: InvoiceDao,
) : InvoiceActivityEventSource {
    override fun observeSince(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<InvoiceActivityRecord>> =
        invoiceDao.observeActivityInvoices(organizationId, sinceEpochMillis, limit).map { rows ->
            rows.map { row ->
                InvoiceActivityRecord(
                    id = row.invoice.id,
                    invoiceNumber = row.invoice.invoiceNumber,
                    partyName = row.partyName,
                    description = row.invoice.description,
                    totalAmount = row.invoice.totalAmount,
                    category = when (row.invoice.category) {
                        InvoiceCategory.SALE -> InvoiceActivityCategory.SALE
                        InvoiceCategory.PURCHASE -> InvoiceActivityCategory.PURCHASE
                    },
                    createdAtEpochMillis = row.invoice.createdAt,
                    createdBy = row.invoice.createdBy,
                )
            }
        }
}
