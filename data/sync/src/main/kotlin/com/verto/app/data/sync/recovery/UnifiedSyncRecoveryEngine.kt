package com.verto.app.data.sync.recovery

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.SyncBootstrapStageEntity
import com.verto.app.data.local.entity.SyncCursorEntity
import com.verto.app.data.local.entity.SyncHealthStateEntity
import com.verto.app.data.local.entity.SyncRecoveryStateEntity
import com.verto.app.data.sync.SyncBootstrapPage
import com.verto.app.data.sync.SyncBootstrapRow
import com.verto.app.data.sync.SyncScope
import com.verto.app.data.sync.UnifiedSyncAggregateRegistry
import com.verto.app.data.sync.UnifiedSyncBootstrapRemote
import com.verto.app.data.sync.UnifiedSyncContractRules
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class UnifiedSyncRecoveryFailure(val code: String, detail: String, cause: Throwable? = null) :
    IllegalStateException("$code: $detail", cause)

enum class RecoveryRunOutcome { READY, CONTINUATION_REQUIRED, PROMOTION_BLOCKED, AUTH_BLOCKED }

enum class RecoveryReason {
    INITIAL_BOOTSTRAP, CURSOR_EXPIRED, CURSOR_CORRUPT, SCOPE_CHANGED, LOCAL_ANCHOR_MISSING,
    RECONCILIATION_MISMATCH, MANUAL_SAFE_RESYNC, BOOTSTRAP_SESSION_RESTART,
}

