package com.verto.app.feature.payment.data.search

import com.verto.app.data.local.dao.PaymentDao
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.feature.payment.application.search.PaymentHomeSearchCategory
import com.verto.app.feature.payment.application.search.PaymentHomeSearchRecord
import com.verto.app.feature.payment.application.search.PaymentHomeSearchSource
import javax.inject.Inject

class RoomPaymentHomeSearchSource @Inject constructor(
    private val paymentDao: PaymentDao,
) : PaymentHomeSearchSource {
    override suspend fun search(
        organizationId: String,
        textQuery: String,
        identifierQuery: String,
        includeSales: Boolean,
        includePurchases: Boolean,
        limit: Int,
    ): List<PaymentHomeSearchRecord> = paymentDao
        .searchPaymentCards(
            organizationId = organizationId,
            textQuery = textQuery,
            identifierQuery = identifierQuery,
            includeSales = if (includeSales) 1 else 0,
            includePurchases = if (includePurchases) 1 else 0,
            limit = limit,
        )
        .map { row ->
            PaymentHomeSearchRecord(
                id = row.payment.id,
                invoiceId = row.payment.invoiceId,
                invoiceNumber = row.invoiceNumber,
                partyName = row.partyName,
                amount = row.payment.amount,
                paidAtEpochMillis = row.payment.paidAt,
                reversedPaymentId = row.payment.reversedPaymentId,
                invoiceCategory = when (row.invoiceCategory) {
                    InvoiceCategory.SALE -> PaymentHomeSearchCategory.SALE
                    InvoiceCategory.PURCHASE -> PaymentHomeSearchCategory.PURCHASE
                },
                invoiceVoided = row.invoiceVoided,
            )
        }
}
