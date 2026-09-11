package com.verto.app.ui.navigation

import com.verto.app.notifications.NotificationRoutePolicy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.verto.app.feature.invoice.presentation.activeinvoices.InvoicesByCategoryScreen
import com.verto.app.ui.components.PermissionGate
import com.verto.app.feature.party.presentation.addclient.AddEditClientScreen
import com.verto.app.feature.payment.presentation.invoiceeditor.InvoiceEditorScreen
import com.verto.app.feature.party.presentation.client.ClientDashboardScreen
import com.verto.app.feature.party.presentation.client.ClientPaymentScreen
import com.verto.app.feature.party.presentation.client.ClientsListScreen
import com.verto.app.feature.party.presentation.client.ClientStatementScreen
import com.verto.app.ui.screens.home.HomeScreen
import com.verto.app.ui.screens.home.PendingActionsScreen
import com.verto.app.ui.screens.home.search.HomeSearchRoute
import com.verto.app.ui.navigation.search.HomeSearchDestinationResolver
import com.verto.app.feature.invoice.presentation.invoice.InvoiceScreen
import com.verto.app.feature.party.presentation.supplier.SupplierDashboardScreen
import com.verto.app.feature.party.presentation.supplier.SupplierPaymentScreen
import com.verto.app.feature.party.presentation.supplier.SupplierStatementScreen
import com.verto.app.feature.party.domain.model.SupplierScope

internal fun NavGraphBuilder.homeClientsGraph(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
) {
    val quickActionResolver = QuickActionDestinationResolver()
    val homeDestinationResolver = HomeSearchDestinationResolver()

    // ── Home ───────────────────────────────────────────────
    composable(Screen.Home.route) {
        HomeScreen(
            onNewInvoice = { navController.navigate(Screen.NewInvoice.route) },
            onNewPurchase = { navController.navigate(Screen.NewPurchase.route) },
            onNewInternationalInvoice = { currency, rate ->
                navController.navigate(Screen.InternationalPurchase.create(currency, rate))
            },
            onNotifications = { navController.navigate("notifications_standalone") },
            onSearch = { navController.navigate(Screen.HomeSearch.route) },
            onOpenDrawer = onOpenDrawer,
            onOpenPendingActions = { navController.navigate(Screen.HomePendingActions.route) },
            onOpenHomeDestination = { destination ->
                val route = quickActionResolver.resolve(destination)
                    ?: homeDestinationResolver.resolve(destination)
                route?.let { navController.navigate(it) { launchSingleTop = true } }
            },
        )
    }

    composable(Screen.HomePendingActions.route) {
        PendingActionsScreen(
            onBack = { navController.popBackStack() },
            onOpenHomeDestination = { destination ->
                val route = quickActionResolver.resolve(destination)
                    ?: homeDestinationResolver.resolve(destination)
                route?.let { navController.navigate(it) { launchSingleTop = true } }
            },
        )
    }

    composable(Screen.HomeSearch.route) {
        val resolver = HomeSearchDestinationResolver()
        HomeSearchRoute(
            onBack = { navController.popBackStack() },
            onOpenDestination = { destination ->
                resolver.resolve(destination)?.let { route ->
                    navController.navigate(route) { launchSingleTop = true }
                }
            },
        )
    }

    composable(Screen.Notifications.route) { backStackEntry ->
        com.verto.app.feature.notifications.presentation.NotificationsScreen(
            onBack = { navController.popBackStack() },
            onNotificationClick = { notification ->
                NotificationRoutePolicy.resolve(
                    type = notification.type,
                    requestedRoute = notification.navigationRoute,
                    relatedEntityId = notification.relatedEntityId,
                    relatedEntityType = notification.relatedEntityType,
                ).let { route -> navController.navigate(route) { launchSingleTop = true } }
            }
        )
    }
// غيّر suppliers_list
    composable(Screen.SuppliersList.route) {
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            ClientsListScreen(
                showSuppliers     = true,
                onBack            = { navController.popBackStack() },
                onClientClick     = { id -> navController.navigate("supplier_dashboard/$id") },
                onCompetitorClick = { id -> navController.navigate(Screen.Competitor.create(id)) },
                onAddClient       = { navController.navigate(Screen.AddSupplier.route) }
            )
        }
    }

    composable(
        Screen.SuppliersByScope.route,
        arguments = listOf(navArgument("supplierScope") { type = NavType.StringType })
    ) { back ->
        val rawScope = back.arguments?.getString("supplierScope")?.uppercase().orEmpty()
        val scope = runCatching { SupplierScope.valueOf(rawScope) }.getOrNull()
            ?.takeIf { it == SupplierScope.LOCAL || it == SupplierScope.INTERNATIONAL }
        if (scope == null) {
            navController.popBackStack()
            return@composable
        }
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            ClientsListScreen(
                showSuppliers = true,
                supplierScope = scope,
                onBack = { navController.popBackStack() },
                onClientClick = { id -> navController.navigate(Screen.SupplierDashboard.create(id)) },
                onCompetitorClick = { id -> navController.navigate(Screen.Competitor.create(id)) },
                onAddClient = { navController.navigate(Screen.AddSupplier.route) }
            )
        }
    }

