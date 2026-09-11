package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_entity_version",
    primaryKeys = ["organization_id", "scope_id", "version_family", "aggregate_id"],
    indices = [
        Index(
            value = ["organization_id", "version_family", "aggregate_id"],
            name = "index_sync_entity_version_lookup",
        ),
    ],
)
data class SyncEntityVersionEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "version_family") val versionFamily: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "applied_server_version") val appliedServerVersion: Long?,
    @ColumnInfo(name = "observed_server_version") val observedServerVersion: Long?,
    @ColumnInfo(name = "last_applied_revision") val lastAppliedRevision: Long?,
    @ColumnInfo(name = "applied_content_hash") val appliedContentHash: String?,
    @ColumnInfo(name = "tombstone", defaultValue = "0") val tombstone: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "sync_local_generation",
    primaryKeys = ["organization_id", "aggregate_type", "aggregate_id"],
)
data class SyncLocalGenerationEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "sync_mutation_packet",
    primaryKeys = ["organization_id", "mutation_id"],
    indices = [
        Index(
            value = ["organization_id", "source_owner", "source_id"],
            unique = true,
            name = "index_sync_mutation_packet_source",
        ),
        Index(value = ["batch_id"], name = "index_sync_mutation_packet_batch"),
        Index(value = ["predecessor_mutation_id"], name = "index_sync_mutation_packet_predecessor"),
        Index(value = ["supersedes_mutation_id"], name = "index_sync_mutation_packet_supersedes"),
    ],
)
data class SyncMutationPacketEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "source_owner") val sourceOwner: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "business_identity") val businessIdentity: String,
    @ColumnInfo(name = "intent_json") val intentJson: String,
    @ColumnInfo(name = "intent_hash") val intentHash: String,
    @ColumnInfo(name = "captured_generation") val capturedGeneration: Long,
    @ColumnInfo(name = "captured_content_hash") val capturedContentHash: String,
    @ColumnInfo(name = "version_family") val versionFamily: String,
    @ColumnInfo(name = "initial_base_version") val initialBaseVersion: Long?,
    @ColumnInfo(name = "predecessor_mutation_id") val predecessorMutationId: String?,
    @ColumnInfo(name = "supersedes_mutation_id") val supersedesMutationId: String?,
    @ColumnInfo(name = "batch_id") val batchId: String?,
    @ColumnInfo(name = "wire_json") val wireJson: String?,
    @ColumnInfo(name = "wire_sha256") val wireSha256: String?,
    @ColumnInfo(name = "prepared_at") val preparedAt: Long?,
    @ColumnInfo(name = "first_dispatch_at") val firstDispatchAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sync_pending_reference",
    primaryKeys = ["organization_id", "source_owner", "source_id", "protected_type", "protected_id"],
    indices = [
        Index(
            value = ["organization_id", "protected_type", "protected_id"],
            name = "index_sync_pending_reference_protected",
        ),
    ],
)
data class SyncPendingReferenceEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "source_owner") val sourceOwner: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "protected_type") val protectedType: String,
    @ColumnInfo(name = "protected_id") val protectedId: String,
    @ColumnInfo(name = "captured_generation") val capturedGeneration: Long,
    @ColumnInfo(name = "captured_content_hash") val capturedContentHash: String,
    @ColumnInfo(name = "dependency_kind") val dependencyKind: String,
)

@Entity(
    tableName = "sync_write_batch",
    primaryKeys = ["organization_id", "batch_id"],
    indices = [
        Index(value = ["organization_id", "created_at"], name = "index_sync_write_batch_created"),
    ],
)
data class SyncWriteBatchEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "member_count") val memberCount: Int,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "wire_json") val wireJson: String?,
    @ColumnInfo(name = "wire_sha256") val wireSha256: String?,
    @ColumnInfo(name = "prepared_at") val preparedAt: Long?,
    @ColumnInfo(name = "sealed_at") val sealedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sync_write_batch_member",
    primaryKeys = ["organization_id", "batch_id", "member_order"],
    indices = [
        Index(
            value = ["organization_id", "source_owner", "source_id"],
            unique = true,
            name = "index_sync_write_batch_member_source",
        ),
        Index(
            value = ["organization_id", "mutation_id"],
            unique = true,
            name = "index_sync_write_batch_member_mutation",
        ),
    ],
)
data class SyncWriteBatchMemberEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "member_order") val memberOrder: Int,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "source_owner") val sourceOwner: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
)

