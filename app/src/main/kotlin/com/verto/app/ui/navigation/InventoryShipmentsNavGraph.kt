package com.verto.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.inventory.presentation.inventory.AddEditItemScreen
import com.verto.app.feature.inventory.presentation.inventory.BulkPriceEditScreen
import com.verto.app.feature.inventory.presentation.inventory.CategoryManagementScreen
import com.verto.app.feature.inventory.presentation.inventory.InventoryScreen
import com.verto.app.feature.inventory.presentation.pricelist.PriceListScreen
import com.verto.app.feature.party.presentation.client.ClientStatementScreen
import com.verto.app.feature.party.presentation.competitor.CompetitorScreen
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsRouteEntry
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsRouteKind
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsRouteTarget
import com.verto.app.feature.shipment.presentation.logisticsv2.LogisticsV2Access
import com.verto.app.ui.components.AdminGate
import com.verto.app.ui.components.PermissionGate
import com.verto.app.ui.components.PermissionGateViewModel
import com.verto.app.ui.screens.auditlog.AuditLogScreen

internal fun NavGraphBuilder.inventoryShipmentsGraph(navController: NavHostController) {
    inventoryCoreRoutes(navController)
    inventoryManagementRoutes(navController)
    inventoryClientRoutes(navController)
    logisticsEntryRoutes(navController)
    logisticsDetailRoutes(navController)
    logisticsOperationalRoutes(navController)
}

private fun NavGraphBuilder.inventoryCoreRoutes(navController: NavHostController) {
    composable(Screen.Inventory.route) {
        PermissionGate(
            check = { it.inventoryView },
            deniedMessage = "لا تملك صلاحية عرض المخزون",
            onDenied = { navController.popBackStack() },
        ) {
            InventoryScreen(
                onAddItem = { navController.navigate(Screen.AddInventoryItem.route) },
                onEditItem = { id -> navController.navigate(Screen.EditInventoryItem.create(id)) },
                onBulkPriceEdit = { navController.navigate(Screen.BulkPriceEdit.route) },
                onManageCategories = { navController.navigate(Screen.CategoryManagement.route) },
                onPriceList = { navController.navigate("price_list") },
                onLogistics = { navController.navigate(Screen.ShipmentsList.route) },
            )
        }
    }
    composable(Screen.AddInventoryItem.route) {
        PermissionGate(
            check = { it.inventoryEdit },
            deniedMessage = "لا تملك صلاحية تعديل الأصناف",
            onDenied = { navController.popBackStack() },
        ) {
            AddEditItemScreen(itemId = null, onBack = { navController.popBackStack() })
        }
    }
    composable(
        Screen.EditInventoryItem.route,
        arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
    ) { back ->
        val id = back.arguments?.getString("itemId") ?: return@composable
        PermissionGate(
            check = { it.inventoryEdit },
            deniedMessage = "لا تملك صلاحية تعديل الأصناف",
            onDenied = { navController.popBackStack() },
        ) {
            AddEditItemScreen(itemId = id, onBack = { navController.popBackStack() })
        }
    }
}

