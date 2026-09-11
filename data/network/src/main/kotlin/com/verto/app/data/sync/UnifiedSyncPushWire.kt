package com.verto.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Legacy V1 DTO remains read-compatible for historical reconciliation only. */
@Serializable
data class UnifiedSyncMutationWire(
    @SerialName("mutation_id") val mutationId: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("aggregate_type") val aggregateType: String,
    @SerialName("aggregate_id") val aggregateId: String,
    @SerialName("operation_type") val operationType: String,
    @SerialName("base_version") val baseVersion: Long? = null,
    @SerialName("payload_version") val payloadVersion: Int,
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("command_batch_id") val commandBatchId: String? = null,
    @SerialName("command_order") val commandOrder: Int? = null,
    @SerialName("depends_on_mutation_id") val dependsOnMutationId: String? = null,
)

@Serializable
data class UnifiedSyncPushRpcRequestWire(
    @SerialName("p_mutation") val mutation: UnifiedSyncMutationWire,
)

/** B07 exact UTF-8 text/hash boundary. PostgREST must not deserialize/re-serialize the frozen body. */
@Serializable
data class UnifiedSyncBatchRpcRequestWire(
    @SerialName("p_wire_json") val wireJson: String,
    @SerialName("p_wire_sha256") val wireSha256: String,
)

@Serializable
data class UnifiedSyncBatchResponseWire(
    @SerialName("contract_family") val contractFamily: String,
    @SerialName("contract_version") val contractVersion: Int,
    val status: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("request_hash") val requestHash: String,
    @SerialName("validation_code") val validationCode: String? = null,
    val detail: String? = null,
    @SerialName("member_receipts") val memberReceipts: List<UnifiedSyncPushResponseWire> = emptyList(),
)

@Serializable
data class UnifiedSyncPushResponseWire(
    @SerialName("contract_family") val contractFamily: String,
    @SerialName("contract_version") val contractVersion: Int,
    val status: String,
    @SerialName("mutation_id") val mutationId: String,
    @SerialName("aggregate_id") val aggregateId: String,
    @SerialName("server_version") val serverVersion: Long? = null,
    @SerialName("server_revision") val serverRevision: Long? = null,
    @SerialName("authoritative_payload") val authoritativePayload: JsonObject? = null,
    @SerialName("conflict_code") val conflictCode: String? = null,
    @SerialName("validation_code") val validationCode: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("retry_after_epoch_millis") val retryAfterEpochMillis: Long? = null,
    @SerialName("request_hash") val requestHash: String? = null,
    @SerialName("resolution_requirement") val resolutionRequirement: String? = null,
)

fun SyncMutation.toPushWire(): UnifiedSyncMutationWire = UnifiedSyncMutationWire(
    mutationId = mutationId,
    organizationId = organizationId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    operationType = operationType.name,
    baseVersion = baseVersion,
    payloadVersion = payloadVersion,
    payload = payload,
    commandBatchId = commandBatchId,
    commandOrder = commandOrder,
    dependsOnMutationId = dependsOnMutationId,
)
