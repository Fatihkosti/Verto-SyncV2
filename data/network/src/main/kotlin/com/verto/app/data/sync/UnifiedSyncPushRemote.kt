package com.verto.app.data.sync

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Domain-facing response carries only verified V2 server receipts on the production route. */
data class UnifiedSyncPushResponse(
    val receipt: SyncReceipt,
    val resolutionRequirement: SyncConflictResolutionRequirement? = null,
    val contractFamily: String = UNIFIED_SYNC_CONTRACT_FAMILY,
    val contractVersion: Int = UNIFIED_SYNC_CONTRACT_VERSION,
)

interface UnifiedSyncPushRemote {
    suspend fun apply(mutation: SyncMutation): UnifiedSyncPushResponse

    suspend fun applyFrozen(wireJson: String, wireSha256: String, leaseToken: String): UnifiedSyncPushResponse

    /** Sends an already-sealed multi-owner batch without reconstructing any member bytes. */
    suspend fun applyFrozenBatch(wireJson: String, wireSha256: String): List<UnifiedSyncPushResponse> =
        error("ATOMIC_BATCH_REMOTE_UNAVAILABLE")
}

@Singleton
class SupabaseUnifiedSyncPushRemote @Inject constructor() : UnifiedSyncPushRemote {
    override suspend fun apply(mutation: SyncMutation): UnifiedSyncPushResponse {
        UnifiedSyncContractRules.requireValidMutation(mutation)
        val member = buildJsonObject {
            put("contractFamily", UNIFIED_SYNC_CONTRACT_FAMILY)
            put("contractVersion", SYNC_REPAIR_CONTRACT_VERSION)
            put("mutationId", mutation.mutationId)
            put("organizationId", mutation.organizationId)
            put("aggregateType", mutation.aggregateType)
            put("aggregateId", mutation.aggregateId)
            put("operationType", mutation.operationType.name)
            put("baseVersion", mutation.baseVersion?.let(::JsonPrimitive) ?: JsonNull)
            put("localSequence", mutation.localSequence)
            put("aggregateSequence", mutation.aggregateSequence)
            put("payloadVersion", mutation.payloadVersion)
            put("payload", mutation.payload)
            put("createdAtEpochMillis", mutation.createdAtEpochMillis)
            put("commandBatchId", mutation.commandBatchId?.let(::JsonPrimitive) ?: JsonNull)
            put("commandOrder", mutation.commandOrder?.let(::JsonPrimitive) ?: JsonNull)
            put("dependsOnMutationId", mutation.dependsOnMutationId?.let(::JsonPrimitive) ?: JsonNull)
        }.toString()
        return applyFrozen(member, sha256(member), "direct-v2")
    }

    override suspend fun applyFrozen(wireJson: String, wireSha256: String, leaseToken: String): UnifiedSyncPushResponse {
        require(leaseToken.isNotBlank())
        require(sha256(wireJson) == wireSha256) { "FROZEN_WIRE_HASH_MISMATCH" }
        val member = SyncContractV2Codec.json.parseToJsonElement(wireJson).jsonObject
        val mutationId = member.requiredText("mutationId")
        val organizationId = member.requiredText("organizationId")
        val commandBatchId = member["commandBatchId"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
        require(commandBatchId.isNullOrBlank()) { "BATCH_MEMBER_REQUIRES_ATOMIC_DISPATCH" }
        val batchId = "single:$mutationId"
        val batchWire = SyncContractV2Codec.encode(
            SyncBatchRequestDtoV2(
                organizationId = organizationId,
                batchId = batchId,
                memberCount = 1,
                members = listOf(SyncBatchMemberDtoV2(0, wireJson, wireSha256)),
                snapshotBlobs = emptyList(),
            )
        )
        val responses = applyFrozenBatch(batchWire, sha256(batchWire))
        return responses.singleOrNull { it.receipt.mutationId == mutationId }
            ?: error("SERVER_PROTOCOL_INCONSISTENCY: singleton batch receipt missing")
    }

    override suspend fun applyFrozenBatch(wireJson: String, wireSha256: String): List<UnifiedSyncPushResponse> {
        require(sha256(wireJson) == wireSha256) { "FROZEN_BATCH_HASH_MISMATCH" }
        val wire = VertoSupabase.client.postgrest.rpc(
            function = "verto_apply_sync_batch_v2",
            parameters = UnifiedSyncBatchRpcRequestWire(wireJson, wireSha256),
        ).decodeAs<UnifiedSyncBatchResponseWire>()
        require(wire.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY && wire.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) {
            "CONTRACT_BLOCKED: batch response contract mismatch"
        }
        require(wire.requestHash == wireSha256) { "SERVER_PROTOCOL_INCONSISTENCY: batch request hash mismatch" }
        if (wire.status != "APPLIED") {
            throw IllegalArgumentException("BATCH_REJECTED:${wire.validationCode ?: "UNKNOWN"}:${wire.detail.orEmpty()}")
        }
        require(wire.memberReceipts.isNotEmpty()) { "SERVER_PROTOCOL_INCONSISTENCY: applied batch returned no receipts" }
        return wire.memberReceipts.map(::toDomain)
    }

    private fun toDomain(wire: UnifiedSyncPushResponseWire): UnifiedSyncPushResponse {
        require(wire.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY && wire.contractVersion == SYNC_REPAIR_CONTRACT_VERSION) {
            "CONTRACT_BLOCKED: member receipt contract mismatch"
        }
        val status = runCatching { SyncReceiptStatus.valueOf(wire.status) }
            .getOrElse { throw IllegalArgumentException("CONTRACT_UNSUPPORTED: receipt status=${wire.status}") }
        val requirement = wire.resolutionRequirement?.let { raw ->
            runCatching { SyncConflictResolutionRequirement.valueOf(raw) }
                .getOrElse { throw IllegalArgumentException("FAIL_CONFLICT_REQUIREMENT_UNKNOWN: $raw") }
        }
        if (status == SyncReceiptStatus.CONFLICT) {
            require(wire.serverVersion != null && wire.serverVersion > 0) { "SERVER_PROTOCOL_INCONSISTENCY: conflict serverVersion" }
            require(wire.authoritativePayload != null) { "SERVER_PROTOCOL_INCONSISTENCY: conflict authoritative payload" }
            require(!wire.conflictCode.isNullOrBlank() && requirement != null) { "FAIL_CONFLICT_REQUIREMENT_UNKNOWN" }
        }
        return UnifiedSyncPushResponse(
            receipt = SyncReceipt(
                status = status, mutationId = wire.mutationId, aggregateId = wire.aggregateId,
                serverVersion = wire.serverVersion, serverRevision = wire.serverRevision,
                authoritativePayload = wire.authoritativePayload, conflictCode = wire.conflictCode,
                validationCode = wire.validationCode, transactionId = wire.transactionId,
                retryAfterEpochMillis = wire.retryAfterEpochMillis, requestHash = wire.requestHash,
            ),
            resolutionRequirement = requirement,
            contractFamily = wire.contractFamily,
            contractVersion = wire.contractVersion,
        )
    }

    private fun JsonObject.requiredText(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("CONTRACT_FIELD_MISSING: $name")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