private fun NavGraphBuilder.inventoryManagementRoutes(navController: NavHostController) {
    composable("price_list") {
        PermissionGate(
            check = { it.inventoryView },
            deniedMessage = "لا تملك صلاحية عرض المخزون",
            onDenied = { navController.popBackStack() },
        ) { PriceListScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Screen.BulkPriceEdit.route) {
        PermissionGate(
            check = { it.inventoryPrice },
            deniedMessage = "لا تملك صلاحية تعديل الأسعار",
            onDenied = { navController.popBackStack() },
        ) { BulkPriceEditScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Screen.AuditLog.route) {
        AdminGate(
            deniedMessage = "سجل التدقيق متاح للمدير فقط",
            onDenied = { navController.popBackStack() },
        ) { AuditLogScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Screen.CategoryManagement.route) {
        PermissionGate(
            check = { it.inventoryEdit },
            deniedMessage = "لا تملك صلاحية تعديل الأصناف",
            onDenied = { navController.popBackStack() },
        ) { CategoryManagementScreen(onBack = { navController.popBackStack() }) }
    }
}

private fun NavGraphBuilder.inventoryClientRoutes(navController: NavHostController) {
    composable(
        "competitor_statement/{clientId}",
        arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
    ) { back ->
        val id = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() },
        ) {
            ClientStatementScreen(clientId = id, onBack = { navController.popBackStack() }, fromCompetitor = true)
        }
    }
    composable(
        Screen.Competitor.route,
        arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
    ) { back ->
        val id = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() },
        ) {
            CompetitorScreen(
                clientId = id,
                onBack = { navController.popBackStack() },
                onEditClient = { navController.navigate(Screen.EditClient.create(id, false)) },
                onAddPayment = { cId -> navController.navigate("client_payment/$cId") },
                onStatement = { cId -> navController.navigate("competitor_statement/$cId") },
                onInvoice = { invId -> navController.navigate(Screen.Invoice.create(invId)) },
            )
        }
    }
}

private fun NavGraphBuilder.logisticsEntryRoutes(navController: NavHostController) {
    composable(Screen.ShipmentsList.route) {
        logisticsPermissionRoute(
            navController = navController,
            check = { it.shipmentsView },
            deniedMessage = "لا تملك صلاحية عرض الشحنات",
            route = LogisticsRouteKind.LIST,
            shipmentId = null,
        )
    }
    composable(LOGISTICS_V2_CREATE_ROUTE) {
        logisticsPermissionRoute(
            navController = navController,
            check = { it.shipmentsManage },
            deniedMessage = "لا تملك صلاحية إنشاء شحنات لوجستية",
            route = LogisticsRouteKind.CREATE,
            shipmentId = null,
        )
    }
}

private fun NavGraphBuilder.logisticsDetailRoutes(navController: NavHostController) {
    composable(
        LOGISTICS_V2_DETAIL_ROUTE,
        arguments = listOf(navArgument("shipmentId") { type = NavType.StringType }),
    ) { back ->
        logisticsPermissionRoute(
            navController = navController,
            check = { it.shipmentsView },
            deniedMessage = "لا تملك صلاحية عرض الشحنات",
            route = LogisticsRouteKind.DETAIL,
            shipmentId = back.arguments?.getString("shipmentId") ?: return@composable,
        )
    }
    composable(
        LOGISTICS_V2_PLAN_ROUTE,
        arguments = listOf(navArgument("shipmentId") { type = NavType.StringType }),
    ) { back ->
        logisticsPermissionRoute(
            navController = navController,
            check = { it.shipmentsManage },
            deniedMessage = "تخطيط الشحنات يحتاج صلاحية الإدارة",
            route = LogisticsRouteKind.PLAN,
            shipmentId = back.arguments?.getString("shipmentId") ?: return@composable,
        )
    }
}

private fun NavGraphBuilder.logisticsOperationalRoutes(navController: NavHostController) {
    composable(
        LOGISTICS_V2_RECEIVE_ROUTE,
        arguments = listOf(navArgument("shipmentId") { type = NavType.StringType }),
    ) { back ->
        logisticsPermissionRoute(
            navController, { it.shipmentsConfirm }, "استلام الشحنات يحتاج صلاحية التأكيد",
            LogisticsRouteKind.RECEIVE, back.arguments?.getString("shipmentId") ?: return@composable,
        )
    }
    composable(
        LOGISTICS_V2_COSTS_ROUTE,
        arguments = listOf(navArgument("shipmentId") { type = NavType.StringType }),
    ) { back ->
        logisticsPermissionRoute(
            navController, { it.shipmentsView }, "لا تملك صلاحية عرض الشحنات",
            LogisticsRouteKind.COSTS, back.arguments?.getString("shipmentId") ?: return@composable,
        )
    }
    composable(
        LOGISTICS_V2_PARTNERS_ROUTE,
        arguments = listOf(navArgument("shipmentId") { type = NavType.StringType }),
    ) { back ->
        logisticsPermissionRoute(
            navController, { it.shipmentsView }, "لا تملك صلاحية عرض الشحنات",
            LogisticsRouteKind.PARTNERS, back.arguments?.getString("shipmentId") ?: return@composable,
        )
    }
}

