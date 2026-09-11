package com.verto.app.data.sync.pull

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.*
import com.verto.app.data.sync.*
import com.verto.app.data.sync.ownership.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room/domain integration. A close/reopen test is not a claim that OS force-stop was executed. */
@RunWith(AndroidJUnit4::class)
class DurableInboxApplyCoordinatorInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "b10-inbox-${UUID.randomUUID()}.db"
    private val scope = SyncScope("org", "principal", "scope", scopeDefinitionVersion = 1)
    private lateinit var database: AppDatabase
    private lateinit var inbox: DurableInboxApplyCoordinator
    private lateinit var protection: SyncPendingProtection
    private val mapper = UnifiedSyncInboxMapper()
    private val validator = DurableInboxPageValidator(mapper)
    private var diskBytes = Long.MAX_VALUE

    @Before fun setup() = runBlocking<Unit> { open(); seedCursor() }
    @After fun close() { database.close(); context.deleteDatabase(databaseName) }
    private fun open() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).allowMainThreadQueries().build()
        protection = SyncPendingProtection(database)
        val financial = FinancialMaterializerV2(database, protection, FinancialEffectVerifierV2(database, protection))
        val applier = UnifiedSyncChangeApplier(database, UnifiedStrongerSyncChangeApplier(database, financial))
        inbox = DurableInboxApplyCoordinator(database, mapper, validator, UnifiedSyncPullRegistry(), applier, protection,
            DurableInboxEchoReconciler(database, FrozenMutationStore(database, protection), mapper), InboxStorageProbe { diskBytes })
    }
    private suspend fun seedCursor() = database.unifiedSyncDao().insertInitialCursor(SyncCursorEntity(
        scopeId = scope.scopeId, organizationId = scope.organizationId, syncPrincipalId = scope.syncPrincipalId,
        contractFamily = scope.contractFamily, contractVersion = scope.contractVersion, scopeDefinitionVersion = 1,
        cursorToken = "opaque-0", lastAppliedChangeRevision = null, pageHighWatermark = null,
        minAvailableRevision = null, state = "ACTIVE", updatedAt = 1))
    private fun change(revision: Long, id: String = "c-$revision", tx: String = "g-$revision", version: Long = 1) = SyncChange(
        revision, scope.organizationId, scope.scopeId, "CATEGORY", id, SyncMutationOperation.UPSERT, version, 1,
        buildJsonObject { put("name", "remote-$id-$revision") }, null, tx, 0, 1, null, 1)
    private fun page(changes: List<SyncChange>, from: String = "opaque-0", to: String = "opaque-1",
        dependencies: Map<String, List<String>> = emptyMap(), more: Boolean = false) = SyncPullPage(
        changes, to, more, pageHighWatermark = changes.last().revision, endsAtTransactionBoundary = true,
        scopeIdentity = scope, fromCursor = from, coveredThroughRevision = changes.last().revision,
        groups = changes.groupBy { it.transactionId }.values.map { validator.manifest(it, dependencies = dependencies[it.first().transactionId].orEmpty()) })
    private suspend fun cursor() = database.unifiedSyncDao().getCursor(scope.scopeId)!!
    private suspend fun state(tx: String) = database.unifiedSyncDao().getInboxGroup(scope.scopeId, tx)!!.state
    private fun count(table: String) = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }
    private fun name(id: String) = database.openHelper.readableDatabase.query("SELECT name FROM categories WHERE id=?", arrayOf(id)).use {
        if (it.moveToFirst()) it.getString(0) else null }
    private suspend fun rejects(block: suspend () -> Unit) { try { block(); fail("must fail") } catch (_: Exception) { } }
    private suspend fun settle() { repeat(4) { inbox.drain(scope) } }
    private suspend fun protect(id: String = "x") = database.withTransaction {
        database.categoryDao().insertCategory(CategoryEntity(id, "local"))
        database.unifiedSyncDao().insertOutboxRaw(SyncOutboxEntity(
            mutationId = "local-$id", organizationId = "org", aggregateType = "CATEGORY", aggregateId = id,
            operationType = "UPSERT", baseVersion = null, localSequence = 1, aggregateSequence = 1,
            payloadVersion = 1, payloadJson = "{\"name\":\"local\"}", semanticFingerprint = "a".repeat(64),
            commandBatchId = null, commandOrder = null, dependsOnMutationId = null, createdAt = 1))
        protection.capture(PendingSourceRef("org", SyncSourceOwner.UNIFIED, "local-$id"),
            listOf(ProtectedSyncKey("CATEGORY", id)), 1, "a".repeat(64), "ROOT")
    }
    private suspend fun release(id: String = "x") = database.withTransaction {
        database.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET state='ACKNOWLEDGED',acked_at=2 WHERE mutation_id=?", arrayOf("local-$id"))
        protection.releaseAfterTerminal(PendingSourceRef("org", SyncSourceOwner.UNIFIED, "local-$id"))
    }

    @Test fun receiveCommitsPayloadManifestAndTokenButNotBusinessOrAppliedAuthority() = runBlocking {
        inbox.receive(scope, "opaque-0", page(listOf(change(10))))
        assertEquals(1L, count("sync_inbox")); assertEquals(1L, count("sync_inbox_group")); assertEquals(0L, count("categories"))
        assertEquals("opaque-1", cursor().receivedCursorToken); assertEquals(10L, cursor().receivedHighWatermark)
        assertNull(cursor().appliedCheckpoint)
        val v = database.unifiedSyncDao().readEntityVersion("org", "scope", "CATEGORY", "c-10")!!
        assertEquals(1L, v.observedServerVersion); assertNull(v.appliedServerVersion)
    }
    @Test fun closeAndReopenAppliesStoredGroupWithoutFetchingItAgain() = runBlocking {
        inbox.receive(scope, "opaque-0", page(listOf(change(10))))
        database.close(); open()
        inbox.drain(scope)
        assertEquals("remote-c-10-10", name("c-10")); assertEquals("APPLIED", state("g-10"))
        assertEquals(10L, cursor().appliedCheckpoint); assertEquals(1L, count("sync_inbox"))
    }
    @Test fun identicalReceiveIsNoOpAndDifferentContentCannotReplaceRevision() = runBlocking {
        val p = page(listOf(change(10)))
        inbox.receive(scope, "opaque-0", p)
        assertEquals(0, inbox.receive(scope, "opaque-0", p).insertedChanges)
        rejects { inbox.receive(scope, "opaque-0", page(listOf(change(10).copy(payload = buildJsonObject { put("name", "changed") })))) }
        assertEquals(1L, count("sync_inbox")); assertEquals("opaque-1", cursor().receivedCursorToken)
        assertEquals(p.groups.single().contentSha256, database.unifiedSyncDao().getInboxGroup("scope", "g-10")!!.manifestSha256)
    }
    @Test fun failureBeforeReceiveCommitRollsBackEveryMemberManifestObservationAndToken() = runBlocking {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER receive_cut BEFORE UPDATE ON sync_cursor BEGIN SELECT RAISE(ABORT,'injected receive failure'); END")
        rejects { inbox.receive(scope, "opaque-0", page(listOf(change(1), change(2)))) }
        for (table in listOf("sync_inbox", "sync_inbox_group", "sync_entity_version", "sync_inbox_apply_request")) assertEquals(0L, count(table))
        assertEquals("opaque-0", cursor().receivedCursorToken)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER receive_cut")
        inbox.receive(scope, "opaque-0", page(listOf(change(1), change(2))))
        assertEquals(2L, count("sync_inbox"))
    }
    @Test fun lateBusinessSqlFailureRollsBackGroupButPreservesReceiptForRetry() = runBlocking {
        val first = change(1, tx = "group").copy(transactionSize = 2)
        val second = change(2, tx = "group").copy(transactionSize = 2, transactionOrder = 1)
        inbox.receive(scope, "opaque-0", page(listOf(first, second)))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER apply_cut BEFORE INSERT ON categories WHEN NEW.id='c-2' BEGIN SELECT RAISE(ABORT,'injected apply failure'); END")
        rejects { inbox.drain(scope) }
        assertEquals(0L, count("categories")); assertEquals("RECEIVED", state("group"))
        assertEquals(2L, count("sync_inbox")); assertNull(cursor().appliedCheckpoint)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER apply_cut")
        inbox.drain(scope); assertEquals(2L, count("categories")); assertEquals(2L, cursor().appliedCheckpoint)
    }
    @Test fun malformedDtoReviewsWholeGroupWithoutHalfBusinessWrite() = runBlocking {
        val first = change(1, tx = "bad").copy(transactionSize = 2)
        val second = change(2, tx = "bad").copy(transactionOrder = 1, transactionSize = 2, payload = JsonObject(emptyMap()))
        inbox.receive(scope, "opaque-0", page(listOf(first, second)))
        val result = inbox.drain(scope)
        assertEquals(1L, result.reviewGroups); assertEquals("REQUIRES_REVIEW", state("bad"))
        assertEquals(0L, count("categories")); assertNull(cursor().appliedCheckpoint)
    }
    @Test fun protectedXWaitsIndependentYAppliesAndTouchingZDoesNotJumpX() = runBlocking {
        protect()
        inbox.receive(scope, "opaque-0", page(listOf(change(10, "x"), change(20, "y"), change(30, "x", version = 2))))
        settle()
        assertEquals("WAITING_LOCAL", state("g-10")); assertEquals("APPLIED", state("g-20"))
        assertEquals("WAITING_DEPENDENCY", state("g-30")); assertEquals("local", name("x")); assertNotNull(name("y"))
        assertEquals("opaque-1", cursor().receivedCursorToken); assertNull(cursor().appliedCheckpoint)
        release(); settle()
        assertEquals("remote-x-30", name("x")); assertEquals(30L, cursor().appliedCheckpoint)
        assertEquals(0L, database.unifiedSyncDao().unappliedInboxGroupCount("scope"))
    }
    @Test fun explicitDependencyCanArriveLaterOnTheSameKeyWithoutCausalDeadlock() = runBlocking {
        inbox.receive(scope, "opaque-0", page(listOf(change(1, "x", "dependent", 2)), dependencies = mapOf("dependent" to listOf("origin"))))
        settle(); assertNull(name("x")); assertEquals("WAITING_DEPENDENCY", state("dependent"))
        inbox.receive(scope, "opaque-1", page(listOf(change(2, "x", "origin")), "opaque-1", "opaque-2"))
        settle()
        assertEquals("APPLIED", state("dependent")); assertEquals("APPLIED", state("origin"))
        assertEquals("remote-x-1", name("x")); assertEquals(2L, cursor().appliedCheckpoint)
    }
    @Test fun waitRunsOncePerGenerationAndTerminalWakeIsCommittedAtomically() = runBlocking {
        protect(); inbox.receive(scope, "opaque-0", page(listOf(change(1, "x")))); settle()
        assertEquals(0, inbox.drain(scope).attemptedGroups)
        val before = database.unifiedSyncDao().getInboxApplyRequest("scope")!!
        rejects { database.withTransaction { release(); throw IllegalStateException("rollback owner") } }
        assertEquals(before.requestedGeneration, database.unifiedSyncDao().getInboxApplyRequest("scope")!!.requestedGeneration)
        assertTrue(protection.isProtected("org", ProtectedSyncKey("CATEGORY", "x")))
        release()
        val after = database.unifiedSyncDao().getInboxApplyRequest("scope")!!
        assertTrue(after.requestedGeneration > before.requestedGeneration); assertNotNull(after.nextWakeAt)
        inbox.drain(scope); assertEquals("APPLIED", state("g-1"))
    }
    @Test fun firstLegalGroupWith1001ChangesCommitsOnceAsOneGroup() = runBlocking {
        val changes = (1..1001).map { change(it.toLong(), tx = "large").copy(transactionOrder = it - 1, transactionSize = 1001) }
        inbox.receive(scope, "opaque-0", page(changes))
        val result = inbox.drain(scope)
        assertEquals(1, result.appliedGroups); assertEquals(1001, result.appliedChanges)
        assertEquals(1001L, count("categories")); assertEquals(1001L, cursor().appliedCheckpoint)
        assertEquals(0, inbox.drain(scope).appliedGroups)
    }
    @Test fun budgetContinuationStopsOnlyAtCommittedGroupBoundary() = runBlocking {
        inbox.receive(scope, "opaque-0", page(listOf(change(1), change(2))))
        val first = inbox.drain(scope, changeBudget = 1)
        assertEquals(1, first.appliedGroups); assertTrue(first.continuationRequired)
        assertEquals("APPLIED", state("g-1")); assertEquals("RECEIVED", state("g-2"))
        assertNotNull(database.unifiedSyncDao().getInboxApplyRequest("scope")!!.nextWakeAt)
        assertEquals(1L, cursor().appliedCheckpoint)
        assertEquals(1, inbox.drain(scope, changeBudget = 1).appliedGroups)
        assertEquals(2L, cursor().appliedCheckpoint)
    }
    @Test fun diskAdmissionWaitDoesNotAdvanceReceiptAndStillAllowsStoredApply() = runBlocking {
        inbox.receive(scope, "opaque-0", page(listOf(change(1))))
        diskBytes = 0L
        val result = inbox.receive(scope, "opaque-1", page(listOf(change(2)), "opaque-1", "opaque-2"))
        assertTrue(result.storageWait); assertEquals("opaque-1", cursor().receivedCursorToken)
        assertEquals(1L, count("sync_inbox")); inbox.drain(scope)
        assertEquals("APPLIED", state("g-1"))
        diskBytes = Long.MAX_VALUE
        inbox.receive(scope, "opaque-1", page(listOf(change(2)), "opaque-1", "opaque-2")); inbox.drain(scope)
        assertEquals(2L, cursor().appliedCheckpoint)
    }
    @Test fun scopeMismatchAndForgedEmptyCoverageCannotMoveEitherCheckpoint() = runBlocking {
        rejects { inbox.receive(scope.copy(syncPrincipalId = "other"), "opaque-0", page(listOf(change(1)))) }
        val empty = SyncPullPage(emptyList(), "opaque-0", false, pageHighWatermark = 100,
            endsAtTransactionBoundary = true, scopeIdentity = scope, fromCursor = "opaque-0", coveredThroughRevision = 100)
        rejects { inbox.receive(scope, "opaque-0", empty) }
        assertEquals("opaque-0", cursor().receivedCursorToken); assertEquals(0L, count("sync_inbox"))
    }
    @Test fun waitingGroupsAreNotFetchedAgainAndEngineDoesNotReportCaughtUp() = runBlocking {
        protect(); val calls = mutableListOf<String>()
        val remote = object : UnifiedSyncPullRemote {
            override suspend fun resolveScope() = scope
            override suspend fun pull(scope: SyncScope, afterCursor: String, limit: Int): SyncPullPage {
                calls += afterCursor
                return if (afterCursor == "opaque-0") page(listOf(change(1, "x"))) else SyncPullPage(
                    emptyList(), afterCursor, false, pageHighWatermark = 1, endsAtTransactionBoundary = true,
                    scopeIdentity = scope, fromCursor = afterCursor, coveredThroughRevision = 1)
            }
        }
        val engine = UnifiedSyncPullEngine(database, remote, inbox)
        assertEquals(UnifiedSyncPullOutcome.WAITING_LOCAL_OR_DEPENDENCY, engine.pull("org").outcome)
        assertEquals(UnifiedSyncPullOutcome.WAITING_LOCAL_OR_DEPENDENCY, engine.pull("org").outcome)
        assertEquals(listOf("opaque-0", "opaque-1"), calls)
        assertEquals(1L, count("sync_inbox")); assertEquals("local", name("x"))
    }
}
