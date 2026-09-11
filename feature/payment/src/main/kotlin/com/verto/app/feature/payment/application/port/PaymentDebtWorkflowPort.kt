package com.verto.app.feature.payment.application.port

import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.payment.application.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PaymentDebtWorkflowPort {
    val isAdmin: StateFlow<Boolean>
    val permissions: StateFlow<EmployeePermissions?>
    fun observeInventoryItems(): Flow<List<InventoryItemView>>
    fun observeClients(): Flow<List<ClientItem>>
    fun observeCustomerDecision(clientId: String): Flow<PaymentCustomerDecision>
    fun observeSupplierRecommendation(inventoryItemId: String): Flow<PaymentSupplierRecommendation>
    suspend fun loadInvoiceForEdit(invoiceId: String): InvoiceEditViewData?
    suspend fun insertClient(client: ClientItem)
    suspend fun findInventoryItemByName(name: String): InventoryItemView?
    suspend fun saveInventoryItem(item: InventoryItemView)
    fun observeVehicleSuggestions(query: InvoiceVehicleSuggestionsQuery): Flow<List<VehicleSuggestion>>
    suspend fun saveInvoice(command: PaymentSaveInvoiceCommand): InvoiceSaveResult
    suspend fun loadInvoiceDraft(draftKey: String, organizationId: String): InvoiceEditorDraftData?
    suspend fun saveInvoiceDraft(draft: InvoiceEditorDraftData)
    suspend fun deleteInvoiceDraft(draftKey: String, organizationId: String)
}
