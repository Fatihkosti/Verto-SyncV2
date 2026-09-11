package com.verto.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.verto.app.data.local.entity.OptimalOutboxStatus
import kotlinx.coroutines.flow.Flow

/** Tenant-scoped projection used only by the Optimal maintenance history. */
data class OptimalMaintenanceRecordReadRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: Int?,
    @ColumnInfo(name = "company_id") val companyId: String?,
    @ColumnInfo(name = "company_name") val companyName: String?,
    @ColumnInfo(name = "vehicle_client_id") val vehicleClientId: String?,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String?,
    @ColumnInfo(name = "vehicle_name_snapshot") val vehicleNameSnapshot: String,
    @ColumnInfo(name = "vehicle_type_snapshot") val vehicleTypeSnapshot: String,
    @ColumnInfo(name = "plate_number_snapshot") val plateNumberSnapshot: String,
    @ColumnInfo(name = "driver_or_delegate") val driverOrDelegate: String,
    @ColumnInfo(name = "maintenance_notes") val maintenanceNotes: String,
    @ColumnInfo(name = "invoice_description") val invoiceDescription: String?,
    @ColumnInfo(name = "sync_status") val syncStatus: OptimalOutboxStatus,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class OptimalMaintenanceActivityRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "company_name") val companyName: String?,
    @ColumnInfo(name = "vehicle_name_snapshot") val vehicleNameSnapshot: String,
    @ColumnInfo(name = "vehicle_type_snapshot") val vehicleTypeSnapshot: String,
    @ColumnInfo(name = "plate_number_snapshot") val plateNumberSnapshot: String,
    @ColumnInfo(name = "driver_or_delegate") val driverOrDelegate: String,
    @ColumnInfo(name = "maintenance_notes") val maintenanceNotes: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

data class OptimalMaintenanceDateBoundsRow(
    @ColumnInfo(name = "earliest_date") val earliestDate: Long?,
    @ColumnInfo(name = "latest_date") val latestDate: Long?,
)

@Dao
interface OptimalMaintenanceReadDao {
    @Query(
        """
        SELECT maintenance.organization_id AS organization_id,
               maintenance.record_id AS record_id,
               company.name AS company_name,
               maintenance.vehicle_name_snapshot AS vehicle_name_snapshot,
               maintenance.vehicle_type_snapshot AS vehicle_type_snapshot,
               maintenance.plate_number_snapshot AS plate_number_snapshot,
               maintenance.driver_or_delegate AS driver_or_delegate,
               maintenance.notes AS maintenance_notes,
               maintenance.created_at AS created_at
        FROM optimal_maintenance_records maintenance
        LEFT JOIN invoices invoice ON invoice.id = maintenance.invoice_id
        LEFT JOIN clients company ON company.id = invoice.clientId
        WHERE maintenance.organization_id = :organizationId
          AND maintenance.created_at >= :sinceEpochMillis
        ORDER BY maintenance.created_at DESC, maintenance.record_id ASC
        LIMIT :limit
        """,
    )
    fun observeActivityRecords(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<OptimalMaintenanceActivityRow>>

    /**
     * The maintenance table is constrained by organization_id before every user-controlled
     * predicate. Joins only enrich rows already selected from the active tenant.
     */
    @Query(
        """
        SELECT
            maintenance.organization_id AS organization_id,
            maintenance.record_id AS record_id,
            maintenance.invoice_id AS invoice_id,
            invoice.invoiceNumber AS invoice_number,
            invoice.clientId AS company_id,
            company.name AS company_name,
            maintenance.vehicle_client_id AS vehicle_client_id,
            maintenance.vehicle_id AS vehicle_id,
            maintenance.vehicle_name_snapshot AS vehicle_name_snapshot,
            maintenance.vehicle_type_snapshot AS vehicle_type_snapshot,
            maintenance.plate_number_snapshot AS plate_number_snapshot,
            maintenance.driver_or_delegate AS driver_or_delegate,
            maintenance.notes AS maintenance_notes,
            invoice.description AS invoice_description,
            maintenance.sync_status AS sync_status,
            maintenance.created_at AS created_at
        FROM optimal_maintenance_records maintenance
        LEFT JOIN invoices invoice
            ON invoice.id = maintenance.invoice_id
        LEFT JOIN clients company
            ON company.id = invoice.clientId
        WHERE maintenance.organization_id = :organizationId
          AND (
              :companySearch = ''
              OR lower(COALESCE(company.name, '')) LIKE '%' || lower(:companySearch) || '%'
          )
          AND (
              :vehicleSearch = ''
              OR lower(maintenance.vehicle_name_snapshot) LIKE '%' || lower(:vehicleSearch) || '%'
              OR lower(maintenance.vehicle_type_snapshot) LIKE '%' || lower(:vehicleSearch) || '%'
              OR lower(maintenance.plate_number_snapshot) LIKE '%' || lower(:vehicleSearch) || '%'
          )
          AND (
              :driver = ''
              OR maintenance.driver_or_delegate = :driver
          )
          AND (
              :fromDateInclusive IS NULL
              OR maintenance.created_at >= :fromDateInclusive
          )
          AND (
              :toDateExclusive IS NULL
              OR maintenance.created_at < :toDateExclusive
          )
          AND (
              :syncStatus = ''
              OR maintenance.sync_status = :syncStatus
          )
          AND (
              :freeText = ''
              OR lower(maintenance.notes) LIKE '%' || lower(:freeText) || '%'
              OR lower(maintenance.driver_or_delegate) LIKE '%' || lower(:freeText) || '%'
              OR lower(maintenance.vehicle_name_snapshot) LIKE '%' || lower(:freeText) || '%'
              OR lower(maintenance.vehicle_type_snapshot) LIKE '%' || lower(:freeText) || '%'
              OR lower(maintenance.plate_number_snapshot) LIKE '%' || lower(:freeText) || '%'
              OR lower(COALESCE(company.name, '')) LIKE '%' || lower(:freeText) || '%'
              OR lower(COALESCE(invoice.description, '')) LIKE '%' || lower(:freeText) || '%'
              OR CAST(COALESCE(invoice.invoiceNumber, '') AS TEXT) LIKE '%' || :freeText || '%'
          )
        ORDER BY maintenance.created_at DESC, maintenance.record_id ASC
        """,
    )
    fun observeRecords(
        organizationId: String,
        companySearch: String,
        vehicleSearch: String,
        driver: String,
        fromDateInclusive: Long?,
        toDateExclusive: Long?,
        syncStatus: String,
        freeText: String,
    ): Flow<List<OptimalMaintenanceRecordReadRow>>

    @Query(
        """
        SELECT DISTINCT driver_or_delegate
        FROM optimal_maintenance_records
        WHERE organization_id = :organizationId
          AND trim(driver_or_delegate) != ''
        ORDER BY driver_or_delegate COLLATE NOCASE ASC
        """,
    )
    fun observeDrivers(organizationId: String): Flow<List<String>>

    @Query(
        """
        SELECT DISTINCT sync_status
        FROM optimal_maintenance_records
        WHERE organization_id = :organizationId
        ORDER BY sync_status ASC
        """,
    )
    fun observeSyncStatuses(organizationId: String): Flow<List<OptimalOutboxStatus>>

    @Query(
        """
        SELECT
            MIN(created_at) AS earliest_date,
            MAX(created_at) AS latest_date
        FROM optimal_maintenance_records
        WHERE organization_id = :organizationId
        """,
    )
    fun observeDateBounds(organizationId: String): Flow<OptimalMaintenanceDateBoundsRow>
}
