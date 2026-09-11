package com.verto.app.feature.inventory.application.quickaction

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

class InventoryQuickActionProvider @Inject constructor() : QuickActionProvider {
    override val providerId: String = "inventory.quick-actions"
    override val actionIds: Set<String> = ACTIONS.mapTo(linkedSetOf(), QuickAction::id)

    override fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>> =
        flowOf(ACTIONS.filter { action -> action.isAllowedBy(context) })

    private companion object {
        val ACTIONS = listOf(
            QuickAction(
                id = QuickActionIds.ADD_INVENTORY_ITEM,
                label = "إضافة صنف",
                destination = HomeDestination(QuickActionDestinationIds.ADD_INVENTORY_ITEM),
                icon = QuickActionIcon.INVENTORY_ITEM,
                defaultOrder = QuickActionDefaultOrder.ADD_INVENTORY_ITEM,
                requiredPermission = HomePermissionKeys.INVENTORY_EDIT,
            ),
            QuickAction(
                id = QuickActionIds.PRICE_LIST,
                label = "كشف أسعار",
                destination = HomeDestination(QuickActionDestinationIds.PRICE_LIST),
                icon = QuickActionIcon.PRICE_LIST,
                defaultOrder = QuickActionDefaultOrder.PRICE_LIST,
                requiredPermission = HomePermissionKeys.INVENTORY_VIEW,
            ),
        )
    }
}
