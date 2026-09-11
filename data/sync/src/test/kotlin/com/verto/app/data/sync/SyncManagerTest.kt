package com.verto.app.data.sync

import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.data.sync.pull.UnifiedSyncPullOutcome
import com.verto.app.data.sync.pull.UnifiedSyncPullResult
import com.verto.app.data.sync.push.UnifiedSyncPushOutcome
import com.verto.app.data.sync.push.UnifiedSyncPushRunResult
import com.verto.app.data.sync.recovery.RecoveryReason
import com.verto.app.data.sync.recovery.RecoveryRunOutcome
import com.verto.app.data.sync.recovery.SyncHealthSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerTest {
    @Test
    fun `profile readiness reflects deterministic runtime principal`() = runTest {
        val fixture = Fixture()
        fixture.runtime.principal = null
        assertFalse(fixture.manager.isProfileReady())
        fixture.runtime.principal = SyncManagerPrincipal("org", "user")
        assertTrue(fixture.manager.isProfileReady())
    }

    @Test
    fun `invalid session epoch produces no work scope`() = runTest {
        val fixture = Fixture()
        fixture.runtime.epoch = 0L
        assertNull(fixture.manager.currentWorkScope())
    }

    @Test
    fun `request sync persists generation before wake and wake failure stays accepted`() = runTest {
        val fixture = Fixture()
        fixture.scheduler.failWake = true

        val receipt = fixture.manager.requestSync(SyncRequestReason.MANUAL)

        assertFalse(receipt.wakeEnqueued)
        assertEquals(1L, receipt.generation)
        assertEquals(listOf("persist:org:4242", "wake:org"), fixture.events.take(2))
        assertEquals(1L, fixture.store.requested)
    }

    @Test
    fun `periodic intent persists generation without scheduler wake`() = runTest {
        val fixture = Fixture()
        val scope = fixture.manager.currentWorkScope()!!

        val receipt = fixture.manager.ensurePeriodicIntent(scope)

        assertEquals(1L, receipt.generation)
        assertEquals(listOf("persist:org:4242"), fixture.events)
    }

    @Test
    fun `public request contract always routes through durable V2 orchestration`() = runTest {
        val fixture = Fixture()

        val result = fixture.manager.request(SyncRequestReason.REALTIME)

        assertTrue(result.isSuccess)
        assertEquals(1L, fixture.store.requested)
        assertEquals(1, fixture.scheduler.wakeCount)
        assertEquals(0, fixture.runtime.legacyRuns)
        assertEquals(SyncCoordinatorPhase.REQUESTED, fixture.manager.persistedReport.value?.coordinatorPhase)
    }

    @Test
    fun `continuation scheduling is routed through scheduler seam`() = runTest {
        val fixture = Fixture()
        fixture.engine.reason = RecoveryReason.CURSOR_CORRUPT
        fixture.engine.recoveryOutcome = RecoveryRunOutcome.CONTINUATION_REQUIRED
        val scope = fixture.manager.currentWorkScope()!!

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.CONTINUATION_SCHEDULED, result)
        assertEquals(listOf(scope to 0L), fixture.scheduler.continuations)
    }

    @Test
    fun `m03 review allows one independent push but blocks bootstrap promotion and pull`() = runTest {
        val fixture = Fixture()
        fixture.runtime.migrationReviewCount = 1
        fixture.engine.reason = RecoveryReason.CURSOR_CORRUPT
        fixture.engine.recoveryOutcome = RecoveryRunOutcome.PROMOTION_BLOCKED
        val scope = fixture.manager.currentWorkScope()!!

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.CONTINUATION_SCHEDULED, result)
        assertFalse(fixture.engine.lastPromotionAllowed)
        assertEquals(1, fixture.engine.pushCount)
        assertEquals(0, fixture.engine.pullCount)
        assertEquals(listOf(scope to 0L), fixture.scheduler.continuations)
    }

    @Test
    fun `m03 review does not let bootstrap continuation starve independent push`() = runTest {
        val fixture = Fixture()
        fixture.runtime.migrationReviewCount = 1
        fixture.engine.reason = RecoveryReason.CURSOR_CORRUPT
        fixture.engine.recoveryOutcome = RecoveryRunOutcome.CONTINUATION_REQUIRED
        val scope = fixture.manager.currentWorkScope()!!

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.CONTINUATION_SCHEDULED, result)
        assertEquals(1, fixture.engine.pushCount)
        assertEquals(0, fixture.engine.pullCount)
        assertEquals(listOf(scope to 0L), fixture.scheduler.continuations)
    }

    @Test
    fun `future retry schedules delayed continuation instead of declaring drain complete`() = runTest {
        val fixture = Fixture(now = 10_000L)
        val scope = fixture.manager.currentWorkScope()!!
        fixture.engine.pushResult = fixture.engine.pushResult.copy(
            retried = 1,
            backlog = 1,
            nextEligibleAt = 25_000L,
        )

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.CONTINUATION_SCHEDULED, result)
        assertEquals(listOf(scope to 15_000L), fixture.scheduler.continuations)
    }

    @Test
    fun `terminal push issue never records push health success`() = runTest {
        val fixture = Fixture()
        val scope = fixture.manager.currentWorkScope()!!
        fixture.engine.pushResult = fixture.engine.pushResult.copy(requiresReview = 1, backlog = 1)

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.NEEDS_REVIEW, result)
        assertEquals(0, fixture.engine.pushSuccessCount)
        assertEquals(listOf("PUSH_TERMINAL" to "REVIEW_1_REJECTED_0"), fixture.engine.failures)
    }

    @Test
    fun `v2 drain does not execute legacy participant push operations`() = runTest {
        val fixture = Fixture()
        val scope = fixture.manager.currentWorkScope()!!

        fixture.manager.drainOrchestration(scope)

        assertEquals(0, fixture.participantOperationCalls)
    }

    @Test
    fun `session preparation cancels scheduler and advances epoch only after clear`() = runTest {
        val fixture = Fixture()
        fixture.runtime.lastOrganizationId = "old-org"
        fixture.events.clear()

        fixture.manager.prepareSessionForOrg("org")

        assertEquals(listOf("cancelAll", "clear", "setOrg:org", "activateEpoch"), fixture.events)
        assertEquals(2L, fixture.runtime.epoch)
    }

    @Test
    fun `request arriving during active drain is not lost`() = runTest {
        val fixture = Fixture()
        val scope = fixture.manager.currentWorkScope()!!
        fixture.store.requested = 1L
        var injected = false
        fixture.engine.onPush = {
            if (!injected) {
                injected = true
                fixture.store.requested += 1L
            }
        }

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.IDLE, result)
        assertEquals(2L, fixture.store.drained)
        assertEquals(2, fixture.engine.pushCount)
    }

    @Test
    fun `stale session scope cannot apply work after epoch changes`() = runTest {
        val fixture = Fixture()
        val staleScope = fixture.manager.currentWorkScope()!!
        fixture.runtime.epoch += 1L

        val failure = runCatching { fixture.manager.drainOrchestration(staleScope) }.exceptionOrNull()

        assertTrue(failure is StaleSyncWorkScopeException)
        assertEquals(0, fixture.engine.pushCount)
    }

    @Test
    fun `organization switch is blocked while old scope owns unconfirmed work`() = runTest {
        val fixture = Fixture()
        fixture.runtime.lastOrganizationId = "old-org"
        fixture.engine.unconfirmed = true
        fixture.events.clear()

        val failure = runCatching { fixture.manager.prepareSessionForOrg("new-org") }.exceptionOrNull()

        assertTrue(failure is BusinessRuleFailureException)
        assertEquals("old-org", fixture.runtime.lastOrganizationId)
        assertEquals(listOf("cancelAll"), fixture.events)
    }

    @Test
    fun `manual drain converges with realtime diagnostics disabled`() = runTest {
        val fixture = Fixture()
        val scope = fixture.manager.currentWorkScope()!!
        fixture.store.requested = 1L

        val result = fixture.manager.drainOrchestration(scope)

        assertEquals(SyncDrainResult.IDLE, result)
        assertEquals(1L, fixture.store.drained)
        assertEquals(1, fixture.engine.pushCount)
    }

    @Test
    fun `orchestration timestamps come only from injected clock`() = runTest {
        val fixture = Fixture(now = 987654321L)

        fixture.manager.requestSync(SyncRequestReason.OUTBOX_WRITE)

        assertEquals(987654321L, fixture.store.lastUpdatedAt)
    }

    private class Fixture(
        now: Long = 4242L,
    ) {
        val events = mutableListOf<String>()
        val runtime = FakeRuntime(events)
        val scheduler = FakeScheduler(events)
        val store = FakeStore(events)
        val engine = FakeV2Engine()
        var participantOperationCalls = 0
        val participants = requiredParticipants { participantOperationCalls += 1 }
        val orchestration = SyncOrchestrationControl(scheduler, store, SyncClock { now })
        val manager = SyncManager(
            runtime = runtime,
            participants = participants,
            orchestration = orchestration,
            v2Engine = engine,
        )
    }

    private class FakeRuntime(private val events: MutableList<String>) : SyncManagerRuntimePort {
        var principal: SyncManagerPrincipal? = SyncManagerPrincipal("org", "user")
        var epoch = 1L
        var lastOrganizationId = "org"
        var legacyRuns = 0
        var migrationReviewCount = 0
        private var report: String? = null

        override suspend fun currentPrincipal() = principal
        override suspend fun sessionEpoch() = epoch
        override suspend fun isLocalDatabaseEmpty() = false
        override suspend fun captureDeletionSnapshot() = SyncDeletionSnapshot(
            clientIds = emptySet(), invoiceIds = emptySet(), inventoryIds = emptySet(), expenseIds = emptySet(),
            categoryIds = emptySet(), commissionIds = emptySet(), unitIds = emptySet(),
            budgetIds = emptySet(), reconciliationIds = emptySet(),
        )
        override suspend fun prepareLegacyV2Migration(scope: SyncWorkScope, deletions: SyncDeletionSnapshot, now: Long) = migrationReviewCount
        override suspend fun getPersistedSyncReport(organizationId: String, userId: String) = report
        override suspend fun setPersistedSyncReport(organizationId: String, userId: String, reportJson: String) {
            report = reportJson
        }
        override suspend fun clearLocalData() {
            events += "clear"
        }
        override suspend fun getLastOrganizationId() = lastOrganizationId
        override suspend fun setLastOrganizationId(organizationId: String) {
            lastOrganizationId = organizationId
            events += "setOrg:$organizationId"
        }
        override suspend fun activateNextSessionEpoch(): Long {
            epoch += 1
            events += "activateEpoch"
            return epoch
        }
        override suspend fun currentOrganizationDisplayName() = "Org"
        override suspend fun prepareForSync() {
            legacyRuns += 1
        }
        override suspend fun beginOrResumeSyncRun(organizationId: String, startingRevision: Long) =
            SyncRunCheckpoint(organizationId, "run-1", startingRevision, emptySet(), false)
        override suspend fun markSyncOperationCompleted(organizationId: String, runId: String, operationKey: String) = Unit
        override suspend fun completeSyncRun(organizationId: String, runId: String) = Unit
        override fun markSyncCompleted() = Unit
        override suspend fun pullNotifications(organizationId: String) = Unit
        override suspend fun pullCommissions(organizationId: String) = Unit
    }

    private class FakeScheduler(private val events: MutableList<String>) : SyncWakeScheduler {
        var wakeCount = 0
        var failWake = false
        val continuations = mutableListOf<Pair<SyncWorkScope, Long>>()
        override fun wakeNow(scope: SyncWorkScope) {
            wakeCount += 1
            events += "wake:${scope.organizationId}"
            if (failWake) throw IllegalStateException("wake failed")
        }
        override fun enqueueContinuation(scope: SyncWorkScope, delayMillis: Long) {
            continuations += scope to delayMillis
        }
        override fun cancelAll() {
            events += "cancelAll"
        }
    }

    private class FakeStore(private val events: MutableList<String>) : SyncOrchestrationStore {
        var requested = 0L
        var drained = 0L
        var lastUpdatedAt = 0L
        override suspend fun requestGeneration(organizationId: String, updatedAt: Long): Long {
            requested += 1
            lastUpdatedAt = updatedAt
            events += "persist:$organizationId:$updatedAt"
            return requested
        }
        override suspend fun readGenerations(organizationId: String) = SyncOrchestrationGenerations(requested, drained)
        override suspend fun countEligibleOutboxNow(organizationId: String, now: Long) = 0L
        override suspend fun markDrainedIfIdle(organizationId: String, observedRequested: Long, updatedAt: Long): Boolean {
            if (requested != observedRequested) return false
            drained = observedRequested
            return true
        }
    }

    private class FakeV2Engine : SyncV2EnginePort {
        var reason: RecoveryReason? = null
        var recoveryOutcome = RecoveryRunOutcome.READY
        var pushResult = UnifiedSyncPushRunResult(
            outcome = UnifiedSyncPushOutcome.CAUGHT_UP,
            sent = 0, acknowledged = 0, retried = 0, requiresReview = 0, rejected = 0,
            staleLeaseResults = 0, blocked = 0, backlog = 0, eligibleNow = 0, nextEligibleAt = null,
        )
        var pushSuccessCount = 0
        var unconfirmed = false
        var pushCount = 0
        var pullCount = 0
        var lastPromotionAllowed = true
        var onPush: (() -> Unit)? = null
        val failures = mutableListOf<Pair<String, String>>()

        override suspend fun requiredRecoveryReason(
            organizationId: String,
            scopeGuard: suspend () -> Unit,
        ): RecoveryReason? {
            scopeGuard()
            return reason
        }

        override suspend fun runRecovery(
            organizationId: String,
            reason: RecoveryReason,
            promotionAllowed: Boolean,
            scopeGuard: suspend () -> Unit,
        ): RecoveryRunOutcome {
            scopeGuard()
            lastPromotionAllowed = promotionAllowed
            return recoveryOutcome
        }

        override suspend fun pushAvailable(scope: SyncWorkScope): UnifiedSyncPushRunResult {
            pushCount += 1
            onPush?.invoke()
            return pushResult
        }

        override suspend fun pull(organizationId: String): UnifiedSyncPullResult {
            pullCount += 1
            return UnifiedSyncPullResult(UnifiedSyncPullOutcome.CAUGHT_UP, pagesCommitted = 0, changesAccounted = 0)
        }

        override suspend fun markPushSuccess(organizationId: String) { pushSuccessCount += 1 }
        override suspend fun markPullSuccess(organizationId: String) = Unit
        override suspend fun markFailure(organizationId: String, category: String, code: String) {
            failures += category to code
        }

        override suspend fun healthSnapshot(scope: SyncWorkScope): SyncHealthSnapshot = SyncHealthSnapshot(
            currentTenant = scope.organizationId, scopeId = null, recoveryState = "READY",
            lastObservedServerRevision = null, lastAppliedRevision = null, syncLag = null,
            unifiedOutboxDepth = 0, strongerOutboxDepth = 0, attachmentOutboxDepth = 0,
            oldestPendingMutationAgeMillis = null, retryCount = 0, conflictCount = 0, deadLetterOrReviewCount = 0,
            pendingCount = pushResult.backlog, requiresReviewCount = pushResult.requiresReview.toLong(), rejectedCount = pushResult.rejected.toLong(),
            lastSuccessfulPushAt = null, lastSuccessfulPullAt = null, lastFailureCategory = null, lastFailureCode = null,
            realtimeState = "DISABLED", requestedGeneration = 0, drainedGeneration = 0, fullResyncCount = 0,
            lastReconciliationStatus = null, rolloutWave = "M07", ownershipMode = "V2", shadowMismatchCount = null,
            legacyFallbackUseCount = null, v2PullEnabled = true, v2PushEnabled = true, financialEnabled = true,
            inventoryEnabled = true, realtimeEnabled = false,
        )

        override suspend fun hasUnconfirmedWork(organizationId: String): Boolean = unconfirmed
    }

    private companion object {
        fun requiredParticipants(onOperations: () -> Unit = {}): Set<SyncParticipant> = setOf(
            "clients", "invoices", "inventory", "logistics-v2", "cash", "organization", "optimal_outbox", "educational_content",
        ).mapTo(linkedSetOf()) { key ->
            object : SyncParticipant {
                override val key: String = key
                override fun operations(context: SyncRunContext): List<SyncOperation> {
                    onOperations()
                    return emptyList()
                }
            }
        }
    }
}
