package com.verto.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Read-only projection over the original Verto invoice tables for linked Optimal companies. */
data class OptimalCompanyInvoiceReadRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: Int,
    @ColumnInfo(name = "company_id") val companyId: String,
    @ColumnInfo(name = "company_name") val companyName: String,
    @ColumnInfo(name = "optimal_company_id") val optimalCompanyId: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "total_amount") val totalAmount: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "due_date") val dueDate: Long,
    @ColumnInfo(name = "invoice_status") val invoiceStatus: String,
    @ColumnInfo(name = "voided") val voided: Boolean,
    @ColumnInfo(name = "is_dirty") val isDirty: Boolean,
    @ColumnInfo(name = "vehicle_name") val vehicleName: String,
    @ColumnInfo(name = "vehicle_type") val vehicleType: String,
    @ColumnInfo(name = "plate_number") val plateNumber: String,
)

data class OptimalCompanyInvoiceCompanyRow(
    @ColumnInfo(name = "company_id") val companyId: String,
    @ColumnInfo(name = "company_name") val companyName: String,
)

data class OptimalCompanyInvoiceDateBoundsRow(
    @ColumnInfo(name = "earliest_date") val earliestDate: Long?,
    @ColumnInfo(name = "latest_date") val latestDate: Long?,
)

@Dao
interface OptimalCompanyInvoiceReadDao {
    /**
     * Tenant ownership is established by the organization-scoped Optimal link before filters are
     * evaluated. The invoice, client, and payment data remain in their original Verto tables.
     */
    @Query(
        """
        SELECT
            link.organization_id AS organization_id,
            invoice.id AS invoice_id,
            invoice.invoiceNumber AS invoice_number,
            invoice.clientId AS company_id,
            company.name AS company_name,
            link.optimal_company_id AS optimal_company_id,
            invoice.description AS description,
            invoice.totalAmount AS total_amount,
            invoice.createdAt AS created_at,
            invoice.dueDate AS due_date,
            invoice.status AS invoice_status,
            invoice.voided AS voided,
            invoice.isDirty AS is_dirty,
            COALESCE(NULLIF(maintenance.vehicle_name_snapshot, ''), NULLIF(profile.vehicle_models, ''), '') AS vehicle_name,
            COALESCE(maintenance.vehicle_type_snapshot, '') AS vehicle_type,
            COALESCE(maintenance.plate_number_snapshot, '') AS plate_number
        FROM optimal_company_links link
        INNER JOIN clients company
            ON company.id = link.client_id
        INNER JOIN party_roles role
            ON role.party_id = link.client_id
            AND role.organization_id = link.organization_id
            AND role.role = 'CUSTOMER'
            AND role.status = 'ACTIVE'
            AND role.deleted_at IS NULL
        LEFT JOIN customer_profiles profile
            ON profile.party_id = link.client_id
            AND profile.organization_id = link.organization_id
        INNER JOIN invoices invoice
            ON invoice.clientId = link.client_id
            AND invoice.organization_id = link.organization_id
        LEFT JOIN optimal_maintenance_records maintenance
            ON maintenance.organization_id = link.organization_id
            AND maintenance.invoice_id = invoice.id
        WHERE link.organization_id = :organizationId
          AND (:companyId = '' OR link.client_id = :companyId)
          AND (:fromDateInclusive IS NULL OR invoice.createdAt >= :fromDateInclusive)
          AND (:toDateExclusive IS NULL OR invoice.createdAt < :toDateExclusive)
          AND (
              :lifecycle = ''
              OR (:lifecycle = 'ACTIVE' AND invoice.voided = 0)
              OR (:lifecycle = 'VOIDED' AND invoice.voided = 1)
          )
          AND (
              :settlement = ''
              OR (:settlement = 'CASH' AND invoice.status = 'CLOSED_CASH')
              OR (:settlement = 'CREDIT' AND invoice.status = 'CLOSED_CREDIT')
          )
          AND (
              :vehicleSearch = ''
              OR lower(COALESCE(maintenance.vehicle_name_snapshot, '')) LIKE '%' || lower(:vehicleSearch) || '%'
              OR lower(COALESCE(maintenance.vehicle_type_snapshot, '')) LIKE '%' || lower(:vehicleSearch) || '%'
              OR lower(COALESCE(maintenance.plate_number_snapshot, '')) LIKE '%' || lower(:vehicleSearch) || '%'
              OR lower(COALESCE(profile.vehicle_models, '')) LIKE '%' || lower(:vehicleSearch) || '%'
          )
          AND (
              :syncStatus = ''
              OR (:syncStatus = 'PENDING' AND invoice.isDirty = 1)
              OR (:syncStatus = 'SYNCED' AND invoice.isDirty = 0)
          )
        ORDER BY invoice.createdAt DESC, invoice.invoiceNumber DESC, invoice.id ASC
        """,
    )
    fun observeInvoices(
        organizationId: String,
        companyId: String,
        fromDateInclusive: Long?,
        toDateExclusive: Long?,
        lifecycle: String,
        settlement: String,
        vehicleSearch: String,
        syncStatus: String,
    ): Flow<List<OptimalCompanyInvoiceReadRow>>

    @Query(
        """
        SELECT DISTINCT
            link.client_id AS company_id,
            company.name AS company_name
        FROM optimal_company_links link
        INNER JOIN clients company
            ON company.id = link.client_id
        INNER JOIN invoices invoice
            ON invoice.clientId = link.client_id
            AND invoice.organization_id = link.organization_id
        WHERE link.organization_id = :organizationId
        ORDER BY company.name COLLATE NOCASE ASC, link.client_id ASC
        """,
    )
    fun observeCompanies(organizationId: String): Flow<List<OptimalCompanyInvoiceCompanyRow>>

    @Query(
        """
        SELECT
            MIN(invoice.createdAt) AS earliest_date,
            MAX(invoice.createdAt) AS latest_date
        FROM optimal_company_links link
        INNER JOIN invoices invoice
            ON invoice.clientId = link.client_id
            AND invoice.organization_id = link.organization_id
        WHERE link.organization_id = :organizationId
        """,
    )
    fun observeDateBounds(organizationId: String): Flow<OptimalCompanyInvoiceDateBoundsRow>

    /** Used by the navigation guard before rendering the original invoice screen. */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM optimal_company_links link
            INNER JOIN invoices invoice
                ON invoice.clientId = link.client_id
                AND invoice.organization_id = link.organization_id
            WHERE link.organization_id = :organizationId
              AND invoice.id = :invoiceId
        )
        """,
    )
    fun observeInvoiceOwnedByOrganization(
        organizationId: String,
        invoiceId: String,
    ): Flow<Boolean>
}
