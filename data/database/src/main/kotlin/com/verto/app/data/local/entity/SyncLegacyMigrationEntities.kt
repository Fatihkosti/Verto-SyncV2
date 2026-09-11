package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * M03 durable cutover journal. It records the fate of every legacy pending intent without deleting
 * the source row. Stronger outboxes remain the single delivery authority until their M05 bridge is retired.
 */
@Entity(
    tableName = "sync_legacy_migration_entry",
    primaryKeys = ["organization_id", "source_kind", "source_id"],
    indices = [
        Index(value = ["organization_id", "disposition", "created_at"], name = "index_sync_legacy_migration_disposition"),
        Index(value = ["organization_id", "aggregate_type", "aggregate_id"], name = "index_sync_legacy_migration_aggregate"),
        Index(value = ["target_mutation_id"], name = "index_sync_legacy_migration_target_mutation"),
    ],
)
data class SyncLegacyMigrationEntryEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "sync_principal_id") val syncPrincipalId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "source_state") val sourceState: String,
    @ColumnInfo(name = "business_identity") val businessIdentity: String,
    @ColumnInfo(name = "source_sequence") val sourceSequence: Long?,
    @ColumnInfo(name = "command_batch_id") val commandBatchId: String?,
    @ColumnInfo(name = "command_order") val commandOrder: Int?,
    @ColumnInfo(name = "depends_on_source_id") val dependsOnSourceId: String?,
    @ColumnInfo(name = "target_kind") val targetKind: String,
    @ColumnInfo(name = "target_mutation_id") val targetMutationId: String?,
    val disposition: String,
    @ColumnInfo(name = "reason_code") val reasonCode: String?,
    @ColumnInfo(name = "source_fingerprint") val sourceFingerprint: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Per trusted account/tenant progress marker. No wall-clock value is an ordering authority. */
@Entity(
    tableName = "sync_legacy_migration_state",
    primaryKeys = ["organization_id", "sync_principal_id"],
)
data class SyncLegacyMigrationStateEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "sync_principal_id") val syncPrincipalId: String,
    val phase: String,
    @ColumnInfo(name = "source_count") val sourceCount: Int,
    @ColumnInfo(name = "migrated_count") val migratedCount: Int,
    @ColumnInfo(name = "receipt_confirmed_count") val receiptConfirmedCount: Int,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "source_digest") val sourceDigest: String,
    @ColumnInfo(name = "legacy_writes_fenced") val legacyWritesFenced: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
)
