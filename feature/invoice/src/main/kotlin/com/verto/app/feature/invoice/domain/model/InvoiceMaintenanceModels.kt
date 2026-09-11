package com.verto.app.feature.invoice.domain.model

/** Vehicle data exposed to the invoice feature without leaking Optimal persistence models. */
data class VehicleSuggestion(
    val organizationId: String,
    val clientId: String,
    val remoteVehicleId: String,
    val name: String,
    val vehicleType: String,
    val plateNumber: String,
    val updatedAt: Long,
)

data class CompanyMaintenanceImageData(
    val imageId: String,
    val localUri: String,
    val mimeType: String,
    val byteSize: Long,
    val sortOrder: Int,
)

/** Optional maintenance extension attached to a company sales invoice. */
data class CompanyMaintenanceData(
    val recordId: String,
    /** Tenant carried by an official Optimal selection; null for manual vehicle text. */
    val officialVehicleOrganizationId: String? = null,
    val officialVehicleId: String? = null,
    val vehicleName: String = "",
    val vehicleType: String = "",
    val plateNumber: String = "",
    val driverOrDelegate: String = "",
    val notes: String = "",
    val images: List<CompanyMaintenanceImageData> = emptyList(),
    val createdAt: Long,
) {
    fun hasOfficialVehicle(): Boolean = !officialVehicleId.isNullOrBlank()

    fun hasContent(): Boolean =
        hasOfficialVehicle() ||
            vehicleName.isNotBlank() ||
            vehicleType.isNotBlank() ||
            plateNumber.isNotBlank() ||
            driverOrDelegate.isNotBlank() ||
            notes.isNotBlank() ||
            images.isNotEmpty()
}

data class InvoiceVehicleSuggestionsQuery(
    val organizationId: String,
    val clientId: String,
    val searchTerm: String = "",
    val limit: Int = 20,
)

data class SaveInvoiceMaintenanceCommand(
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val maintenance: CompanyMaintenanceData?,
)

/** Nature of the local invoice write represented by the durable integration event. */
enum class InvoiceIntegrationWriteKind {
    CREATED,
    UPDATED,
}

/**
 * Database-only integration work that must execute inside the invoice Room transaction.
 * The command is platform independent and carries the tenant explicitly.
 */
data class PersistInvoiceIntegrationCommand(
    val writeId: String,
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val writeKind: InvoiceIntegrationWriteKind,
    val isSale: Boolean,
    val companyClient: Boolean,
    val maintenance: CompanyMaintenanceData?,
    val occurredAt: Long,
)
/** Terminal invoice lifecycle event written inside the same Room transaction as voiding. */
data class PersistInvoiceVoidIntegrationCommand(
    val writeId: String,
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val occurredAt: Long,
)

