package com.verto.app.ui.navigation

import com.verto.app.notifications.NotificationRoutePolicy
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.verto.app.ui.components.AdminGate
import com.verto.app.ui.components.PermissionGate
import com.verto.app.feature.reports.presentation.ReportsScreen
import com.verto.app.feature.organization.presentation.team.CreateInviteScreen
import com.verto.app.feature.organization.presentation.team.EmployeeDetailScreen
import com.verto.app.feature.organization.presentation.team.EmployeePermissionsScreen
import com.verto.app.feature.organization.presentation.team.EmployeesScreen
import com.verto.app.ui.screens.settings.InvoicePrintScreen
import com.verto.app.feature.notifications.presentation.NotificationsScreen
import com.verto.app.feature.organization.presentation.OrganizationSettingsScreen
import com.verto.app.feature.organization.presentation.OrganizationSettingsViewModel
import com.verto.app.feature.profile.presentation.ProfileSettingsViewModel
import com.verto.app.feature.settings.presentation.appearance.AppearanceSettingsViewModel
import com.verto.app.feature.settings.presentation.printing.PrintingSettingsViewModel
import com.verto.app.feature.settings.presentation.main.SettingsOperationsViewModel
import com.verto.app.ui.screens.settings.SettingsScreen
import com.verto.app.feature.organization.presentation.team.OrganizationTeamViewModel
import com.verto.app.feature.settings.presentation.ThemesScreen
import com.verto.app.feature.settings.presentation.ThemesLanding
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.feature.integration.optimal.presentation.OptimalCompaniesScreen
import com.verto.app.feature.integration.optimal.presentation.OptimalCompanyDetailsScreen
import com.verto.app.feature.integration.optimal.presentation.OptimalHomeScreen
import com.verto.app.feature.integration.optimal.presentation.CompanyInvoicesScreen
import com.verto.app.feature.integration.optimal.presentation.MaintenanceRecordsScreen
import com.verto.app.feature.integration.optimal.presentation.MaintenanceDetailsScreen
import com.verto.app.feature.integration.optimal.presentation.OptimalConversationsScreen
import com.verto.app.feature.integration.optimal.presentation.OptimalChatScreen
import com.verto.app.feature.integration.optimal.presentation.SyncIssuesScreen
import com.verto.app.feature.integration.optimal.presentation.OptimalCodesScreen
import com.verto.app.ui.integration.optimal.OptimalCompanyInvoiceRouteScreen
import com.verto.app.feature.management.navigation.BenzineNavigation
import com.verto.app.feature.management.presentation.BenzineManagementScreen
import com.verto.app.feature.management.presentation.ManagementScreen
import com.verto.app.feature.dashboard.presentation.education.EducationalTopicsRoute
import com.verto.app.feature.dashboard.presentation.observation.TeamObservationsRoute

internal const val SETTINGS_GRAPH = "settings_graph"

internal fun NavGraphBuilder.reportsGraph(navController: NavHostController) {

    composable(Screen.Reports.route) {
        PermissionGate(
            check = { it.reportsSummary },
            deniedMessage = "لا تملك صلاحية عرض التقارير"
        ) {
            ReportsScreen(
                onBack        = { navController.popBackStack() },
                onClientClick = { clientId -> navController.navigate(Screen.ClientDashboard.create(clientId)) }
            )
        }
    }
}

