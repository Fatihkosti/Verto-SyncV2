package com.verto.app.ui.navigation.search

import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.feature.management.navigation.BenzineNavigation
import com.verto.app.feature.management.navigation.ManagementNavigation
import com.verto.app.ui.navigation.Screen
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomePermissionKeys

/** Local, immutable catalog owned by the app shell. */
enum class SearchCatalogSection(val label: String) {
    RECORDS("السجلات"),
    SPECIALIZED_OPERATIONS("العمليات المتخصصة"),
    MANAGEMENT("الإدارة"),
    OVERSIGHT("الرقابة"),
    SYSTEM("النظام"),
}

data class SearchableScreenDefinition(
    val id: String,
    val name: String,
    val section: SearchCatalogSection,
    val keywords: Set<String>,
    val route: String,
    val requiredPermission: String? = null,
) {
    val destinationId: String = "screen.$id"

    init {
        require(id.isNotBlank()) { "screen id must not be blank" }
        require(name.isNotBlank()) { "screen name must not be blank" }
        require(keywords.isNotEmpty() && keywords.none(String::isBlank)) {
            "screen keywords must not be empty or blank"
        }
        require(route.isConcreteRoute()) { "screen route must be concrete: $route" }
    }
}

data class SearchableActionDefinition(
    val id: String,
    val name: String,
    val section: SearchCatalogSection,
    val keywords: Set<String>,
    val route: String,
    val requiredPermission: String? = null,
) {
    val destinationId: String = "action.$id"

    init {
        require(id.isNotBlank()) { "action id must not be blank" }
        require(name.isNotBlank()) { "action name must not be blank" }
        require(keywords.isNotEmpty() && keywords.none(String::isBlank)) {
            "action keywords must not be empty or blank"
        }
        require(route.isConcreteRoute()) { "action route must be concrete: $route" }
    }
}

class AppSearchCatalog(
    val screens: List<SearchableScreenDefinition>,
    val actions: List<SearchableActionDefinition>,
    knownRoutes: Set<String>,
) {
    private val routesByDestinationId: Map<String, String>

    init {
        val allIds = screens.map(SearchableScreenDefinition::destinationId) +
            actions.map(SearchableActionDefinition::destinationId)
        require(allIds.size == allIds.toSet().size) { "duplicate search catalog id" }

        val duplicateRoutes = (screens.map { it.route } + actions.map { it.route })
            .groupingBy { it }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateRoutes.isEmpty()) {
            "duplicate catalog route with conflicting names: ${duplicateRoutes.sorted()}"
        }

        val unknownRoutes = (screens.map(SearchableScreenDefinition::route) +
            actions.map(SearchableActionDefinition::route))
            .filterNot(knownRoutes::contains)
            .distinct()
        require(unknownRoutes.isEmpty()) { "unregistered search routes: ${unknownRoutes.sorted()}" }

        routesByDestinationId = buildMap {
            screens.forEach { put(it.destinationId, it.route) }
            actions.forEach { put(it.destinationId, it.route) }
        }
    }

    fun resolve(destination: HomeDestination): String? =
        routesByDestinationId[destination.id]?.takeIf { destination.arguments.isEmpty() }

    companion object {
        val default: AppSearchCatalog by lazy {
            val screens = defaultScreens()
            val actions = defaultActions()
            AppSearchCatalog(
                screens = screens,
                actions = actions,
                knownRoutes = defaultKnownRoutes(),
            )
        }
    }
}


