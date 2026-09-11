package com.verto.app.data.sync

import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncSourceOwner
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Converts a specialized owner's already-captured v2 DTO into an immutable packet. */
@Singleton
class SpecializedMutationCaptureV2 @Inject constructor(
    private val frozenMutationStore: FrozenMutationStore,
) {
    suspend fun capture(
        sourceOwner: SyncSourceOwner,
        sourceId: String,
        mutationId: String,
        source: StrongerMutationSource,
        protectedKeys: Set<ProtectedSyncKey>,
    ) {
        require(source.payloadVersion == SYNC_REPAIR_PAYLOAD_VERSION) { "CONTRACT_UNSUPPORTED: payloadVersion" }
        require(protectedKeys.isNotEmpty()) { "CONTRACT_FIELD_MISSING: protectedKeys" }
        val intent = buildJsonObject {
            put("contractFamily", UNIFIED_SYNC_CONTRACT_FAMILY)
            put("contractVersion", SYNC_REPAIR_CONTRACT_VERSION)
            put("mutationId", mutationId); put("organizationId", source.organizationId)
            put("aggregateType", source.aggregateType); put("aggregateId", source.aggregateId)
            put("operationType", source.domainOperation); put("localSequence", source.localSequence)
            put("aggregateSequence", source.aggregateSequence); put("payloadVersion", source.payloadVersion)
            put("payload", source.payload); put("createdAtEpochMillis", source.createdAtEpochMillis)
            put("commandBatchId", source.transactionId?.let(::JsonPrimitive) ?: JsonNull)
        }.toString()
        val contentHash = sha256(source.payload.toString())
        frozenMutationStore.captureOwner(FrozenOwnerIntent(
            organizationId = source.organizationId, mutationId = mutationId, sourceOwner = sourceOwner,
            sourceId = sourceId, businessIdentity = source.businessIdentity, intentJson = intent,
            versionFamily = source.aggregateType, initialBaseVersion = source.baseVersion,
            batchId = source.transactionId,
            protectedContent = protectedKeys.associateWith { contentHash },
            createdAt = source.createdAtEpochMillis,
        ))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