@Composable
private fun logisticsPermissionRoute(
    navController: NavHostController,
    check: (EmployeePermissions) -> Boolean,
    deniedMessage: String,
    route: LogisticsRouteKind,
    shipmentId: String?,
) {
    PermissionGate(check = check, deniedMessage = deniedMessage, onDenied = { navController.popBackStack() }) {
        LogisticsRouteEntry(
            route = route,
            shipmentId = shipmentId,
            access = logisticsAccess(),
            onNavigate = { navigateLogisticsTarget(navController, it) },
            onBack = { navController.popBackStack() },
        )
    }
}

private const val LOGISTICS_V2_CREATE_ROUTE = "logistics_v2_create"
private const val LOGISTICS_V2_DETAIL_ROUTE = "logistics_v2_detail/{shipmentId}"
private const val LOGISTICS_V2_PLAN_ROUTE = "logistics_v2_plan/{shipmentId}"
private const val LOGISTICS_V2_RECEIVE_ROUTE = "logistics_v2_receive/{shipmentId}"
private const val LOGISTICS_V2_COSTS_ROUTE = "logistics_v2_costs/{shipmentId}"
private const val LOGISTICS_V2_PARTNERS_ROUTE = "logistics_v2_partners/{shipmentId}"

private fun String.createLogisticsRoute(shipmentId: String): String = replace("{shipmentId}", shipmentId)

@Composable
private fun logisticsAccess(): LogisticsV2Access {
    val permissionViewModel: PermissionGateViewModel = hiltViewModel()
    val permissions by permissionViewModel.permissions.collectAsStateWithLifecycle()
    return permissions.toLogisticsAccess()
}

private fun navigateLogisticsTarget(navController: NavHostController, target: LogisticsRouteTarget) {
    val (route, replaceCurrent) = when (target) {
        LogisticsRouteTarget.Create -> LOGISTICS_V2_CREATE_ROUTE to false
        is LogisticsRouteTarget.Detail -> LOGISTICS_V2_DETAIL_ROUTE.createLogisticsRoute(target.shipmentId) to target.replaceCurrent
        is LogisticsRouteTarget.Plan -> LOGISTICS_V2_PLAN_ROUTE.createLogisticsRoute(target.shipmentId) to target.replaceCurrent
        is LogisticsRouteTarget.Receive -> LOGISTICS_V2_RECEIVE_ROUTE.createLogisticsRoute(target.shipmentId) to target.replaceCurrent
        is LogisticsRouteTarget.Costs -> LOGISTICS_V2_COSTS_ROUTE.createLogisticsRoute(target.shipmentId) to target.replaceCurrent
        is LogisticsRouteTarget.Partners -> LOGISTICS_V2_PARTNERS_ROUTE.createLogisticsRoute(target.shipmentId) to target.replaceCurrent
    }
    navController.navigate(route) {
        if (replaceCurrent) {
            navController.currentBackStackEntry?.destination?.route?.let { currentRoute ->
                popUpTo(currentRoute) { inclusive = true }
            }
        }
    }
}

private fun EmployeePermissions?.toLogisticsAccess(): LogisticsV2Access {
    val current = this ?: return LogisticsV2Access.None
    return LogisticsV2Access(
        canView = current.shipmentsView,
        canManage = current.shipmentsManage,
        canConfirm = current.shipmentsConfirm,
    )
}
