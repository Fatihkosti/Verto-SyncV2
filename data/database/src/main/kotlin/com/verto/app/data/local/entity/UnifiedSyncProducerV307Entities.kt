package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Session 307 canonical Room mirror for synchronized organization settings. */
@Entity(tableName = "organization_settings_local")
data class OrganizationSettingsLocalEntity(
    @PrimaryKey @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "shop_name") val shopName: String = "",
    @ColumnInfo(name = "shop_phone") val shopPhone: String = "",
    val city: String = "",
    val address: String = "",
    val currency: String = "",
    @ColumnInfo(name = "invoice_footer") val invoiceFooter: String = "",
    @ColumnInfo(name = "tax_number") val taxNumber: String = "",
    @ColumnInfo(name = "logo_url") val logoUrl: String = "",
    @ColumnInfo(name = "signature_url") val signatureUrl: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "is_dirty") val isDirty: Boolean = false,
)

/** Session 307 durable metadata-only attachment transfer intent. Never stores binary bytes. */
@Entity(
    tableName = "sync_attachment_transfer",
    indices = [
        Index(value = ["organization_id", "state", "created_at"], name = "index_sync_attachment_transfer_delivery"),
        Index(value = ["mutation_id"], name = "index_sync_attachment_transfer_mutation"),
        Index(value = ["organization_id", "aggregate_type", "aggregate_id"], name = "index_sync_attachment_transfer_aggregate"),
        Index(value = ["object_key"], unique = true, name = "index_sync_attachment_transfer_object_key"),
    ],
)
data class SyncAttachmentTransferEntity(
    @PrimaryKey @ColumnInfo(name = "transfer_id") val transferId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String? = null,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "local_uri") val localUri: String,
    @ColumnInfo(name = "object_key") val objectKey: String,
    @ColumnInfo(name = "content_checksum") val contentChecksum: String,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "byte_size") val byteSize: Long? = null,
    val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "lease_owner") val leaseOwner: String? = null,
    @ColumnInfo(name = "lease_token") val leaseToken: String? = null,
    @ColumnInfo(name = "lease_expires_at") val leaseExpiresAt: Long? = null,
    @ColumnInfo(name = "next_attempt_at", defaultValue = "0") val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "remote_checksum") val remoteChecksum: String? = null,
    @ColumnInfo(name = "remote_byte_size") val remoteByteSize: Long? = null,
    @ColumnInfo(name = "remote_verified_at") val remoteVerifiedAt: Long? = null,
    @ColumnInfo(name = "metadata_mutation_id") val metadataMutationId: String? = null,
    @ColumnInfo(name = "cancel_reason") val cancelReason: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)
