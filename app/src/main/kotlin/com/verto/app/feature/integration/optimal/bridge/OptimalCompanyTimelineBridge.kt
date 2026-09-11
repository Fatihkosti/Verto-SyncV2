package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.domain.port.OptimalCompanyTimelinePort
import com.verto.app.feature.integration.optimal.domain.port.OptimalInvoiceTimelineDetails
import com.verto.app.feature.integration.optimal.domain.port.OptimalInvoiceTimelineItem
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageTimelineDetails
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageTimelineItem
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageTimelineKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalPaymentTimelineDetails
import com.verto.app.feature.integration.optimal.domain.port.OptimalPaymentTimelineItem
import com.verto.app.feature.integration.optimal.domain.port.OptimalTimelineIdentity
import com.verto.app.feature.invoice.domain.port.InvoiceCompanyTimelinePort
import com.verto.app.feature.messages.domain.port.CompanyMessageTimelineKind
import com.verto.app.feature.messages.domain.port.CompanyMessageTimelinePort
import com.verto.app.feature.payment.domain.port.PaymentCompanyTimelinePort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OptimalCompanyTimelineBridge @Inject constructor(
    private val invoices: InvoiceCompanyTimelinePort,
    private val payments: PaymentCompanyTimelinePort,
    private val messages: CompanyMessageTimelinePort,
) : OptimalCompanyTimelinePort {
    override fun observeInvoices(organizationId: String, clientId: String): Flow<List<OptimalInvoiceTimelineItem>> =
        invoices.observeCompanyInvoices(organizationId, clientId).map { rows -> rows.map { row ->
            OptimalInvoiceTimelineItem(
                identity = OptimalTimelineIdentity(row.organizationId, row.clientId, row.invoiceId),
                details = OptimalInvoiceTimelineDetails(
                    row.invoiceNumber, row.category, row.status, row.description, row.totalAmount, row.voided,
                ),
                occurredAt = row.occurredAt,
            )
        } }

    override fun observePayments(organizationId: String, clientId: String): Flow<List<OptimalPaymentTimelineItem>> =
        payments.observeCompanyPayments(organizationId, clientId).map { rows -> rows.map { row ->
            OptimalPaymentTimelineItem(
                identity = OptimalTimelineIdentity(row.organizationId, row.clientId, row.paymentId),
                details = OptimalPaymentTimelineDetails(
                    row.invoiceId, row.amount, row.paymentMethod, row.note, row.isReversal,
                ),
                occurredAt = row.occurredAt,
            )
        } }

    override fun observeMessages(organizationId: String, clientId: String): Flow<List<OptimalMessageTimelineItem>> =
        messages.observeCompanyMessages(organizationId, clientId).map { rows -> rows.map { row ->
            OptimalMessageTimelineItem(
                identity = OptimalTimelineIdentity(row.organizationId, row.clientId, row.messageId),
                details = OptimalMessageTimelineDetails(
                    conversationId = row.conversationId,
                    senderType = row.senderType,
                    kind = when (row.kind) {
                        CompanyMessageTimelineKind.TEXT -> OptimalMessageTimelineKind.TEXT
                        CompanyMessageTimelineKind.IMAGE -> OptimalMessageTimelineKind.IMAGE
                        CompanyMessageTimelineKind.VOICE -> OptimalMessageTimelineKind.VOICE
                        CompanyMessageTimelineKind.VIDEO -> OptimalMessageTimelineKind.VIDEO
                        CompanyMessageTimelineKind.DOCUMENT -> OptimalMessageTimelineKind.DOCUMENT
                    },
                    body = row.body,
                ),
                occurredAt = row.occurredAt,
            )
        } }
}
