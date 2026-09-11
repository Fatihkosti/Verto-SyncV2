package com.verto.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.verto.app.data.local.entity.OptimalOutboxStatus
import kotlinx.coroutines.flow.Flow

/** Tenant-owned header. A row exists only when the invoice's company is linked to the same tenant. */
data class OptimalMaintenanceDetailsHeaderRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: Int,
    @ColumnInfo(name = "company_id") val companyId: String,
    @ColumnInfo(name = "company_name") val companyName: String,
    @ColumnInfo(name = "vehicle_client_id") val vehicleClientId: String?,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String?,
    @ColumnInfo(name = "vehicle_name_snapshot") val vehicleNameSnapshot: String,
    @ColumnInfo(name = "vehicle_type_snapshot") val vehicleTypeSnapshot: String,
    @ColumnInfo(name = "plate_number_snapshot") val plateNumberSnapshot: String,
    @ColumnInfo(name = "driver_or_delegate") val driverOrDelegate: String,
    @ColumnInfo(name = "maintenance_notes") val maintenanceNotes: String,
    @ColumnInfo(name = "maintenance_sync_status") val maintenanceSyncStatus: OptimalOutboxStatus,
    @ColumnInfo(name = "maintenance_created_at") val maintenanceCreatedAt: Long,
    @ColumnInfo(name = "maintenance_updated_at") val maintenanceUpdatedAt: Long,
    @ColumnInfo(name = "invoice_description") val invoiceDescription: String,
    @ColumnInfo(name = "invoice_total_amount") val invoiceTotalAmount: Double,
    @ColumnInfo(name = "invoice_status") val invoiceStatus: String,
    @ColumnInfo(name = "invoice_voided") val invoiceVoided: Boolean,
    @ColumnInfo(name = "invoice_created_at") val invoiceCreatedAt: Long,
    @ColumnInfo(name = "invoice_due_date") val invoiceDueDate: Long,
)

data class OptimalMaintenanceInvoiceItemRow(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "quantity") val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    @ColumnInfo(name = "total_price") val totalPrice: Double,
    @ColumnInfo(name = "description") val description: String,
)

data class OptimalMaintenancePaymentRow(
    @ColumnInfo(name = "payment_id") val paymentId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "paid_at") val paidAt: Long,
    @ColumnInfo(name = "employee_name") val employeeName: String,
    @ColumnInfo(name = "reversed_payment_id") val reversedPaymentId: String?,
)

data class OptimalMaintenanceImageReadRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "image_id") val imageId: String,
    @ColumnInfo(name = "local_uri") val localUri: String,
    @ColumnInfo(name = "storage_path") val storagePath: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

