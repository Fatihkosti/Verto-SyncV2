package com.verto.app.data.sync.pull

import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.UnifiedInboxInsertResult
import com.verto.app.data.local.entity.*
import com.verto.app.data.sync.*
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncPendingProtection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** No network in either transaction boundary. A received payload is not an applied business fact. */
@Singleton
class DurableInboxApplyCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val mapper: UnifiedSyncInboxMapper,
    private val validator: DurableInboxPageValidator,
    private val registry: UnifiedSyncPullRegistry,
    private val applier: UnifiedSyncChangeApplier,
    private val protection: SyncPendingProtection,
    private val echoes: DurableInboxEchoReconciler,
    private val storage: InboxStorageProbe,
) {
    data class ReceiveResult(val committed: Boolean, val insertedChanges: Int, val storageWait: Boolean = false)
    data class DrainResult(val appliedGroups: Int, val appliedChanges: Int, val pendingGroups: Long,
        val reviewGroups: Long, val continuationRequired: Boolean, val storageWait: Boolean,
        val attemptedGroups: Int = 0, val attemptedChanges: Int = 0)
    private val json = SyncContractV2Codec.json

    /** Entire page is admitted, persisted and cursor-CASed, or none of it is committed. */
    suspend fun receive(scope: SyncScope, expectedCursor: String, page: SyncPullPage): ReceiveResult {
        val groups = validator.validate(scope, expectedCursor, page) // hash/count/order/bytes outside Room
        if (groups.isEmpty()) return database.withTransaction {
            val cursor = requireActiveScope(scope)
            checkProtocol(cursor.receivedCursorToken == expectedCursor &&
                page.coveredThroughRevision == (cursor.receivedHighWatermark ?: cursor.appliedCheckpoint ?: 0L),
                "INBOX_COVERAGE_INVALID", "empty response changed durable receipt coverage")
            ReceiveResult(false, 0)
        }
        try {
            return database.withTransaction {
                val dao = database.unifiedSyncDao()
                val cursor = requireActiveScope(scope)
                var additionalBytes = 0L
                val newGroups = mutableSetOf<String>()
                for (group in groups) {
                    val old = dao.getInboxGroup(scope.scopeId, group.manifest.transactionId)
                    if (old == null) {
                        checkProtocol(group.manifest.firstRevision > (cursor.receivedHighWatermark ?: 0L),
                            "INBOX_COVERAGE_INVALID", "new group overlaps previously received coverage")
                        newGroups += group.manifest.transactionId
                        additionalBytes = Math.addExact(additionalBytes, group.manifest.serializedBytes)
                    } else checkSameManifest(old, group.manifest, scope)
                }
                val exactReplay = cursor.receivedCursorToken == page.nextCursor && newGroups.isEmpty()
                checkProtocol(cursor.receivedCursorToken == expectedCursor || exactReplay,
                    "CURSOR_STALE", "receive compare-and-set lost its scope cursor")
                if (dao.unprovenInboxRowCount(scope.scopeId) > 0L || !DurableInboxPolicy.mayReceive(
                        dao.unappliedInboxBytes(scope.scopeId), additionalBytes, storage.availableBytes())) {
                    persistStorageWait(scope, "WAITING_STORAGE_OR_REVIEW")
                    return@withTransaction ReceiveResult(false, 0, true)
                }
                var inserted = 0
                val now = System.currentTimeMillis()
                for (group in groups) {
                    val manifest = group.manifest
                    val isNew = manifest.transactionId in newGroups
                    if (isNew) {
                        check(dao.insertInboxGroupRaw(manifest.toEntity(scope, now)) != -1L)
                        dao.insertInboxDependencies(manifest.dependsOnTransactionIds.map {
                            SyncInboxDependencyEntity(scope.scopeId, manifest.transactionId, it) })
                        dao.insertInboxTouchedKeys(manifest.touchedKeys.map {
                            SyncInboxTouchedKeyEntity(scope.scopeId, manifest.transactionId, it.type, it.id) })
                    }
                    for (change in group.changes) {
                        val result = dao.insertInboxChecked(mapper.toEntity(change, now))
                        if (isNew) {
                            checkProtocol(result == UnifiedInboxInsertResult.INSERTED,
                                "INBOX_LEGACY_GROUP_COLLISION", "a new manifest cannot adopt an older unexplained receipt")
                            inserted++
                        } else checkProtocol(result == UnifiedInboxInsertResult.DUPLICATE,
                            "INCOMPLETE_TRANSACTION_GROUP", "stored group lost a member")
                        dao.recordObservedVersion(change.organizationId, scope.scopeId, versionFamily(change),
                            change.aggregateId, change.entityVersion, now)
                    }
                }
                if (!exactReplay) {
                    checkProtocol(dao.advanceReceivedCursor(scope.scopeId, scope.organizationId, scope.syncPrincipalId,
                        scope.contractFamily, scope.contractVersion, scope.scopeDefinitionVersion, expectedCursor,
                        page.nextCursor, checkNotNull(page.coveredThroughRevision), checkNotNull(page.pageHighWatermark),
                        page.minAvailableRevision, now) == 1, "CURSOR_STALE", "receive cursor CAS failed")
                    dao.requestInboxApply(scope.scopeId, scope.organizationId, now, now)
                }
                dao.setInboxStorageWait(scope.scopeId, scope.organizationId, null, now)
                ReceiveResult(!exactReplay, inserted)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (full: SQLiteFullException) {
            // The failed Room transaction rolled back its payloads, manifest, observation and token.
            persistStorageWait(scope, "WAITING_STORAGE_OR_REVIEW")
            return ReceiveResult(false, 0, true)
        }
    }

    /** Reopening the process calls this before any new network page is requested. */
    suspend fun drain(scope: SyncScope, changeBudget: Int = DurableInboxPolicy.SOFT_CHANGE_BUDGET,
        maxDurationMillis: Long = 20_000L, previouslyAttemptedGroups: Int = 0): DrainResult {
        require(changeBudget >= 0 && maxDurationMillis > 0L && previouslyAttemptedGroups >= 0)
        val dao = database.unifiedSyncDao()
        requireActiveScope(scope)
        val request = dao.getInboxApplyRequest(scope.scopeId)
        if (request == null) return result(scope, 0, 0, false)
        checkProtocol(request.organizationId == scope.organizationId, "SCOPE_MISMATCH", "inbox request tenant")
        val generation = request.requestedGeneration
        val started = System.nanoTime()
        var attemptedGroups = 0
        var attemptedChanges = 0
        var appliedGroups = 0
        var appliedChanges = 0
        var full = false
        while (true) {
            val candidate = dao.inboxApplyCandidates(scope.scopeId, generation, 1).firstOrNull() ?: break
            if (changeBudget == 0 || !DurableInboxPolicy.mayProcessGroup(previouslyAttemptedGroups + attemptedGroups,
                    attemptedChanges, candidate.memberCount, changeBudget) ||
                (attemptedGroups > 0 && (System.nanoTime() - started) / 1_000_000 >= maxDurationMillis)) break
            try {
                if (applyGroup(scope, candidate.transactionId, generation)) {
                    appliedGroups++
                    appliedChanges += candidate.memberCount
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SQLiteFullException) {
                persistStorageWait(scope, "WAITING_STORAGE_OR_REVIEW")
                full = true
                break
            } catch (failure: Throwable) {
                val disposition = classifyDomainFailure(failure) ?: throw failure
                database.withTransaction {
                    requireActiveScope(scope)
                    val current = checkNotNull(dao.getInboxGroup(scope.scopeId, candidate.transactionId))
                    if (current.state != "APPLIED") {
                        setGroupState(current, disposition.first, disposition.second)
                        dao.markInboxGroupAttempt(scope.scopeId, current.transactionId, generation)
                    }
                }
            }
            attemptedGroups++
            attemptedChanges = Math.addExact(attemptedChanges, candidate.memberCount)
        }
        // No fast polling: waits are evaluated once per durable generation. A completed prerequisite
        // or terminal local intent increments it; budget continuation keeps the SAME generation.
        val after = checkNotNull(dao.getInboxApplyRequest(scope.scopeId))
        val more = !full && dao.inboxApplyCandidates(scope.scopeId, after.requestedGeneration, 1).isNotEmpty()
        database.withTransaction {
            requireActiveScope(scope)
            val now = System.currentTimeMillis()
            if (more) dao.scheduleInboxContinuation(scope.scopeId, now + DurableInboxPolicy.CONTINUATION_DELAY_MILLIS, now)
            else dao.drainInboxRequestIfUnchanged(scope.scopeId, after.requestedGeneration, now)
        }
        return result(scope, appliedGroups, appliedChanges, more, full).copy(
            attemptedGroups = attemptedGroups, attemptedChanges = attemptedChanges)
    }

    suspend fun receiveLimit(scope: SyncScope, requested: Int): Int {
        val dao = database.unifiedSyncDao()
        requireActiveScope(scope)
        val used = dao.unappliedInboxBytes(scope.scopeId)
        val limit = DurableInboxPolicy.receiveSoftLimit(used, requested)
        if (limit == 0 || dao.unprovenInboxRowCount(scope.scopeId) > 0L ||
            storage.availableBytes() < DurableInboxPolicy.diskBytesRequired(DurableInboxPolicy.MAX_GROUP_BYTES)) {
            persistStorageWait(scope, "WAITING_STORAGE_OR_REVIEW")
            return 0
        }
        dao.setInboxStorageWait(scope.scopeId, scope.organizationId, null, System.currentTimeMillis())
        return limit
    }

    private suspend fun applyGroup(scope: SyncScope, transactionId: String, generation: Long): Boolean = database.withTransaction {
        val dao = database.unifiedSyncDao()
        requireActiveScope(scope)
        val group = checkNotNull(dao.getInboxGroup(scope.scopeId, transactionId))
        if (group.state == "APPLIED" || group.state == "REQUIRES_REVIEW" || group.lastAttemptGeneration >= generation) return@withTransaction false
        checkProtocol(group.organizationId == scope.organizationId, "SCOPE_MISMATCH", "stored manifest tenant")
        val rows = dao.listInboxGroupMembers(scope.scopeId, transactionId)
        checkProtocol(rows.size == group.memberCount && rows.none { it.applyState == "APPLIED" },
            "INCOMPLETE_TRANSACTION_GROUP", "partially applied group is not a valid boundary")
        val changes = rows.map { row ->
            val change = mapper.toChange(row)
            checkProtocol(change.organizationId == scope.organizationId && change.syncScopeId == scope.scopeId &&
                mapper.toEntity(change, row.receivedAt).contentFingerprint == row.contentFingerprint,
                "CONTRACT_GROUP_HASH_MISMATCH", "stored event differs from its immutable receipt")
            change
        }
        validator.validateGroup(group.toManifest(), changes)
        val missing = dao.missingInboxDependencies(scope.scopeId, transactionId)
        if (missing.isNotEmpty()) {
            setGroupState(group, "WAITING_DEPENDENCY", "TRANSACTION_DEPENDENCY_MISSING")
            dao.markInboxGroupAttempt(scope.scopeId, transactionId, generation)
            return@withTransaction false
        }
        if (dao.hasEarlierUnappliedTouch(scope.scopeId, transactionId, group.firstRevision) ||
            (changes.any { it.aggregateType == "CASH_REGISTER" } && dao.hasUnappliedBalancePredecessor(scope.scopeId, group.firstRevision))) {
            setGroupState(group, "WAITING_DEPENDENCY", "EARLIER_TOUCHED_GROUP_UNAPPLIED")
            dao.markInboxGroupAttempt(scope.scopeId, transactionId, generation)
            return@withTransaction false
        }
        changes.forEach { registry.validate(it) }
        // Exact echo ACKs are joined to this transaction; a later wait/failure must roll them back.
        changes.forEach { echoes.reconcile(it) }
        val derived = changes.flatMap { DurableInboxTouchedKeys.derive(it).entries }.associate { it.toPair() }
        for (key in group.toManifest().touchedKeys) {
            val root = derived[key]
            val protected = protection.isProtected(scope.organizationId, ProtectedSyncKey(key.type, key.id),
                financialRootId = root?.takeIf { it.type == "INVOICE" && it != key }?.id,
                projectionRoot = root?.takeIf { it != key && it.type != "INVOICE" }?.let { ProtectedSyncKey(it.type, it.id) })
            if (protected) throw UnifiedSyncPullFailure("PENDING_LOCAL_MUTATION", "protected DTO/root key")
        }
        setGroupState(group, "READY", null)
        val batch = applier.beginFinancialBatch(scope.organizationId, scope.scopeId)
        for (change in changes) {
            validateAppliedAuthority(change, scope)
            applier.apply(change, batch)
            val entityVersion = change.entityVersion
            if (change.aggregateType !in financialTypes && entityVersion != null) {
                dao.recordAppliedVersion(scope.organizationId, scope.scopeId, change.aggregateType, change.aggregateId,
                    entityVersion, change.revision, sha256Utf8(mapper.canonicalJson(change.payload)),
                    change.operationType == SyncMutationOperation.DELETE, System.currentTimeMillis())
            }
        }
        applier.completeFinancialBatch(batch) // all financially owned facts must now exist
        setGroupState(group, "APPLIED", null)
        advanceCoveredCheckpoint(scope)
        dao.requestInboxApply(scope.scopeId, scope.organizationId, System.currentTimeMillis(), System.currentTimeMillis())
        true
    }

    private suspend fun validateAppliedAuthority(change: SyncChange, scope: SyncScope) {
        if (change.aggregateType in financialTypes) return // B09 validates stream version/content and immutable facts
        val prior = database.unifiedSyncDao().readEntityVersion(scope.organizationId, scope.scopeId, change.aggregateType, change.aggregateId)
        val applied = prior?.appliedServerVersion ?: return
        val incoming = change.entityVersion
        checkProtocol(incoming != null && incoming >= applied, "APPLIED_VERSION_CONFLICT", "remote version regressed or disappeared")
        if (incoming == applied) checkProtocol(prior.appliedContentHash == sha256Utf8(mapper.canonicalJson(change.payload)),
            "IMMUTABLE_FACT_CONFLICT", "same applied version has different content")
    }

    private suspend fun advanceCoveredCheckpoint(scope: SyncScope) {
        val dao = database.unifiedSyncDao()
        val cursor = requireActiveScope(scope)
        var checkpoint = cursor.appliedCheckpoint
        var scan = checkpoint ?: 0L
        while (true) {
            val groups = dao.listInboxGroupsAfter(scope.scopeId, scan, 256)
            if (groups.isEmpty()) break
            val next = DurableInboxPolicy.appliedCheckpoint(checkpoint, cursor.receivedHighWatermark,
                groups.map { DurableInboxPolicy.CoveredGroup(it.firstRevision, it.lastRevision, it.state == "APPLIED") })
            checkpoint = next
            if (groups.any { it.state != "APPLIED" }) break
            scan = groups.last().lastRevision
        }
        if (checkpoint != null && checkpoint != cursor.appliedCheckpoint) {
            checkProtocol(dao.advanceAppliedCheckpoint(scope.scopeId, scope.organizationId, checkpoint, System.currentTimeMillis()) == 1,
                "INBOX_COVERAGE_INVALID", "applied prefix exceeds received coverage")
        }
    }

    private suspend fun setGroupState(group: SyncInboxGroupEntity, state: String, reason: String?) {
        require(state in setOf("READY", "APPLIED", "WAITING_LOCAL", "WAITING_DEPENDENCY", "REQUIRES_REVIEW"))
        val dao = database.unifiedSyncDao()
        val now = System.currentTimeMillis()
        check(dao.setInboxMembersStateRaw(group.scopeId, group.transactionId, state, reason, now) == group.memberCount)
        check(dao.setInboxGroupStateRaw(group.scopeId, group.transactionId, state, reason, now) == 1)
    }

    private suspend fun result(scope: SyncScope, groups: Int, changes: Int, more: Boolean, full: Boolean = false): DrainResult {
        val dao = database.unifiedSyncDao()
        return DrainResult(groups, changes, dao.unappliedInboxGroupCount(scope.scopeId), dao.reviewInboxGroupCount(scope.scopeId),
            more, full || dao.getInboxApplyRequest(scope.scopeId)?.storageWaitReason != null)
    }

    private suspend fun persistStorageWait(scope: SyncScope, reason: String) = database.withTransaction {
        requireActiveScope(scope)
        val dao = database.unifiedSyncDao()
        val now = System.currentTimeMillis()
        dao.insertInboxApplyRequestRaw(SyncInboxApplyRequestEntity(scope.scopeId, scope.organizationId, 0, 0, null, reason, now))
        check(dao.setInboxStorageWait(scope.scopeId, scope.organizationId, reason, now) == 1)
    }

    private suspend fun requireActiveScope(scope: SyncScope): SyncCursorEntity {
        val cursor = database.unifiedSyncDao().getCursor(scope.scopeId)
            ?: throw UnifiedSyncPullFailure("CURSOR_STALE", "missing receive cursor")
        checkProtocol(cursor.state == "ACTIVE" && cursor.organizationId == scope.organizationId &&
            cursor.syncPrincipalId == scope.syncPrincipalId && cursor.contractFamily == scope.contractFamily &&
            cursor.contractVersion == scope.contractVersion && cursor.scopeDefinitionVersion == scope.scopeDefinitionVersion &&
            cursor.cursorToken == cursor.receivedCursorToken && cursor.receivedCursorToken.isNotBlank(),
            "SCOPE_MISMATCH", "stored cursor is not this active scope")
        return cursor
    }

    private fun SyncInboxGroupManifestV2.toEntity(scope: SyncScope, now: Long) = SyncInboxGroupEntity(
        scope.scopeId, transactionId, scope.organizationId, "RECEIVED", memberCount, firstRevision, lastRevision,
        contentSha256, json.encodeToString(touchedKeys), json.encodeToString(dependsOnTransactionIds), null, now, now, null,
        serializedBytes = serializedBytes)

    private fun SyncInboxGroupEntity.toManifest() = SyncInboxGroupManifestV2(transactionId, memberCount, firstRevision,
        lastRevision, manifestSha256, serializedBytes, json.decodeFromString(touchedKeysJson), json.decodeFromString(dependencyTransactionIdsJson))

    private fun checkSameManifest(old: SyncInboxGroupEntity, incoming: SyncInboxGroupManifestV2, scope: SyncScope) {
        checkProtocol(old.organizationId == scope.organizationId && old.scopeId == scope.scopeId && old.toManifest() == incoming,
            "DUPLICATE_REVISION_CONTENT", "same transaction id has different immutable manifest")
    }

    private fun versionFamily(change: SyncChange) = if (change.aggregateType in financialTypes) "FINANCIAL_INVOICE" else change.aggregateType

    private fun classifyDomainFailure(t: Throwable): Pair<String, String>? {
        val code = when (t) {
            is UnifiedSyncPullFailure -> t.code
            is SyncContractViolation -> t.code
            is IllegalArgumentException, is IllegalStateException -> t.message?.substringBefore(':')?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]+")) }
            else -> null
        } ?: return null
        return when (code) {
            "PENDING_LOCAL_MUTATION" -> "WAITING_LOCAL" to code
            "WAITING_DEPENDENCY", "BATCH_DEPENDENCY_MISSING", "PREDECESSOR_NOT_ACKNOWLEDGED" -> "WAITING_DEPENDENCY" to code
            else -> "REQUIRES_REVIEW" to code
        }
    }

    private fun checkProtocol(ok: Boolean, code: String, detail: String) { if (!ok) throw UnifiedSyncPullFailure(code, detail) }
    private companion object { val financialTypes = setOf("INVOICE", "PAYMENT") }
}