@Entity(
    tableName = "sync_inbox_group",
    primaryKeys = ["scope_id", "transaction_id"],
    indices = [
        Index(
            value = ["scope_id", "state", "first_revision"],
            name = "index_sync_inbox_group_state",
        ),
    ],
)
data class SyncInboxGroupEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "member_count") val memberCount: Int,
    @ColumnInfo(name = "first_revision") val firstRevision: Long,
    @ColumnInfo(name = "last_revision") val lastRevision: Long,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "touched_keys_json") val touchedKeysJson: String,
    @ColumnInfo(name = "dependency_transaction_ids_json") val dependencyTransactionIdsJson: String,
    @ColumnInfo(name = "wait_reason") val waitReason: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "applied_at") val appliedAt: Long?,
    @ColumnInfo(name = "serialized_bytes", defaultValue = "0") val serializedBytes: Long = 0L,
    @ColumnInfo(name = "last_attempt_generation", defaultValue = "-1") val lastAttemptGeneration: Long = -1L,
)

@Entity(
    tableName = "sync_migration_evidence_v2",
    primaryKeys = [
        "organization_id",
        "source_kind",
        "source_id",
        "source_content_hash",
        "repair_version",
    ],
    indices = [
        Index(
            value = ["organization_id", "disposition", "source_kind"],
            name = "index_sync_migration_evidence_v2_disposition",
        ),
        Index(value = ["target_mutation_id"], name = "index_sync_migration_evidence_v2_mutation"),
        Index(value = ["target_batch_id"], name = "index_sync_migration_evidence_v2_batch"),
    ],
)
data class SyncMigrationEvidenceV2Entity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_content_hash") val sourceContentHash: String,
    @ColumnInfo(name = "repair_version") val repairVersion: Int,
    @ColumnInfo(name = "raw_type") val rawType: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "source_state") val sourceState: String,
    @ColumnInfo(name = "legacy_record_reference") val legacyRecordReference: String?,
    @ColumnInfo(name = "classification") val classification: String,
    @ColumnInfo(name = "evidence_type") val evidenceType: String,
    @ColumnInfo(name = "target_mutation_id") val targetMutationId: String?,
    @ColumnInfo(name = "target_batch_id") val targetBatchId: String?,
    @ColumnInfo(name = "receipt_hash") val receiptHash: String?,
    @ColumnInfo(name = "disposition") val disposition: String,
    @ColumnInfo(name = "reason_code") val reasonCode: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "sync_snapshot_blob")
data class SyncSnapshotBlobEntity(
    @PrimaryKey @ColumnInfo(name = "content_hash") val contentHash: String,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "expense_revision_history",
    primaryKeys = ["organization_id", "expense_id", "server_version"],
    indices = [
        Index(
            value = ["organization_id", "expense_id", "server_version"],
            unique = true,
            name = "index_expense_revision_history_version",
        ),
        Index(
            value = ["organization_id", "write_id"],
            unique = true,
            name = "index_expense_revision_history_write",
        ),
    ],
)
data class ExpenseRevisionHistoryEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "expense_id") val expenseId: String,
    @ColumnInfo(name = "server_version") val serverVersion: Long,
    @ColumnInfo(name = "previous_version") val previousVersion: Long?,
    @ColumnInfo(name = "write_id") val writeId: String,
    @ColumnInfo(name = "before_content_hash") val beforeContentHash: String?,
    @ColumnInfo(name = "after_content_hash") val afterContentHash: String,
    @ColumnInfo(name = "actor_id") val actorId: String,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)