// أضف composable جديد للمورد
    composable(
        "supplier_dashboard/{supplierId}",
        arguments = listOf(navArgument("supplierId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("supplierId") ?: return@composable
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            SupplierDashboardScreen(
                supplierId    = id,
                onBack        = { navController.popBackStack() },
                onInvoice     = { invId -> navController.navigate(Screen.Invoice.create(invId)) },
                onEditSupplier = { navController.navigate(Screen.EditClient.create(id, true)) },
                onAddPayment = { sId -> navController.navigate("supplier_payment/$sId") },
                onStatement   = { sId -> navController.navigate("statement/$sId") }
            )
        }
    }
    composable(
        "statement/{supplierId}",
        arguments = listOf(navArgument("supplierId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("supplierId") ?: return@composable
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            SupplierStatementScreen(
                supplierId             = id,
                onBack                 = { navController.popBackStack() },
                onNavigateToCompetitor = { cId ->
                    navController.navigate(Screen.Competitor.create(cId)) {
                        popUpTo("statement/$id") { inclusive = true }
                    }
                }
            )
        }
    }
    composable(
        "supplier_payment/{supplierId}",
        arguments = listOf(navArgument("supplierId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("supplierId") ?: return@composable
        PermissionGate(
            check = { it.suppliersAddPayment },
            deniedMessage = "لا تملك صلاحية تسجيل دفعة لمورد",
            onDenied = { navController.popBackStack() }
        ) {
            SupplierPaymentScreen(
                supplierId = id,
                onBack     = { navController.popBackStack() }
            )
        }
    }
    // ── فواتير مبيعات/مشتريات ──────────────────────────────
    composable(
        Screen.PurchaseInvoicesByScope.route,
        arguments = listOf(navArgument("purchaseScope") { type = NavType.StringType })
    ) { back ->
        val raw = back.arguments?.getString("purchaseScope")?.uppercase().orEmpty()
        val scope = raw.takeIf { it == "LOCAL" || it == "INTERNATIONAL" }
        if (scope == null) {
            navController.popBackStack()
            return@composable
        }
        PermissionGate(
            check = { it.purchasesView },
            deniedMessage = "لا تملك صلاحية عرض فواتير الشراء",
            onDenied = { navController.popBackStack() }
        ) {
            InvoicesByCategoryScreen(
                category = "purchases",
                purchaseScope = scope,
                onBack = { navController.popBackStack() },
                onInvoice = { id -> navController.navigate(Screen.Invoice.create(id)) }
            )
        }
    }

    composable(
        Screen.InvoicesByCategory.route,
        arguments = listOf(navArgument("category") { type = NavType.StringType })
    ) { back ->
        val cat = back.arguments?.getString("category") ?: "sales"
        PermissionGate(
            check = {
                if (cat == "purchases") it.purchasesView else it.salesView
            },
            deniedMessage = if (cat == "purchases") {
                "لا تملك صلاحية عرض فواتير الشراء"
            } else {
                "لا تملك صلاحية عرض فواتير البيع"
            },
            onDenied = { navController.popBackStack() }
        ) {
            InvoicesByCategoryScreen(
                category  = cat,
                onBack    = { navController.popBackStack() },
                onInvoice = { id -> navController.navigate(Screen.Invoice.create(id)) }
            )
        }
    }

    // ── قائمة العملاء ──────────────────────────────────────
    composable(Screen.ClientsList.route) {
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            ClientsListScreen(
                showSuppliers     = false,
                onBack            = { navController.popBackStack() },
                onClientClick     = { id -> navController.navigate(Screen.ClientDashboard.create(id)) },
                onCompetitorClick = { id -> navController.navigate(Screen.Competitor.create(id)) },
                onAddClient       = { navController.navigate(Screen.AddClient.route) }
            )
        }
    }



    composable(Screen.AddClient.route) {
        PermissionGate(
            check = { it.clientsEdit },
            deniedMessage = "لا تملك صلاحية تعديل بيانات العملاء",
            onDenied = { navController.popBackStack() }
        ) {
            AddEditClientScreen(clientId = null, isSupplier = false, onBack = { navController.popBackStack() })
        }
    }

    composable(Screen.AddSupplier.route) {
        PermissionGate(
            check = { it.clientsEdit },
            deniedMessage = "لا تملك صلاحية تعديل بيانات العملاء",
            onDenied = { navController.popBackStack() }
        ) {
            AddEditClientScreen(clientId = null, isSupplier = true, onBack = { navController.popBackStack() })
        }
    }

    composable(
        Screen.ClientDashboard.route,
        arguments = listOf(navArgument("clientId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            ClientDashboardScreen(
                clientId     = id,
                onBack       = { navController.popBackStack() },
                onInvoice    = { invId -> navController.navigate(Screen.Invoice.create(invId)) },
                onEditClient = { navController.navigate(Screen.EditClient.create(id, false)) },
                onAddPayment = { cId -> navController.navigate("client_payment/$cId") },
                onStatement  = { cId -> navController.navigate("client_statement/$cId") }
            )
        }
    }
    composable(
        "client_statement/{clientId}",
        arguments = listOf(navArgument("clientId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.clientsView },
            deniedMessage = "لا تملك صلاحية عرض العملاء والموردين",
            onDenied = { navController.popBackStack() }
        ) {
            ClientStatementScreen(
                clientId               = id,
                onBack                 = { navController.popBackStack() },
                onNavigateToCompetitor = { cId ->
                    navController.navigate(Screen.Competitor.create(cId)) {
                        popUpTo("client_statement/$id") { inclusive = true }
                    }
                }
            )
        }
    }
    composable(
        "client_payment/{clientId}",
        arguments = listOf(navArgument("clientId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.clientsAddPayment },
            deniedMessage = "لا تملك صلاحية إضافة دفعة",
            onDenied = { navController.popBackStack() }
        ) {
            ClientPaymentScreen(
                clientId = id,
                onBack   = { navController.popBackStack() }
            )
        }
    }


    composable(
        Screen.EditClient.route,
        arguments = listOf(
            navArgument("clientId")  { type = NavType.StringType },
            navArgument("isSupplier") { type = NavType.BoolType; defaultValue = false }
        )
    ) { back ->
        val id         = back.arguments?.getString("clientId")    ?: return@composable
        val isSupplier = back.arguments?.getBoolean("isSupplier") ?: false
        PermissionGate(
            check = { it.clientsEdit },
            deniedMessage = "لا تملك صلاحية تعديل بيانات العملاء",
            onDenied = { navController.popBackStack() }
        ) {
            AddEditClientScreen(clientId = id, isSupplier = isSupplier, onBack = { navController.popBackStack() })
        }
    }

    composable(
        Screen.Invoice.route,
        arguments = listOf(
            navArgument("invoiceId") { type = NavType.StringType },
            navArgument("openCommission") {
                type = NavType.BoolType; defaultValue = false
            }
        )
    ) { back ->
        val id = back.arguments?.getString("invoiceId") ?: return@composable
        val openCommission = back.arguments?.getBoolean("openCommission") ?: false
        InvoiceScreen(
            invoiceId        = id,
            openCommission   = openCommission,
            onBack           = { navController.popBackStack() },
            onAddPayment     = { invId, cId -> navController.navigate(Screen.AddPayment.create(invId, cId)) },
            onPayFull        = { invId, cId -> navController.navigate(Screen.AddPayment.create(invId, cId, full = true)) },
            onEditInvoice    = { invId, cId -> navController.navigate(Screen.EditInvoice.create(cId, invId)) },
            onDeleteSuccess  = { navController.popBackStack() }
        )
    }

    composable(
        Screen.NewInvoiceForClient.route,
        arguments = listOf(navArgument("clientId") { type = NavType.StringType })
    ) { back ->
        val id = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.salesCreate },
            deniedMessage = "لا تملك صلاحية إنشاء فاتورة بيع",
            onDenied = { navController.popBackStack() }
        ) {
            InvoiceEditorScreen(
                clientId  = id,
                invoiceId = null,
                onBack    = { navController.popBackStack() },
                onSaved   = { invId ->
                    navController.navigate(Screen.Invoice.create(invId)) {
                        popUpTo(Screen.NewInvoiceForClient.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
