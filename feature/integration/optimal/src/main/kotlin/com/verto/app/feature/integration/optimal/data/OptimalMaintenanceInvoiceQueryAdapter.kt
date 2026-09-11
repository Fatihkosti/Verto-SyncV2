package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OptimalMaintenanceAuditRow
import com.verto.app.data.local.dao.OptimalMaintenanceDetailsHeaderRow
import com.verto.app.data.local.dao.OptimalMaintenanceDetailsReadDao
import com.verto.app.data.local.dao.OptimalMaintenanceImageReadRow
import com.verto.app.data.local.dao.OptimalMaintenanceInvoiceItemRow
import com.verto.app.data.local.dao.OptimalMaintenancePaymentRow
import com.verto.app.data.local.entity.OptimalMaintenanceStoragePaths
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceAuditEntry
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetailImage
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetails
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceInvoiceDetails
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceInvoiceItem
import com.verto.app.feature.integration.optimal.domain.model.MaintenancePayment
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceSyncStatus
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleReference
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceDetailsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Reads the canonical invoice live; no financial value is persisted in maintenance rows. */
class OptimalMaintenanceInvoiceQueryAdapter @Inject constructor(
    private val dao: OptimalMaintenanceDetailsReadDao,
) : OptimalMaintenanceDetailsRepository {
    override fun observeDetails(
        organizationId: String,
        recordId: String,
    ): Flow<MaintenanceDetails?> {
        val normalizedOrganizationId = organizationId.trim()
        val normalizedRecordId = recordId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        require(normalizedRecordId.isNotEmpty()) { "recordId is required" }

        return combine(
            dao.observeHeader(normalizedOrganizationId, normalizedRecordId),
            dao.observeInvoiceItems(normalizedOrganizationId, normalizedRecordId),
            dao.observePayments(normalizedOrganizationId, normalizedRecordId),
            dao.observeImages(normalizedOrganizationId, normalizedRecordId),
            dao.observeAudit(normalizedOrganizationId, normalizedRecordId),
        ) { header, items, payments, images, audit ->
            header?.toDomainOrNull(
                requestedOrganizationId = normalizedOrganizationId,
                requestedRecordId = normalizedRecordId,
                items = items,
                payments = payments,
                images = images,
                audit = audit,
            )
        }
    }
}

private fun OptimalMaintenanceDetailsHeaderRow.toDomainOrNull(
    requestedOrganizationId: String,
    requestedRecordId: String,
    items: List<OptimalMaintenanceInvoiceItemRow>,
    payments: List<OptimalMaintenancePaymentRow>,
    images: List<OptimalMaintenanceImageReadRow>,
    audit: List<OptimalMaintenanceAuditRow>,
): MaintenanceDetails? {
    if (organizationId != requestedOrganizationId || recordId != requestedRecordId) return null
    if (invoiceId.isBlank() || companyId.isBlank()) return null
    if (items.any { it.invoiceId != invoiceId }) return null
    if (payments.any { it.invoiceId != invoiceId }) return null
    if (images.any { !it.belongsTo(organizationId, recordId) }) return null

    val normalizedVehicleClientId = vehicleClientId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedVehicleId = vehicleId?.trim()?.takeIf(String::isNotEmpty)
    if ((normalizedVehicleClientId == null) != (normalizedVehicleId == null)) return null
    val paidAmount = payments.sumOf(OptimalMaintenancePaymentRow::amount)

    return MaintenanceDetails(
        organizationId = organizationId,
        recordId = recordId,
        invoiceId = invoiceId,
        companyId = companyId,
        companyName = companyName.trim().ifBlank { "شركة غير معروفة" },
        vehicleReference = normalizedVehicleId?.let { vehicle ->
            MaintenanceVehicleReference(
                clientId = requireNotNull(normalizedVehicleClientId),
                vehicleId = vehicle,
            )
        },
        vehicleSnapshot = MaintenanceVehicleSnapshot(
            name = vehicleNameSnapshot.trim(),
            vehicleType = vehicleTypeSnapshot.trim(),
            plateNumber = plateNumberSnapshot.trim(),
        ),
        driverOrDelegate = driverOrDelegate.trim(),
        notes = maintenanceNotes.trim(),
        syncStatus = MaintenanceSyncStatus.valueOf(maintenanceSyncStatus.name),
        createdAt = maintenanceCreatedAt,
        updatedAt = maintenanceUpdatedAt,
        invoice = MaintenanceInvoiceDetails(
            invoiceId = invoiceId,
            invoiceNumber = invoiceNumber,
            description = invoiceDescription.trim(),
            totalAmount = invoiceTotalAmount,
            paidAmount = paidAmount,
            remainingAmount = invoiceTotalAmount - paidAmount,
            status = invoiceStatus,
            isVoided = invoiceVoided,
            createdAt = invoiceCreatedAt,
            dueDate = invoiceDueDate,
            items = items.map(OptimalMaintenanceInvoiceItemRow::toDomain),
            payments = payments.map(OptimalMaintenancePaymentRow::toDomain),
        ),
        images = images.map(OptimalMaintenanceImageReadRow::toDomain),
        audit = audit.map(OptimalMaintenanceAuditRow::toDomain),
    )
}

private fun OptimalMaintenanceImageReadRow.belongsTo(
    organizationId: String,
    recordId: String,
): Boolean =
    this.organizationId == organizationId &&
        this.recordId == recordId &&
        storagePath == OptimalMaintenanceStoragePaths.image(organizationId, recordId, imageId)

private fun OptimalMaintenanceInvoiceItemRow.toDomain(): MaintenanceInvoiceItem =
    MaintenanceInvoiceItem(
        itemId = itemId,
        name = itemName.trim().ifBlank { "بند غير مسمى" },
        quantity = quantity,
        unitPrice = unitPrice,
        totalPrice = totalPrice,
        description = description.trim(),
    )

private fun OptimalMaintenancePaymentRow.toDomain(): MaintenancePayment =
    MaintenancePayment(
        paymentId = paymentId,
        amount = amount,
        method = paymentMethod,
        note = note.trim(),
        paidAt = paidAt,
        employeeName = employeeName.trim(),
        reversedPaymentId = reversedPaymentId,
    )

private fun OptimalMaintenanceImageReadRow.toDomain(): MaintenanceDetailImage =
    MaintenanceDetailImage(
        imageId = imageId,
        localUri = localUri.trim().takeIf(String::isNotEmpty),
        mimeType = mimeType,
        byteSize = byteSize,
        sortOrder = sortOrder,
    )

private fun OptimalMaintenanceAuditRow.toDomain(): MaintenanceAuditEntry =
    MaintenanceAuditEntry(
        auditId = auditId,
        action = action,
        table = auditTable,
        summary = recordSummary.trim(),
        employeeName = employeeName.trim(),
        createdAt = createdAt,
    )
