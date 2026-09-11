package com.verto.app.feature.expenses.application.quickaction

import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import com.verto.feature.dashboard.api.QuickAction
import com.verto.feature.dashboard.api.QuickActionDefaultOrder
import com.verto.feature.dashboard.api.QuickActionDestinationIds
import com.verto.feature.dashboard.api.QuickActionIcon
import com.verto.feature.dashboard.api.QuickActionIds
import com.verto.feature.dashboard.api.QuickActionProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ExpensesQuickActionProvider @Inject constructor() : QuickActionProvider {
    override val providerId: String = "expenses.quick-actions"
    override val actionIds: Set<String> = ACTIONS.mapTo(linkedSetOf(), QuickAction::id)

    override fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>> =
        flowOf(ACTIONS.filter { action -> action.isAllowedBy(context) })

    private companion object {
        val ACTIONS = listOf(
            QuickAction(
                id = QuickActionIds.RECORD_EXPENSE,
                label = "إضافة مصروف",
                destination = HomeDestination(QuickActionDestinationIds.EXPENSE_ENTRY),
                icon = QuickActionIcon.EXPENSE,
                defaultOrder = QuickActionDefaultOrder.RECORD_EXPENSE,
                requiredPermission = HomePermissionKeys.EXPENSES_CREATE,
            ),
        )
    }
}
