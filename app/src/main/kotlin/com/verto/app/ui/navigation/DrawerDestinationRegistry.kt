package com.verto.app.ui.navigation

import androidx.annotation.StringRes
import com.verto.app.R
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.feature.management.navigation.BenzineNavigation

enum class DrawerSection(
    val storageKey: String,
    @StringRes val labelResId: Int,
) {
    SALES_CUSTOMERS("sales_customers", R.string.app_drawer_section_sales_customers),
    PURCHASES_SUPPLIERS("purchases_suppliers", R.string.app_drawer_section_purchases_suppliers),
    INVENTORY_PRICING("inventory_pricing", R.string.app_drawer_section_inventory_pricing),
    FINANCE_REPORTS("finance_reports", R.string.app_drawer_section_finance_reports),
    MANAGEMENT("management", R.string.app_drawer_section_management),
    SETTINGS("settings", R.string.app_drawer_section_settings);

    companion object {
        fun fromStorageKey(value: String): DrawerSection? = when (value) {
            "records" -> SALES_CUSTOMERS
            "specialized_operations" -> PURCHASES_SUPPLIERS
            "oversight" -> MANAGEMENT
            "system" -> SETTINGS
            else -> entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class DrawerIconKey {
    CLIENTS, SALES_INVOICES, LOCAL_SUPPLIERS, INTERNATIONAL_SUPPLIERS,
    LOCAL_PURCHASE_INVOICES, INTERNATIONAL_PURCHASE_INVOICES, SHIPMENTS,
    INVENTORY, CATEGORIES, PRICE_LIST, BULK_PRICE_EDIT, EXPENSES, REPORTS, AUDIT_LOG,
    OPTIMAL, BENZINE, EMPLOYEES, EDUCATION, TEAM_OBSERVATIONS, PROFILE, ORGANIZATION, APPEARANCE,
    NOTIFICATIONS, PRINT, THEMES,
}

enum class DrawerAccess {
    AUTHENTICATED, CLIENTS_VIEW, INVENTORY_VIEW, INVENTORY_PRICE, SALES_VIEW, PURCHASES_VIEW,
    REPORTS_VIEW, EXPENSES_VIEW, SHIPMENTS_VIEW, SETTINGS_ORG_DATA, ADMIN_ONLY, VIEW_MANAGEMENT,
    VIEW_OPTIMAL,
}

data class DrawerDestination(
    val id: String,
    @StringRes val labelResId: Int,
    val route: String,
    val section: DrawerSection,
    val access: DrawerAccess,
    val iconKey: DrawerIconKey,
    val routePattern: String = route,
    val requiredArgument: Pair<String, String>? = null,
    val ownedRoutePatterns: Set<String> = emptySet(),
)

object DrawerDestinationRegistry {
    val destinations: List<DrawerDestination> = listOf(
        DrawerDestination("clients", R.string.app_drawer_clients, Screen.ClientsList.route, DrawerSection.SALES_CUSTOMERS, DrawerAccess.CLIENTS_VIEW, DrawerIconKey.CLIENTS,
            ownedRoutePatterns = setOf(Screen.ClientDashboard.route)),
        DrawerDestination("sales_invoices", R.string.app_drawer_sales_invoices, Screen.InvoicesByCategory.create("sales"), DrawerSection.SALES_CUSTOMERS, DrawerAccess.SALES_VIEW, DrawerIconKey.SALES_INVOICES,
            routePattern = Screen.InvoicesByCategory.route, requiredArgument = "category" to "sales"),

        DrawerDestination("local_suppliers", R.string.app_drawer_local_suppliers, Screen.SuppliersByScope.create("local"), DrawerSection.PURCHASES_SUPPLIERS, DrawerAccess.CLIENTS_VIEW, DrawerIconKey.LOCAL_SUPPLIERS,
            routePattern = Screen.SuppliersByScope.route, requiredArgument = "supplierScope" to "local"),
        DrawerDestination("international_suppliers", R.string.app_drawer_international_suppliers, Screen.SuppliersByScope.create("international"), DrawerSection.PURCHASES_SUPPLIERS, DrawerAccess.CLIENTS_VIEW, DrawerIconKey.INTERNATIONAL_SUPPLIERS,
            routePattern = Screen.SuppliersByScope.route, requiredArgument = "supplierScope" to "international"),
        DrawerDestination("local_purchase_invoices", R.string.app_drawer_local_purchase_invoices, Screen.PurchaseInvoicesByScope.create("local"), DrawerSection.PURCHASES_SUPPLIERS, DrawerAccess.PURCHASES_VIEW, DrawerIconKey.LOCAL_PURCHASE_INVOICES,
            routePattern = Screen.PurchaseInvoicesByScope.route, requiredArgument = "purchaseScope" to "local"),
        DrawerDestination("international_purchase_invoices", R.string.app_drawer_international_purchase_invoices, Screen.PurchaseInvoicesByScope.create("international"), DrawerSection.PURCHASES_SUPPLIERS, DrawerAccess.PURCHASES_VIEW, DrawerIconKey.INTERNATIONAL_PURCHASE_INVOICES,
            routePattern = Screen.PurchaseInvoicesByScope.route, requiredArgument = "purchaseScope" to "international"),
        DrawerDestination("shipments", R.string.app_drawer_shipments, Screen.ShipmentsList.route, DrawerSection.PURCHASES_SUPPLIERS, DrawerAccess.SHIPMENTS_VIEW, DrawerIconKey.SHIPMENTS,
            ownedRoutePatterns = setOf(Screen.ShipmentDetail.route)),

        DrawerDestination("inventory", R.string.app_drawer_inventory, Screen.Inventory.route, DrawerSection.INVENTORY_PRICING, DrawerAccess.INVENTORY_VIEW, DrawerIconKey.INVENTORY,
            ownedRoutePatterns = setOf(Screen.AddInventoryItem.route, Screen.EditInventoryItem.route)),
        DrawerDestination("categories", R.string.app_drawer_categories, Screen.CategoryManagement.route, DrawerSection.INVENTORY_PRICING, DrawerAccess.INVENTORY_VIEW, DrawerIconKey.CATEGORIES),
        DrawerDestination("price_list", R.string.app_drawer_kashf, Screen.PriceList.route, DrawerSection.INVENTORY_PRICING, DrawerAccess.INVENTORY_VIEW, DrawerIconKey.PRICE_LIST),
        DrawerDestination("bulk_price_edit", R.string.app_drawer_bulk_price_edit, Screen.BulkPriceEdit.route, DrawerSection.INVENTORY_PRICING, DrawerAccess.INVENTORY_PRICE, DrawerIconKey.BULK_PRICE_EDIT),

        DrawerDestination("expenses", R.string.app_drawer_expenses, Screen.Expenses.route, DrawerSection.FINANCE_REPORTS, DrawerAccess.EXPENSES_VIEW, DrawerIconKey.EXPENSES),
        DrawerDestination("reports", R.string.app_drawer_reports, Screen.Reports.route, DrawerSection.FINANCE_REPORTS, DrawerAccess.REPORTS_VIEW, DrawerIconKey.REPORTS),
        DrawerDestination("audit_log", R.string.app_drawer_audit_log, Screen.AuditLog.route, DrawerSection.FINANCE_REPORTS, DrawerAccess.ADMIN_ONLY, DrawerIconKey.AUDIT_LOG),

        DrawerDestination("optimal_home", R.string.app_drawer_optimal_home, OptimalNavigation.ROUTE, DrawerSection.MANAGEMENT, DrawerAccess.VIEW_OPTIMAL, DrawerIconKey.OPTIMAL,
            ownedRoutePatterns = setOf(OptimalNavigation.COMPANIES_ROUTE, OptimalNavigation.COMPANY_DETAILS_ROUTE, OptimalNavigation.MESSAGES_ROUTE, OptimalNavigation.CHAT_ROUTE, OptimalNavigation.INVOICES_ROUTE, OptimalNavigation.COMPANY_INVOICE_ROUTE, OptimalNavigation.MAINTENANCE_ROUTE, OptimalNavigation.MAINTENANCE_DETAILS_ROUTE, OptimalNavigation.CODES_ROUTE, OptimalNavigation.SYNC_ISSUES_ROUTE)),
        DrawerDestination("benzine", R.string.app_drawer_benzine, BenzineNavigation.ROUTE, DrawerSection.MANAGEMENT, DrawerAccess.VIEW_MANAGEMENT, DrawerIconKey.BENZINE,
            ownedRoutePatterns = setOf(Screen.UsersDashboard.route, Screen.UserDashboardDetail.route, Screen.CommissionManagement.route, Screen.Leaderboard.route)),
        DrawerDestination("employees", R.string.app_drawer_employees, Screen.SettingsEmployees.route, DrawerSection.MANAGEMENT, DrawerAccess.ADMIN_ONLY, DrawerIconKey.EMPLOYEES,
            ownedRoutePatterns = setOf(Screen.SettingsEmployeeDetail.route, Screen.SettingsEmployeePermissions.route, Screen.SettingsCreateInvite.route)),
        DrawerDestination("educational_topics", R.string.app_drawer_educational_topics, Screen.SettingsEducationalTopics.route, DrawerSection.MANAGEMENT, DrawerAccess.ADMIN_ONLY, DrawerIconKey.EDUCATION),
        DrawerDestination("team_observations", R.string.app_drawer_team_observations, Screen.TeamObservations.route, DrawerSection.MANAGEMENT, DrawerAccess.ADMIN_ONLY, DrawerIconKey.TEAM_OBSERVATIONS),

        DrawerDestination("profile", R.string.app_drawer_profile, Screen.SettingsProfile.route, DrawerSection.SETTINGS, DrawerAccess.AUTHENTICATED, DrawerIconKey.PROFILE),
        DrawerDestination("organization_settings", R.string.app_drawer_organization, Screen.SettingsOrgSettings.route, DrawerSection.SETTINGS, DrawerAccess.SETTINGS_ORG_DATA, DrawerIconKey.ORGANIZATION),
        DrawerDestination("appearance", R.string.app_drawer_appearance, Screen.SettingsAppearance.route, DrawerSection.SETTINGS, DrawerAccess.AUTHENTICATED, DrawerIconKey.APPEARANCE),
        DrawerDestination("notification_settings", R.string.app_drawer_notification_settings, Screen.SettingsNotifications.route, DrawerSection.SETTINGS, DrawerAccess.AUTHENTICATED, DrawerIconKey.NOTIFICATIONS),
        DrawerDestination("invoice_print", R.string.app_drawer_invoice_print_short, Screen.SettingsInvoicePrint.route, DrawerSection.SETTINGS, DrawerAccess.AUTHENTICATED, DrawerIconKey.PRINT),
        DrawerDestination("themes", R.string.app_drawer_themes, Screen.SettingsThemeStyle.route, DrawerSection.SETTINGS, DrawerAccess.AUTHENTICATED, DrawerIconKey.THEMES),
    )

    fun destinationById(id: String): DrawerDestination? = destinations.firstOrNull { it.id == id }

    fun visibleDestinations(permissions: EmployeePermissions?, role: String?): List<DrawerDestination> =
        destinations.filter { canAccess(it, permissions, role) }

    fun visibleBySection(permissions: EmployeePermissions?, role: String?): Map<DrawerSection, List<DrawerDestination>> {
        val grouped = visibleDestinations(permissions, role).groupBy { it.section }
        return DrawerSection.entries.mapNotNull { section -> grouped[section]?.takeIf { it.isNotEmpty() }?.let { section to it } }.toMap()
    }

    fun canAccess(destination: DrawerDestination, permissions: EmployeePermissions?, role: String?): Boolean {
        val authenticated = !role.isNullOrBlank()
        val isAdmin = role == "admin"
        val p = permissions
        return when (destination.access) {
            DrawerAccess.AUTHENTICATED -> authenticated
            DrawerAccess.ADMIN_ONLY -> isAdmin
            DrawerAccess.CLIENTS_VIEW -> p?.clientsView == true
            DrawerAccess.INVENTORY_VIEW -> p?.inventoryView == true
            DrawerAccess.INVENTORY_PRICE -> p?.inventoryPrice == true
            DrawerAccess.SALES_VIEW -> p?.salesView == true
            DrawerAccess.PURCHASES_VIEW -> p?.purchasesView == true
            DrawerAccess.REPORTS_VIEW -> p?.reportsSummary == true
            DrawerAccess.EXPENSES_VIEW -> p?.expensesView == true
            DrawerAccess.SHIPMENTS_VIEW -> p?.shipmentsView == true
            DrawerAccess.SETTINGS_ORG_DATA -> isAdmin || p?.settingsOrgData == true
            DrawerAccess.VIEW_MANAGEMENT -> p?.viewManagement == true
            DrawerAccess.VIEW_OPTIMAL -> p?.viewManagement == true && p.viewOptimal
        }
    }

    fun selectedDestination(currentRoutePattern: String?, currentArguments: Map<String, String>): DrawerDestination? =
        destinations.firstOrNull { destination ->
            val direct = destination.routePattern == currentRoutePattern
            val owned = currentRoutePattern != null && currentRoutePattern in destination.ownedRoutePatterns
            if (!direct && !owned) return@firstOrNull false
            if (owned && !direct) return@firstOrNull true
            val required = destination.requiredArgument ?: return@firstOrNull true
            currentArguments[required.first]?.lowercase() == required.second.lowercase()
        }

    fun selectedSection(currentRoutePattern: String?, currentArguments: Map<String, String>): DrawerSection? {
        selectedDestination(currentRoutePattern, currentArguments)?.let { return it.section }
        return when (currentRoutePattern) {
            Screen.SuppliersList.route, Screen.SupplierDashboard.route, Screen.SupplierPayment.route, Screen.SupplierStatement.route, "supplier_payment/{supplierId}", "statement/{supplierId}" -> DrawerSection.PURCHASES_SUPPLIERS
            Screen.EditClient.route -> if (currentArguments["isSupplier"] == "true") DrawerSection.PURCHASES_SUPPLIERS else DrawerSection.SALES_CUSTOMERS
            Screen.Settings.route, Screen.SettingsThemes.route -> DrawerSection.SETTINGS
            else -> null
        }
    }
}
