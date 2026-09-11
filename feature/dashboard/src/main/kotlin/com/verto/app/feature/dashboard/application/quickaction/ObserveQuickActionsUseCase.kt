package com.verto.app.feature.dashboard.application.quickaction

import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.QuickActionDestinationIds
import com.verto.feature.dashboard.api.QuickActionIds
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveQuickActionsUseCase @Inject constructor(
    private val registry: QuickActionProviderRegistry,
) {
    operator fun invoke(context: HomePermissionContext): Flow<List<QuickAction>> {
        if (registry.all.isEmpty()) return flowOf(emptyList())

        val contributions = registry.all.map { provider ->
            provider.observeQuickActions(context).map { actions ->
                if (provider.actionIds.isNotEmpty()) {
                    val undeclared = actions.map(QuickAction::id).filterNot(provider.actionIds::contains)
                    require(undeclared.isEmpty()) {
                        "provider ${provider.providerId} emitted undeclared quick action ids: ${undeclared.sorted()}"
                    }
                }
                actions.forEach(::requireKnownQuickActionDestination)
                actions
            }
        }
        return combine(contributions) { emitted ->
            mergeQuickActions(
                actions = emitted.flatMap(List<QuickAction>::asIterable),
                context = context,
            )
        }
    }
}

internal fun mergeQuickActions(
    actions: List<QuickAction>,
    context: HomePermissionContext,
): List<QuickAction> {
    val duplicateIds = actions.groupingBy(QuickAction::id)
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
    require(duplicateIds.isEmpty()) {
        "duplicate quick action ids: ${duplicateIds.sorted()}"
    }

    return actions
        .asSequence()
        .filter { action -> action.isVisibleBy(context) }
        .sortedWith(compareBy(QuickAction::defaultOrder, QuickAction::id))
        .toList()
}

internal fun requireKnownQuickActionDestination(action: QuickAction) {
    val expectedDestination = when (action.id) {
        QuickActionIds.SALES_INVOICE, QuickActionIds.PURCHASE -> QuickActionDestinationIds.INVOICE_EDITOR
        QuickActionIds.INTERNATIONAL_PURCHASE -> QuickActionDestinationIds.INTERNATIONAL_PURCHASE_SETUP
        QuickActionIds.ADD_CLIENT -> QuickActionDestinationIds.ADD_CLIENT
        QuickActionIds.ADD_SUPPLIER -> QuickActionDestinationIds.ADD_SUPPLIER
        QuickActionIds.ADD_INVENTORY_ITEM -> QuickActionDestinationIds.ADD_INVENTORY_ITEM
        QuickActionIds.RECORD_EXPENSE -> QuickActionDestinationIds.EXPENSE_ENTRY
        QuickActionIds.PRICE_LIST -> QuickActionDestinationIds.PRICE_LIST
        QuickActionIds.RECORD_PAYMENT -> QuickActionDestinationIds.PAYMENT_ENTRY
        QuickActionIds.QUICK_STOCK_COUNT -> QuickActionDestinationIds.STOCK_COUNT
        else -> return
    }
    require(action.destination.id == expectedDestination) {
        "quick action ${action.id} must target $expectedDestination, got ${action.destination.id}"
    }
}
