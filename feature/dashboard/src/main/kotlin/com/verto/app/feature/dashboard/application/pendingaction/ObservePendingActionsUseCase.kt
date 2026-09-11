package com.verto.app.feature.dashboard.application.pendingaction

import com.verto.feature.dashboard.api.HomeEventState
import com.verto.feature.dashboard.api.HomeEventStateStore
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeStorageScope
import com.verto.feature.dashboard.api.PendingAction
import com.verto.feature.dashboard.api.PendingActionPriority
import com.verto.feature.dashboard.api.PendingActionProvider
import com.verto.app.utils.CrashReporter
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
 * Aggregates provider-owned pending conditions without knowing any feature-specific event type.
 * Provider emissions are the validity source: removing an action invalidates it immediately.
 */
@Singleton
class ObservePendingActionsUseCase @Inject constructor(
    private val registry: PendingActionProviderRegistry,
    private val stateStore: HomeEventStateStore,
) {
    operator fun invoke(
        context: HomePermissionContext,
        nowEpochMillis: Long,
    ): Flow<List<PendingAction>> {
        require(nowEpochMillis >= 0L) { "nowEpochMillis must not be negative" }
        return observe(context, flowOf(nowEpochMillis))
    }

    /** The clock remains caller-controlled for deterministic observation and future reminder policies. */
    fun observe(
        context: HomePermissionContext,
        nowEpochMillis: Flow<Long>,
    ): Flow<List<PendingAction>> = combine(
        observeProviderActions(context),
        stateStore.observeStates(context.storageScope()),
        nowEpochMillis,
    ) { contributions, states, now ->
        require(now >= 0L) { "nowEpochMillis must not be negative" }
        mergePendingActions(
            contributions = contributions,
            states = states,
            context = context,
            nowEpochMillis = now,
        )
    }.distinctUntilChanged()

    private fun observeProviderActions(
        context: HomePermissionContext,
    ): Flow<List<PendingActionContribution>> {
        if (registry.all.isEmpty()) return flowOf(emptyList())
        val contributions = registry.all.map { provider -> provider.observeSafely(context) }
        return combine(contributions) { emitted ->
            emitted.flatMap(List<PendingActionContribution>::asIterable)
        }
    }

    private fun PendingActionProvider.observeSafely(
        context: HomePermissionContext,
    ): Flow<List<PendingActionContribution>> = observePendingActions(context)
        .map { actions -> actions.map { action -> PendingActionContribution(providerId, action) } }
        .catch { error ->
            if (error is CancellationException) throw error
            runCatching {
                CrashReporter.log(
                    "pending_action_provider_failure providerId=$providerId " +
                        "exception=${error::class.java.simpleName} " +
                        "message=${error.message.orEmpty().take(160)}",
                )
            }
            emit(emptyList())
        }

}

internal data class PendingActionContribution(
    val providerId: String,
    val action: PendingAction,
)

internal fun mergePendingActions(
    contributions: List<PendingActionContribution>,
    states: List<HomeEventState>,
    context: HomePermissionContext,
    nowEpochMillis: Long,
): List<PendingAction> {
    require(nowEpochMillis >= 0L) { "nowEpochMillis must not be negative" }
    val statesByKey = states.associateBy(HomeEventState::eventKey)

    return contributions
        .asSequence()
        .filter { contribution -> contribution.action.isAllowedBy(context) }
        .map { contribution ->
            val allowedActions = contribution.action.actions.filter { action ->
                context.allows(action.requiredPermission)
            }
            contribution.copy(
                action = if (allowedActions.size == contribution.action.actions.size) {
                    contribution.action
                } else {
                    contribution.action.copy(actions = allowedActions)
                },
            )
        }
        .groupBy { contribution -> contribution.action.eventKey }
        .values
        .map { duplicates -> duplicates.minWith(PENDING_CONTRIBUTION_RANKING) }
        .filterNot { contribution ->
            statesByKey[contribution.action.eventKey].isHiddenAt(nowEpochMillis)
        }
        .sortedWith(PENDING_CONTRIBUTION_RANKING)
        .map(PendingActionContribution::action)
}

private fun HomeEventState?.isHiddenAt(nowEpochMillis: Long): Boolean {
    require(nowEpochMillis >= 0L)
    return this?.dismissedAtEpochMillis != null
}

private val PENDING_CONTRIBUTION_RANKING: Comparator<PendingActionContribution> =
    compareByDescending<PendingActionContribution> { contribution -> contribution.action.priority.rank }
        .thenBy { contribution -> contribution.action.occurredAtEpochMillis }
        .thenBy(PendingActionContribution::providerId)
        .thenBy { contribution -> contribution.action.eventKey }

private val PendingActionPriority.rank: Int
    get() = when (this) {
        PendingActionPriority.LOW -> 0
        PendingActionPriority.NORMAL -> 1
        PendingActionPriority.HIGH -> 2
        PendingActionPriority.CRITICAL -> 3
    }

private fun HomePermissionContext.storageScope(): HomeStorageScope = HomeStorageScope(
    organizationId = organizationId,
    userId = userId,
)
