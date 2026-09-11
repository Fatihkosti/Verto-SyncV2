package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Session 313/B13: scope-bound durable bootstrap/recovery authority. */
@Entity(tableName = "sync_recovery_state", primaryKeys = ["scope_id"])
data class SyncRecoveryStateEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "sync_principal_id") val syncPrincipalId: String,
    @ColumnInfo(name = "contract_family") val contractFamily: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    @ColumnInfo(name = "scope_definition_version") val scopeDefinitionVersion: Int,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "bootstrap_session_id") val bootstrapSessionId: String?,
    @ColumnInfo(name = "baseline_cursor") val baselineCursor: String?,
    @ColumnInfo(name = "baseline_revision") val baselineRevision: Long?,
    @ColumnInfo(name = "next_page_token") val nextPageToken: String?,
    @ColumnInfo(name = "expected_snapshot_rows") val expectedSnapshotRows: Long?,
    @ColumnInfo(name = "staged_snapshot_rows", defaultValue = "0") val stagedSnapshotRows: Long = 0L,
    @ColumnInfo(name = "recovery_generation", defaultValue = "0") val recoveryGeneration: Long = 0L,
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
    @ColumnInfo(name = "started_at") val startedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "expected_snapshot_digest") val expectedSnapshotDigest: String? = null,
    @ColumnInfo(name = "expected_coverage_json") val expectedCoverageJson: String? = null,
    @ColumnInfo(name = "bootstrap_high_watermark") val bootstrapHighWatermark: Long? = null,
    @ColumnInfo(name = "bootstrap_delta_token") val bootstrapDeltaToken: String? = null,
    @ColumnInfo(name = "stage_verified_at") val stageVerifiedAt: Long? = null,
)

/** B13: raw authoritative snapshot staging. Never a live-domain table. */
@Entity(
    tableName = "sync_bootstrap_stage",
    primaryKeys = ["scope_id", "bootstrap_session_id", "ordinal"],
    indices = [
        Index(value = ["scope_id", "bootstrap_session_id", "aggregate_type", "aggregate_id"], unique = true, name = "index_sync_bootstrap_stage_identity"),
        Index(value = ["scope_id", "bootstrap_session_id", "aggregate_type", "ordinal"], name = "index_sync_bootstrap_stage_aggregate"),
    ],
)
data class SyncBootstrapStageEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "bootstrap_session_id") val bootstrapSessionId: String,
    @ColumnInfo(name = "ordinal") val ordinal: Long,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "entity_version") val entityVersion: Long?,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "partition_key") val partitionKey: String,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String,
    @ColumnInfo(name = "is_tombstone", defaultValue = "0") val isTombstone: Boolean = false,
    @ColumnInfo(name = "promotion_state", defaultValue = "'STAGED'") val promotionState: String = "STAGED",
    @ColumnInfo(name = "wait_reason") val waitReason: String? = null,
    @ColumnInfo(name = "applied_at") val appliedAt: Long? = null,
)

/**
 * B13 durable before-promotion content seal. Each component hashes every persisted column of the
 * unresolved owner rows or protection metadata named by the column; captured_at is diagnostic only.
 */
@Entity(
    tableName = "sync_recovery_protection_manifest",
    primaryKeys = ["scope_id", "bootstrap_session_id"],
    indices = [Index(value = ["organization_id"], name = "index_sync_recovery_protection_manifest_org")],
)
data class SyncRecoveryProtectionManifestEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "bootstrap_session_id") val bootstrapSessionId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "combined_sha256") val combinedSha256: String,
    @ColumnInfo(name = "unified_sha256") val unifiedSha256: String,
    @ColumnInfo(name = "party_sha256") val partySha256: String,
    @ColumnInfo(name = "financial_sha256") val financialSha256: String,
    @ColumnInfo(name = "inventory_stock_sha256") val inventoryStockSha256: String,
    @ColumnInfo(name = "inventory_cost_sha256") val inventoryCostSha256: String,
    @ColumnInfo(name = "optimal_sha256") val optimalSha256: String,
    @ColumnInfo(name = "attachment_sha256") val attachmentSha256: String,
    @ColumnInfo(name = "pending_reference_sha256") val pendingReferenceSha256: String,
    @ColumnInfo(name = "mutation_packet_sha256") val mutationPacketSha256: String,
    @ColumnInfo(name = "local_generation_sha256") val localGenerationSha256: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
)

/** Session 313: privacy-safe operational metadata only. */
@Entity(tableName = "sync_health_state", primaryKeys = ["scope_id"])
data class SyncHealthStateEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "last_successful_push_at") val lastSuccessfulPushAt: Long?,
    @ColumnInfo(name = "last_successful_pull_at") val lastSuccessfulPullAt: Long?,
    @ColumnInfo(name = "last_failure_category") val lastFailureCategory: String?,
    @ColumnInfo(name = "last_failure_code") val lastFailureCode: String?,
    @ColumnInfo(name = "last_reconciliation_at") val lastReconciliationAt: Long?,
    @ColumnInfo(name = "last_reconciliation_status") val lastReconciliationStatus: String?,
    @ColumnInfo(name = "last_observed_server_revision") val lastObservedServerRevision: Long?,
    @ColumnInfo(name = "full_resync_count", defaultValue = "0") val fullResyncCount: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
