package com.verto.app.ui.navigation

import android.net.Uri
import com.verto.app.feature.management.navigation.ManagementNavigation

sealed class Screen(val route: String) {
    object Home        : Screen("home")
    object HomeSearch  : Screen("home_search")
    object HomePendingActions : Screen("home_pending_actions")
    object Notifications : Screen("notifications_standalone")
    object ClientsList : Screen("clients_list")
    object SuppliersList : Screen("suppliers_list")
    object SuppliersByScope : Screen("suppliers_list/{supplierScope}") {
        fun create(scope: String) = "suppliers_list/${scope.lowercase()}"
    }
    object AddClient   : Screen("add_client")
    object AddSupplier : Screen("add_supplier")
    object EditClient  : Screen("edit_client/{clientId}/{isSupplier}") {
        fun create(id: String, isSupplier: Boolean = false) = "edit_client/$id/$isSupplier"
    }
    object ClientDashboard : Screen("client_dashboard/{clientId}") {
        fun create(id: String) = "client_dashboard/$id"
    }
    object Invoice     : Screen("invoice/{invoiceId}?openCommission={openCommission}") {
        fun create(id: String, openCommission: Boolean = false) =
            "invoice/$id?openCommission=$openCommission"
    }
    object NewInvoiceForClient : Screen("new_invoice/client/{clientId}") {
        fun create(id: String) = "new_invoice/client/$id"
    }
    object EditInvoice : Screen("edit_invoice/{clientId}/{invoiceId}") {
        fun create(clientId: String, invoiceId: String) = "edit_invoice/$clientId/$invoiceId"
    }
    object NewInvoice  : Screen("new_invoice")
    object NewPurchase : Screen("new_purchase")
    object InternationalPurchase : Screen("international_purchase/{currency}/{rate}") {
        fun create(currency: String, rate: Double) = "international_purchase/$currency/$rate"
    }
    object AddPayment  : Screen("add_payment/{invoiceId}/{clientId}?full={full}") {
        fun create(invoiceId: String, clientId: String, full: Boolean = false) =
            "add_payment/$invoiceId/$clientId?full=$full"
    }
    object Expenses    : Screen("expenses")
    object CommissionManagement : Screen("commission_management?withdrawClientId={withdrawClientId}") {
        fun create(withdrawClientId: String? = null) =
            if (withdrawClientId != null) "commission_management?withdrawClientId=$withdrawClientId"
            else "commission_management"
    }
    object Reports     : Screen("reports")
    object Settings    : Screen("settings")
    object SettingsProfile : Screen("settings_profile")

    // ── الإعدادات — شاشات فرعية ───────────────────────
    object SettingsThemes        : Screen("settings_themes") // legacy full appearance screen
    object SettingsAppearance    : Screen("settings_themes/appearance")
    object SettingsThemeStyle    : Screen("settings_themes/style")
    object SettingsNotifications : Screen("settings_notifications")
    object SettingsInvoicePrint  : Screen("settings_invoice_print")
    object SettingsOrgSettings   : Screen("settings_org_settings")
    object SettingsManagement    : Screen(ManagementNavigation.ROUTE)
    object SettingsEducationalTopics : Screen("settings_educational_topics")
    object TeamObservations : Screen("team_observations")

    // ── إدارة الموظفين — شاشات فرعية ─────────────────
    object SettingsEmployees : Screen("settings_employees")
    object SettingsEmployeeDetail : Screen("settings_employee_detail/{userId}") {
        fun create(userId: String) = "settings_employee_detail/${Uri.encode(userId)}"
    }
    object SettingsEmployeePermissions : Screen("settings_employee_permissions/{userId}") {
        fun create(userId: String) = "settings_employee_permissions/${Uri.encode(userId)}"
    }
    object SettingsCreateInvite : Screen("settings_create_invite")

    // ── المصادقة ──────────────────────────────────────
    object Login         : Screen("login")
    object Register      : Screen("register")
    object JoinOrg       : Screen("join_org")
    object ResetPassword : Screen("reset_password")
    // البريد يُمرَّر عبر savedStateHandle (مفتاح ARG_RESET_EMAIL) لا في المسار — تفادياً لتسريب PII في الـ back stack/السجلات.
    object PasswordResetSent : Screen("password_reset_sent") {
        const val ARG_RESET_EMAIL = "reset_email"
    }

    // ── المخزون ───────────────────────────────────────
    object Inventory         : Screen("inventory")
    object PriceList         : Screen("price_list")
    object AddInventoryItem  : Screen("add_inventory_item")
    object EditInventoryItem : Screen("edit_inventory_item/{itemId}") {
        fun create(id: String) = "edit_inventory_item/$id"
    }

    // ── الفواتير المصنفة (sales/purchases) ───────────
    object InvoicesByCategory : Screen("invoices_by_category/{category}") {
        fun create(category: String) = "invoices_by_category/$category"
    }
    object PurchaseInvoicesByScope : Screen("purchase_invoices/{purchaseScope}") {
        fun create(scope: String) = "purchase_invoices/${scope.lowercase()}"
    }

    object BulkPriceEdit       : Screen("bulk_price_edit")
    object AuditLog            : Screen("audit_log")
    object CategoryManagement  : Screen("category_management")

    // ── المورد ────────────────────────────────────────
    object SupplierPayment : Screen("supplier_payment/{clientId}") {
        fun create(id: String) = "supplier_payment/$id"
    }
    object SupplierStatement : Screen("supplier_statement/{clientId}") {
        fun create(id: String) = "supplier_statement/$id"
    }
    object SupplierDashboard : Screen("supplier_dashboard/{supplierId}") {
        fun create(id: String) = "supplier_dashboard/$id"
    }

    object Competitor : Screen("competitor/{clientId}") {
        fun create(id: String) = "competitor/$id"
    }

    // ── اللوجستيات / الشحنات ──────────────────────────
    object ShipmentsList   : Screen("shipments_list")
    object ShipmentDetail  : Screen("logistics_v2_detail/{shipmentId}") {
        fun create(id: String) = "logistics_v2_detail/$id"
    }

    // ── المحادثات ─────────────────────────────────────────────────────────────
    object Messages : Screen("messages")
    object ChatDetail : Screen("chat_detail/{conversationId}/{clientId}/{clientName}") {
        fun create(conversationId: String, clientId: String, clientName: String) =
            "chat_detail/$conversationId/$clientId/${Uri.encode(clientName)}"
    }

    // ── لوحة تحكم المستخدمين (المرحلة 4.4) ────────────────────────────────────
    object UsersDashboard : Screen("users_dashboard")
    object UserDashboardDetail : Screen("user_dashboard_detail/{clientId}") {
        fun create(clientId: String) = "user_dashboard_detail/$clientId"
    }

    // ── لوحة الصدارة الموحّدة (المرحلة 5.4) ───────────────────────────────────
    object Leaderboard : Screen("leaderboard")

    // ── تقرير عمولات مسوّق (معاينة الأدمن) ────────────────────────────────────
    object MarketerCommissionReport :
        Screen("marketer_commission_report/{clientId}?from={from}&to={to}") {
        fun create(clientId: String, from: Long? = null, to: Long? = null) =
            "marketer_commission_report/$clientId?from=${from ?: -1L}&to=${to ?: -1L}"
    }
}
