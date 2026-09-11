package com.verto.app.feature.payment.data.activityevent

import com.verto.app.data.local.dao.PaymentDao
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.feature.payment.application.activityevent.PaymentActivityCategory
import com.verto.app.feature.payment.application.activityevent.PaymentActivityEventSource
import com.verto.app.feature.payment.application.activityevent.PaymentActivityRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPaymentActivityEventSource @Inject constructor(
    private val paymentDao: PaymentDao,
) : PaymentActivityEventSource {
    override fun observeSince(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<PaymentActivityRecord>> =
        paymentDao.observeActivityPayments(organizationId, sinceEpochMillis, limit).map { rows ->
            rows.map { row ->
                PaymentActivityRecord(
                    id = row.payment.id,
                    invoiceId = row.payment.invoiceId,
                    invoiceNumber = row.invoiceNumber,
                    partyName = row.partyName,
                    amount = row.payment.amount,
                    paidAtEpochMillis = row.payment.paidAt,
                    employeeId = row.payment.employeeId,
                    employeeName = row.payment.employeeName,
                    reversedPaymentId = row.payment.reversedPaymentId,
                    invoiceCategory = when (row.invoiceCategory) {
                        InvoiceCategory.SALE -> PaymentActivityCategory.SALE
                        InvoiceCategory.PURCHASE -> PaymentActivityCategory.PURCHASE
                    },
                    invoiceVoided = row.invoiceVoided,
                )
            }
        }
}