private fun defaultKnownRoutes(): Set<String> = setOf(
    Screen.ClientsList.route,
    Screen.SuppliersList.route,
    Screen.InvoicesByCategory.create("sales"),
    Screen.InvoicesByCategory.create("purchases"),
    Screen.Inventory.route,
    Screen.PriceList.route,
    Screen.Reports.route,
    Screen.AuditLog.route,
    Screen.Expenses.route,
    Screen.CommissionManagement.create(),
    Screen.ShipmentsList.route,
    Screen.Messages.route,
    Screen.SettingsEmployees.route,
    ManagementNavigation.ROUTE,
    BenzineNavigation.ROUTE,
    Screen.UsersDashboard.route,
    Screen.Leaderboard.route,
    OptimalNavigation.ROUTE,
    OptimalNavigation.COMPANIES_ROUTE,
    OptimalNavigation.MESSAGES_ROUTE,
    OptimalNavigation.INVOICES_ROUTE,
    OptimalNavigation.MAINTENANCE_ROUTE,
    OptimalNavigation.CODES_ROUTE,
    OptimalNavigation.SYNC_ISSUES_ROUTE,
    Screen.Settings.route,
    Screen.Notifications.route,
    Screen.SettingsThemes.route,
    Screen.SettingsNotifications.route,
    Screen.SettingsInvoicePrint.route,
    Screen.SettingsOrgSettings.route,
    Screen.SettingsEducationalTopics.route,
    Screen.AddClient.route,
    Screen.AddSupplier.route,
    Screen.NewInvoice.route,
    Screen.AddInventoryItem.route,
    Screen.BulkPriceEdit.route,
    Screen.CategoryManagement.route,
    Screen.SettingsCreateInvite.route,
    Screen.SettingsProfile.route,
)

