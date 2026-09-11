package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.verto.app.data.local.entity.OptimalMaintenanceImageEntity
import com.verto.app.data.local.entity.OptimalMaintenanceRecordEntity
import com.verto.app.data.local.entity.OptimalMaintenanceStoragePaths
import com.verto.app.data.local.entity.OptimalVehicleEntity

/** Immutable aggregate returned by the maintenance persistence boundary. */
data class OptimalMaintenanceAggregate(
    val record: OptimalMaintenanceRecordEntity,
    val images: List<OptimalMaintenanceImageEntity>,
)

@Dao
abstract class OptimalMaintenanceDao {
    @Query(
        """
        SELECT *
        FROM optimal_maintenance_records
        WHERE organization_id = :organizationId
          AND invoice_id = :invoiceId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findRecordByInvoice(
        organizationId: String,
        invoiceId: String,
    ): OptimalMaintenanceRecordEntity?

    @Query(
        """
        SELECT *
        FROM optimal_maintenance_images
        WHERE organization_id = :organizationId
          AND record_id = :recordId
        ORDER BY sort_order ASC, image_id ASC
        """,
    )
    protected abstract suspend fun findImagesForRecord(
        organizationId: String,
        recordId: String,
    ): List<OptimalMaintenanceImageEntity>

    @Query(
        """
        SELECT *
        FROM optimal_vehicles
        WHERE organization_id = :organizationId
          AND client_id = :clientId
          AND remote_vehicle_id = :vehicleId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findOfficialVehicle(
        organizationId: String,
        clientId: String,
        vehicleId: String,
    ): OptimalVehicleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRecord(record: OptimalMaintenanceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertImages(images: List<OptimalMaintenanceImageEntity>)

    @Update
    protected abstract suspend fun updateRecord(record: OptimalMaintenanceRecordEntity): Int

    @Query(
        """
        DELETE FROM optimal_maintenance_images
        WHERE organization_id = :organizationId
          AND record_id = :recordId
        """,
    )
    protected abstract suspend fun deleteImagesForRecord(
        organizationId: String,
        recordId: String,
    ): Int

    /**
     * Creates one complete aggregate atomically. Official snapshots are copied from the tenant's
     * local vehicle cache; callers cannot inject snapshots from another organization.
     */
    @Transaction
    open suspend fun create(
        record: OptimalMaintenanceRecordEntity,
        images: List<OptimalMaintenanceImageEntity>,
    ): OptimalMaintenanceAggregate {
        val aggregate = normalize(record, images)
        insertRecord(aggregate.record)
        if (aggregate.images.isNotEmpty()) insertImages(aggregate.images)
        return aggregate
    }

    /**
     * Updates the one maintenance aggregate allowed for an invoice. The original record identity
     * and creation time are retained, while image metadata is replaced atomically.
     */
    @Transaction
    open suspend fun upsert(
        record: OptimalMaintenanceRecordEntity,
        images: List<OptimalMaintenanceImageEntity>,
    ): OptimalMaintenanceAggregate {
        val existing = findRecordByInvoice(record.organizationId.trim(), record.invoiceId.trim())
        val canonicalRecordId = existing?.recordId ?: record.recordId
        val canonicalRecord = record.copy(
            recordId = canonicalRecordId,
            createdAt = existing?.createdAt ?: record.createdAt,
            updatedAt = maxOf(existing?.updatedAt ?: record.updatedAt, record.updatedAt),
        )
        val canonicalImages = images.map { image ->
            image.copy(
                organizationId = canonicalRecord.organizationId,
                recordId = canonicalRecordId,
                storagePath = OptimalMaintenanceStoragePaths.image(
                    organizationId = canonicalRecord.organizationId,
                    recordId = canonicalRecordId,
                    imageId = image.imageId,
                ),
            )
        }
        val aggregate = normalize(canonicalRecord, canonicalImages)
        if (existing == null) {
            insertRecord(aggregate.record)
        } else {
            deleteImagesForRecord(existing.organizationId, existing.recordId)
            check(updateRecord(aggregate.record) == 1) { "maintenance record disappeared during update" }
        }
        if (aggregate.images.isNotEmpty()) insertImages(aggregate.images)
        return aggregate
    }

    private suspend fun normalize(
        record: OptimalMaintenanceRecordEntity,
        images: List<OptimalMaintenanceImageEntity>,
    ): OptimalMaintenanceAggregate {
        validateRecordIdentity(record)
        val officialVehicle = resolveOfficialVehicle(record)
        val persistedRecord = if (officialVehicle == null) {
            require(
                record.vehicleNameSnapshot.isNotBlank() ||
                    record.vehicleTypeSnapshot.isNotBlank() ||
                    record.plateNumberSnapshot.isNotBlank(),
            ) { "manual vehicle requires a name, type, or plate snapshot" }
            record.copy(
                vehicleNameSnapshot = record.vehicleNameSnapshot.trim(),
                vehicleTypeSnapshot = record.vehicleTypeSnapshot.trim(),
                plateNumberSnapshot = record.plateNumberSnapshot.trim(),
                driverOrDelegate = record.driverOrDelegate.trim(),
                notes = record.notes.trim(),
            )
        } else {
            record.copy(
                vehicleNameSnapshot = officialVehicle.name,
                vehicleTypeSnapshot = officialVehicle.vehicleType,
                plateNumberSnapshot = officialVehicle.plateNumber,
                driverOrDelegate = record.driverOrDelegate.trim(),
                notes = record.notes.trim(),
            )
        }
        val normalizedImages = images.sortedBy { it.sortOrder }.also { rows ->
            require(rows.map { it.sortOrder }.distinct().size == rows.size) {
                "image sort order must be unique inside a maintenance record"
            }
            rows.forEach { image -> validateImage(persistedRecord, image) }
        }
        return OptimalMaintenanceAggregate(persistedRecord, normalizedImages)
    }

    @Transaction
    open suspend fun getByInvoice(
        organizationId: String,
        invoiceId: String,
    ): OptimalMaintenanceAggregate? {
        val normalizedOrganizationId = organizationId.trim()
        val normalizedInvoiceId = invoiceId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        require(normalizedInvoiceId.isNotEmpty()) { "invoiceId is required" }
        val record = findRecordByInvoice(normalizedOrganizationId, normalizedInvoiceId) ?: return null
        return OptimalMaintenanceAggregate(
            record = record,
            images = findImagesForRecord(normalizedOrganizationId, record.recordId),
        )
    }

    @Query(
        """
        DELETE FROM optimal_maintenance_records
        WHERE organization_id = :organizationId
        """,
    )
    abstract suspend fun deleteForOrganization(organizationId: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM optimal_maintenance_records
        WHERE organization_id = :organizationId
        """,
    )
    abstract suspend fun countForOrganization(organizationId: String): Int

    private suspend fun resolveOfficialVehicle(
        record: OptimalMaintenanceRecordEntity,
    ): OptimalVehicleEntity? {
        val clientId = record.vehicleClientId
        val vehicleId = record.vehicleId
        require((clientId == null) == (vehicleId == null)) {
            "vehicleClientId and vehicleId must both be present or both be absent"
        }
        if (clientId == null || vehicleId == null) return null
        require(clientId.isNotBlank() && vehicleId.isNotBlank()) {
            "official vehicle reference cannot be blank"
        }
        return requireNotNull(
            findOfficialVehicle(record.organizationId, clientId, vehicleId),
        ) { "official vehicle does not belong to the maintenance organization" }
    }

    private fun validateRecordIdentity(record: OptimalMaintenanceRecordEntity) {
        require(record.organizationId.isNotBlank()) { "organizationId is required" }
        require(record.organizationId == record.organizationId.trim()) { "organizationId must be normalized" }
        require(record.recordId.isNotBlank()) { "recordId is required" }
        require(record.recordId == record.recordId.trim()) { "recordId must be normalized" }
        require(record.invoiceId.isNotBlank()) { "invoiceId is required" }
        require(record.invoiceId == record.invoiceId.trim()) { "invoiceId must be normalized" }
        require(record.updatedAt >= record.createdAt) { "updatedAt cannot precede createdAt" }
    }

    private fun validateImage(
        record: OptimalMaintenanceRecordEntity,
        image: OptimalMaintenanceImageEntity,
    ) {
        require(image.organizationId == record.organizationId && image.recordId == record.recordId) {
            "maintenance image crosses tenant or record boundary"
        }
        require(image.imageId.isNotBlank() && image.imageId == image.imageId.trim()) {
            "imageId is required and must be normalized"
        }
        require(image.localUri.isNotBlank()) { "localUri is required" }
        require(image.mimeType.startsWith("image/")) { "maintenance media must be an image" }
        require(image.byteSize >= 0L) { "byteSize cannot be negative" }
        require(image.sortOrder >= 0) { "sortOrder cannot be negative" }
        require(
            OptimalMaintenanceStoragePaths.isCanonicalImagePath(
                organizationId = image.organizationId,
                recordId = image.recordId,
                imageId = image.imageId,
                path = image.storagePath,
            ),
        ) { "maintenance image storage path crosses tenant boundary" }
    }
}
