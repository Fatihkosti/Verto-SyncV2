package com.verto.app.feature.integration.optimal.domain.model

/** Independent filters for the tenant-scoped maintenance history. */
data class MaintenanceRecordQuery(
    val companySearch: String = "",
    val vehicleSearch: String = "",
    val driver: String? = null,
    val fromDateInclusive: Long? = null,
    val toDateExclusive: Long? = null,
    val syncStatus: MaintenanceSyncStatus? = null,
    val freeText: String = "",
) {
    fun normalized(): MaintenanceRecordQuery {
        val from = fromDateInclusive
        val to = toDateExclusive
        require(from == null || from >= 0L) { "fromDateInclusive cannot be negative" }
        require(to == null || to >= 0L) { "toDateExclusive cannot be negative" }
        require(from == null || to == null || from < to) {
            "fromDateInclusive must precede toDateExclusive"
        }
        return copy(
            companySearch = companySearch.trim(),
            vehicleSearch = vehicleSearch.trim(),
            driver = driver?.trim()?.takeIf(String::isNotEmpty),
            freeText = freeText.trim(),
        )
    }

    fun hasActiveFilters(): Boolean =
        companySearch.isNotBlank() ||
            vehicleSearch.isNotBlank() ||
            driver != null ||
            fromDateInclusive != null ||
            toDateExclusive != null ||
            syncStatus != null
}

/** Read-only row rendered by the maintenance records screen. */
data class MaintenanceRecordListItem(
    val organizationId: String,
    val recordId: String,
    val invoiceId: String,
    val invoiceNumber: Int?,
    val companyId: String?,
    val companyName: String,
    val vehicleReference: MaintenanceVehicleReference?,
    val vehicleSnapshot: MaintenanceVehicleSnapshot,
    val driverOrDelegate: String,
    val summary: String,
    val syncStatus: MaintenanceSyncStatus,
    val createdAt: Long,
)

data class MaintenanceRecordFilterOptions(
    val drivers: List<String> = emptyList(),
    val syncStatuses: List<MaintenanceSyncStatus> = emptyList(),
    val earliestDate: Long? = null,
    val latestDate: Long? = null,
) {
    val hasDateValues: Boolean
        get() = earliestDate != null && latestDate != null
}

data class MaintenanceRecordsSnapshot(
    val records: List<MaintenanceRecordListItem> = emptyList(),
    val filterOptions: MaintenanceRecordFilterOptions = MaintenanceRecordFilterOptions(),
)
