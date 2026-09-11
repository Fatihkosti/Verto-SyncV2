package com.verto.app.feature.payment.application

import com.verto.app.feature.payment.application.model.*
import com.verto.app.feature.payment.application.port.PaymentDebtWorkflowPort
import javax.inject.Inject

class PaymentDebtWorkflowService @Inject constructor(
    private val port: PaymentDebtWorkflowPort,
) {
    val isAdmin get() = port.isAdmin
    val permissions get() = port.permissions
    fun observeInventoryItems() = port.observeInventoryItems()
    fun observeClients() = port.observeClients()
    fun observeCustomerDecision(clientId: String) = port.observeCustomerDecision(clientId)
    fun observeSupplierRecommendation(inventoryItemId: String) = port.observeSupplierRecommendation(inventoryItemId)
    suspend fun loadInvoiceForEdit(invoiceId: String) = port.loadInvoiceForEdit(invoiceId)
    suspend fun insertClient(client: ClientItem) = port.insertClient(client)
    suspend fun findInventoryItemByName(name: String) = port.findInventoryItemByName(name)
    suspend fun saveInventoryItem(item: InventoryItemView) = port.saveInventoryItem(item)
    fun observeVehicleSuggestions(query: InvoiceVehicleSuggestionsQuery) = port.observeVehicleSuggestions(query)
    suspend fun saveInvoice(command: PaymentSaveInvoiceCommand) = port.saveInvoice(command)
    suspend fun loadInvoiceDraft(draftKey: String, organizationId: String) = port.loadInvoiceDraft(draftKey, organizationId)
    suspend fun saveInvoiceDraft(draft: InvoiceEditorDraftData) = port.saveInvoiceDraft(draft)
    suspend fun deleteInvoiceDraft(draftKey: String, organizationId: String) = port.deleteInvoiceDraft(draftKey, organizationId)
}
