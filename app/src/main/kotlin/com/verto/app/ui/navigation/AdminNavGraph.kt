package com.verto.app.ui.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.verto.app.ui.components.PermissionGate
import com.verto.app.ui.screens.messages.ChatDetailScreen
import com.verto.app.ui.screens.messages.MessagesScreen
import com.verto.app.ui.screens.usersdashboard.UserDashboardDetailScreen
import com.verto.app.ui.screens.usersdashboard.UsersDashboardScreen

internal fun NavGraphBuilder.adminGraph(navController: NavHostController) {

    // ── لوحة تحكم المستخدمين (المرحلة 4.4) ──────────────────
    composable(Screen.UsersDashboard.route) {
        PermissionGate(
            check = { it.marketingDashboards },
            deniedMessage = "لا تملك صلاحية عرض لوحات المسوّقين",
            onDenied = { navController.popBackStack() }
        ) {
            val context = LocalContext.current
            UsersDashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenUser = { id -> navController.navigate(Screen.UserDashboardDetail.create(id)) },
                onOpenLeaderboard = { navController.navigate(Screen.Leaderboard.route) },
                onCall = { phone -> BenzineContactActions.dial(context, phone) },
                onWhatsApp = { phone -> BenzineContactActions.openWhatsApp(context, phone) },
            )
        }
    }

    // ── لوحة الصدارة الموحّدة (المرحلة 5.4) ──────────────────
    composable(Screen.Leaderboard.route) {
        PermissionGate(
            check = { it.marketingDashboards },
            deniedMessage = "لا تملك صلاحية عرض لوحات المسوّقين",
            onDenied = { navController.popBackStack() }
        ) {
            com.verto.app.ui.screens.leaderboard.LeaderboardScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }

    composable(
        Screen.UserDashboardDetail.route,
        arguments = listOf(navArgument("clientId") { type = NavType.StringType })
    ) { back ->
        val cId = back.arguments?.getString("clientId") ?: return@composable
        PermissionGate(
            check = { it.marketingDashboards },
            deniedMessage = "لا تملك صلاحية عرض لوحات المسوّقين",
            onDenied = { navController.popBackStack() }
        ) {
            UserDashboardDetailScreen(
                clientId = cId,
                onBack = { navController.popBackStack() },
                onOpenChat = { convId, clientId, clientName ->
                    navController.navigate(Screen.ChatDetail.create(convId, clientId, clientName))
                },
                onOpenReport = { id, from, to ->
                    navController.navigate(Screen.MarketerCommissionReport.create(id, from, to))
                },
                onOpenInvoice = { invId -> navController.navigate(Screen.Invoice.create(invId)) }
            )
        }
    }

    // ── تقرير عمولات مسوّق (معاينة الأدمن) ────────────────────
    composable(
        Screen.MarketerCommissionReport.route,
        arguments = listOf(
            navArgument("clientId") { type = NavType.StringType },
            navArgument("from") { type = NavType.LongType; defaultValue = -1L },
            navArgument("to")   { type = NavType.LongType; defaultValue = -1L }
        )
    ) { back ->
        val cId = back.arguments?.getString("clientId") ?: return@composable
        val from = back.arguments?.getLong("from")?.takeIf { it > 0 }
        val to   = back.arguments?.getLong("to")?.takeIf { it > 0 }
        PermissionGate(
            check = { it.commissionManage },
            deniedMessage = "لا تملك صلاحية إدارة العمولات",
            onDenied = { navController.popBackStack() }
        ) {
            com.verto.app.ui.screens.commissionreport.MarketerCommissionReportScreen(
                clientId = cId,
                from = from,
                to = to,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // ── شاشات المحادثات ────────────────────────────────────
    composable(Screen.Messages.route) {
        PermissionGate(
            check = { it.marketingDashboards },
            deniedMessage = "لا تملك صلاحية عرض لوحات المسوّقين",
            onDenied = { navController.popBackStack() }
        ) {
            MessagesScreen(
                onBack     = { navController.popBackStack() },
                onOpenChat = { convId, clientId, clientName ->
                    navController.navigate(Screen.ChatDetail.create(convId, clientId, clientName))
                }
            )
        }
    }

    composable(
        Screen.ChatDetail.route,
        arguments = listOf(
            navArgument("conversationId") { type = NavType.StringType },
            navArgument("clientId")       { type = NavType.StringType },
            navArgument("clientName")     { type = NavType.StringType }
        )
    ) { back ->
        val convId     = back.arguments?.getString("conversationId") ?: return@composable
        val clientId   = back.arguments?.getString("clientId")       ?: return@composable
        val clientName = back.arguments?.getString("clientName")     ?: ""
        PermissionGate(
            check = { it.marketingDashboards },
            deniedMessage = "لا تملك صلاحية عرض لوحات المسوّقين",
            onDenied = { navController.popBackStack() }
        ) {
            ChatDetailScreen(
                conversationId = convId,
                clientId       = clientId,
                clientName     = clientName,
                onBack         = { navController.popBackStack() }
            )
        }
    }
}
