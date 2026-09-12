package com.verto.app.data.sync

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncMutationPacketEntity
import com.verto.app.data.local.entity.SyncConflictResolutionAuditEntity
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.local.entity.SyncWriteBatchMemberEntity
import com.verto.app.data.sync.ownership.PendingSourceRef
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import com.verto.app.data.sync.ownership.SyncSourceOwner
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class FrozenMutationBytes(
    val mutationId: String,
    val wireJson: String,
    val wireSha256: String,
    val baseVersion: Long?,
)

data class FrozenOwnerIntent(
    val organizationId: String,
    val mutationId: String,
    val sourceOwner: SyncSourceOwner,
    val sourceId: String,
    val businessIdentity: String,
    val intentJson: String,
    val versionFamily: String,
    val initialBaseVersion: Long?,
    val predecessorMutationId: String? = null,
    val batchId: String? = null,
    val protectedContent: Map<ProtectedSyncKey, String>,
    val createdAt: Long,
)

enum class FrozenAckOutcome {
    ACKNOWLEDGED_CURRENT,
    ACKNOWLEDGED_LOCAL_CHANGED,
    STALE_LEASE,
    RECEIPT_MISMATCH,
}

/**
 * Durable boundary between a producer snapshot and transport. Capture is transaction-only; prepare
 * can happen later, but it can only fill the previously-null wire columns once.
 */
