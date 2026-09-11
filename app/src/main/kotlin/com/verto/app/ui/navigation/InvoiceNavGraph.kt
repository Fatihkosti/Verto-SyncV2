package com.verto.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.verto.app.ui.components.PermissionGate
import com.verto.app.feature.payment.presentation.invoiceeditor.InvoiceEditorScreen
import com.verto.app.ui.screens.commission.CommissionManagementScreen
import com.verto.app.pdf.generateMarketerCommissionPdf as createMarketerCommissionPdf
import com.verto.app.ui.screens.expenses.ExpensesScreen
import com.verto.app.feature.payment.presentation.payment.AddPaymentScreen

internal fun NavGraphBuilder.invoiceGraph(navController: NavHostController) {

    composable(Screen.NewInvoice.route) {
        PermissionGate(
            check = { it.salesCreate },
            deniedMessage = "لا تملك صلاحية إنشاء فاتورة بيع",
            onDenied = { navController.popBackStack() }
        ) {
            InvoiceEditorScreen(
                clientId  = "",
                invoiceId = null,
                onBack    = { navController.popBackStack() },
                onSaved   = { id ->
                    navController.navigate(Screen.Invoice.create(id)) {
                        popUpTo(Screen.NewInvoice.route) { inclusive = true }
                    }
                }
            )
        }
    }

    composable(Screen.NewPurchase.route) {
        PermissionGate(
            check = { it.purchasesCreate },
            deniedMessage = "لا تملك صلاحية إنشاء فاتورة شراء",
            onDenied = { navController.popBackStack() }
        ) {
            InvoiceEditorScreen(
                clientId = "",
                invoiceId = null,
                initialIsSale = false,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(Screen.Invoice.create(id)) {
                        popUpTo(Screen.NewPurchase.route) { inclusive = true }
                    }
                }
            )
        }
    }

    composable(
        Screen.InternationalPurchase.route,
        arguments = listOf(
            navArgument("currency") { type = NavType.StringType },
            navArgument("rate")     { type = NavType.StringType }
        )
    ) { back ->
        val currency = back.arguments?.getString("currency") ?: "USD"
        val rate     = back.arguments?.getString("rate")?.toDoubleOrNull() ?: 1.0
        PermissionGate(
            check = { it.purchasesCreate },
            deniedMessage = "لا تملك صلاحية إنشاء فاتورة شراء",
            onDenied = { navController.popBackStack() }
        ) {
            InvoiceEditorScreen(
                clientId             = "",
                invoiceId            = null,
                isInternational      = true,
                foreignCurrencyName  = currency,
                exchangeRate         = rate,
                onBack               = { navController.popBackStack() },
                onSaved              = { id ->
                    navController.navigate(Screen.Invoice.create(id)) {
                        popUpTo(Screen.InternationalPurchase.route) { inclusive = true }
                    }
                }
            )
        }
    }

    composable(
        Screen.EditInvoice.route,
        arguments = listOf(
            navArgument("clientId")  { type = NavType.StringType },
            navArgument("invoiceId") { type = NavType.StringType }
        )
    ) { back ->
        val cId   = back.arguments?.getString("clientId")  ?: return@composable
        val invId = back.arguments?.getString("invoiceId") ?: return@composable
        InvoiceEditorScreen(
            clientId  = cId,
            invoiceId = invId,
            onBack    = { navController.popBackStack() },
            onSaved   = { savedId ->
                navController.navigate(Screen.Invoice.create(savedId)) {
                    popUpTo(Screen.EditInvoice.route) { inclusive = true }
                }
            }
        )
    }

    composable(
        Screen.AddPayment.route,
        arguments = listOf(
            navArgument("invoiceId") { type = NavType.StringType },
            navArgument("clientId")  { type = NavType.StringType },
            navArgument("full") { type = NavType.BoolType; defaultValue = false }
        )
    ) { back ->
        val invId = back.arguments?.getString("invoiceId") ?: return@composable
        val cId   = back.arguments?.getString("clientId")  ?: return@composable
        val full  = back.arguments?.getBoolean("full") ?: false
        PermissionGate(
            check = { it.clientsAddPayment || it.suppliersAddPayment },
            deniedMessage = "لا تملك صلاحية إضافة دفعة",
            onDenied = { navController.popBackStack() }
        ) {
            AddPaymentScreen(
                invoiceId = invId,
                clientId = cId,
                prefillFullPayment = full,
                onBack = { navController.popBackStack() },
            )
        }
    }

    composable(Screen.Expenses.route) {
        PermissionGate(
            check = { it.expensesView },
            deniedMessage = "لا تملك صلاحية عرض المصاريف"
        ) {
            ExpensesScreen(
                onBack       = { navController.popBackStack() },
                onCommission = { navController.navigate(Screen.CommissionManagement.create()) }
            )
        }
    }

    composable(
        Screen.CommissionManagement.route,
        arguments = listOf(navArgument("withdrawClientId") {
            type = NavType.StringType; nullable = true; defaultValue = null
        })
    ) { back ->
        val withdrawClientId = back.arguments?.getString("withdrawClientId")
        PermissionGate(
            check = { it.commissionManage },
            deniedMessage = "لا تملك صلاحية إدارة العمولات",
            onDenied = { navController.popBackStack() }
        ) {
        CommissionManagementScreen(
            withdrawClientId = withdrawClientId,
            onBack = {
                // عند الفتح من إشعار (deep link) قد تكون المكدّسة فارغة فلا يفعل
                // popBackStack شيئاً — نرجع للرئيسية كحل احتياطي مضمون.
                if (!navController.popBackStack()) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
            onNavigateToInvoice = { id -> navController.navigate(Screen.Invoice.create(id)) },
            onNavigateToClientDashboard = { id -> navController.navigate(Screen.ClientDashboard.create(id)) },
            onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
            onNavigateToUsersDashboard = { navController.navigate(Screen.UsersDashboard.route) },
            generateMarketerCommissionPdf = { report ->
                createMarketerCommissionPdf(navController.context, report)
            }
        )
        }
    }
}
