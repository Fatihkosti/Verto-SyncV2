package com.verto.app.data.sync

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.UnifiedSyncMutationDraft
import com.verto.app.data.local.entity.SyncAttachmentTransferEntity
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 producer-only facade. It performs no network, scheduling, ACK, cursor, inbox or conflict work.
 * M04 requires the caller to own the Room transaction containing both the domain mutation and enqueue.
 */
@Singleton
class UnifiedOutboxWriter @Inject constructor(
    private val database: AppDatabase,
    private val frozenMutationStore: FrozenMutationStore,
) {
    suspend fun enqueue(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        operationType: String,
        payload: Map<String, Any?>,
        mutationId: String = UUID.randomUUID().toString(),
        baseVersion: Long? = null,
        commandBatchId: String? = null,
        commandOrder: Int? = null,
        dependsOnMutationId: String? = null,
        businessIdentity: String = aggregateId,
        versionFamily: String = aggregateType,
        protectedContentHashes: Map<ProtectedSyncKey, String>? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): SyncOutboxEntity {
        requireProducerTransaction()
        val org = organizationId.trim()
        val aggregate = aggregateType.trim()
        val id = aggregateId.trim()
        require(org.isNotBlank()) { "FAIL_ORG_SCOPE" }
        require(id.isNotBlank()) { "aggregate id is required" }
        val contract = UnifiedSyncAggregateRegistry.requireById(aggregate)
        require(operationType in allowedOperations(contract.deletePolicy)) { "FAIL_DELETE_POLICY" }
        val payloadJson = canonicalSyncPayload(payload)
        val row = database.unifiedSyncDao().enqueueMutation(
            UnifiedSyncMutationDraft(
                mutationId = mutationId,
                organizationId = org,
                aggregateType = aggregate,
                aggregateId = id,
                operationType = operationType,
                baseVersion = baseVersion,
                payloadVersion = contract.payloadVersion,
                payloadJson = payloadJson,
                commandBatchId = commandBatchId,
                commandOrder = commandOrder,
                dependsOnMutationId = dependsOnMutationId,
                createdAt = createdAt,
            ),
            updatedAt = createdAt,
        )
        frozenMutationStore.captureUnified(
            row = row,
            businessIdentity = businessIdentity,
            versionFamily = versionFamily,
            protectedContent = protectedContentHashes ?: mapOf(
                ProtectedSyncKey(aggregate, id) to sha256Utf8(payloadJson),
            ),
        )
        return row
    }

    suspend fun enqueueAttachmentIntent(intent: SyncAttachmentTransferEntity) {
        requireProducerTransaction()
        require(intent.organizationId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        require(intent.localUri.isNotBlank()) { "local uri is required" }
        require(intent.objectKey.isNotBlank()) { "object key is required" }
        require(intent.contentChecksum.isNotBlank()) { "checksum is required" }
        require(intent.state == "PENDING") { "new transfer must be pending" }
        database.unifiedSyncProducerV307Dao().insertAttachmentTransfer(intent)
    }

    internal fun canonicalPayload(fields: Map<String, Any?>): String = canonicalSyncPayload(fields)

    private fun requireProducerTransaction() {
        check(database.inTransaction()) { "M04_PRODUCER_TRANSACTION_REQUIRED" }
    }

    private fun allowedOperations(policy: UnifiedSyncDeletePolicy): Set<String> = when (policy) {
        UnifiedSyncDeletePolicy.ARCHIVE -> setOf("UPSERT", "COMMAND", "ARCHIVE")
        UnifiedSyncDeletePolicy.VOID_OR_REVERSE -> setOf("UPSERT", "COMMAND", "VOID", "REVERSE")
        UnifiedSyncDeletePolicy.CANCEL_STATE_TRANSITION -> setOf("UPSERT", "COMMAND", "CANCEL")
        UnifiedSyncDeletePolicy.NO_CLIENT_DELETE, UnifiedSyncDeletePolicy.SERVER_OWNED -> setOf("UPSERT", "COMMAND")
        UnifiedSyncDeletePolicy.VERSIONED_DELETE, UnifiedSyncDeletePolicy.TOMBSTONE -> setOf("UPSERT", "COMMAND", "DELETE")
    }
}

/** Canonical JSON for V2 payloads. Scalar JSON types must stay scalar, not be stringified. */
internal fun canonicalSyncPayload(fields: Map<String, Any?>): String = JsonObject(
    fields.toSortedMap().mapValues { (_, value) -> value.toCanonicalJsonElement() },
).toString()

private fun Any?.toCanonicalJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonObject -> JsonObject(this.toSortedMap().mapValues { (_, value) -> value.toCanonicalJsonElement() })
    is JsonArray -> JsonArray(map { it.toCanonicalJsonElement() })
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Char -> JsonPrimitive(toString())
    is Boolean -> JsonPrimitive(this)
    is Double -> {
        require(isFinite()) { "non-finite Double is not a valid sync payload number" }
        JsonPrimitive(this)
    }
    is Float -> {
        require(isFinite()) { "non-finite Float is not a valid sync payload number" }
        JsonPrimitive(this)
    }
    is Number -> JsonPrimitive(this)
    is Enum<*> -> JsonPrimitive(name)
    is Map<*, *> -> {
        val keyed = entries.associate { (key, value) ->
            require(key is String) { "sync payload object keys must be strings" }
            key to value
        }
        JsonObject(keyed.toSortedMap().mapValues { (_, value) -> value.toCanonicalJsonElement() })
    }
    is Iterable<*> -> JsonArray(map { it.toCanonicalJsonElement() })
    is Array<*> -> JsonArray(map { it.toCanonicalJsonElement() })
    else -> error("unsupported sync payload value type: ${this::class.qualifiedName}")
}
