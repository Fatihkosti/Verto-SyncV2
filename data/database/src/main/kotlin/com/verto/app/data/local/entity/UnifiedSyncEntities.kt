package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Session 306: durable unified mutation envelope. Runtime producers remain disabled until Session 307. */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["organization_id", "local_sequence"], unique = true, name = "index_sync_outbox_org_local_sequence"),
        Index(value = ["organization_id", "aggregate_type", "aggregate_id", "aggregate_sequence"], unique = true, name = "index_sync_outbox_aggregate_sequence"),
        Index(value = ["organization_id", "state", "next_attempt_at", "local_sequence"], name = "index_sync_outbox_delivery"),
        Index(value = ["organization_id", "aggregate_type", "aggregate_id", "state", "aggregate_sequence"], name = "index_sync_outbox_aggregate_delivery"),
        Index(value = ["depends_on_mutation_id"], name = "index_sync_outbox_dependency"),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "base_version") val baseVersion: Long?,
    @ColumnInfo(name = "local_sequence") val localSequence: Long,
    @ColumnInfo(name = "aggregate_sequence") val aggregateSequence: Long,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "semantic_fingerprint") val semanticFingerprint: String,
    @ColumnInfo(name = "command_batch_id") val commandBatchId: String?,
    @ColumnInfo(name = "command_order") val commandOrder: Int?,
    @ColumnInfo(name = "depends_on_mutation_id") val dependsOnMutationId: String?,
    @ColumnInfo(name = "state", defaultValue = "'PENDING'") val state: String = "PENDING",
    @ColumnInfo(name = "attempt_count", defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error_type") val lastErrorType: String? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "next_attempt_at", defaultValue = "0") val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "lease_owner") val leaseOwner: String? = null,
    @ColumnInfo(name = "lease_token") val leaseToken: String? = null,
    @ColumnInfo(name = "lease_scope_epoch") val leaseScopeEpoch: Long? = null,
    @ColumnInfo(name = "lease_expires_at") val leaseExpiresAt: Long? = null,
    @ColumnInfo(name = "acked_server_revision") val ackedServerRevision: Long? = null,
    @ColumnInfo(name = "acked_server_version") val ackedServerVersion: Long? = null,
    @ColumnInfo(name = "receipt_status") val receiptStatus: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "acked_at") val ackedAt: Long? = null,
)

/** Session 306: durable scoped server-change receipt. changedAt is metadata, never ordering authority. */
@Entity(
    tableName = "sync_inbox",
    primaryKeys = ["scope_id", "server_revision"],
    indices = [
        Index(value = ["organization_id", "scope_id", "server_revision"], name = "index_sync_inbox_org_scope_revision"),
        Index(value = ["scope_id", "apply_state", "server_revision"], name = "index_sync_inbox_apply"),
        Index(value = ["scope_id", "transaction_id", "transaction_order"], name = "index_sync_inbox_transaction"),
        Index(value = ["organization_id", "aggregate_type", "aggregate_id", "server_revision"], name = "index_sync_inbox_aggregate_revision"),
    ],
)
data class SyncInboxEntity(
    @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "server_revision") val serverRevision: Long,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "entity_version") val entityVersion: Long?,
    @ColumnInfo(name = "payload_version") val payloadVersion: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "origin_mutation_id") val originMutationId: String?,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "transaction_order") val transactionOrder: Int,
    @ColumnInfo(name = "transaction_size") val transactionSize: Int,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String,
    @ColumnInfo(name = "apply_state", defaultValue = "'RECEIVED'") val applyState: String = "RECEIVED",
    @ColumnInfo(name = "apply_error_code") val applyErrorCode: String? = null,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "applied_at") val appliedAt: Long? = null,
)



/** Session 309: durable authoritative conflict outcome for one immutable mutation. */
@Entity(
    tableName = "sync_conflict",
    indices = [
        Index(value = ["mutation_id"], unique = true, name = "index_sync_conflict_mutation_id"),
        Index(value = ["organization_id", "state", "created_at"], name = "index_sync_conflict_org_state_created"),
        Index(value = ["organization_id", "aggregate_type", "aggregate_id", "state"], name = "index_sync_conflict_aggregate_state"),
        Index(value = ["resolution_mutation_id"], name = "index_sync_conflict_resolution_mutation"),
    ],
)
data class SyncConflictEntity(
    @PrimaryKey @ColumnInfo(name = "conflict_id") val conflictId: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "aggregate_type") val aggregateType: String,
    @ColumnInfo(name = "aggregate_id") val aggregateId: String,
    @ColumnInfo(name = "conflict_code") val conflictCode: String,
    @ColumnInfo(name = "local_payload_version") val localPayloadVersion: Int,
    @ColumnInfo(name = "server_version") val serverVersion: Long,
    @ColumnInfo(name = "authoritative_payload_json") val authoritativePayloadJson: String,
    @ColumnInfo(name = "resolution_requirement") val resolutionRequirement: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "request_hash") val requestHash: String?,
    @ColumnInfo(name = "resolution_mutation_id") val resolutionMutationId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
)

