package com.verto.app.feature.dashboard.application.pendingaction

import com.verto.feature.dashboard.api.HomeAction
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeEventState
import com.verto.feature.dashboard.api.HomeEventStateStore
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeStorageScope
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservePendingActionsUseCase337Test {
    private val context = HomePermissionContext("org-a", "user-a", setOf("read", "act"))

    @Test
    fun allOpenActions_dedupe_and_deterministicRanking_arePreserved() = runBlocking {
        val providerB = StaticProvider(
            "provider-b",
            listOf(
                action("dup", PendingActionPriority.CRITICAL, 50),
                action("b-old", PendingActionPriority.HIGH, 10),
                action("b-new", PendingActionPriority.HIGH, 30),
                action("b-normal", PendingActionPriority.NORMAL, 1),
            ),
        )
        val providerA = StaticProvider(
            "provider-a",
            listOf(
                action("dup", PendingActionPriority.CRITICAL, 50),
                action("a-old", PendingActionPriority.HIGH, 10),
                action("a-mid", PendingActionPriority.HIGH, 20),
                action("a-normal", PendingActionPriority.NORMAL, 0),
            ),
        )
        val useCase = ObservePendingActionsUseCase(
            PendingActionProviderRegistry(setOf(providerB, providerA)),
            FakeStateStore(),
        )

        val result = useCase(context, nowEpochMillis = 100).first()

        assertEquals(7, result.size)
        assertEquals(
            listOf("dup", "a-old", "b-old", "a-mid", "b-new", "a-normal", "b-normal"),
            result.map { it.eventKey },
        )
    }

    @Test
    fun snoozedEvent_remainsVisibleUntilClosed() = runBlocking {
        val states = FakeStateStore(
            listOf(HomeEventState(eventKey = "event", snoozedUntilEpochMillis = 200)),
        )
        val useCase = ObservePendingActionsUseCase(
            PendingActionProviderRegistry(setOf(StaticProvider("provider", listOf(action("event", PendingActionPriority.HIGH, 1))))),
            states,
        )
        val clock = MutableStateFlow(199L)

        assertEquals("event", useCase.observe(context, clock).first().single().eventKey)
    }

    @Test
    fun dismissedAndUnauthorizedActions_areFiltered() = runBlocking {
        val event = action("event", PendingActionPriority.HIGH, 1).copy(
            actions = listOf(
                HomeAction("allowed", "Allowed", HomeDestination("dest"), "act"),
                HomeAction("denied", "Denied", HomeDestination("dest"), "other"),
            ),
        )
        val store = FakeStateStore()
        val useCase = ObservePendingActionsUseCase(
            PendingActionProviderRegistry(setOf(StaticProvider("provider", listOf(event)))),
            store,
        )
        val visible = useCase(context, 10).first().single()
        assertEquals(listOf("allowed"), visible.actions.map(HomeAction::id))

        store.states.value = listOf(HomeEventState("event", dismissedAtEpochMillis = 10))
        assertTrue(useCase(context, 11).first().isEmpty())
    }

    @Test
    fun providerFailure_isIsolated() = runBlocking {
        val broken = object : PendingActionProvider {
            override val providerId = "broken"
            override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
                throw IllegalStateException("synthetic")
            }
        }
        val healthy = StaticProvider("healthy", listOf(action("ok", PendingActionPriority.NORMAL, 1)))
        val useCase = ObservePendingActionsUseCase(PendingActionProviderRegistry(setOf(broken, healthy)), FakeStateStore())

        assertEquals(listOf("ok"), useCase(context, 1).first().map(PendingAction::eventKey))
    }

    @Test
    fun cancellation_isNeverConvertedToEmptyList() = runBlocking {
        val cancelled = object : PendingActionProvider {
            override val providerId = "cancelled"
            override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flow {
                throw CancellationException("synthetic")
            }
        }
        val useCase = ObservePendingActionsUseCase(PendingActionProviderRegistry(setOf(cancelled)), FakeStateStore())
        var propagated = false
        try {
            useCase(context, 1).first()
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test
    fun providerRemoval_disappearsImmediately() = runBlocking {
        val emissions = MutableStateFlow(listOf(action("event", PendingActionPriority.NORMAL, 1)))
        val provider = object : PendingActionProvider {
            override val providerId = "provider"
            override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = emissions
        }
        val useCase = ObservePendingActionsUseCase(PendingActionProviderRegistry(setOf(provider)), FakeStateStore())
        val observed = useCase(context, 1)
        assertFalse(observed.first().isEmpty())
        emissions.value = emptyList()
        assertTrue(observed.first { it.isEmpty() }.isEmpty())
    }

    private fun action(key: String, priority: PendingActionPriority, occurredAt: Long) = PendingAction(
        eventKey = key,
        title = key,
        summary = "summary-$key",
        occurredAtEpochMillis = occurredAt,
        priority = priority,
        destination = HomeDestination("destination"),
        requiredPermission = "read",
    )

    private class StaticProvider(
        override val providerId: String,
        private val actions: List<PendingAction>,
    ) : PendingActionProvider {
        override fun observePendingActions(context: HomePermissionContext): Flow<List<PendingAction>> = flowOf(actions)
    }

    private class FakeStateStore(initial: List<HomeEventState> = emptyList()) : HomeEventStateStore {
        val states = MutableStateFlow(initial)
        override fun observeStates(scope: HomeStorageScope): Flow<List<HomeEventState>> = states
        override suspend fun markSeen(scope: HomeStorageScope, eventKey: String, seenAtEpochMillis: Long) = Unit
        override suspend fun snooze(scope: HomeStorageScope, eventKey: String, snoozedUntilEpochMillis: Long) = Unit
        override suspend fun dismiss(scope: HomeStorageScope, eventKey: String, dismissedAtEpochMillis: Long) = Unit
        override suspend fun clearExpiredSnoozes(scope: HomeStorageScope, nowEpochMillis: Long): Int = 0
    }
}