@Singleton
class FrozenMutationStore @Inject constructor(
    private val database: AppDatabase,
    private val pendingProtection: SyncPendingProtection,
) {
    suspend fun captureUnified(
        row: SyncOutboxEntity,
        businessIdentity: String = row.aggregateId,
        supersedesMutationId: String? = null,
        useUnresolvedPredecessor: Boolean = true,
        versionFamily: String = row.aggregateType,
        protectedContent: Map<ProtectedSyncKey, String> = mapOf(
            ProtectedSyncKey(row.aggregateType, row.aggregateId) to sha256Utf8(row.payloadJson),
        ),
    ): SyncMutationPacketEntity {
        check(database.inTransaction()) { "FROZEN_INTENT_TRANSACTION_REQUIRED" }
        require(row.organizationId.isNotBlank() && businessIdentity.isNotBlank() && versionFamily.isNotBlank())
        require(protectedContent.isNotEmpty())
        protectedContent.values.forEach(::requireSha256)

        val dao = database.unifiedSyncDao()
        val expectedIntent = FrozenMutationCodec.encodeIntent(row)
        dao.readMutationPacket(row.organizationId, row.mutationId)?.let { existing ->
            check(existing.sourceOwner == SyncSourceOwner.UNIFIED.tableName &&
                existing.sourceId == row.mutationId && existing.businessIdentity == businessIdentity &&
                existing.versionFamily == versionFamily && existing.intentJson == expectedIntent &&
                existing.intentHash == sha256Utf8(expectedIntent) && existing.batchId == row.commandBatchId &&
                existing.supersedesMutationId == supersedesMutationId &&
                (useUnresolvedPredecessor || (existing.predecessorMutationId == null && existing.initialBaseVersion == row.baseVersion))) {
                "FAIL_IDEMPOTENCY_CONFLICT"
            }
            return existing
        }

        val predecessor = if (useUnresolvedPredecessor) {
            dao.readLatestUnresolvedUnifiedPredecessor(
                row.organizationId, versionFamily, businessIdentity, row.mutationId,
            )
        } else null
        val generations = linkedMapOf<ProtectedSyncKey, com.verto.app.data.local.entity.SyncLocalGenerationEntity>()
        protectedContent.forEach { (key, hash) ->
            generations[key] = dao.recordLocalGeneration(
                row.organizationId, key.type, key.id, hash, row.createdAt,
            )
        }
        val rootKey = ProtectedSyncKey(row.aggregateType, row.aggregateId)
        val root = generations[rootKey] ?: generations.values.first()
        val intentJson = expectedIntent
        val packet = SyncMutationPacketEntity(
            organizationId = row.organizationId,
            mutationId = row.mutationId,
            sourceOwner = SyncSourceOwner.UNIFIED.tableName,
            sourceId = row.mutationId,
            businessIdentity = businessIdentity,
            intentJson = intentJson,
            intentHash = sha256Utf8(intentJson),
            capturedGeneration = root.generation,
            capturedContentHash = root.contentHash,
            versionFamily = versionFamily,
            initialBaseVersion = row.baseVersion ?: dao.readUnambiguousAppliedVersion(
                row.organizationId, versionFamily, row.aggregateId,
            ),
            predecessorMutationId = predecessor?.mutationId,
            supersedesMutationId = supersedesMutationId,
            batchId = row.commandBatchId,
            wireJson = null,
            wireSha256 = null,
            preparedAt = null,
            firstDispatchAt = null,
            createdAt = row.createdAt,
        )
        dao.insertMutationPacketIfAbsent(packet)
        val stored = checkNotNull(dao.readMutationPacket(row.organizationId, row.mutationId))
        check(stored == packet) { "FAIL_IDEMPOTENCY_CONFLICT" }

        generations.forEach { (key, generation) ->
            pendingProtection.capture(
                source = PendingSourceRef(row.organizationId, SyncSourceOwner.UNIFIED, row.mutationId),
                protectedKeys = listOf(key),
                capturedGeneration = generation.generation,
                capturedContentHash = generation.contentHash,
                dependencyKind = if (key == rootKey) "AGGREGATE_ROOT" else "BUSINESS_DEPENDENCY",
            )
        }
        return stored
    }

    /** Adapter boundary for financial, Party, inventory, Optimal and attachment source owners. */
    suspend fun captureOwner(intent: FrozenOwnerIntent): SyncMutationPacketEntity {
        check(database.inTransaction()) { "FROZEN_INTENT_TRANSACTION_REQUIRED" }
        require(intent.sourceOwner != SyncSourceOwner.SERVER_ONLY)
        require(intent.organizationId.isNotBlank() && intent.mutationId.isNotBlank())
        require(intent.sourceId.isNotBlank() && intent.businessIdentity.isNotBlank() && intent.versionFamily.isNotBlank())
        require(intent.protectedContent.isNotEmpty())
        intent.protectedContent.values.forEach(::requireSha256)
        val parsed = Json.parseToJsonElement(intent.intentJson)
        require(parsed is JsonObject) { "FROZEN_INTENT_INVALID" }
        val dao = database.unifiedSyncDao()
        dao.readMutationPacket(intent.organizationId, intent.mutationId)?.let { existing ->
            check(existing.sourceOwner == intent.sourceOwner.tableName && existing.sourceId == intent.sourceId &&
                existing.businessIdentity == intent.businessIdentity && existing.intentJson == intent.intentJson &&
                existing.intentHash == sha256Utf8(intent.intentJson) && existing.versionFamily == intent.versionFamily &&
                existing.initialBaseVersion == intent.initialBaseVersion &&
                existing.predecessorMutationId == intent.predecessorMutationId && existing.batchId == intent.batchId) {
                "FAIL_IDEMPOTENCY_CONFLICT"
            }
            return existing
        }
        val generations = intent.protectedContent.map { (key, hash) ->
            key to dao.recordLocalGeneration(intent.organizationId, key.type, key.id, hash, intent.createdAt)
        }
        val root = generations.first().second
        val packet = SyncMutationPacketEntity(
            organizationId = intent.organizationId,
            mutationId = intent.mutationId,
            sourceOwner = intent.sourceOwner.tableName,
            sourceId = intent.sourceId,
            businessIdentity = intent.businessIdentity,
            intentJson = intent.intentJson,
            intentHash = sha256Utf8(intent.intentJson),
            capturedGeneration = root.generation,
            capturedContentHash = root.contentHash,
            versionFamily = intent.versionFamily,
            initialBaseVersion = intent.initialBaseVersion,
            predecessorMutationId = intent.predecessorMutationId,
            supersedesMutationId = null,
            batchId = intent.batchId,
            wireJson = null,
            wireSha256 = null,
            preparedAt = null,
            firstDispatchAt = null,
            createdAt = intent.createdAt,
        )
        dao.insertMutationPacketIfAbsent(packet)
        val stored = checkNotNull(dao.readMutationPacket(intent.organizationId, intent.mutationId))
        check(stored == packet) { "FAIL_IDEMPOTENCY_CONFLICT" }
        generations.forEach { (key, generation) ->
            pendingProtection.capture(
                PendingSourceRef(intent.organizationId, intent.sourceOwner, intent.sourceId),
                listOf(key), generation.generation, generation.contentHash, "BUSINESS_DEPENDENCY",
            )
        }
        return stored
    }

    suspend fun prepareOnce(organizationId: String, mutationId: String, preparedAt: Long): FrozenMutationBytes {
        require(organizationId.isNotBlank() && mutationId.isNotBlank())
        val dao = database.unifiedSyncDao()
        val packet = checkNotNull(dao.readMutationPacket(organizationId, mutationId)) { "FROZEN_INTENT_MISSING" }
        if (packet.wireJson != null || packet.wireSha256 != null || packet.preparedAt != null) {
            val existingWire = packet.wireJson
            val existingHash = packet.wireSha256
            check(existingWire != null && existingHash != null && packet.preparedAt != null) {
                "FROZEN_WIRE_PARTIAL_STATE"
            }
            check(sha256Utf8(existingWire) == existingHash) { "FROZEN_WIRE_HASH_MISMATCH" }
            return FrozenMutationBytes(mutationId, existingWire, existingHash, FrozenMutationCodec.baseVersion(existingWire))
        }

        val baseVersion = packet.predecessorMutationId?.let { predecessorId ->
            resolvedPredecessorServerVersion(dao, organizationId, predecessorId)
        } ?: packet.initialBaseVersion
        val wireJson = FrozenMutationCodec.encodeWire(packet.intentJson, baseVersion)
        val hash = sha256Utf8(wireJson)
        dao.freezeMutationWireRaw(organizationId, mutationId, wireJson, hash, preparedAt)
        val stored = checkNotNull(dao.readMutationPacket(organizationId, mutationId))
        check(stored.wireJson == wireJson && stored.wireSha256 == hash && stored.preparedAt != null) {
            "FROZEN_WIRE_CONTENT_MISMATCH"
        }
        return FrozenMutationBytes(mutationId, wireJson, hash, baseVersion)
    }

    /** B11: a later frozen intent may still point to a mutation explicitly superseded during review. */
    private suspend fun resolvedPredecessorServerVersion(
        dao: com.verto.app.data.local.dao.UnifiedSyncDao,
        organizationId: String,
        predecessorId: String,
    ): Long {
        val predecessor = checkNotNull(dao.getOutbox(predecessorId)) { "PREDECESSOR_MISSING" }
        check(predecessor.organizationId == organizationId) { "SCOPE_MISMATCH" }
        if (predecessor.state == "ACKNOWLEDGED") {
            return checkNotNull(predecessor.ackedServerVersion) { "PREDECESSOR_VERSION_MISSING" }
        }
        if (predecessor.state == "SUPERSEDED_WITH_PROOF") {
            val conflict = checkNotNull(dao.getConflictByMutationId(predecessorId)) { "SUPERSEDED_PROOF_MISSING" }
            check(conflict.organizationId == organizationId) { "SCOPE_MISMATCH" }
            if (conflict.state == "RESOLVED_REPLACEMENT_PROVED") {
                val replacementId = checkNotNull(conflict.resolutionMutationId) { "REPLACEMENT_PROOF_MISSING" }
                val replacement = checkNotNull(dao.getOutbox(replacementId)) { "REPLACEMENT_PROOF_MISSING" }
                check(replacement.organizationId == organizationId && replacement.state == "ACKNOWLEDGED") {
                    "REPLACEMENT_NOT_ACKNOWLEDGED"
                }
                return checkNotNull(replacement.ackedServerVersion) { "REPLACEMENT_VERSION_MISSING" }
            }
            if (conflict.state == "RESOLVED_SERVER_ACCEPTED") {
                return checkNotNull(predecessor.ackedServerVersion) { "PREDECESSOR_VERSION_MISSING" }
            }
        }
        error("PREDECESSOR_NOT_ACKNOWLEDGED")
    }

    suspend fun recordDispatch(bytes: FrozenMutationBytes, organizationId: String, dispatchedAt: Long) {
        val packet = checkNotNull(database.unifiedSyncDao().readMutationPacket(organizationId, bytes.mutationId))
        check(packet.wireJson == bytes.wireJson && packet.wireSha256 == bytes.wireSha256) {
            "FROZEN_WIRE_CONTENT_MISMATCH"
        }
        database.unifiedSyncDao().recordFirstDispatch(organizationId, bytes.mutationId, dispatchedAt)
    }

    /**
     * Commits local acknowledgement of a server-atomic batch in one Room transaction. Every
     * receipt is proven against the immutable member bytes before any owner row is advanced.
     */
    suspend fun acknowledgeSealedBatch(
        organizationId: String,
        batchId: String,
        members: List<SyncWriteBatchMemberEntity>,
        receipts: List<SyncReceipt>,
        acknowledgedAt: Long,
    ): Int = database.withTransaction {
        require(organizationId.isNotBlank() && batchId.isNotBlank()) { "SCOPE_MISMATCH" }
        val dao = database.unifiedSyncDao()
        val batch = checkNotNull(dao.readWriteBatch(organizationId, batchId)) { "BATCH_MANIFEST_MISSING" }
        check(members.size == batch.memberCount && members.map { it.memberOrder } == members.indices.toList()) {
            "BATCH_MEMBERSHIP_MISMATCH"
        }
        val byMutation = receipts.associateBy { it.mutationId }
        check(byMutation.size == receipts.size && receipts.size == members.size) {
            "SERVER_PROTOCOL_INCONSISTENCY:BATCH_RECEIPT_COVERAGE"
        }
        members.forEach { member ->
            check(member.organizationId == organizationId && member.batchId == batchId) { "SCOPE_MISMATCH" }
            val packet = checkNotNull(dao.readMutationPacket(organizationId, member.mutationId)) {
                "BATCH_PACKET_MISSING"
            }
            val receipt = checkNotNull(byMutation[member.mutationId]) {
                "SERVER_PROTOCOL_INCONSISTENCY:BATCH_MEMBER_RECEIPT_MISSING"
            }
            check(packet.sourceOwner == member.sourceOwner && packet.sourceId == member.sourceId &&
                packet.batchId == batchId && packet.wireSha256 != null &&
                receipt.requestHash == packet.wireSha256 &&
                receipt.status in setOf(SyncReceiptStatus.APPLIED, SyncReceiptStatus.REPLAYED, SyncReceiptStatus.NO_OP)) {
                "SERVER_PROTOCOL_INCONSISTENCY:BATCH_MEMBER_RECEIPT_MISMATCH"
            }
        }
        members.forEach { member ->
            val receipt = checkNotNull(byMutation[member.mutationId])
            val changed = when (member.sourceOwner) {
                SyncSourceOwner.UNIFIED.tableName -> dao.acknowledgeSealedUnifiedSource(
                    organizationId, member.mutationId, receipt.serverRevision, receipt.serverVersion,
                    receipt.status.name, acknowledgedAt,
                )
                SyncSourceOwner.FINANCIAL.tableName -> {
                    val revision = receipt.serverRevision ?: error("SERVER_PROTOCOL_INCONSISTENCY:MISSING_FINANCIAL_REVISION")
                    check(revision > 0L)
                    database.invoiceDao().acknowledgeFinancialOutbox(member.sourceId, revision, acknowledgedAt)
                }
                SyncSourceOwner.INVENTORY_STOCK.tableName -> {
                    val sequence = receipt.serverVersion ?: error("SERVER_PROTOCOL_INCONSISTENCY:MISSING_SERVER_SEQUENCE")
                    check(sequence > 0L)
                    database.inventoryDao().acknowledgeInventoryStockOutbox(member.sourceId, sequence, acknowledgedAt)
                }
                SyncSourceOwner.INVENTORY_COST.tableName -> {
                    val sequence = receipt.serverVersion ?: error("SERVER_PROTOCOL_INCONSISTENCY:MISSING_COST_SEQUENCE")
                    check(sequence > 0L)
                    database.inventoryDao().acknowledgeInventoryCostOutbox(member.sourceId, sequence, acknowledgedAt)
                }
                SyncSourceOwner.PARTY_ROLE.tableName ->
                    database.partyRoleDao().markPartyRoleTerminal(member.sourceId, "ACKNOWLEDGED")
                SyncSourceOwner.OPTIMAL.tableName -> dao.acknowledgeSealedOptimalSource(
                    organizationId, member.sourceId, receipt.serverVersion, acknowledgedAt,
                )
                else -> error("BATCH_SOURCE_OWNER_UNSUPPORTED:${member.sourceOwner}")
            }
            check(changed == 1) { "BATCH_SOURCE_STATE_CHANGED:${member.sourceOwner}:${member.sourceId}" }
            pendingProtection.releaseAfterTerminal(
                PendingSourceRef(organizationId, SyncSourceOwner.fromTable(member.sourceOwner), member.sourceId),
            )
            if (member.sourceOwner == SyncSourceOwner.UNIFIED.tableName) {
                finalizeReplacementProofChain(
                    organizationId, member.mutationId, "MATCHING_BATCH_RECEIPT", receipt.requestHash, acknowledgedAt,
                )
            }
        }
        members.size
    }

    suspend fun acknowledgeUnified(
        organizationId: String,
        mutationId: String,
        leaseToken: String,
        scopeEpoch: Long,
        expectedSemanticFingerprint: String,
        receiptMutationId: String,
        receiptRequestHash: String?,
        serverRevision: Long?,
        serverVersion: Long?,
        receiptStatus: String,
        acknowledgedAt: Long,
    ): FrozenAckOutcome = database.withTransaction {
        val dao = database.unifiedSyncDao()
        val packet = checkNotNull(dao.readMutationPacket(organizationId, mutationId)) { "FROZEN_INTENT_MISSING" }
        val expectedHash = packet.wireSha256
        if (receiptMutationId != mutationId || expectedHash == null || receiptRequestHash != expectedHash) {
            return@withTransaction FrozenAckOutcome.RECEIPT_MISMATCH
        }
        val localChanged = dao.countChangedProtectedKeys(
            organizationId, SyncSourceOwner.UNIFIED.tableName, mutationId,
        ) > 0 || dao.countLaterUnresolvedProtectedMutations(
            organizationId, SyncSourceOwner.UNIFIED.tableName, mutationId,
        ) > 0
        val changed = dao.markTerminal(
            mutationId = mutationId,
            leaseToken = leaseToken,
            scopeEpoch = scopeEpoch,
            expectedSemanticFingerprint = expectedSemanticFingerprint,
            terminalState = "ACKNOWLEDGED",
            serverRevision = serverRevision,
            serverVersion = serverVersion,
            receiptStatus = receiptStatus,
            ackedAt = acknowledgedAt,
        )
        if (changed != 1) return@withTransaction FrozenAckOutcome.STALE_LEASE
        pendingProtection.releaseAfterTerminal(
            PendingSourceRef(organizationId, SyncSourceOwner.UNIFIED, mutationId),
        )
        finalizeReplacementProofChain(
            organizationId = organizationId,
            provedMutationId = mutationId,
            proofType = "MATCHING_RECEIPT",
            proofReference = receiptRequestHash,
            provedAt = acknowledgedAt,
        )
        if (localChanged) FrozenAckOutcome.ACKNOWLEDGED_LOCAL_CHANGED
        else FrozenAckOutcome.ACKNOWLEDGED_CURRENT
    }

    /** A pull echo is proof only when its server content fingerprint matches the captured business bytes. */
    suspend fun acknowledgeAuthoritativeEcho(
        organizationId: String,
        mutationId: String,
        aggregateType: String,
        aggregateId: String,
        contentHash: String,
        serverRevision: Long,
        serverVersion: Long?,
        acknowledgedAt: Long,
    ): FrozenAckOutcome {
        val dao = database.unifiedSyncDao()
        val packet = dao.readMutationPacket(organizationId, mutationId)
            ?: return FrozenAckOutcome.RECEIPT_MISMATCH
        if (packet.capturedContentHash != contentHash) return FrozenAckOutcome.RECEIPT_MISMATCH
        return database.withTransaction {
            val localChanged = dao.countChangedProtectedKeys(
                organizationId, SyncSourceOwner.UNIFIED.tableName, mutationId,
            ) > 0 || dao.countLaterUnresolvedProtectedMutations(
                organizationId, SyncSourceOwner.UNIFIED.tableName, mutationId,
            ) > 0
            val changed = dao.acknowledgeByAuthoritativeEcho(
                mutationId, organizationId, aggregateType, aggregateId,
                serverRevision, serverVersion, acknowledgedAt,
            )
            if (changed != 1) return@withTransaction FrozenAckOutcome.STALE_LEASE
            pendingProtection.releaseAfterTerminal(
                PendingSourceRef(organizationId, SyncSourceOwner.UNIFIED, mutationId),
            )
            resolveMatchingEchoConflict(organizationId, mutationId, serverRevision, contentHash, acknowledgedAt)
            finalizeReplacementProofChain(
                organizationId = organizationId,
                provedMutationId = mutationId,
                proofType = "MATCHING_AUTHORITATIVE_ECHO",
                proofReference = "$serverRevision:$contentHash",
                provedAt = acknowledgedAt,
            )
            if (localChanged) FrozenAckOutcome.ACKNOWLEDGED_LOCAL_CHANGED
            else FrozenAckOutcome.ACKNOWLEDGED_CURRENT
        }
    }

    /** Closes only the parent intent whose replacement is now proved by receipt/echo. */
    private suspend fun finalizeReplacementProofChain(
        organizationId: String,
        provedMutationId: String,
        proofType: String,
        proofReference: String?,
        provedAt: Long,
    ) {
        check(database.inTransaction()) { "CONFLICT_PROOF_TRANSACTION_REQUIRED" }
        val dao = database.unifiedSyncDao()
        var childMutationId = provedMutationId
        repeat(16) {
            val parent = dao.getConflictByResolutionMutationId(childMutationId) ?: return
            if (parent.organizationId != organizationId || parent.state != "WAITING_REPLACEMENT_RECEIPT") return
            val evidence = dao.getConflictEvidence(parent.conflictId) ?: error("CONFLICT_EVIDENCE_MISSING")
            val old = dao.getOutbox(parent.mutationId) ?: error("CONFLICT_LOCAL_INTENT_MISSING")
            check(old.state == "SUPERSEDED_PENDING_PROOF") { "CONFLICT_STATE_CHANGED" }
            check(dao.proveSupersededMutation(
                old.mutationId, old.organizationId, old.semanticFingerprint, "REPLACEMENT_PROVED", provedAt,
            ) == 1) { "CONFLICT_STATE_CHANGED" }
            check(dao.transitionConflict(
                parent.conflictId, "WAITING_REPLACEMENT_RECEIPT", "RESOLVED_REPLACEMENT_PROVED",
                childMutationId, provedAt,
            ) == 1) { "CONFLICT_STATE_CHANGED" }
            dao.insertConflictDecision(
                SyncConflictResolutionAuditEntity(
                    decisionId = java.util.UUID.randomUUID().toString(),
                    conflictId = parent.conflictId,
                    organizationId = organizationId,
                    mutationId = old.mutationId,
                    decisionType = "REPLACEMENT_PROVED",
                    actorId = null,
                    actorRole = null,
                    localPayloadSha256 = evidence.localPayloadSha256,
                    remotePayloadSha256 = evidence.remotePayloadSha256,
                    expectedServerVersion = evidence.serverVersion,
                    resolutionMutationId = childMutationId,
                    proofType = proofType,
                    proofReference = proofReference,
                    beforeState = "WAITING_REPLACEMENT_RECEIPT",
                    afterState = "RESOLVED_REPLACEMENT_PROVED",
                    decidedAt = provedAt,
                )
            )
            pendingProtection.releaseAfterTerminal(
                PendingSourceRef(organizationId, SyncSourceOwner.UNIFIED, old.mutationId),
            )
            childMutationId = old.mutationId
        }
        error("CONFLICT_SUPERSEDES_CHAIN_TOO_DEEP")
    }

    private suspend fun resolveMatchingEchoConflict(
        organizationId: String,
        mutationId: String,
        serverRevision: Long,
        contentHash: String,
        resolvedAt: Long,
    ) {
        check(database.inTransaction()) { "CONFLICT_PROOF_TRANSACTION_REQUIRED" }
        val dao = database.unifiedSyncDao()
        val conflict = dao.getConflictByMutationId(mutationId) ?: return
        if (conflict.organizationId != organizationId || conflict.state !in setOf("OPEN", "DOMAIN_CORRECTION_REQUIRED")) return
        val evidence = dao.getConflictEvidence(conflict.conflictId) ?: error("CONFLICT_EVIDENCE_MISSING")
        if (dao.transitionConflict(
                conflict.conflictId, conflict.state, "RESOLVED_MATCHING_ECHO", null, resolvedAt,
            ) != 1) return
        dao.insertConflictDecision(
            SyncConflictResolutionAuditEntity(
                decisionId = java.util.UUID.randomUUID().toString(),
                conflictId = conflict.conflictId,
                organizationId = organizationId,
                mutationId = mutationId,
                decisionType = "MATCHING_ECHO",
                actorId = null,
                actorRole = null,
                localPayloadSha256 = evidence.localPayloadSha256,
                remotePayloadSha256 = evidence.remotePayloadSha256,
                expectedServerVersion = evidence.serverVersion,
                resolutionMutationId = null,
                proofType = "AUTHORITATIVE_ECHO",
                proofReference = "$serverRevision:$contentHash",
                beforeState = conflict.state,
                afterState = "RESOLVED_MATCHING_ECHO",
                decidedAt = resolvedAt,
            )
        )
        dao.requestInboxApplyForOrganization(organizationId, resolvedAt)
    }
}