private fun defaultScreens(): List<SearchableScreenDefinition> = listOf(
    screen("clients", "العملاء", SearchCatalogSection.RECORDS, "الزبائن", "حسابات العملاء", route = Screen.ClientsList.route, permission = HomePermissionKeys.CLIENTS_VIEW),
    screen("suppliers", "الموردون", SearchCatalogSection.RECORDS, "الموردين", "المشتريات", route = Screen.SuppliersList.route, permission = HomePermissionKeys.CLIENTS_VIEW),
    screen("sales_invoices", "فواتير المبيعات", SearchCatalogSection.RECORDS, "المبيعات", "فواتير البيع", route = Screen.InvoicesByCategory.create("sales"), permission = HomePermissionKeys.SALES_VIEW),
    screen("purchase_invoices", "فواتير المشتريات", SearchCatalogSection.RECORDS, "المشتريات", "فواتير الشراء", route = Screen.InvoicesByCategory.create("purchases"), permission = HomePermissionKeys.PURCHASES_VIEW),
    screen("inventory", "المخزون", SearchCatalogSection.RECORDS, "الأصناف", "قطع الغيار", route = Screen.Inventory.route, permission = HomePermissionKeys.INVENTORY_VIEW),
    screen("price_list", "قائمة الأسعار", SearchCatalogSection.RECORDS, "الأسعار", "كشف الأسعار", route = Screen.PriceList.route, permission = HomePermissionKeys.INVENTORY_VIEW),
    screen("reports", "التقارير", SearchCatalogSection.RECORDS, "التحليلات", "الملخصات", route = Screen.Reports.route, permission = HomePermissionKeys.REPORTS_VIEW),
    screen("audit_log", "سجل التدقيق", SearchCatalogSection.RECORDS, "سجل النشاط", "المراجعة", route = Screen.AuditLog.route, permission = HomePermissionKeys.ADMIN),
    screen("expenses", "المصاريف", SearchCatalogSection.SPECIALIZED_OPERATIONS, "النفقات", "صرف", route = Screen.Expenses.route, permission = HomePermissionKeys.EXPENSES_VIEW),
    screen("commissions", "إدارة العمولات", SearchCatalogSection.SPECIALIZED_OPERATIONS, "العمولات", "سحب عمولة", route = Screen.CommissionManagement.create(), permission = HomePermissionKeys.COMMISSION_MANAGE),
    screen("shipments", "الشحنات", SearchCatalogSection.SPECIALIZED_OPERATIONS, "الشحن", "الواردات", route = Screen.ShipmentsList.route, permission = HomePermissionKeys.SHIPMENTS_VIEW),
    screen("messages", "المحادثات", SearchCatalogSection.SPECIALIZED_OPERATIONS, "الرسائل", "الدردشة", route = Screen.Messages.route, permission = HomePermissionKeys.MARKETING_DASHBOARDS),
    screen("employees", "الموظفون والصلاحيات", SearchCatalogSection.MANAGEMENT, "الموظفين", "صلاحيات المستخدمين", route = Screen.SettingsEmployees.route, permission = HomePermissionKeys.ADMIN),
    screen("management", "مركز الإدارة", SearchCatalogSection.MANAGEMENT, "الإدارة", "الربط", route = ManagementNavigation.ROUTE, permission = HomePermissionKeys.VIEW_MANAGEMENT),
    screen("benzine", "بنزين", SearchCatalogSection.MANAGEMENT, "بنزين", "المسوقين", "الورش", "العمولات", route = BenzineNavigation.ROUTE, permission = HomePermissionKeys.VIEW_MANAGEMENT),
    screen("users_dashboard", "المسوقون والورش", SearchCatalogSection.MANAGEMENT, "أداء المستخدمين", "المسوقين", "الورش", route = Screen.UsersDashboard.route, permission = HomePermissionKeys.MARKETING_DASHBOARDS),
    screen("leaderboard", "لوحة الصدارة", SearchCatalogSection.MANAGEMENT, "الترتيب", "أفضل المسوقين", route = Screen.Leaderboard.route, permission = HomePermissionKeys.MARKETING_DASHBOARDS),
    screen("optimal_home", "Optimal", SearchCatalogSection.OVERSIGHT, "اوبتيمال", "أوبتيمال", route = OptimalNavigation.ROUTE, permission = HomePermissionKeys.VIEW_OPTIMAL),
    screen("optimal_companies", "شركات Optimal", SearchCatalogSection.OVERSIGHT, "شركات أوبتيمال", "المنشآت", route = OptimalNavigation.COMPANIES_ROUTE, permission = HomePermissionKeys.VIEW_OPTIMAL_COMPANIES),
    screen("optimal_messages", "رسائل Optimal", SearchCatalogSection.OVERSIGHT, "محادثات أوبتيمال", "الرسائل", route = OptimalNavigation.MESSAGES_ROUTE, permission = HomePermissionKeys.VIEW_OPTIMAL_MESSAGES),
    screen("optimal_invoices", "فواتير Optimal", SearchCatalogSection.OVERSIGHT, "فواتير أوبتيمال", "حساب الشركات", route = OptimalNavigation.INVOICES_ROUTE, permission = HomePermissionKeys.VIEW_OPTIMAL_INVOICES),
    screen("optimal_maintenance", "صيانة Optimal", SearchCatalogSection.OVERSIGHT, "صيانة أوبتيمال", "سجل الصيانة", route = OptimalNavigation.MAINTENANCE_ROUTE, permission = HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE),
    screen("optimal_codes", "كود ربط Optimal", SearchCatalogSection.OVERSIGHT, "Optimal", "ربط المؤسسة", route = OptimalNavigation.CODES_ROUTE, permission = HomePermissionKeys.ISSUE_OPTIMAL_CODE),
    screen("optimal_sync_issues", "مشكلات مزامنة Optimal", SearchCatalogSection.OVERSIGHT, "أخطاء المزامنة", "تعذر الإرسال", route = OptimalNavigation.SYNC_ISSUES_ROUTE, permission = HomePermissionKeys.VIEW_OPTIMAL_SYNC_ISSUES),
    screen("settings", "الإعدادات", SearchCatalogSection.SYSTEM, "الضبط", "إعداد التطبيق", route = Screen.Settings.route),
    screen("notifications", "الإشعارات", SearchCatalogSection.SYSTEM, "التنبيهات", "مركز الإشعارات", route = Screen.Notifications.route),
    screen("themes", "المظهر والثيم", SearchCatalogSection.SYSTEM, "الوضع الداكن", "الألوان", route = Screen.SettingsThemes.route),
    screen("notification_settings", "إعدادات الإشعارات", SearchCatalogSection.SYSTEM, "تنبيهات التطبيق", "إدارة الإشعارات", route = Screen.SettingsNotifications.route),
    screen("invoice_print", "إعدادات طباعة الفاتورة", SearchCatalogSection.SYSTEM, "شكل الفاتورة", "الطباعة", route = Screen.SettingsInvoicePrint.route),
    screen("organization_settings", "بيانات المؤسسة", SearchCatalogSection.SYSTEM, "اسم المحل", "الشعار والعملة", route = Screen.SettingsOrgSettings.route, permission = HomePermissionKeys.ADMIN),
    screen("educational_topics", "المواضيع التعليمية", SearchCatalogSection.MANAGEMENT, "المعلومات التعليمية", "محتوى الموظفين", route = Screen.SettingsEducationalTopics.route, permission = HomePermissionKeys.ADMIN),
)

