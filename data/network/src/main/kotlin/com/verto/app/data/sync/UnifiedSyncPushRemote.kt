package com.verto.app.data.sync

import com.verto.app.data.remote.VertoSupabase
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Domain-facing response keeps v304 contract types frozen while carrying v309 resolution metadata. */
data class UnifiedSyncPushResponse(
    val receipt: SyncReceipt,
    val resolutionRequirement: SyncConflictResolutionRequirement? = null,
    val contractFamily: String = UNIFIED_SYNC_CONTRACT_FAMILY,
    val contractVersion: Int = UNIFIED_SYNC_CONTRACT_VERSION,
)

interface UnifiedSyncPushRemote {
    suspend fun apply(mutation: SyncMutation): UnifiedSyncPushResponse

    /** B05 boundary: callers hand transport the exact durable UTF-8 text; lease data stays separate. */
    suspend fun applyFrozen(wireJson: String, wireSha256: String, leaseToken: String): UnifiedSyncPushResponse {
        require(leaseToken.isNotBlank())
        val actual = MessageDigest.getInstance("SHA-256").digest(wireJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        require(actual == wireSha256) { "FROZEN_WIRE_HASH_MISMATCH" }
        val mutation = Json { ignoreUnknownKeys = true; explicitNulls = true }
            .decodeFromString<SyncMutation>(wireJson)
        return apply(mutation)
    }
}

@Singleton
class SupabaseUnifiedSyncPushRemote @Inject constructor() : UnifiedSyncPushRemote {
    override suspend fun apply(mutation: SyncMutation): UnifiedSyncPushResponse {
        UnifiedSyncContractRules.requireValidMutation(mutation)
        val wire = VertoSupabase.client.postgrest.rpc(
            function = "verto_apply_sync_mutation",
            parameters = UnifiedSyncPushRpcRequestWire(mutation.toPushWire()),
        ).decodeAs<UnifiedSyncPushResponseWire>()

        require(wire.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY && wire.contractVersion == UNIFIED_SYNC_CONTRACT_VERSION) {
            "CONTRACT_UNSUPPORTED: push response contract mismatch"
        }
        require(wire.mutationId == mutation.mutationId && wire.aggregateId == mutation.aggregateId) {
            "SERVER_PROTOCOL_INCONSISTENCY: receipt identity mismatch"
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
                status = status,
                mutationId = wire.mutationId,
                aggregateId = wire.aggregateId,
                serverVersion = wire.serverVersion,
                serverRevision = wire.serverRevision,
                authoritativePayload = wire.authoritativePayload,
                conflictCode = wire.conflictCode,
                validationCode = wire.validationCode,
                transactionId = wire.transactionId,
                retryAfterEpochMillis = wire.retryAfterEpochMillis,
                requestHash = wire.requestHash,
            ),
            resolutionRequirement = requirement,
            contractFamily = wire.contractFamily,
            contractVersion = wire.contractVersion,
        )
    }
}