internal object FrozenMutationCodec {
    private val json = Json { explicitNulls = true; ignoreUnknownKeys = false }

    fun encodeIntent(row: SyncOutboxEntity): String = JsonObject(linkedMapOf(
        "contractFamily" to JsonPrimitive("verto-unified-sync"),
        "contractVersion" to JsonPrimitive(2),
        "mutationId" to JsonPrimitive(row.mutationId),
        "organizationId" to JsonPrimitive(row.organizationId),
        "aggregateType" to JsonPrimitive(row.aggregateType),
        "aggregateId" to JsonPrimitive(row.aggregateId),
        "operationType" to JsonPrimitive(row.operationType),
        "localSequence" to JsonPrimitive(row.localSequence),
        "aggregateSequence" to JsonPrimitive(row.aggregateSequence),
        "payloadVersion" to JsonPrimitive(row.payloadVersion),
        "payload" to json.parseToJsonElement(row.payloadJson),
        "createdAtEpochMillis" to JsonPrimitive(row.createdAt),
        "commandBatchId" to (row.commandBatchId?.let(::JsonPrimitive) ?: JsonNull),
        "commandOrder" to (row.commandOrder?.let(::JsonPrimitive) ?: JsonNull),
        "dependsOnMutationId" to (row.dependsOnMutationId?.let(::JsonPrimitive) ?: JsonNull),
    )).toString()

    fun encodeWire(intentJson: String, baseVersion: Long?): String {
        val intent = json.parseToJsonElement(intentJson) as? JsonObject
            ?: error("FROZEN_INTENT_INVALID")
        val fields = LinkedHashMap(intent)
        val insertion = fields.entries.indexOfFirst { it.key == "localSequence" }.let { if (it < 0) 7 else it }
        val rebuilt = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        fields.entries.forEachIndexed { index, entry ->
            if (index == insertion) rebuilt["baseVersion"] = baseVersion?.let(::JsonPrimitive) ?: JsonNull
            rebuilt[entry.key] = entry.value
        }
        if ("baseVersion" !in rebuilt) rebuilt["baseVersion"] = baseVersion?.let(::JsonPrimitive) ?: JsonNull
        return JsonObject(rebuilt).toString()
    }

    fun baseVersion(wireJson: String): Long? =
        (json.parseToJsonElement(wireJson) as JsonObject)["baseVersion"]?.let {
            if (it is JsonNull) null else (it as JsonPrimitive).content.toLong()
        }
}

internal fun sha256Utf8(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun requireSha256(value: String) {
    require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
        "lowercase SHA-256 required"
    }
}
