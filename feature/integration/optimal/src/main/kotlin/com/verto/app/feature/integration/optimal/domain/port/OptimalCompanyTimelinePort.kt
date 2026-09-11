package com.verto.app.feature.integration.optimal.domain.port

import kotlinx.coroutines.flow.Flow

data class OptimalTimelineIdentity(val organizationId: String, val clientId: String, val recordId: String)

data class OptimalInvoiceTimelineDetails(
    val invoiceNumber: Int,
    val category: String,
    val status: String,
    val description: String,
    val totalAmount: Double,
    val voided: Boolean,
)

data class OptimalInvoiceTimelineItem(
    val identity: OptimalTimelineIdentity,
    val details: OptimalInvoiceTimelineDetails,
    val occurredAt: Long,
) {
    val organizationId: String get() = identity.organizationId
    val clientId: String get() = identity.clientId
    val invoiceId: String get() = identity.recordId
    val invoiceNumber: Int get() = details.invoiceNumber
    val category: String get() = details.category
    val status: String get() = details.status
    val description: String get() = details.description
    val totalAmount: Double get() = details.totalAmount
    val voided: Boolean get() = details.voided
}

data class OptimalPaymentTimelineDetails(
    val invoiceId: String,
    val amount: Double,
    val paymentMethod: String,
    val note: String,
    val isReversal: Boolean,
)

data class OptimalPaymentTimelineItem(
    val identity: OptimalTimelineIdentity,
    val details: OptimalPaymentTimelineDetails,
    val occurredAt: Long,
) {
    val organizationId: String get() = identity.organizationId
    val clientId: String get() = identity.clientId
    val paymentId: String get() = identity.recordId
    val invoiceId: String get() = details.invoiceId
    val amount: Double get() = details.amount
    val paymentMethod: String get() = details.paymentMethod
    val note: String get() = details.note
    val isReversal: Boolean get() = details.isReversal
}

enum class OptimalMessageTimelineKind { TEXT, IMAGE, VOICE, VIDEO, DOCUMENT }

data class OptimalMessageTimelineDetails(
    val conversationId: String,
    val senderType: String,
    val kind: OptimalMessageTimelineKind,
    val body: String,
)

data class OptimalMessageTimelineItem(
    val identity: OptimalTimelineIdentity,
    val details: OptimalMessageTimelineDetails,
    val occurredAt: Long,
) {
    val organizationId: String get() = identity.organizationId
    val clientId: String get() = identity.clientId
    val messageId: String get() = identity.recordId
    val conversationId: String get() = details.conversationId
    val senderType: String get() = details.senderType
    val kind: OptimalMessageTimelineKind get() = details.kind
    val body: String get() = details.body
}

/** Optimal-owned read boundary. Provider timeline models are translated in the app composition root. */
interface OptimalCompanyTimelinePort {
    fun observeInvoices(organizationId: String, clientId: String): Flow<List<OptimalInvoiceTimelineItem>>
    fun observePayments(organizationId: String, clientId: String): Flow<List<OptimalPaymentTimelineItem>>
    fun observeMessages(organizationId: String, clientId: String): Flow<List<OptimalMessageTimelineItem>>
}
