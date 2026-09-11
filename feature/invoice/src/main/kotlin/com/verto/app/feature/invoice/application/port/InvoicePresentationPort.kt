package com.verto.app.feature.invoice.application.port

import androidx.paging.PagingData
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.invoice.application.*
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface InvoicePresentationPort {
    val isAdmin: StateFlow<Boolean>
    val permissions: StateFlow<EmployeePermissions?>
    fun observeSummary(invoiceId: String): Flow<InvoiceSummary?>
    fun observeClient(clientId: String): Flow<ClientItem?>
    fun observeInvoiceItems(invoiceId: String): Flow<List<InvoiceLineView>>
    fun observeCommunicationHistory(invoiceId: String): Flow<List<InvoiceCommunicationEvent>>
    fun observeOrganizationSettings(): Flow<InvoiceOrgSettings>
    fun observePrintSettings(): Flow<InvoicePrintSettings>
    fun observeClients(): Flow<List<ClientItem>>
    fun observePagedInvoices(
        category: String,
        tab: Int,
        from: Long,
        to: Long,
        search: String,
        sort: String,
        purchaseScope: String? = null,
    ): Flow<PagingData<InvoicePaymentSummary>>
    suspend fun canManageCommission(): Boolean
    suspend fun canExport(category: InvoiceCategory): Boolean
    suspend fun updateCommission(invoiceId: String, commission: Double, beneficiaryClientId: String, commissionSource: String)
    suspend fun reversePayment(paymentId: String): InvoicePaymentReversalResult
    suspend fun fullSync(): Result<Unit>
    suspend fun createInvoicePdf(request: InvoicePdfRequest): File
}
