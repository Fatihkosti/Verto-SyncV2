package com.verto.app.feature.integration.optimal.domain.model

/** Optional pointer to a vehicle supplied by the linked Optimal company. */
data class MaintenanceVehicleReference(
    val clientId: String,
    val vehicleId: String,
)

/** Immutable vehicle details retained with the invoice maintenance history. */
data class MaintenanceVehicleSnapshot(
    val name: String = "",
    val vehicleType: String = "",
    val plateNumber: String = "",
)

enum class MaintenanceSyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    BLOCKED,
}

data class MaintenanceImageDraft(
    val imageId: String,
    val localUri: String,
    val mimeType: String,
    val byteSize: Long,
    val sortOrder: Int,
)

data class MaintenanceRecordDraft(
    val recordId: String,
    val invoiceId: String,
    val vehicleReference: MaintenanceVehicleReference? = null,
    val vehicleSnapshot: MaintenanceVehicleSnapshot,
    val driverOrDelegate: String = "",
    val notes: String = "",
    val images: List<MaintenanceImageDraft> = emptyList(),
    val createdAt: Long,
)

data class MaintenanceImageMetadata(
    val organizationId: String,
    val imageId: String,
    val recordId: String,
    val localUri: String,
    val storagePath: String,
    val mimeType: String,
    val byteSize: Long,
    val sortOrder: Int,
    val syncStatus: MaintenanceSyncStatus,
    val createdAt: Long,
)

data class MaintenanceRecord(
    val organizationId: String,
    val recordId: String,
    val invoiceId: String,
    val vehicleReference: MaintenanceVehicleReference?,
    val vehicleSnapshot: MaintenanceVehicleSnapshot,
    val driverOrDelegate: String,
    val notes: String,
    val syncStatus: MaintenanceSyncStatus,
    val images: List<MaintenanceImageMetadata>,
    val createdAt: Long,
    val updatedAt: Long,
)
