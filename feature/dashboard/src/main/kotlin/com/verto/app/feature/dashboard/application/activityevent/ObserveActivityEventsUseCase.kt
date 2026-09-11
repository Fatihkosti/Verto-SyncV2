package com.verto.app.feature.dashboard.application.activityevent

import android.util.Log
import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventProvider
import com.verto.feature.dashboard.api.HomePermissionContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Builds the local-first seven-day Home activity read model from independent feature providers.
 * Provider failures are isolated and observable; cancellation is always propagated.
 */
@Singleton
class ObserveActivityEventsUseCase @Inject constructor(
    private val registry: ActivityEventProviderRegistry,
) {
    operator fun invoke(
        context: HomePermissionContext,
        nowEpochMillis: Long,
    ): Flow<List<ActivityEvent>> = observe(
        context = context,
        windowReferenceEpochMillis = nowEpochMillis,
        currentTimeProvider = System::currentTimeMillis,
    )

    internal fun observe(
        context: HomePermissionContext,
        windowReferenceEpochMillis: Long,
        currentTimeProvider: () -> Long,
    ): Flow<List<ActivityEvent>> {
        require(windowReferenceEpochMillis >= 0L) { "nowEpochMillis must not be negative" }
        val sinceEpochMillis = (windowReferenceEpochMillis - ACTIVITY_WINDOW_MILLIS).coerceAtLeast(0L)
        if (registry.all.isEmpty()) return flowOf(emptyList())

        val contributions = registry.all.map { provider ->
            provider.observeSafely(context, sinceEpochMillis)
        }
        return combine(contributions) { emitted ->
            val currentNow = currentTimeProvider().coerceAtLeast(sinceEpochMillis)
            mergeActivityEvents(
                contributions = emitted.flatMap { contributionList -> contributionList },
                context = context,
                sinceEpochMillis = sinceEpochMillis,
                nowEpochMillis = currentNow,
            )
        }.distinctUntilChanged()
    }

    private fun ActivityEventProvider.observeSafely(
        context: HomePermissionContext,
        sinceEpochMillis: Long,
    ): Flow<List<ActivityEventContribution>> = observeActivityEvents(context, sinceEpochMillis)
        .map { events -> events.map { event -> ActivityEventContribution(providerId, event) } }
        .catch { error ->
            if (error is CancellationException) throw error
            ActivityEventFailureDiagnostics.record(providerId, error)
            // Deliberate no-retry policy for 339: isolate this provider for the current subscription.
            // A future source invalidation/context/window subscription can restore it without a tight loop.
            emit(emptyList())
        }

    companion object {
        const val ACTIVITY_WINDOW_DAYS: Long = 7L
        const val ACTIVITY_WINDOW_MILLIS: Long = ACTIVITY_WINDOW_DAYS * 24L * 60L * 60L * 1_000L
        internal const val MAX_HOME_ACTIVITY_EVENTS: Int = 30
    }
}

internal object ActivityEventFailureDiagnostics {
    private const val TAG = "VertoActivityFeed"
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()

    fun record(providerId: String, error: Throwable): Int {
        val count = failureCounts.computeIfAbsent(providerId) { AtomicInteger(0) }.incrementAndGet()
        runCatching {
            Log.w(TAG, "provider=$providerId failure=${error::class.java.simpleName} count=$count")
        }
        return count
    }

    fun failureCount(providerId: String): Int = failureCounts[providerId]?.get() ?: 0
    fun clearForTests() = failureCounts.clear()
}

internal data class ActivityEventContribution(
    val providerId: String,
    val event: ActivityEvent,
)

internal fun mergeActivityEvents(
    contributions: List<ActivityEventContribution>,
    context: HomePermissionContext,
    sinceEpochMillis: Long,
    nowEpochMillis: Long,
): List<ActivityEvent> {
    require(sinceEpochMillis >= 0L) { "sinceEpochMillis must not be negative" }
    require(nowEpochMillis >= sinceEpochMillis) { "nowEpochMillis must not precede sinceEpochMillis" }

    return contributions
        .asSequence()
        .filter { contribution -> contribution.event.isAllowedBy(context) }
        .filter { contribution ->
            contribution.event.occurredAtEpochMillis in sinceEpochMillis..nowEpochMillis
        }
        .groupBy { contribution -> contribution.event.eventKey }
        .values
        .map { duplicates -> duplicates.minWith(ACTIVITY_EVENT_RANKING) }
        .sortedWith(ACTIVITY_EVENT_RANKING)
        .take(ObserveActivityEventsUseCase.MAX_HOME_ACTIVITY_EVENTS)
        .map(ActivityEventContribution::event)
}

private val ACTIVITY_EVENT_RANKING: Comparator<ActivityEventContribution> =
    compareByDescending<ActivityEventContribution> { contribution -> contribution.event.occurredAtEpochMillis }
        .thenBy(ActivityEventContribution::providerId)
        .thenBy { contribution -> contribution.event.eventKey }
