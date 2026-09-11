package com.verto.app.feature.payment.bridge

import com.verto.app.data.local.dao.PaymentDao
import com.verto.app.feature.payment.domain.port.CompanyPaymentTimelineItem
import com.verto.app.feature.payment.domain.port.PaymentCompanyTimelinePort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPaymentCompanyTimelineAdapter @Inject constructor(
    private val paymentDao: PaymentDao,
) : PaymentCompanyTimelinePort {
    override fun observeCompanyPayments(
        organizationId: String,
        clientId: String,
    ): Flow<List<CompanyPaymentTimelineItem>> = paymentDao.getPaymentsForClient(clientId)
        .map { payments ->
            payments.map { payment ->
                CompanyPaymentTimelineItem(
                    organizationId = organizationId,
                    clientId = payment.clientId,
                    paymentId = payment.id,
                    invoiceId = payment.invoiceId,
                    amount = payment.amount,
                    paymentMethod = payment.paymentMethod.name,
                    note = payment.note,
                    occurredAt = payment.paidAt,
                    isReversal = payment.reversedPaymentId != null,
                )
            }
        }
}
