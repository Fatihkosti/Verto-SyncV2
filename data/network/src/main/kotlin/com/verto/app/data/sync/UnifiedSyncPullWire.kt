package com.verto.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Session 308 wire-only DTOs. They deliberately mirror the v305 RPC JSON and not Room/domain naming policy. */
@Serializable
data class UnifiedSyncScopeWire(
    @SerialName("scope_id") val scopeId: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("sync_principal_id") val syncPrincipalId: String,
    @SerialName("contract_family") val contractFamily: String,
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("scope_definition_version") val scopeDefinitionVersion: Int,
)

@Serializable
data class UnifiedSyncPullRequestWire(
    @SerialName("p_scope_id") val scopeId: String,
    @SerialName("p_after_cursor") val afterCursor: String,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
data class UnifiedSyncChangeWire(
    val revision: Long,
    val organizationId: String,
    val syncScopeId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operationType: String,
    val entityVersion: Long? = null,
    val payloadVersion: Int,
    val payload: JsonObject = JsonObject(emptyMap()),
    val originMutationId: String? = null,
    val transactionId: String,
    val transactionOrder: Int,
    val transactionSize: Int,
    val deletedAtEpochMillis: Long? = null,
    val changedAtEpochMillis: Long,
)

@Serializable
data class UnifiedSyncPullPageWire(
    @SerialName("contract_family") val contractFamily: String,
    @SerialName("contract_version") val contractVersion: Int,
    @SerialName("scope_id") val scopeId: String,
    val coverage: String,
    val changes: List<UnifiedSyncChangeWire> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("min_available_revision") val minAvailableRevision: Long? = null,
    @SerialName("page_high_watermark") val pageHighWatermark: Long? = null,
    @SerialName("ends_at_transaction_boundary") val endsAtTransactionBoundary: Boolean,
    @SerialName("advances_global_cursor") val advancesGlobalCursor: Boolean,
)
