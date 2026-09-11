package com.verto.app.data.sync.pull

import com.verto.app.data.local.entity.SyncInboxEntity
import com.verto.app.data.sync.SyncChange
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@Singleton
class UnifiedSyncInboxMapper @Inject constructor() {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    fun toEntity(change: SyncChange, receivedAt: Long): SyncInboxEntity {
        val canonicalPayload = canonicalJson(change.payload)
        return SyncInboxEntity(
            scopeId = change.syncScopeId,
            organizationId = change.organizationId,
            serverRevision = change.revision,
            aggregateType = change.aggregateType,
            aggregateId = change.aggregateId,
            operationType = change.operationType.name,
            entityVersion = change.entityVersion,
            payloadVersion = change.payloadVersion,
            payloadJson = canonicalPayload,
            originMutationId = change.originMutationId,
            transactionId = change.transactionId,
            transactionOrder = change.transactionOrder,
            transactionSize = change.transactionSize,
            deletedAt = change.deletedAtEpochMillis,
            changedAt = change.changedAtEpochMillis,
            contentFingerprint = semanticFingerprint(change, canonicalPayload),
            applyState = "RECEIVED",
            applyErrorCode = null,
            receivedAt = receivedAt,
            appliedAt = null,
        )
    }

    fun toChange(row: SyncInboxEntity): SyncChange = SyncChange(
        revision = row.serverRevision, organizationId = row.organizationId, syncScopeId = row.scopeId,
        aggregateType = row.aggregateType, aggregateId = row.aggregateId,
        operationType = com.verto.app.data.sync.SyncMutationOperation.valueOf(row.operationType),
        entityVersion = row.entityVersion, payloadVersion = row.payloadVersion,
        payload = json.parseToJsonElement(row.payloadJson) as JsonObject, originMutationId = row.originMutationId,
        transactionId = row.transactionId, transactionOrder = row.transactionOrder, transactionSize = row.transactionSize,
        deletedAtEpochMillis = row.deletedAt, changedAtEpochMillis = row.changedAt,
    )

    fun canonicalJson(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), canonicalize(element))

    private fun canonicalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { (k, v) -> k to canonicalize(v) })
        is JsonArray -> JsonArray(element.map(::canonicalize))
        JsonNull -> JsonNull
        else -> element
    }

    private fun semanticFingerprint(change: SyncChange, canonicalPayload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            change.syncScopeId,
            change.organizationId,
            change.revision.toString(),
            change.aggregateType,
            change.aggregateId,
            change.operationType.name,
            change.entityVersion?.toString() ?: "<null>",
            change.payloadVersion.toString(),
            canonicalPayload,
            change.originMutationId ?: "<null>",
            change.transactionId,
            change.transactionOrder.toString(),
            change.transactionSize.toString(),
            change.deletedAtEpochMillis?.toString() ?: "<null>",
            change.changedAtEpochMillis.toString(),
        ).forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