/** B11: immutable proof bundle used by the human conflict-review flow. */
@Entity(
    tableName = "sync_conflict_review_evidence",
    indices = [
        Index(value = ["organization_id", "created_at"], name = "index_sync_conflict_review_evidence_org_created"),
        Index(value = ["mutation_id"], unique = true, name = "index_sync_conflict_review_evidence_mutation"),
    ],
)
data class SyncConflictReviewEvidenceEntity(
    @PrimaryKey @ColumnInfo(name = "conflict_id") val conflictId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "local_payload_json") val localPayloadJson: String,
    @ColumnInfo(name = "local_payload_sha256") val localPayloadSha256: String,
    @ColumnInfo(name = "local_semantic_fingerprint") val localSemanticFingerprint: String,
    @ColumnInfo(name = "local_base_version") val localBaseVersion: Long?,
    @ColumnInfo(name = "remote_payload_json") val remotePayloadJson: String,
    @ColumnInfo(name = "remote_payload_sha256") val remotePayloadSha256: String,
    @ColumnInfo(name = "server_revision") val serverRevision: Long?,
    @ColumnInfo(name = "server_version") val serverVersion: Long,
    @ColumnInfo(name = "outcome_proof") val outcomeProof: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** B11: append-only decision/proof trail. Payloads stay in evidence; audit rows store hashes only. */
@Entity(
    tableName = "sync_conflict_resolution_audit",
    indices = [
        Index(value = ["conflict_id", "decided_at"], name = "index_sync_conflict_resolution_audit_conflict_time"),
        Index(value = ["resolution_mutation_id"], name = "index_sync_conflict_resolution_audit_resolution_mutation"),
    ],
)
data class SyncConflictResolutionAuditEntity(
    @PrimaryKey @ColumnInfo(name = "decision_id") val decisionId: String,
    @ColumnInfo(name = "conflict_id") val conflictId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "decision_type") val decisionType: String,
    @ColumnInfo(name = "actor_id") val actorId: String?,
    @ColumnInfo(name = "actor_role") val actorRole: String?,
    @ColumnInfo(name = "local_payload_sha256") val localPayloadSha256: String,
    @ColumnInfo(name = "remote_payload_sha256") val remotePayloadSha256: String,
    @ColumnInfo(name = "expected_server_version") val expectedServerVersion: Long,
    @ColumnInfo(name = "resolution_mutation_id") val resolutionMutationId: String?,
    @ColumnInfo(name = "proof_type") val proofType: String,
    @ColumnInfo(name = "proof_reference") val proofReference: String?,
    @ColumnInfo(name = "before_state") val beforeState: String,
    @ColumnInfo(name = "after_state") val afterState: String,
    @ColumnInfo(name = "decided_at") val decidedAt: Long,
)

/** Session 306: scope-bound opaque continuation state. Numeric revisions are diagnostic anchors only. */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "scope_id") val scopeId: String,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "sync_principal_id") val syncPrincipalId: String,
    @ColumnInfo(name = "contract_family") val contractFamily: String,
    @ColumnInfo(name = "contract_version") val contractVersion: Int,
    @ColumnInfo(name = "scope_definition_version") val scopeDefinitionVersion: Int,
    @ColumnInfo(name = "cursor_token") val cursorToken: String,
    @ColumnInfo(name = "received_cursor_token", defaultValue = "''") val receivedCursorToken: String = cursorToken,
    @ColumnInfo(name = "received_high_watermark") val receivedHighWatermark: Long? = null,
    @ColumnInfo(name = "applied_checkpoint") val appliedCheckpoint: Long? = null,
    @ColumnInfo(name = "last_applied_change_revision") val lastAppliedChangeRevision: Long?,
    @ColumnInfo(name = "page_high_watermark") val pageHighWatermark: Long?,
    @ColumnInfo(name = "min_available_revision") val minAvailableRevision: Long?,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Session 306: Room-persisted sequence authority; never reconstructed from outbox rows or wall clock. */
@Entity(
    tableName = "sync_sequence_state",
    primaryKeys = ["organization_id", "counter_kind", "aggregate_type", "aggregate_id"],
)
data class SyncSequenceStateEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "counter_kind") val counterKind: String,
    @ColumnInfo(name = "aggregate_type", defaultValue = "''") val aggregateType: String = "",
    @ColumnInfo(name = "aggregate_id", defaultValue = "''") val aggregateId: String = "",
    @ColumnInfo(name = "last_value") val lastValue: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
