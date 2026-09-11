package com.verto.app.feature.integration.optimal.domain.model

data class MaintenanceDetails(
    val organizationId: String,
    val recordId: String,
    val invoiceId: String,
    val companyId: String,
    val companyName: String,
    val vehicleReference: MaintenanceVehicleReference?,
    val vehicleSnapshot: MaintenanceVehicleSnapshot,
    val driverOrDelegate: String,
    val notes: String,
    val syncStatus: MaintenanceSyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val invoice: MaintenanceInvoiceDetails,
    val images: List<MaintenanceDetailImage>,
    val audit: List<MaintenanceAuditEntry>,
)

data class MaintenanceInvoiceDetails(
    val invoiceId: String,
    val invoiceNumber: Int,
    val description: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val status: String,
    val isVoided: Boolean,
    val createdAt: Long,
    val dueDate: Long,
    val items: List<MaintenanceInvoiceItem>,
    val payments: List<MaintenancePayment>,
)

data class MaintenanceInvoiceItem(
    val itemId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val description: String,
)

data class MaintenancePayment(
    val paymentId: String,
    val amount: Double,
    val method: String,
    val note: String,
    val paidAt: Long,
    val employeeName: String,
    val reversedPaymentId: String?,
)

data class MaintenanceDetailImage(
    val imageId: String,
    val localUri: String?,
    val mimeType: String,
    val byteSize: Long,
    val sortOrder: Int,
)

data class MaintenanceAuditEntry(
    val auditId: String,
    val action: String,
    val table: String,
    val summary: String,
    val employeeName: String,
    val createdAt: Long,
)
