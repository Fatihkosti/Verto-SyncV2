package com.verto.app.feature.invoice.application.quickaction

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

class InvoiceQuickActionProvider @Inject constructor() : QuickActionProvider {
    override val providerId: String = "invoice.quick-actions"
    override val actionIds: Set<String> = ACTIONS.mapTo(linkedSetOf(), QuickAction::id)

    override fun observeQuickActions(context: HomePermissionContext): Flow<List<QuickAction>> =
        flowOf(ACTIONS.filter { action -> action.isVisibleBy(context) })

    private companion object {
        val ACTIONS = listOf(
            QuickAction(
                id = QuickActionIds.SALES_INVOICE,
                label = "فاتورة بيع",
                destination = HomeDestination(
                    id = QuickActionDestinationIds.INVOICE_EDITOR,
                    arguments = mapOf("type" to "sale"),
                ),
                icon = QuickActionIcon.SALE_INVOICE,
                defaultOrder = QuickActionDefaultOrder.SALES_INVOICE,
                requiredPermission = HomePermissionKeys.SALES_CREATE,
            ),
            QuickAction(
                id = QuickActionIds.PURCHASE,
                label = "فاتورة شراء",
                destination = HomeDestination(
                    id = QuickActionDestinationIds.INVOICE_EDITOR,
                    arguments = mapOf("type" to "purchase"),
                ),
                icon = QuickActionIcon.PURCHASE,
                defaultOrder = QuickActionDefaultOrder.PURCHASE,
                requiredPermission = HomePermissionKeys.PURCHASES_CREATE,
                alwaysVisible = true,
            ),
            QuickAction(
                id = QuickActionIds.INTERNATIONAL_PURCHASE,
                label = "فاتورة شراء دولية",
                destination = HomeDestination(
                    id = QuickActionDestinationIds.INTERNATIONAL_PURCHASE_SETUP,
                ),
                icon = QuickActionIcon.INTERNATIONAL_PURCHASE,
                defaultOrder = QuickActionDefaultOrder.INTERNATIONAL_PURCHASE,
                requiredPermission = HomePermissionKeys.PURCHASES_CREATE,
            ),
        )
    }
}
