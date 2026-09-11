package com.verto.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UnifiedSyncBootstrapBeginRequestWire(@SerialName("p_scope_id") val scopeId: String)

@Serializable
data class UnifiedSyncBootstrapStartWire(
    @SerialName("contract_family") val contractFamily: String,
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("scope_id") val scopeId: String,
    @SerialName("bootstrap_session_id") val bootstrapSessionId: String,
    @SerialName("baseline_revision") val baselineRevision: Long? = null,
    @SerialName("baseline_cursor") val baselineCursor: String,
    @SerialName("snapshot_row_count") val snapshotRowCount: Long,
    @SerialName("snapshot_digest_sha256") val snapshotDigestSha256: String? = null,
    @SerialName("coverage_aggregate_types") val coverageAggregateTypes: List<String>? = null,
    @SerialName("high_watermark") val highWatermark: Long? = null,
    @SerialName("delta_token") val deltaToken: String? = null,
    @SerialName("first_page_token") val firstPageToken: String,
    @SerialName("expires_at_epoch_millis") val expiresAtEpochMillis: Long,
)

@Serializable
data class UnifiedSyncBootstrapPageRequestWire(
    @SerialName("p_bootstrap_session_id") val bootstrapSessionId: String,
    @SerialName("p_page_token") val pageToken: String,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
data class UnifiedSyncBootstrapRowWire(
    val ordinal: Long,
    val aggregateType: String,
    val aggregateId: String,
    val entityVersion: Long? = null,
    val payloadVersion: Int,
    val payload: JsonObject = JsonObject(emptyMap()),
    val partitionKey: String,
    @SerialName("is_tombstone") val isTombstone: Boolean = false,
)

@Serializable
data class UnifiedSyncBootstrapPageWire(
    @SerialName("bootstrap_session_id") val bootstrapSessionId: String,
    @SerialName("baseline_cursor") val baselineCursor: String,
    val rows: List<UnifiedSyncBootstrapRowWire> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_page_token") val nextPageToken: String? = null,
    @SerialName("snapshot_complete") val snapshotComplete: Boolean,
)

@Serializable
data class UnifiedSyncManifestRequestWire(
    @SerialName("p_scope_id") val scopeId: String,
    @SerialName("p_partition_token") val partitionToken: String? = null,
)

@Serializable
data class UnifiedSyncManifestBodyWire(
    val aggregateType: String,
    val partitionKey: String,
    @SerialName("row_count") val rowCount: Long,
    @SerialName("content_hash_or_version_digest") val contentHashOrVersionDigest: String,
    @SerialName("manifest_revision") val manifestRevision: Long,
    @SerialName("scope_id") val scopeId: String,
)

@Serializable
data class UnifiedSyncManifestPageWire(
    @SerialName("scope_id") val scopeId: String,
    val manifest: UnifiedSyncManifestBodyWire? = null,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("next_partition_token") val nextPartitionToken: String? = null,
)

data class SyncBootstrapStart(
    val scope: SyncScope,
    val bootstrapSessionId: String,
    val baselineRevision: Long?,
    val baselineCursor: String,
    val expectedSnapshotRows: Long,
    val firstPageToken: String,
    val expiresAtEpochMillis: Long,
    val expectedSnapshotDigest: String? = null,
    val coverageAggregateTypes: List<String>? = null,
    val highWatermark: Long? = null,
    val deltaToken: String? = null,
)

data class SyncBootstrapRow(
    val ordinal: Long,
    val aggregateType: String,
    val aggregateId: String,
    val entityVersion: Long?,
    val payloadVersion: Int,
    val payload: JsonObject,
    val partitionKey: String,
    val isTombstone: Boolean = false,
)

data class SyncBootstrapPage(
    val bootstrapSessionId: String,
    val baselineCursor: String,
    val rows: List<SyncBootstrapRow>,
    val hasMore: Boolean,
    val nextPageToken: String?,
    val snapshotComplete: Boolean,
)

data class SyncReconciliationPage(
    val scopeId: String,
    val manifest: SyncReconciliationManifest?,
    val hasMore: Boolean,
    val nextPartitionToken: String?,
)
