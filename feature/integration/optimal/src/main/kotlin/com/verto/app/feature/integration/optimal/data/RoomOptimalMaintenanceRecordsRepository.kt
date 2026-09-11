package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OptimalMaintenanceReadDao
import com.verto.app.data.local.dao.OptimalMaintenanceRecordReadRow
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordFilterOptions
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordListItem
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordQuery
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordsSnapshot
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceSyncStatus
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleReference
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRecordsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomOptimalMaintenanceRecordsRepository @Inject constructor(
    private val dao: OptimalMaintenanceReadDao,
) : OptimalMaintenanceRecordsRepository {
    override fun observeRecords(
        organizationId: String,
        query: MaintenanceRecordQuery,
    ): Flow<MaintenanceRecordsSnapshot> {
        val normalizedOrganizationId = organizationId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        val normalizedQuery = query.normalized()

        val records = dao.observeRecords(
            organizationId = normalizedOrganizationId,
            companySearch = normalizedQuery.companySearch,
            vehicleSearch = normalizedQuery.vehicleSearch,
            driver = normalizedQuery.driver.orEmpty(),
            fromDateInclusive = normalizedQuery.fromDateInclusive,
            toDateExclusive = normalizedQuery.toDateExclusive,
            syncStatus = normalizedQuery.syncStatus?.name.orEmpty(),
            freeText = normalizedQuery.freeText,
        )
        return combine(
            records,
            dao.observeDrivers(normalizedOrganizationId),
            dao.observeSyncStatuses(normalizedOrganizationId),
            dao.observeDateBounds(normalizedOrganizationId),
        ) { rows, drivers, statuses, bounds ->
            MaintenanceRecordsSnapshot(
                records = rows.map(OptimalMaintenanceRecordReadRow::toDomain),
                filterOptions = MaintenanceRecordFilterOptions(
                    drivers = drivers
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinctBy { it.lowercase() }
                        .sortedBy { it.lowercase() },
                    syncStatuses = statuses
                        .map { MaintenanceSyncStatus.valueOf(it.name) }
                        .distinct()
                        .sortedBy(MaintenanceSyncStatus::ordinal),
                    earliestDate = bounds.earliestDate,
                    latestDate = bounds.latestDate,
                ),
            )
        }
    }
}

private fun OptimalMaintenanceRecordReadRow.toDomain(): MaintenanceRecordListItem {
    val normalizedVehicleClientId = vehicleClientId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedVehicleId = vehicleId?.trim()?.takeIf(String::isNotEmpty)
    val reference = if (normalizedVehicleClientId != null && normalizedVehicleId != null) {
        MaintenanceVehicleReference(
            clientId = normalizedVehicleClientId,
            vehicleId = normalizedVehicleId,
        )
    } else {
        null
    }
    val normalizedNotes = maintenanceNotes.trim()
    val normalizedInvoiceDescription = invoiceDescription?.trim().orEmpty()
    return MaintenanceRecordListItem(
        organizationId = organizationId,
        recordId = recordId,
        invoiceId = invoiceId,
        invoiceNumber = invoiceNumber,
        companyId = companyId,
        companyName = companyName?.trim()?.takeIf(String::isNotEmpty) ?: "شركة غير معروفة",
        vehicleReference = reference,
        vehicleSnapshot = MaintenanceVehicleSnapshot(
            name = vehicleNameSnapshot.trim(),
            vehicleType = vehicleTypeSnapshot.trim(),
            plateNumber = plateNumberSnapshot.trim(),
        ),
        driverOrDelegate = driverOrDelegate.trim(),
        summary = normalizedNotes.ifBlank { normalizedInvoiceDescription }.ifBlank { "بدون ملاحظات" },
        syncStatus = MaintenanceSyncStatus.valueOf(syncStatus.name),
        createdAt = createdAt,
    )
}
