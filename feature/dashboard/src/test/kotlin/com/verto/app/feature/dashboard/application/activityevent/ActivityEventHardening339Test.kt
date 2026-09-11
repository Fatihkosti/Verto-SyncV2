package com.verto.app.feature.dashboard.application.activityevent

import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.ActivityEventStatus
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityEventHardening339Test {
    private val context = HomePermissionContext("org-a", "user-a", setOf("view"))

    @Test fun `registry rejects blank and duplicate provider ids and sorts deterministically`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActivityEventProviderRegistry(setOf(FakeProvider("", flowOf(emptyList()))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActivityEventProviderRegistry(setOf(
                FakeProvider("dup", flowOf(emptyList())),
                FakeProvider("dup", flowOf(emptyList())),
            ))
        }
        val registry = ActivityEventProviderRegistry(setOf(
            FakeProvider("z", flowOf(emptyList())),
            FakeProvider("a", flowOf(emptyList())),
        ))
        assertEquals(listOf("a", "z"), registry.all.map { it.providerId })
    }

    @Test fun `fresh now admits a valid event emitted after initial subscription`() = runBlocking {
        val source = MutableStateFlow<List<ActivityEvent>>(emptyList())
        val useCase = ObserveActivityEventsUseCase(
            ActivityEventProviderRegistry(setOf(FakeProvider("p", source))),
        )
        var currentNow = minute(10, 0)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            useCase.observe(context, minute(10, 0), currentTimeProvider = { currentNow })
                .first { events -> events.any { it.eventKey == "new" } }
        }
        currentNow = minute(10, 5)
        source.value = listOf(event("new", minute(10, 5)))
        assertEquals(listOf("new"), result.await().map { it.eventKey })
    }

    @Test fun `seven day boundary upper bound permission and organization are enforced`() {
        val now = 8L * DAY
        val since = now - 7L * DAY
        val rows = listOf(
            contribution("p", event("boundary", since)),
            contribution("p", event("outside", since - 1)),
            contribution("p", event("at-now", now)),
            contribution("p", event("future", now + 1)),
            contribution("p", event("wrong-org", now - 1, org = "org-b")),
            contribution("p", event("denied", now - 2, permission = "missing")),
        )
        assertEquals(
            listOf("at-now", "boundary"),
            mergeActivityEvents(rows, context, since, now).map { it.eventKey },
        )
    }

    @Test fun `home activity is capped at thirty events within the seven day window`() {
        assertEquals(7L, ObserveActivityEventsUseCase.ACTIVITY_WINDOW_DAYS)
        assertEquals(30, ObserveActivityEventsUseCase.MAX_HOME_ACTIVITY_EVENTS)
    }

    @Test fun `ranking dedupe and top k are deterministic`() {
        val now = 10_000L
        val rows = buildList {
            listOf("a", "b", "c").forEachIndexed { providerIndex, providerId ->
                repeat(1_000) { index ->
                    val globalIndex = providerIndex * 1_000 + index
                    add(contribution(providerId, event("e$globalIndex", now - globalIndex)))
                }
            }
            add(contribution("z", event("same", now)))
            add(contribution("a", event("same", now)))
        }
        val merged = mergeActivityEvents(rows, context, 0L, now)
        assertEquals(ObserveActivityEventsUseCase.MAX_HOME_ACTIVITY_EVENTS, merged.size)
        assertEquals("e0", merged.first().eventKey)
        assertEquals(1, merged.count { it.eventKey == "same" })
        assertEquals(merged.map { it.eventKey }.distinct().size, merged.size)
        assertTrue(merged.zipWithNext().all { (left, right) -> left.occurredAtEpochMillis >= right.occurredAtEpochMillis })
    }

    @Test fun `failed provider is isolated observable and healthy provider remains visible`() = runBlocking {
        ActivityEventFailureDiagnostics.clearForTests()
        val failed = FakeProvider("bad", flow { error("boom") })
        val healthy = FakeProvider("good", flowOf(listOf(event("healthy", 100L))))
        val useCase = ObserveActivityEventsUseCase(ActivityEventProviderRegistry(setOf(failed, healthy)))
        val result = useCase.observe(context, 100L, currentTimeProvider = { 100L }).first()
        assertEquals(listOf("healthy"), result.map { it.eventKey })
        assertEquals(1, ActivityEventFailureDiagnostics.failureCount("bad"))
    }

    @Test fun `cancellation exception is never swallowed`() {
        val cancelled = FakeProvider("cancelled", flow { throw CancellationException("stop") })
        val useCase = ObserveActivityEventsUseCase(
            ActivityEventProviderRegistry(setOf(cancelled)),
        )
        assertThrows(CancellationException::class.java) {
            runBlocking { useCase.observe(context, 100L, currentTimeProvider = { 100L }).first() }
        }
    }

    private fun event(
        key: String,
        at: Long,
        org: String = "org-a",
        permission: String? = "view",
    ) = ActivityEvent(
        eventKey = key,
        organizationId = org,
        title = "title",
        description = "description",
        occurredAtEpochMillis = at,
        kind = ActivityEventKind.OTHER,
        status = ActivityEventStatus.OTHER,
        destination = HomeDestination("test"),
        requiredPermission = permission,
    )

    private fun contribution(provider: String, event: ActivityEvent) = ActivityEventContribution(provider, event)

    private fun minute(hour: Int, minute: Int): Long = (hour * 60L + minute) * 60_000L

    private class FakeProvider(
        override val providerId: String,
        private val rows: Flow<List<ActivityEvent>>,
    ) : ActivityEventProvider {
        override fun observeActivityEvents(
            context: HomePermissionContext,
            sinceEpochMillis: Long,
        ): Flow<List<ActivityEvent>> = rows
    }

    private companion object {
        const val DAY: Long = 24L * 60L * 60L * 1_000L
    }
}
