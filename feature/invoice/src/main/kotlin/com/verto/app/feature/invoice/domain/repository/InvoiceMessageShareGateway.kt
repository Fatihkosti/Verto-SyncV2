package com.verto.app.feature.invoice.domain.repository

data class InvoiceSharePayload(
    val clientName: String,
    val clientPhone: String,
    val invoiceNumber: Int,
    val invoiceTypeLabel: String,
    val description: String,
    val totalAmount: Double,
    val totalPaid: Double,
    val remaining: Double,
    val dueDate: Long
)

interface InvoiceMessageShareGateway {
    fun shareInvoice(payload: InvoiceSharePayload, useBusiness: Boolean)
    fun shareReminder(payload: InvoiceSharePayload, useBusiness: Boolean)
    fun shareThankYou(
        payload: InvoiceSharePayload,
        paidAmount: Double,
        isFullyPaid: Boolean,
        useBusiness: Boolean
    )
}