private fun defaultActions(): List<SearchableActionDefinition> = listOf(
    action("add_client", "إضافة عميل", SearchCatalogSection.RECORDS, "عميل جديد", "إنشاء زبون", route = Screen.AddClient.route, permission = HomePermissionKeys.CLIENTS_EDIT),
    action("add_supplier", "إضافة مورد", SearchCatalogSection.RECORDS, "مورد جديد", "إنشاء مورد", route = Screen.AddSupplier.route, permission = HomePermissionKeys.CLIENTS_EDIT),
    action("new_sales_invoice", "إنشاء فاتورة مبيعات", SearchCatalogSection.SPECIALIZED_OPERATIONS, "فاتورة بيع جديدة", "بيع", route = Screen.NewInvoice.route, permission = HomePermissionKeys.SALES_CREATE),
    action("add_inventory_item", "إضافة صنف", SearchCatalogSection.RECORDS, "صنف جديد", "قطعة جديدة", route = Screen.AddInventoryItem.route, permission = HomePermissionKeys.INVENTORY_EDIT),
    action("bulk_price_edit", "تعديل أسعار مجموعة أصناف", SearchCatalogSection.SPECIALIZED_OPERATIONS, "تغيير الأسعار", "تحديث جماعي", route = Screen.BulkPriceEdit.route, permission = HomePermissionKeys.INVENTORY_PRICE),
    action("manage_categories", "إدارة التصنيفات", SearchCatalogSection.SPECIALIZED_OPERATIONS, "تصنيفات الأصناف", "الأقسام", route = Screen.CategoryManagement.route, permission = HomePermissionKeys.INVENTORY_EDIT),
    action("create_invite", "إنشاء كود انضمام", SearchCatalogSection.MANAGEMENT, "دعوة موظف", "رمز تسجيل", route = Screen.SettingsCreateInvite.route, permission = HomePermissionKeys.ADMIN),
    action("change_password", "تغيير كلمة المرور", SearchCatalogSection.SYSTEM, "تبديل كلمة السر", "تحديث الباسورد", route = Screen.SettingsProfile.route, permission = HomePermissionKeys.SETTINGS_PASSWORD),
)

private fun screen(
    id: String,
    name: String,
    section: SearchCatalogSection,
    vararg keywords: String,
    route: String,
    permission: String? = null,
) = SearchableScreenDefinition(id, name, section, keywords.toSet(), route, permission)

private fun action(
    id: String,
    name: String,
    section: SearchCatalogSection,
    vararg keywords: String,
    route: String,
    permission: String? = null,
) = SearchableActionDefinition(id, name, section, keywords.toSet(), route, permission)

private fun String.isConcreteRoute(): Boolean =
    isNotBlank() && '{' !in this && '}' !in this