data class OptimalMaintenanceAuditRow(
    @ColumnInfo(name = "audit_id") val auditId: String,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "audit_table") val auditTable: String,
    @ColumnInfo(name = "record_summary") val recordSummary: String,
    @ColumnInfo(name = "employee_name") val employeeName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface OptimalMaintenanceDetailsReadDao {
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
            maintenance.sync_status AS maintenance_sync_status,
            maintenance.created_at AS maintenance_created_at,
            maintenance.updated_at AS maintenance_updated_at,
            invoice.description AS invoice_description,
            invoice.totalAmount AS invoice_total_amount,
            CAST(invoice.status AS TEXT) AS invoice_status,
            invoice.voided AS invoice_voided,
            invoice.createdAt AS invoice_created_at,
            invoice.dueDate AS invoice_due_date
        FROM optimal_maintenance_records maintenance
        INNER JOIN invoices invoice
            ON invoice.id = maintenance.invoice_id
        INNER JOIN optimal_company_links ownership
            ON ownership.organization_id = maintenance.organization_id
            AND ownership.client_id = invoice.clientId
        INNER JOIN clients company
            ON company.id = invoice.clientId
        WHERE maintenance.organization_id = :organizationId
          AND maintenance.record_id = :recordId
          AND ownership.organization_id = :organizationId
        LIMIT 1
        """,
    )
    fun observeHeader(
        organizationId: String,
        recordId: String,
    ): Flow<OptimalMaintenanceDetailsHeaderRow?>

    @Query(
        """
        SELECT
            item.id AS item_id,
            item.invoiceId AS invoice_id,
            item.itemName AS item_name,
            item.quantity AS quantity,
            item.sellPrice AS unit_price,
            item.totalPrice AS total_price,
            item.description AS description
        FROM optimal_maintenance_records maintenance
        INNER JOIN invoices invoice
            ON invoice.id = maintenance.invoice_id
        INNER JOIN optimal_company_links ownership
            ON ownership.organization_id = maintenance.organization_id
            AND ownership.client_id = invoice.clientId
        INNER JOIN invoice_items item
            ON item.invoiceId = invoice.id
        WHERE maintenance.organization_id = :organizationId
          AND maintenance.record_id = :recordId
          AND ownership.organization_id = :organizationId
        ORDER BY item.id ASC
        """,
    )
    fun observeInvoiceItems(
        organizationId: String,
        recordId: String,
    ): Flow<List<OptimalMaintenanceInvoiceItemRow>>

    @Query(
        """
        SELECT
            payment.id AS payment_id,
            payment.invoiceId AS invoice_id,
            payment.amount AS amount,
            CAST(payment.paymentMethod AS TEXT) AS payment_method,
            payment.note AS note,
            payment.paidAt AS paid_at,
            payment.employeeName AS employee_name,
            payment.reversedPaymentId AS reversed_payment_id
        FROM optimal_maintenance_records maintenance
        INNER JOIN invoices invoice
            ON invoice.id = maintenance.invoice_id
        INNER JOIN optimal_company_links ownership
            ON ownership.organization_id = maintenance.organization_id
            AND ownership.client_id = invoice.clientId
        INNER JOIN payments payment
            ON payment.invoiceId = invoice.id
        WHERE maintenance.organization_id = :organizationId
          AND maintenance.record_id = :recordId
          AND ownership.organization_id = :organizationId
        ORDER BY payment.paidAt DESC, payment.id ASC
        """,
    )
    fun observePayments(
        organizationId: String,
        recordId: String,
    ): Flow<List<OptimalMaintenancePaymentRow>>

    @Query(
        """
        SELECT
            image.organization_id AS organization_id,
            image.record_id AS record_id,
            image.image_id AS image_id,
            image.local_uri AS local_uri,
            image.storage_path AS storage_path,
            image.mime_type AS mime_type,
            image.byte_size AS byte_size,
            image.sort_order AS sort_order
        FROM optimal_maintenance_records maintenance
        INNER JOIN optimal_maintenance_images image
            ON image.organization_id = maintenance.organization_id
            AND image.record_id = maintenance.record_id
        WHERE maintenance.organization_id = :organizationId
          AND maintenance.record_id = :recordId
          AND image.organization_id = :organizationId
        ORDER BY image.sort_order ASC, image.image_id ASC
        """,
    )
    fun observeImages(
        organizationId: String,
        recordId: String,
    ): Flow<List<OptimalMaintenanceImageReadRow>>

    @Query(
        """
        SELECT
            audit.id AS audit_id,
            CAST(audit.`action` AS TEXT) AS "action",
            CAST(audit.auditTable AS TEXT) AS audit_table,
            audit.recordSummary AS record_summary,
            audit.employeeName AS employee_name,
            audit.createdAt AS created_at
        FROM optimal_maintenance_records maintenance
        INNER JOIN invoices invoice
            ON invoice.id = maintenance.invoice_id
        INNER JOIN optimal_company_links ownership
            ON ownership.organization_id = maintenance.organization_id
            AND ownership.client_id = invoice.clientId
        INNER JOIN audit_log audit
            ON (
                (audit.auditTable = 'INVOICE' AND audit.recordId = invoice.id)
                OR (
                    audit.auditTable = 'PAYMENT'
                    AND audit.recordId IN (
                        SELECT payment.id FROM payments payment WHERE payment.invoiceId = invoice.id
                    )
                )
                OR (audit.auditTable = 'OPTIMAL' AND audit.recordId = maintenance.record_id)
            )
        WHERE maintenance.organization_id = :organizationId
          AND maintenance.record_id = :recordId
          AND ownership.organization_id = :organizationId
        ORDER BY audit.createdAt DESC, audit.id ASC
        """,
    )
    fun observeAudit(
        organizationId: String,
        recordId: String,
    ): Flow<List<OptimalMaintenanceAuditRow>>
}
