package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OptimalMaintenanceAggregate
import com.verto.app.data.local.dao.OptimalMaintenanceDao
import com.verto.app.data.local.entity.OptimalMaintenanceImageEntity
import com.verto.app.data.local.entity.OptimalMaintenanceRecordEntity
import com.verto.app.data.local.entity.OptimalMaintenanceStoragePaths
import com.verto.app.data.local.entity.OptimalOutboxStatus
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceImageMetadata
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecord
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordDraft
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceSyncStatus
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleReference
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRepository
import javax.inject.Inject

class RoomOptimalMaintenanceRepository @Inject constructor(
    private val dao: OptimalMaintenanceDao,
) : OptimalMaintenanceRepository {
    override suspend fun create(
        organizationId: String,
        draft: MaintenanceRecordDraft,
    ): MaintenanceRecord {
        val normalizedOrganizationId = organizationId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        val normalizedRecordId = draft.recordId.trim()
        val normalizedInvoiceId = draft.invoiceId.trim()
        require(normalizedRecordId.isNotEmpty()) { "recordId is required" }
        require(normalizedInvoiceId.isNotEmpty()) { "invoiceId is required" }

        val reference = draft.vehicleReference?.let {
            MaintenanceVehicleReference(
                clientId = it.clientId.trim(),
                vehicleId = it.vehicleId.trim(),
            )
        }
        val record = OptimalMaintenanceRecordEntity(
            organizationId = normalizedOrganizationId,
            recordId = normalizedRecordId,
            invoiceId = normalizedInvoiceId,
            vehicleClientId = reference?.clientId,
            vehicleId = reference?.vehicleId,
            vehicleNameSnapshot = draft.vehicleSnapshot.name.trim(),
            vehicleTypeSnapshot = draft.vehicleSnapshot.vehicleType.trim(),
            plateNumberSnapshot = draft.vehicleSnapshot.plateNumber.trim(),
            driverOrDelegate = draft.driverOrDelegate.trim(),
            notes = draft.notes.trim(),
            syncStatus = OptimalOutboxStatus.LOCAL_ONLY,
            createdAt = draft.createdAt,
            updatedAt = draft.createdAt,
        )
        val images = draft.images.map { image ->
            val imageId = image.imageId.trim()
            OptimalMaintenanceImageEntity(
                organizationId = normalizedOrganizationId,
                imageId = imageId,
                recordId = normalizedRecordId,
                localUri = image.localUri.trim(),
                storagePath = OptimalMaintenanceStoragePaths.image(
                    organizationId = normalizedOrganizationId,
                    recordId = normalizedRecordId,
                    imageId = imageId,
                ),
                mimeType = image.mimeType.trim().lowercase(),
                byteSize = image.byteSize,
                sortOrder = image.sortOrder,
                syncStatus = OptimalOutboxStatus.LOCAL_ONLY,
                createdAt = draft.createdAt,
            )
        }
        return dao.upsert(record, images).toDomain()
    }

    override suspend fun getByInvoice(
        organizationId: String,
        invoiceId: String,
    ): MaintenanceRecord? = dao.getByInvoice(
        organizationId = organizationId.trim(),
        invoiceId = invoiceId.trim(),
    )?.toDomain()
}

private fun OptimalMaintenanceAggregate.toDomain(): MaintenanceRecord = MaintenanceRecord(
    organizationId = record.organizationId,
    recordId = record.recordId,
    invoiceId = record.invoiceId,
    vehicleReference = record.vehicleId?.let { vehicleId ->
        MaintenanceVehicleReference(
            clientId = requireNotNull(record.vehicleClientId),
            vehicleId = vehicleId,
        )
    },
    vehicleSnapshot = MaintenanceVehicleSnapshot(
        name = record.vehicleNameSnapshot,
        vehicleType = record.vehicleTypeSnapshot,
        plateNumber = record.plateNumberSnapshot,
    ),
    driverOrDelegate = record.driverOrDelegate,
    notes = record.notes,
    syncStatus = record.syncStatus.toDomain(),
    images = images.map { image ->
        MaintenanceImageMetadata(
            organizationId = image.organizationId,
            imageId = image.imageId,
            recordId = image.recordId,
            localUri = image.localUri,
            storagePath = image.storagePath,
            mimeType = image.mimeType,
            byteSize = image.byteSize,
            sortOrder = image.sortOrder,
            syncStatus = image.syncStatus.toDomain(),
            createdAt = image.createdAt,
        )
    },
    createdAt = record.createdAt,
    updatedAt = record.updatedAt,
)

private fun OptimalOutboxStatus.toDomain(): MaintenanceSyncStatus =
    MaintenanceSyncStatus.valueOf(name)
