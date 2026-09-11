package com.verto.app.feature.payment.application.model

data class InvoiceDraftIdentity(
    val draftKey: String,
    val organizationId: String,
)

data class InvoiceDraftMode(
    val isInternational: Boolean,
    val isSale: Boolean,
    val paymentMode: PaymentMode,
)

/** Local editor context only; posting still goes through the invoice workflow. */
data class InvoiceDraftEditorContext(
    val existingInvoiceId: String?,
    val routeClientId: String,
    val mode: InvoiceDraftMode,
    val selectedClientId: String,
    val selectedDateMillis: Long?,
)

data class InvoiceDraftPersistence(
    val writeId: String = "",
    val updatedAt: Long = 0L,
)

data class InvoiceDraftFinancialState(
    val dueDays: String,
    val notes: String,
    val paidAmount: String,
    val discount: String = "",
    val referrerClientId: String = "",
    val dueInstallments: List<PaymentDueInstallmentDraft> = emptyList(),
    val transactionCurrencyCode: String,
    val exchangeRate: String,
    val persistence: InvoiceDraftPersistence = InvoiceDraftPersistence(),
)

data class InvoiceEditorDraftData(
    val identity: InvoiceDraftIdentity,
    val editor: InvoiceDraftEditorContext,
    val financial: InvoiceDraftFinancialState,
    val items: List<InvoiceItemData>,
    val draftItem: InvoiceItemData,
    val maintenance: InvoiceMaintenanceDraftData? = null,
) {
    val draftKey get() = identity.draftKey
    val organizationId get() = identity.organizationId
    val existingInvoiceId get() = editor.existingInvoiceId
    val routeClientId get() = editor.routeClientId
    val isInternational get() = editor.mode.isInternational
    val isSale get() = editor.mode.isSale
    val paymentMode get() = editor.mode.paymentMode
    val selectedClientId get() = editor.selectedClientId
    val selectedDateMillis get() = editor.selectedDateMillis
    val dueDays get() = financial.dueDays
    val notes get() = financial.notes
    val paidAmount get() = financial.paidAmount
    val discount get() = financial.discount
    val referrerClientId get() = financial.referrerClientId
    val dueInstallments get() = financial.dueInstallments
    val transactionCurrencyCode get() = financial.transactionCurrencyCode
    val exchangeRate get() = financial.exchangeRate
    val writeId get() = financial.persistence.writeId
    val updatedAt get() = financial.persistence.updatedAt

    fun withPersistence(writeId: String, updatedAt: Long = financial.persistence.updatedAt): InvoiceEditorDraftData =
        copy(financial = financial.copy(persistence = InvoiceDraftPersistence(writeId, updatedAt)))
}

data class InvoiceMaintenanceDraftFlags(
    val isEnabled: Boolean,
    val isExpanded: Boolean,
)

data class InvoiceMaintenanceDraftOwner(
    val organizationId: String,
    val clientId: String,
    val recordId: String,
    val createdAt: Long,
)

data class InvoiceMaintenanceDraftVehicle(
    val query: String,
    val selectedOfficialVehicle: VehicleSuggestion?,
    val plateNumber: String,
    val driverOrDelegate: String,
)

data class InvoiceMaintenanceDraftData(
    val flags: InvoiceMaintenanceDraftFlags,
    val owner: InvoiceMaintenanceDraftOwner,
    val vehicle: InvoiceMaintenanceDraftVehicle,
    val notes: String,
    val images: List<CompanyMaintenanceImageData>,
) {
    val isEnabled get() = flags.isEnabled
    val isExpanded get() = flags.isExpanded
    val ownerOrganizationId get() = owner.organizationId
    val ownerClientId get() = owner.clientId
    val recordId get() = owner.recordId
    val createdAt get() = owner.createdAt
    val vehicleQuery get() = vehicle.query
    val selectedOfficialVehicle get() = vehicle.selectedOfficialVehicle
    val plateNumber get() = vehicle.plateNumber
    val driverOrDelegate get() = vehicle.driverOrDelegate
}
