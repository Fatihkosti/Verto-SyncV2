package com.verto.app.feature.dashboard.application.quickaction

import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeStorageScope
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.QuickActionOrderStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Applies the user's explicit order without rewriting it during observation.
 * The persisted scope is always organization + user, so accounts never share ordering state.
 */
class ObserveOrderedQuickActionsUseCase @Inject constructor(
    private val observeAvailableActions: ObserveQuickActionsUseCase,
    private val orderStore: QuickActionOrderStore,
) {
    operator fun invoke(context: HomePermissionContext): Flow<List<QuickAction>> = combine(
        observeAvailableActions(context),
        orderStore.observeOrder(context.storageScope()),
    ) { availableActions, savedOrder ->
        applySavedQuickActionOrder(
            defaultOrderedActions = availableActions,
            savedOrder = savedOrder,
        )
    }.distinctUntilChanged()

    /** Saves only after an explicit user confirmation from organize mode. */
    suspend fun saveExplicitOrder(
        context: HomePermissionContext,
        orderedVisibleActionIds: List<String>,
        updatedAtEpochMillis: Long,
    ) {
        require(updatedAtEpochMillis >= 0L) { "updatedAtEpochMillis must not be negative" }
        val requestedOrder = orderedVisibleActionIds.map(String::trim)
        require(requestedOrder.none(String::isBlank)) { "quick action ids must not be blank" }
        require(requestedOrder.size == requestedOrder.toSet().size) {
            "quick action ids must be unique"
        }

        val currentlyAvailable = observeAvailableActions(context).first()
        val availableIds = currentlyAvailable.map(QuickAction::id)
        val availableSet = availableIds.toSet()

        // Permissions may change while organize mode is open. Drop no-longer-visible IDs and
        // append newly-restored/new actions in their deterministic provider order.
        val normalizedVisibleOrder = requestedOrder.filter(availableSet::contains) +
            availableIds.filterNot(requestedOrder.toSet()::contains)

        val scope = context.storageScope()
        val previousStoredOrder = orderStore.observeOrder(scope).first()
        val mergedOrder = mergeVisibleOrderIntoStoredOrder(
            previousStoredOrder = previousStoredOrder,
            orderedVisibleActionIds = normalizedVisibleOrder,
            currentlyVisibleActionIds = availableSet,
        )
        orderStore.saveOrder(scope, mergedOrder, updatedAtEpochMillis)
    }

    suspend fun resetToDefault(context: HomePermissionContext) {
        orderStore.clearOrder(context.storageScope())
    }
}

internal fun applySavedQuickActionOrder(
    defaultOrderedActions: List<QuickAction>,
    savedOrder: List<String>,
): List<QuickAction> {
    if (defaultOrderedActions.isEmpty() || savedOrder.isEmpty()) return defaultOrderedActions

    val actionsById = defaultOrderedActions.associateBy(QuickAction::id)
    val savedIds = savedOrder.toHashSet()
    return buildList(defaultOrderedActions.size) {
        savedOrder.mapNotNullTo(this, actionsById::get)
        defaultOrderedActions.filterTo(this) { action -> action.id !in savedIds }
    }
}

/**
 * Replaces visible slots with the explicit order while preserving inaccessible IDs. Therefore a
 * temporarily forbidden action returns to its previous safe slot; new actions are appended.
 */
internal fun mergeVisibleOrderIntoStoredOrder(
    previousStoredOrder: List<String>,
    orderedVisibleActionIds: List<String>,
    currentlyVisibleActionIds: Set<String>,
): List<String> {
    if (previousStoredOrder.isEmpty()) return orderedVisibleActionIds

    val visibleIterator = orderedVisibleActionIds.iterator()
    val merged = buildList {
        previousStoredOrder.forEach { storedId ->
            if (storedId in currentlyVisibleActionIds) {
                if (visibleIterator.hasNext()) add(visibleIterator.next())
            } else {
                add(storedId)
            }
        }
        while (visibleIterator.hasNext()) add(visibleIterator.next())
    }
    return merged.distinct()
}

private fun HomePermissionContext.storageScope(): HomeStorageScope = HomeStorageScope(
    organizationId = organizationId,
    userId = userId,
)
