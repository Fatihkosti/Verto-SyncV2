package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Request metadata only. Payload authority remains sync_inbox plus its immutable group manifest. */
@Entity(tableName = "sync_inbox_apply_request", primaryKeys = ["scope_id"], indices = [
    Index(value = ["organization_id", "next_wake_at"], name = "index_sync_inbox_apply_request_wake"),
])
data class SyncInboxApplyRequestEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "requested_generation") val requestedGeneration: Long,
    @ColumnInfo(name = "drained_generation") val drainedGeneration: Long,
    @ColumnInfo(name = "next_wake_at") val nextWakeAt: Long?,
    @ColumnInfo(name = "storage_wait_reason") val storageWaitReason: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "sync_inbox_dependency", primaryKeys = ["scope_id", "transaction_id", "depends_on_transaction_id"], indices = [
    Index(value = ["scope_id", "depends_on_transaction_id"], name = "index_sync_inbox_dependency_target"),
])
data class SyncInboxDependencyEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "depends_on_transaction_id") val dependsOnTransactionId: String,
)

@Entity(tableName = "sync_inbox_touched_key", primaryKeys = ["scope_id", "transaction_id", "key_type", "key_id"], indices = [
    Index(value = ["scope_id", "key_type", "key_id"], name = "index_sync_inbox_touched_key_lookup"),
])
data class SyncInboxTouchedKeyEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "key_type") val keyType: String,
    @ColumnInfo(name = "key_id") val keyId: String,
)