/** B13 durable recovery state machine. Network waits are never held inside Room transactions. */
@Singleton
class UnifiedSyncRecoveryEngine @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncBootstrapRemote,
    private val snapshotApplier: UnifiedSyncSnapshotApplier,
) {
    suspend fun requiredReason(organizationId: String, scopeGuard: suspend () -> Unit): RecoveryReason? {
        scopeGuard()
        val scope = classifyRemote { remote.resolveScope() }
        UnifiedSyncContractRules.requireValidScope(scope)
        if (scope.organizationId != organizationId) throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SCOPE_MISMATCH", "resolved tenant differs")
        scopeGuard()
        val state = database.syncRecoveryDao().getRecoveryState(scope.scopeId)
        val cursor = database.unifiedSyncDao().getCursor(scope.scopeId)
        if (state == null) return RecoveryReason.INITIAL_BOOTSTRAP
        validateRecoveryIdentity(state, scope)
        if (state.state in setOf(STATE_IN_PROGRESS, STATE_STAGED, STATE_STAGED_VERIFIED, STATE_RECOVERY_REQUIRED, "NOT_STARTED")) {
            return runCatching { RecoveryReason.valueOf(state.reason) }.getOrDefault(RecoveryReason.CURSOR_CORRUPT)
        }
        if (state.state != STATE_READY || cursor == null || cursor.state != CURSOR_ACTIVE || cursor.cursorToken.isBlank()) {
            return RecoveryReason.CURSOR_CORRUPT
        }
        cursor.lastAppliedChangeRevision?.let {
            if (!database.syncRecoveryDao().hasAppliedInboxAnchor(scope.scopeId, it)) return RecoveryReason.LOCAL_ANCHOR_MISSING
        }
        return null
    }

    suspend fun run(
        organizationId: String,
        reason: RecoveryReason,
        pageBudget: Int = DEFAULT_PAGE_BUDGET,
        promotionAllowed: Boolean = true,
        scopeGuard: suspend () -> Unit,
    ): RecoveryRunOutcome {
        require(organizationId.isNotBlank()) { "FAIL_BOOTSTRAP_SCOPE_MISMATCH: organizationId blank" }
        require(pageBudget in 1..MAX_PAGE_BUDGET) { "VALIDATION: recovery page budget out of range" }
        scopeGuard()
        val scope = classifyRemote { remote.resolveScope() }
        UnifiedSyncContractRules.requireValidScope(scope)
        if (scope.organizationId != organizationId) throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SCOPE_MISMATCH", "resolved tenant differs")
        scopeGuard()

        var state = ensureBootstrapSession(scope, reason, scopeGuard)
        var pages = 0
        var restarted = false
        while (true) {
            if (state.state == STATE_STAGED) {
                state = verifyStaging(scope, state, scopeGuard)
            }
            if (state.state == STATE_STAGED_VERIFIED) {
                if (!promotionAllowed) return RecoveryRunOutcome.PROMOTION_BLOCKED
                return try {
                    captureProtectionManifest(scope, state, scopeGuard)
                    cutover(scope, state, scopeGuard)
                    RecoveryRunOutcome.READY
                } catch (failure: UnifiedSyncRecoveryFailure) {
                    persistFailure(scope, state, failure.code, preserveStaging = true)
                    throw failure
                }
            }
            if (pages >= pageBudget) return RecoveryRunOutcome.CONTINUATION_REQUIRED
            if (state.state != STATE_IN_PROGRESS) {
                state = startFresh(scope, RecoveryReason.BOOTSTRAP_SESSION_RESTART, state, scopeGuard)
            }
            scopeGuard()
            if (state.bootstrapSessionId.isNullOrBlank() || state.nextPageToken.isNullOrBlank()) {
                state = startFresh(scope, RecoveryReason.BOOTSTRAP_SESSION_RESTART, state, scopeGuard)
            }
            val page = try {
                classifyRemote { remote.pullPage(state.bootstrapSessionId!!, state.nextPageToken!!, PAGE_SIZE) }
            } catch (failure: UnifiedSyncRecoveryFailure) {
                if (failure.code == "BOOTSTRAP_RESTART_REQUIRED" && !restarted) {
                    state = startFresh(scope, RecoveryReason.BOOTSTRAP_SESSION_RESTART, state, scopeGuard)
                    restarted = true
                    continue
                }
                persistFailure(scope, state, failure.code, preserveStaging = true)
                if (failure.code == "AUTH_BLOCKED") return RecoveryRunOutcome.AUTH_BLOCKED
                throw failure
            }
            scopeGuard()
            validatePage(scope, state, page)
            state = persistPage(scope, state, page, scopeGuard)
            pages++
            if (page.snapshotComplete) continue
            if (!page.hasMore || state.nextPageToken.isNullOrBlank()) {
                persistFailure(scope, state, "FAIL_BOOTSTRAP_INCOMPLETE", preserveStaging = true)
                throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "non-complete page lost continuation")
            }
        }
    }

    private suspend fun ensureBootstrapSession(
        scope: SyncScope,
        reason: RecoveryReason,
        guard: suspend () -> Unit,
    ): SyncRecoveryStateEntity {
        val existing = database.syncRecoveryDao().getRecoveryState(scope.scopeId)
        if (existing != null) {
            validateRecoveryIdentity(existing, scope)
            if (existing.state == STATE_IN_PROGRESS && !existing.bootstrapSessionId.isNullOrBlank() && !existing.nextPageToken.isNullOrBlank()) return existing
            if (existing.state in setOf(STATE_STAGED, STATE_STAGED_VERIFIED) && !existing.bootstrapSessionId.isNullOrBlank()) return existing
            if (existing.state == STATE_READY) {
                val cursor = database.unifiedSyncDao().getCursor(scope.scopeId)
                if (cursor != null && cursor.state == CURSOR_ACTIVE && cursor.cursorToken.isNotBlank()) {
                    return existing.copy(state = STATE_RECOVERY_REQUIRED, reason = reason.name)
                }
            }
        }
        return startFresh(scope, reason, existing, guard)
    }

    private suspend fun startFresh(
        scope: SyncScope,
        reason: RecoveryReason,
        prior: SyncRecoveryStateEntity?,
        guard: suspend () -> Unit,
    ): SyncRecoveryStateEntity {
        guard()
        val start = classifyRemote { remote.begin(scope) }
        guard()
        if (start.scope != scope || start.baselineCursor.isBlank() || start.bootstrapSessionId.isBlank() || start.firstPageToken.isBlank()) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SCOPE_MISMATCH", "bootstrap handshake is not bound to trusted scope")
        }
        val now = System.currentTimeMillis()
        val coverageJson = start.coverageAggregateTypes?.let { values ->
            JsonArray(values.sorted().map { JsonPrimitive(it) }).toString()
        }
        val state = SyncRecoveryStateEntity(
            scopeId = scope.scopeId, organizationId = scope.organizationId, syncPrincipalId = scope.syncPrincipalId,
            contractFamily = scope.contractFamily, contractVersion = scope.contractVersion, scopeDefinitionVersion = scope.scopeDefinitionVersion,
            state = STATE_IN_PROGRESS, reason = reason.name, bootstrapSessionId = start.bootstrapSessionId,
            baselineCursor = start.baselineCursor, baselineRevision = start.baselineRevision, nextPageToken = start.firstPageToken,
            expectedSnapshotRows = start.expectedSnapshotRows, stagedSnapshotRows = 0L,
            recoveryGeneration = (prior?.recoveryGeneration ?: 0L) + 1L, attemptCount = (prior?.attemptCount ?: 0) + 1,
            lastErrorCode = null, startedAt = now, updatedAt = now, completedAt = null,
            expectedSnapshotDigest = start.expectedSnapshotDigest?.lowercase(), expectedCoverageJson = coverageJson,
            bootstrapHighWatermark = start.highWatermark, bootstrapDeltaToken = start.deltaToken, stageVerifiedAt = null,
        )
        database.withTransaction {
            val dao = database.syncRecoveryDao()
            dao.clearStaleStages(scope.scopeId, start.bootstrapSessionId)
            dao.clearStaleProtectionManifests(scope.scopeId, start.bootstrapSessionId)
            prior?.bootstrapSessionId?.takeIf { it != start.bootstrapSessionId }?.let {
                dao.clearStage(scope.scopeId, it)
                dao.clearProtectionManifest(scope.scopeId, it)
            }
            dao.clearProtectionManifest(scope.scopeId, start.bootstrapSessionId)
            dao.upsertRecoveryState(state)
        }
        return state
    }

    private fun validatePage(scope: SyncScope, state: SyncRecoveryStateEntity, page: SyncBootstrapPage) {
        if (page.bootstrapSessionId != state.bootstrapSessionId || page.baselineCursor != state.baselineCursor || page.baselineCursor.isBlank()) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SCOPE_MISMATCH", "page/session/baseline mismatch")
        }
        if (page.snapshotComplete == page.hasMore) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "snapshot_complete/has_more inconsistent")
        }
        if (page.hasMore && page.nextPageToken.isNullOrBlank()) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "continuation token missing")
        }
        var previous = 0L
        page.rows.forEach { row ->
            validateRow(scope, row)
            if (row.ordinal <= previous) throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "page ordinals not strictly increasing")
            previous = row.ordinal
        }
    }

    private fun validateRow(scope: SyncScope, row: SyncBootstrapRow) {
        if (row.ordinal <= 0L || row.aggregateId.isBlank() || row.partitionKey.isBlank()) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "invalid bootstrap row identity")
        }
        val aggregate = UnifiedSyncAggregateRegistry.findById(row.aggregateType)
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_SNAPSHOT_COVERAGE", row.aggregateType)
        if (row.payloadVersion != aggregate.payloadVersion) throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_PAYLOAD_VERSION", row.aggregateType)
        if (canonical(row.payload).toByteArray(Charsets.UTF_8).size > MAX_SNAPSHOT_ROW_BYTES) {
            throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "snapshot payload exceeds 1 MiB")
        }
        require(scope.scopeId.isNotBlank())
    }

    private suspend fun persistPage(
        scope: SyncScope,
        state: SyncRecoveryStateEntity,
        page: SyncBootstrapPage,
        guard: suspend () -> Unit,
    ): SyncRecoveryStateEntity {
        guard()
        val dao = database.syncRecoveryDao()
        val updatedAt = System.currentTimeMillis()
        return database.withTransaction {
            val live = dao.getRecoveryState(scope.scopeId)
                ?: throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "recovery state vanished")
            validateRecoveryIdentity(live, scope)
            if (live.state != STATE_IN_PROGRESS || live.bootstrapSessionId != state.bootstrapSessionId || live.nextPageToken != state.nextPageToken) {
                throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "stale recovery page commit")
            }
            val sessionId = live.bootstrapSessionId
                ?: throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "bootstrap session missing")
            val existingMax = dao.maxStageOrdinal(scope.scopeId, sessionId) ?: 0L
            var lastNewOrdinal = existingMax
            page.rows.forEach { row ->
                val fingerprint = fingerprint(row)
                val entity = SyncBootstrapStageEntity(
                    scopeId = scope.scopeId, bootstrapSessionId = sessionId, ordinal = row.ordinal,
                    aggregateType = row.aggregateType, aggregateId = row.aggregateId, entityVersion = row.entityVersion,
                    payloadVersion = row.payloadVersion, payloadJson = canonical(row.payload), partitionKey = row.partitionKey,
                    contentFingerprint = fingerprint, isTombstone = row.isTombstone,
                )
                val inserted = dao.insertStage(entity)
                if (inserted == -1L) {
                    val old = dao.getStageIdentity(scope.scopeId, sessionId, row.aggregateType, row.aggregateId)
                        ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_DUPLICATE_DIVERGENT_ROW", "duplicate ordinal/identity mismatch")
                    if (old.contentFingerprint != fingerprint || old.ordinal != row.ordinal || old.isTombstone != row.isTombstone) {
                        throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_DUPLICATE_DIVERGENT_ROW", "divergent duplicate ${row.aggregateType}/${row.aggregateId}")
                    }
                } else {
                    if (row.ordinal <= lastNewOrdinal) throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "ordinal moved backwards across committed pages")
                    lastNewOrdinal = row.ordinal
                }
            }
            val count = dao.countStage(scope.scopeId, sessionId)
            val expected = live.expectedSnapshotRows
            if (expected != null && count > expected) {
                throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "staged row count exceeded handshake")
            }
            if (page.snapshotComplete && expected != null && count != expected) {
                throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "staged=$count expected=$expected")
            }
            val next = live.copy(
                state = if (page.snapshotComplete) STATE_STAGED else STATE_IN_PROGRESS,
                nextPageToken = page.nextPageToken, stagedSnapshotRows = count, updatedAt = updatedAt,
                lastErrorCode = null, stageVerifiedAt = null,
            )
            dao.upsertRecoveryState(next)
            next
        }
    }

    private suspend fun verifyStaging(
        scope: SyncScope,
        state: SyncRecoveryStateEntity,
        guard: suspend () -> Unit,
    ): SyncRecoveryStateEntity {
        guard()
        val dao = database.syncRecoveryDao()
        val sessionId = state.bootstrapSessionId
            ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "staging session missing")
        try {
            val verified = database.withTransaction {
                val live = dao.getRecoveryState(scope.scopeId)
                    ?: throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "staging state missing")
                validateRecoveryIdentity(live, scope)
                if (live.bootstrapSessionId != sessionId || live.state != STATE_STAGED) {
                    throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "staging verification authority mismatch")
                }
                val rows = dao.listStage(scope.scopeId, sessionId)
                BootstrapSealPolicy.verify(live, rows)
                val now = System.currentTimeMillis()
                live.copy(state = STATE_STAGED_VERIFIED, stageVerifiedAt = now, lastErrorCode = null, updatedAt = now)
                    .also { dao.upsertRecoveryState(it) }
            }
            guard()
            return verified
        } catch (failure: UnifiedSyncRecoveryFailure) {
            val now = System.currentTimeMillis()
            database.withTransaction {
                val latest = dao.getRecoveryState(scope.scopeId)
                if (latest?.bootstrapSessionId == sessionId && latest.state == STATE_STAGED) {
                    dao.upsertRecoveryState(latest.copy(lastErrorCode = normalizeFailureCode(failure.code), updatedAt = now))
                }
            }
            throw failure
        }
    }

    private suspend fun captureProtectionManifest(
        scope: SyncScope,
        state: SyncRecoveryStateEntity,
        guard: suspend () -> Unit,
    ) {
        guard()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val dao = database.syncRecoveryDao()
            val live = dao.getRecoveryState(scope.scopeId)
                ?: throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "manifest state missing")
            validateRecoveryIdentity(live, scope)
            val sessionId = live.bootstrapSessionId
                ?: throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_PROTECTION_MANIFEST_MISSING", "session missing")
            if (live.state != STATE_STAGED_VERIFIED || state.bootstrapSessionId != sessionId) {
                throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "manifest authority mismatch")
            }
            dao.upsertProtectionManifest(
                RecoveryProtectionManifest.capture(
                    database.openHelper.writableDatabase, scope.scopeId, sessionId, scope.organizationId, now,
                )
            )
        }
        guard()
    }

    private suspend fun cutover(scope: SyncScope, state: SyncRecoveryStateEntity, guard: suspend () -> Unit) {
        guard()
        val dao = database.syncRecoveryDao()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val live = dao.getRecoveryState(scope.scopeId)
                ?: throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "cutover state missing")
            validateRecoveryIdentity(live, scope)
            val sessionId = live.bootstrapSessionId
                ?: throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_NONATOMIC_CUTOVER", "cutover session missing")
            val baselineCursor = live.baselineCursor
                ?: throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_NONATOMIC_CUTOVER", "cutover cursor missing")
            if (live.state != STATE_STAGED_VERIFIED || sessionId != state.bootstrapSessionId || baselineCursor.isBlank()) {
                throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_NONATOMIC_CUTOVER", "cutover authority mismatch")
            }
            val expected = live.expectedSnapshotRows
                ?: throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "expected count missing")
            if (dao.countStage(scope.scopeId, sessionId) != expected) {
                throw UnifiedSyncRecoveryFailure("FAIL_BOOTSTRAP_INCOMPLETE", "stage incomplete at cutover")
            }
            val manifest = dao.getProtectionManifest(scope.scopeId, sessionId)
                ?: throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_PROTECTION_MANIFEST_MISSING", "promotion manifest missing")
            val before = RecoveryProtectionManifest.capture(
                database.openHelper.writableDatabase, scope.scopeId, sessionId, scope.organizationId, manifest.capturedAt,
            )
            if (!RecoveryProtectionManifest.sameContent(manifest, before)) {
                throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_PROTECTION_MANIFEST_STALE", "local intent changed after manifest capture")
            }
            snapshotApplier.materializeAndPrune(scope, sessionId, live.baselineRevision)
            val after = RecoveryProtectionManifest.capture(
                database.openHelper.writableDatabase, scope.scopeId, sessionId, scope.organizationId, manifest.capturedAt,
            )
            if (!RecoveryProtectionManifest.sameContent(before, after)) {
                throw UnifiedSyncRecoveryFailure("FAIL_RECOVERY_PROTECTION_HASH_CHANGED", "promotion changed protected intent bytes")
            }
            dao.installBootstrapCursor(
                SyncCursorEntity(
                    scopeId = scope.scopeId, organizationId = scope.organizationId, syncPrincipalId = scope.syncPrincipalId,
                    contractFamily = scope.contractFamily, contractVersion = scope.contractVersion,
                    scopeDefinitionVersion = scope.scopeDefinitionVersion, cursorToken = baselineCursor,
                    lastAppliedChangeRevision = null, pageHighWatermark = live.bootstrapHighWatermark ?: live.baselineRevision,
                    minAvailableRevision = null, state = CURSOR_ACTIVE, updatedAt = now,
                )
            )
            val oldHealth = dao.getHealthState(scope.scopeId)
            dao.upsertHealthState(
                SyncHealthStateEntity(
                    scopeId = scope.scopeId, organizationId = scope.organizationId,
                    lastSuccessfulPushAt = oldHealth?.lastSuccessfulPushAt, lastSuccessfulPullAt = now,
                    lastFailureCategory = null, lastFailureCode = null,
                    lastReconciliationAt = oldHealth?.lastReconciliationAt,
                    lastReconciliationStatus = oldHealth?.lastReconciliationStatus,
                    lastObservedServerRevision = live.bootstrapHighWatermark ?: live.baselineRevision,
                    fullResyncCount = (oldHealth?.fullResyncCount ?: 0L) + 1L, updatedAt = now,
                )
            )
            dao.upsertRecoveryState(
                live.copy(state = STATE_READY, nextPageToken = null, stagedSnapshotRows = expected,
                    lastErrorCode = null, updatedAt = now, completedAt = now)
            )
        }
    }

    private suspend fun persistFailure(
        scope: SyncScope,
        state: SyncRecoveryStateEntity,
        code: String,
        preserveStaging: Boolean = false,
    ) {
        val safe = normalizeFailureCode(code)
        val now = System.currentTimeMillis()
        database.withTransaction {
            val live = database.syncRecoveryDao().getRecoveryState(scope.scopeId) ?: state
            val nextState = if (preserveStaging && live.state in setOf(STATE_IN_PROGRESS, STATE_STAGED, STATE_STAGED_VERIFIED)) {
                live.state
            } else STATE_RECOVERY_REQUIRED
            database.syncRecoveryDao().upsertRecoveryState(live.copy(state = nextState, lastErrorCode = safe, updatedAt = now))
        }
    }

    private fun validateRecoveryIdentity(state: SyncRecoveryStateEntity, scope: SyncScope) {
        if (state.scopeId != scope.scopeId || state.organizationId != scope.organizationId || state.syncPrincipalId != scope.syncPrincipalId ||
            state.contractFamily != scope.contractFamily || state.contractVersion != scope.contractVersion ||
            state.scopeDefinitionVersion != scope.scopeDefinitionVersion
        ) throw UnifiedSyncRecoveryFailure("FAIL_STALE_RECOVERY_SCOPE", "recovery identity differs from trusted scope")
    }

    private fun fingerprint(row: SyncBootstrapRow): String = sha256(
        listOf(row.aggregateType, row.aggregateId, row.entityVersion?.toString() ?: "∅", row.payloadVersion.toString(),
            row.partitionKey, if (row.isTombstone) "1" else "0", canonical(row.payload)).joinToString("|")
    )

    private fun canonical(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { (k, v) -> "${JsonPrimitive(k)}:${canonical(v)}" }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        is JsonNull -> "null"
        is JsonPrimitive -> element.toString()
        else -> element.toString()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private suspend fun <T> classifyRemote(block: suspend () -> T): T = try {
        block()
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        val message = generateSequence(t) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        val code = when {
            message.contains("BOOTSTRAP_RESTART_REQUIRED", true) -> "BOOTSTRAP_RESTART_REQUIRED"
            message.contains("SCOPE_MISMATCH", true) -> "FAIL_BOOTSTRAP_SCOPE_MISMATCH"
            message.contains("401") || message.contains("403") || message.contains("AUTH", true) -> "AUTH_BLOCKED"
            message.contains("CONTRACT_UNSUPPORTED", true) -> "FAIL_BOOTSTRAP_PAYLOAD_VERSION"
            else -> "TRANSIENT_NETWORK"
        }
        throw UnifiedSyncRecoveryFailure(code, "bootstrap remote failure (${t::class.java.simpleName})", t)
    }

    private fun normalizeFailureCode(code: String): String = code.uppercase().replace(Regex("[^A-Z0-9_]+"), "_").take(80)

    companion object {
        const val DEFAULT_PAGE_BUDGET = 4
        const val MAX_PAGE_BUDGET = 16
        const val PAGE_SIZE = 100
        const val MAX_SNAPSHOT_ROW_BYTES = 1_048_576
        const val STATE_IN_PROGRESS = "IN_PROGRESS"
        const val STATE_STAGED = "STAGED"
        const val STATE_STAGED_VERIFIED = "STAGED_VERIFIED"
        const val STATE_READY = "READY"
        const val STATE_RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
        const val CURSOR_ACTIVE = "ACTIVE"
    }
}
