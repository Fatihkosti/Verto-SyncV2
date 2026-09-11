package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "party_roles",
    foreignKeys = [ForeignKey(entity = PartyIdentityEntity::class, parentColumns = ["id"], childColumns = ["party_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index(value = ["party_id"]),
        Index(value = ["organization_id", "party_id", "role"], unique = true, name = "index_party_roles_identity"),
        Index(value = ["organization_id", "role", "status"], name = "index_party_roles_listing"),
        Index(value = ["dirty", "sync_revision"], name = "index_party_roles_sync"),
    ],
)
@Serializable
data class PartyRoleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "party_id") val partyId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val role: String,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    @ColumnInfo(name = "archived_by") val archivedBy: String? = null,
    @ColumnInfo(name = "archive_reason") val archiveReason: String? = null,
    @ColumnInfo(name = "sync_revision") val syncRevision: Long = 0,
    val dirty: Boolean = true,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "customer_profiles",
    primaryKeys = ["organization_id", "party_id"],
    foreignKeys = [ForeignKey(entity = PartyIdentityEntity::class, parentColumns = ["id"], childColumns = ["party_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index(value = ["party_id"]),
        Index(value = ["organization_id", "segment"], name = "index_customer_profiles_org_segment"),
    ],
)
@Serializable
data class CustomerProfileEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "party_id") val partyId: String,
    val segment: String,
    @ColumnInfo(name = "age_years") val ageYears: Int? = null,
    @ColumnInfo(name = "purchase_contact_name") val purchaseContactName: String = "",
    @ColumnInfo(name = "business_activity") val businessActivity: String = "",
    @ColumnInfo(name = "workplace_name") val workplaceName: String = "",
    @ColumnInfo(name = "shop_name") val shopName: String = "",
    @ColumnInfo(name = "workshop_name") val workshopName: String = "",
    /** Ordered vehicle model/name list encoded with the Party V2 list delimiter (||). */
    @ColumnInfo(name = "vehicle_models") val vehicleModels: String = "",
    @ColumnInfo(name = "workshop_worker_count") val workshopWorkerCount: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_revision") val syncRevision: Long = 0,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "supplier_profiles",
    primaryKeys = ["organization_id", "party_id"],
    foreignKeys = [ForeignKey(entity = PartyIdentityEntity::class, parentColumns = ["id"], childColumns = ["party_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index(value = ["party_id"]),
        Index(value = ["organization_id", "scope"], name = "index_supplier_profiles_org_scope"),
    ],
)
@Serializable
data class SupplierProfileEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "party_id") val partyId: String,
    val scope: String,
    val country: String = "",
    @ColumnInfo(name = "currency_code") val currencyCode: String = "",
    val specialty: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_revision") val syncRevision: Long = 0,
    val dirty: Boolean = true,
)

@Entity(tableName = "party_migration_issues", indices = [Index("party_id"), Index("resolved")])
data class PartyMigrationIssueEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "party_id") val partyId: String,
    val field: String,
    @ColumnInfo(name = "raw_value") val rawValue: String,
    val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val resolved: Boolean = false,
)

@Entity(tableName = "party_role_audit", indices = [Index("party_id"), Index("occurred_at")])
data class PartyRoleAuditEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "party_id") val partyId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val role: String,
    val action: String,
    @ColumnInfo(name = "actor_id") val actorId: String,
    val reason: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)

@Entity(tableName = "party_sync_outbox", indices = [Index(value = ["operation_id"], unique = true), Index(value = ["state", "next_attempt_at"])])
data class PartySyncOutboxEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int = 2,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val state: String = "PENDING",
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String = "",
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "party_sync_conflicts", indices = [Index("aggregate_id"), Index("resolved")])
data class PartySyncConflictEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "local_payload") val localPayload: String,
    @ColumnInfo(name = "remote_payload") val remotePayload: String,
    @ColumnInfo(name = "base_revision") val baseRevision: Long,
    @ColumnInfo(name = "server_revision") val serverRevision: Long,
    val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val resolved: Boolean = false,
)
