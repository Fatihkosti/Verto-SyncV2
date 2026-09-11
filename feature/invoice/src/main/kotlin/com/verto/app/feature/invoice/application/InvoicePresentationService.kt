package com.verto.app.feature.invoice.application

import com.verto.app.feature.invoice.application.port.InvoicePresentationPort
import javax.inject.Inject

class InvoicePresentationService @Inject constructor(
    private val port: InvoicePresentationPort,
) {
    val isAdmin get() = port.isAdmin
    val permissions get() = port.permissions
    fun observeSummary(invoiceId: String) = port.observeSummary(invoiceId)
    fun observeClient(clientId: String) = port.observeClient(clientId)
    fun observeInvoiceItems(invoiceId: String) = port.observeInvoiceItems(invoiceId)
    fun observeCommunicationHistory(invoiceId: String) = port.observeCommunicationHistory(invoiceId)
    fun observeOrganizationSettings() = port.observeOrganizationSettings()
    fun observePrintSettings() = port.observePrintSettings()
    fun observeClients() = port.observeClients()
    fun observePagedInvoices(category: String, tab: Int, from: Long, to: Long, search: String, sort: String, purchaseScope: String? = null) =
        port.observePagedInvoices(category, tab, from, to, search, sort, purchaseScope)
    suspend fun canManageCommission() = port.canManageCommission()
    suspend fun canExport(category: InvoiceCategory) = port.canExport(category)
    suspend fun updateCommission(invoiceId: String, commission: Double, beneficiaryClientId: String, commissionSource: String) =
        port.updateCommission(invoiceId, commission, beneficiaryClientId, commissionSource)
    suspend fun reversePayment(paymentId: String) = port.reversePayment(paymentId)
    suspend fun fullSync() = port.fullSync()
    suspend fun createInvoicePdf(request: InvoicePdfRequest) = port.createInvoicePdf(request)
}
