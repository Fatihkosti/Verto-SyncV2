package com.verto.app.data.sync

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncSnapshotBlobEntity
import com.verto.app.data.local.entity.SyncWriteBatchEntity
import com.verto.app.data.local.entity.SyncWriteBatchMemberEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class FrozenBatchBytes(val batchId: String, val wireJson: String, val wireSha256: String)

/** A batch is a manifest over original owner rows; it never becomes another outbox. */
@Singleton
class SyncBatchCoordinatorV2 @Inject constructor(
    private val database: AppDatabase,
    private val mutationStore: FrozenMutationStore,
) {
    suspend fun seal(
        organizationId: String,
        batchId: String,
        orderedMutationIds: List<String>,
        sealedAt: Long,
    ): SyncWriteBatchEntity {
        check(database.inTransaction()) { "SYNC_BATCH_TRANSACTION_REQUIRED" }
        require(orderedMutationIds.isNotEmpty() && orderedMutationIds.toSet().size == orderedMutationIds.size)
        val dao = database.unifiedSyncDao()
        val members = orderedMutationIds.mapIndexed { order, mutationId ->
            val packet = checkNotNull(dao.readMutationPacket(organizationId, mutationId)) { "BATCH_PACKET_MISSING" }
            check(packet.batchId == batchId) { "BATCH_MEMBERSHIP_MISMATCH" }
            SyncWriteBatchMemberEntity(
                organizationId, batchId, order, mutationId, packet.sourceOwner, packet.sourceId,
            )
        }
        val manifestJson = JsonArray(members.map { member -> JsonObject(linkedMapOf(
            "memberOrder" to JsonPrimitive(member.memberOrder),
            "mutationId" to JsonPrimitive(member.mutationId),
            "sourceOwner" to JsonPrimitive(member.sourceOwner),
            "sourceId" to JsonPrimitive(member.sourceId),
        )) }).toString()
        val batch = SyncWriteBatchEntity(
            organizationId = organizationId,
            batchId = batchId,
            memberCount = members.size,
            manifestSha256 = sha256Utf8(manifestJson),
            wireJson = null,
            wireSha256 = null,
            preparedAt = null,
            sealedAt = sealedAt,
            createdAt = sealedAt,
        )
        dao.insertSealedWriteBatch(batch, members)
        return checkNotNull(dao.readWriteBatch(organizationId, batchId))
    }

    suspend fun prepareOnce(
        organizationId: String,
        batchId: String,
        preparedAt: Long,
        snapshotHashes: List<String> = emptyList(),
    ): FrozenBatchBytes {
        val dao = database.unifiedSyncDao()
        val batch = checkNotNull(dao.readWriteBatch(organizationId, batchId)) { "BATCH_MANIFEST_MISSING" }
        if (batch.wireJson != null || batch.wireSha256 != null || batch.preparedAt != null) {
            val existingWire = batch.wireJson
            val existingHash = batch.wireSha256
            check(existingWire != null && existingHash != null && batch.preparedAt != null)
            check(sha256Utf8(existingWire) == existingHash) { "FROZEN_BATCH_HASH_MISMATCH" }
            return FrozenBatchBytes(batchId, existingWire, existingHash)
        }
        val members = dao.listWriteBatchMembers(organizationId, batchId)
        check(members.size == batch.memberCount)
        val wires = members.map { member ->
            val frozen = mutationStore.prepareOnce(organizationId, member.mutationId, preparedAt)
            JsonObject(linkedMapOf(
                "memberOrder" to JsonPrimitive(member.memberOrder),
                "memberWireJson" to JsonPrimitive(frozen.wireJson),
                "memberWireSha256" to JsonPrimitive(frozen.wireSha256),
            ))
        }
        val blobs = snapshotHashes.distinct().sorted().map { hash ->
            val blob: SyncSnapshotBlobEntity = checkNotNull(dao.readSnapshotBlob(hash)) { "BATCH_SNAPSHOT_BLOB_MISSING" }
            check(sha256Utf8(blob.snapshotJson) == blob.contentHash) { "SNAPSHOT_HASH_CONTENT_MISMATCH" }
            JsonObject(linkedMapOf(
                "sha256" to JsonPrimitive(blob.contentHash),
                "snapshotJson" to JsonPrimitive(blob.snapshotJson),
            ))
        }
        val wire = JsonObject(linkedMapOf(
            "contractFamily" to JsonPrimitive("verto-unified-sync"),
            "contractVersion" to JsonPrimitive(2),
            "organizationId" to JsonPrimitive(organizationId),
            "batchId" to JsonPrimitive(batchId),
            "memberCount" to JsonPrimitive(batch.memberCount),
            "members" to JsonArray(wires),
            "snapshotBlobs" to JsonArray(blobs),
        )).toString()
        val hash = sha256Utf8(wire)
        dao.freezeBatchWireRaw(organizationId, batchId, wire, hash, preparedAt)
        val stored = checkNotNull(dao.readWriteBatch(organizationId, batchId))
        check(stored.wireJson == wire && stored.wireSha256 == hash) { "FROZEN_BATCH_CONTENT_MISMATCH" }
        return FrozenBatchBytes(batchId, wire, hash)
    }
}
