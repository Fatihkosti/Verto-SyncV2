package com.verto.app.feature.party.application.quickaction

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

class PartyQuickActionProvider @Inject constructor() : QuickActionProvider {
    override val providerId: String = "party.quick-actions"
    override val actionIds: Set<String> = ACTIONS.mapTo(linkedSetOf(), QuickAction::id)

    override fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>> =
        flowOf(ACTIONS.filter { action -> action.isAllowedBy(context) })

    private companion object {
        val ACTIONS = listOf(
            QuickAction(
                id = QuickActionIds.ADD_CLIENT,
                label = "إضافة عميل",
                destination = HomeDestination(QuickActionDestinationIds.ADD_CLIENT),
                icon = QuickActionIcon.CLIENT,
                defaultOrder = QuickActionDefaultOrder.ADD_CLIENT,
                requiredPermission = HomePermissionKeys.CLIENTS_EDIT,
            ),
            QuickAction(
                id = QuickActionIds.ADD_SUPPLIER,
                label = "إضافة مورد",
                destination = HomeDestination(QuickActionDestinationIds.ADD_SUPPLIER),
                icon = QuickActionIcon.SUPPLIER,
                defaultOrder = QuickActionDefaultOrder.ADD_SUPPLIER,
                requiredPermission = HomePermissionKeys.CLIENTS_EDIT,
            ),
        )
    }
}