internal fun NavGraphBuilder.settingsGraph(navController: NavHostController) {

    // ── الإعدادات — Nested Graph ───────────────────────────
    navigation(
        startDestination = Screen.Settings.route,
        route            = SETTINGS_GRAPH
    ) {
        composable(Screen.Settings.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SETTINGS_GRAPH)
            }
            val vm: SettingsOperationsViewModel = hiltViewModel(parentEntry)
            val profileVm: ProfileSettingsViewModel = hiltViewModel(parentEntry)
            SettingsScreen(
                vm                      = vm,
                profileVm               = profileVm,
                onNavigateThemes        = { navController.navigate(Screen.SettingsThemes.route) },
                onNavigateNotifications = { navController.navigate(Screen.SettingsNotifications.route) },
                onNavigateInvoicePrint  = { navController.navigate(Screen.SettingsInvoicePrint.route) },
                onNavigateEmployees     = { navController.navigate(Screen.SettingsEmployees.route) },
                onNavigateAuditLog      = { navController.navigate(Screen.AuditLog.route) },
                onNavigateOrgSettings   = { navController.navigate(Screen.SettingsOrgSettings.route) },
                onNavigateManagement    = { navController.navigate(Screen.SettingsManagement.route) },
                onNavigateEducationalTopics = { navController.navigate(Screen.SettingsEducationalTopics.route) },
                onLogout                = {
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Screen.SettingsProfile.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SETTINGS_GRAPH)
            }
            val vm: SettingsOperationsViewModel = hiltViewModel(parentEntry)
            val profileVm: ProfileSettingsViewModel = hiltViewModel(parentEntry)
            SettingsScreen(
                vm = vm,
                profileVm = profileVm,
                openProfileOnLaunch = true,
                onNavigateThemes = { navController.navigate(Screen.SettingsThemes.route) },
                onNavigateNotifications = { navController.navigate(Screen.SettingsNotifications.route) },
                onNavigateInvoicePrint = { navController.navigate(Screen.SettingsInvoicePrint.route) },
                onNavigateEmployees = { navController.navigate(Screen.SettingsEmployees.route) },
                onNavigateAuditLog = { navController.navigate(Screen.AuditLog.route) },
                onNavigateOrgSettings = { navController.navigate(Screen.SettingsOrgSettings.route) },
                onNavigateManagement = { navController.navigate(Screen.SettingsManagement.route) },
                onNavigateEducationalTopics = { navController.navigate(Screen.SettingsEducationalTopics.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Screen.SettingsManagement.route) {
            PermissionGate(
                check = { it.viewManagement },
                deniedMessage = "لا تملك صلاحية عرض الإدارة",
                onDenied = { navController.popBackStack() },
            ) {
                ManagementScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route ->
                        navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(BenzineNavigation.ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.commissionManage },
                deniedMessage = "لا تملك صلاحية إدارة AutoDrive",
                onDenied = { navController.popBackStack() },
            ) {
                BenzineManagementScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCommissions = { navController.navigate(Screen.CommissionManagement.create()) },
                    onOpenMarketers = { navController.navigate(Screen.UsersDashboard.route) },
                )
            }
        }

        composable(OptimalNavigation.ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal },
                deniedMessage = "لا تملك صلاحية عرض Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalHomeScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route ->
                        navController.navigate(route) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(OptimalNavigation.COMPANIES_ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalCompanies },
                deniedMessage = "لا تملك صلاحية عرض شركات Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalCompaniesScreen(
                    onBack = { navController.popBackStack() },
                    onCompanyClick = { clientId ->
                        navController.navigate(OptimalNavigation.companyDetailsRoute(clientId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable(
            route = OptimalNavigation.COMPANY_DETAILS_ROUTE,
            arguments = listOf(
                navArgument(OptimalNavigation.COMPANY_ID_ARG) { type = NavType.StringType },
            ),
        ) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalCompanies },
                deniedMessage = "لا تملك صلاحية عرض تفاصيل شركة Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalCompanyDetailsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(OptimalNavigation.MESSAGES_ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalMessages },
                deniedMessage = "لا تملك صلاحية عرض رسائل Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalConversationsScreen(
                    onBack = { navController.popBackStack() },
                    onConversationClick = { clientId ->
                        navController.navigate(OptimalNavigation.chatRoute(clientId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable(
            route = OptimalNavigation.CHAT_ROUTE,
            arguments = listOf(
                navArgument(OptimalNavigation.CHAT_CLIENT_ID_ARG) { type = NavType.StringType },
            ),
        ) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalMessages },
                deniedMessage = "لا تملك صلاحية عرض رسائل Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalChatScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(OptimalNavigation.INVOICES_ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalInvoices },
                deniedMessage = "لا تملك صلاحية عرض فواتير Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                CompanyInvoicesScreen(
                    onBack = { navController.popBackStack() },
                    onInvoiceClick = { invoice ->
                        navController.navigate(
                            OptimalNavigation.companyInvoiceRoute(
                                organizationId = invoice.organizationId,
                                invoiceId = invoice.invoiceId,
                            ),
                        ) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(
            route = OptimalNavigation.COMPANY_INVOICE_ROUTE,
            arguments = listOf(
                navArgument(OptimalNavigation.COMPANY_INVOICE_ORGANIZATION_ID_ARG) {
                    type = NavType.StringType
                },
                navArgument(OptimalNavigation.COMPANY_INVOICE_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalInvoices },
                deniedMessage = "لا تملك صلاحية فتح فواتير Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalCompanyInvoiceRouteScreen(
                    onBack = { navController.popBackStack() },
                    onAddPayment = { invoiceId, clientId ->
                        navController.navigate(Screen.AddPayment.create(invoiceId, clientId))
                    },
                    onPayFull = { invoiceId, clientId ->
                        navController.navigate(Screen.AddPayment.create(invoiceId, clientId, full = true))
                    },
                    onEditInvoice = { invoiceId, clientId ->
                        navController.navigate(Screen.EditInvoice.create(clientId, invoiceId))
                    },
                    onDeleteSuccess = { navController.popBackStack() },
                )
            }
        }

        composable(OptimalNavigation.MAINTENANCE_ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalMaintenance },
                deniedMessage = "لا تملك صلاحية عرض صيانة Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                MaintenanceRecordsScreen(
                    onBack = { navController.popBackStack() },
                    onRecordClick = { recordId ->
                        navController.navigate(OptimalNavigation.maintenanceDetailsRoute(recordId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable(
            route = OptimalNavigation.MAINTENANCE_DETAILS_ROUTE,
            arguments = listOf(
                navArgument(OptimalNavigation.MAINTENANCE_RECORD_ID_ARG) { type = NavType.StringType },
            ),
        ) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalMaintenance },
                deniedMessage = "لا تملك صلاحية عرض تفاصيل صيانة Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                MaintenanceDetailsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(OptimalNavigation.CODES_ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.issueOptimalCode },
                deniedMessage = "لا تملك صلاحية إصدار أكواد Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                OptimalCodesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(OptimalNavigation.SYNC_ISSUES_ROUTE) {
            PermissionGate(
                check = { it.viewManagement && it.viewOptimal && it.viewOptimalSyncIssues },
                deniedMessage = "لا تملك صلاحية عرض أخطاء مزامنة Optimal",
                onDenied = { navController.popBackStack() },
            ) {
                SyncIssuesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Screen.SettingsThemes.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
            val vm: AppearanceSettingsViewModel = hiltViewModel(parentEntry)
            ThemesScreen(onBack = { navController.popBackStack() }, vm = vm)
        }
        composable(Screen.SettingsAppearance.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
            val vm: AppearanceSettingsViewModel = hiltViewModel(parentEntry)
            ThemesScreen(onBack = { navController.popBackStack() }, vm = vm, landing = ThemesLanding.APPEARANCE)
        }

        composable(Screen.SettingsThemeStyle.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
            val vm: AppearanceSettingsViewModel = hiltViewModel(parentEntry)
            ThemesScreen(onBack = { navController.popBackStack() }, vm = vm, landing = ThemesLanding.STYLE)
        }

        composable(Screen.SettingsNotifications.route) { backStackEntry ->
            NotificationsScreen(
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

        composable(Screen.SettingsInvoicePrint.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
            val vm: PrintingSettingsViewModel = hiltViewModel(parentEntry)
            val profileVm: ProfileSettingsViewModel = hiltViewModel(parentEntry)
            val organizationVm: OrganizationSettingsViewModel = hiltViewModel(parentEntry)
            InvoicePrintScreen(
                onBack = { navController.popBackStack() },
                vm = vm,
                profileVm = profileVm,
                organizationVm = organizationVm
            )
        }

        composable(Screen.SettingsOrgSettings.route) { backStackEntry ->
            AdminGate(
                deniedMessage = "إعدادات المؤسسة متاحة للمدير فقط",
                onDenied = { navController.popBackStack() }
            ) {
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
                val organizationVm: OrganizationSettingsViewModel = hiltViewModel(parentEntry)
                OrganizationSettingsScreen(
                    onBack = { navController.popBackStack() },
                    vm = organizationVm
                )
            }
        }


        composable(Screen.SettingsEducationalTopics.route) {
            AdminGate(
                deniedMessage = "إدارة المواضيع التعليمية متاحة للمدير فقط",
                onDenied = { navController.popBackStack() },
            ) {
                EducationalTopicsRoute(onBack = { navController.popBackStack() })
            }
        }

        composable(Screen.TeamObservations.route) {
            AdminGate(
                deniedMessage = "ملاحظات الفريق متاحة للمدير فقط",
                onDenied = { navController.popBackStack() },
            ) {
                TeamObservationsRoute(onBack = { navController.popBackStack() })
            }
        }

        // ── شاشات إدارة الموظفين (أدمن حصراً) ────────────────
        composable(Screen.SettingsEmployees.route) { backStackEntry ->
            AdminGate(
                deniedMessage = "إدارة الموظفين متاحة للمدير فقط",
                onDenied = { navController.popBackStack() }
            ) {
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
                val vm: OrganizationTeamViewModel = hiltViewModel(parentEntry)
                EmployeesScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToEmployeeDetail = { userId ->
                        navController.navigate(Screen.SettingsEmployeeDetail.create(userId))
                    },
                    onNavigateToCreateInvite = {
                        navController.navigate(Screen.SettingsCreateInvite.route)
                    },
                    vm = vm
                )
            }
        }

        composable(
            Screen.SettingsEmployeeDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            AdminGate(
                deniedMessage = "إدارة الموظفين متاحة للمدير فقط",
                onDenied = { navController.popBackStack() }
            ) {
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
                val vm: OrganizationTeamViewModel = hiltViewModel(parentEntry)
                val userId = backStackEntry.arguments?.getString("userId").orEmpty()
                EmployeeDetailScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    onManagePermissions = {
                        navController.navigate(Screen.SettingsEmployeePermissions.create(userId))
                    },
                    teamVm = vm
                )
            }
        }

        composable(
            Screen.SettingsEmployeePermissions.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            AdminGate(
                deniedMessage = "إدارة صلاحيات الموظفين متاحة للمدير فقط",
                onDenied = { navController.popBackStack() }
            ) {
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
                val vm: OrganizationTeamViewModel = hiltViewModel(parentEntry)
                val userId = backStackEntry.arguments?.getString("userId").orEmpty()
                android.util.Log.d("EmployeePermissions", "Employee permissions navigation received")
                EmployeePermissionsScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() },
                    vm     = vm
                )
            }
        }

        composable(Screen.SettingsCreateInvite.route) { backStackEntry ->
            AdminGate(
                deniedMessage = "إنشاء رموز الدعوة متاح للمدير فقط",
                onDenied = { navController.popBackStack() }
            ) {
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SETTINGS_GRAPH) }
                val vm: OrganizationTeamViewModel = hiltViewModel(parentEntry)
                CreateInviteScreen(
                    onBack = { navController.popBackStack() },
                    vm     = vm
                )
            }
        }
    }
}
