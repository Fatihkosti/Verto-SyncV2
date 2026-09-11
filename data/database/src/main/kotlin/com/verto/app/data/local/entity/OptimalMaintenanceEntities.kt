package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Tenant-scoped maintenance snapshot attached to one invoice.
 *
 * Vehicle snapshots are retained even when the suggestions cache is replaced. Official vehicle
 * references are therefore validated by the DAO/database guards rather than by a destructive
 * foreign key to the cache table.
 */
@Entity(
    tableName = "optimal_maintenance_records",
    primaryKeys = ["organization_id", "record_id"],
    indices = [
        Index(
            value = ["organization_id", "invoice_id"],
            unique = true,
            name = "index_optimal_maintenance_records_org_invoice",
        ),
        Index(
            value = ["organization_id", "vehicle_client_id", "vehicle_id"],
            name = "index_optimal_maintenance_records_org_vehicle",
        ),
        Index(
            value = ["organization_id", "created_at"],
            name = "index_optimal_maintenance_records_org_created_at",
        ),
    ],
)
data class OptimalMaintenanceRecordEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "vehicle_client_id") val vehicleClientId: String? = null,
    @ColumnInfo(name = "vehicle_id") val vehicleId: String? = null,
    @ColumnInfo(name = "vehicle_name_snapshot") val vehicleNameSnapshot: String,
    @ColumnInfo(name = "vehicle_type_snapshot") val vehicleTypeSnapshot: String,
    @ColumnInfo(name = "plate_number_snapshot") val plateNumberSnapshot: String,
    @ColumnInfo(name = "driver_or_delegate") val driverOrDelegate: String,
    val notes: String,
    @ColumnInfo(name = "sync_status") val syncStatus: OptimalOutboxStatus = OptimalOutboxStatus.LOCAL_ONLY,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)

/** Tenant-scoped image metadata. The actual file remains outside Room. */
@Entity(
    tableName = "optimal_maintenance_images",
    primaryKeys = ["organization_id", "image_id"],
    foreignKeys = [
        ForeignKey(
            entity = OptimalMaintenanceRecordEntity::class,
            parentColumns = ["organization_id", "record_id"],
            childColumns = ["organization_id", "record_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["organization_id", "record_id", "sort_order"],
            unique = true,
            name = "index_optimal_maintenance_images_org_record_order",
        ),
        Index(
            value = ["organization_id", "storage_path"],
            unique = true,
            name = "index_optimal_maintenance_images_org_storage_path",
        ),
    ],
)
data class OptimalMaintenanceImageEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "image_id") val imageId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "local_uri") val localUri: String,
    @ColumnInfo(name = "storage_path") val storagePath: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "sync_status") val syncStatus: OptimalOutboxStatus = OptimalOutboxStatus.LOCAL_ONLY,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)



/** Local-only lifecycle used by Verto home events; it is never part of the Optimal payload. */
enum class OptimalMaintenanceFollowUpStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/**
 * One local follow-up row per maintenance snapshot.
 *
 * The composite foreign key prevents cross-tenant or orphan rows and cascades only when the
 * owning maintenance snapshot is removed. Invoice cancellation/deletion is handled by the
 * operational read query so a retained snapshot never creates a stale home event.
 */
@Entity(
    tableName = "optimal_maintenance_follow_ups",
    primaryKeys = ["organization_id", "record_id"],
    foreignKeys = [
        ForeignKey(
            entity = OptimalMaintenanceRecordEntity::class,
            parentColumns = ["organization_id", "record_id"],
            childColumns = ["organization_id", "record_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["organization_id", "status", "expected_at"],
            name = "index_optimal_maintenance_follow_ups_org_status_expected",
        ),
    ],
)
data class OptimalMaintenanceFollowUpEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val status: OptimalMaintenanceFollowUpStatus = OptimalMaintenanceFollowUpStatus.IN_PROGRESS,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "expected_at") val expectedAt: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = startedAt,
)

/** Canonical private path. IDs are opaque path segments, never user supplied file names. */
object OptimalMaintenanceStoragePaths {
    fun image(
        organizationId: String,
        recordId: String,
        imageId: String,
    ): String {
        requirePathSegment("organizationId", organizationId)
        requirePathSegment("recordId", recordId)
        requirePathSegment("imageId", imageId)
        return "organizations/$organizationId/optimal/maintenance/$recordId/images/$imageId"
    }

    fun isCanonicalImagePath(
        organizationId: String,
        recordId: String,
        imageId: String,
        path: String,
    ): Boolean = runCatching {
        path == image(organizationId, recordId, imageId)
    }.getOrDefault(false)

    private fun requirePathSegment(name: String, value: String) {
        require(value.isNotBlank()) { "$name is required" }
        require(value == value.trim()) { "$name must be normalized" }
        require('/' !in value && '\\' !in value && value != "." && value != "..") {
            "$name is not a safe path segment"
        }
    }
}
