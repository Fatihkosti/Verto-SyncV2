package com.verto.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.verto.app.core.session.model.ManagementOptimalPermission

/**
 * نموذج الصلاحيات المتداخلة للموظف.
 * يُخزَّن في Supabase (جدول employee_permissions)
 * ويُكاش محلياً في DataStore بعد تسجيل الدخول.
 */
@Serializable
data class EmployeePermissions(
    @SerialName("user_id")  val userId: String = "",
    @SerialName("org_id")   val orgId: String  = "",

    // ── المبيعات ──────────────────────────────────────────────
    @SerialName("sales_view")   val salesView: Boolean   = true,
    @SerialName("sales_create") val salesCreate: Boolean = false,
    @SerialName("sales_edit")   val salesEdit: Boolean   = false,
    @SerialName("sales_delete") val salesDelete: Boolean = false,
    @SerialName("sales_export") val salesExport: Boolean = false,

    // ── المشتريات ─────────────────────────────────────────────
    @SerialName("purchases_view")   val purchasesView: Boolean   = true,
    @SerialName("purchases_create") val purchasesCreate: Boolean = false,
    @SerialName("purchases_edit")   val purchasesEdit: Boolean   = false,
    @SerialName("purchases_delete") val purchasesDelete: Boolean = false,
    @SerialName("purchases_export") val purchasesExport: Boolean = false,

    // ── العملاء ───────────────────────────────────────────────
    @SerialName("clients_view")        val clientsView: Boolean       = true,
    @SerialName("clients_edit")        val clientsEdit: Boolean       = false,
    @SerialName("clients_delete")      val clientsDelete: Boolean     = false,
    @SerialName("clients_add_payment") val clientsAddPayment: Boolean = false,
    @SerialName("suppliers_add_payment") val suppliersAddPayment: Boolean = false,

    // ── المخزون ───────────────────────────────────────────────
    @SerialName("inventory_view")   val inventoryView: Boolean   = true,
    @SerialName("inventory_edit")   val inventoryEdit: Boolean   = false,
    @SerialName("inventory_price")  val inventoryPrice: Boolean  = false,
    @SerialName("inventory_import") val inventoryImport: Boolean = false,
    @SerialName("inventory_export") val inventoryExport: Boolean = false,

    // ── التقارير ──────────────────────────────────────────────
    @SerialName("reports_summary") val reportsSummary: Boolean = true,
    @SerialName("reports_details") val reportsDetails: Boolean = false,
    @SerialName("reports_full")    val reportsFull: Boolean    = false,
    @SerialName("reports_export")  val reportsExport: Boolean  = false,

    // ── المصاريف ──────────────────────────────────────────────
    @SerialName("expenses_view")   val expensesView: Boolean   = true,
    @SerialName("expenses_create") val expensesCreate: Boolean = false,
    @SerialName("expenses_delete") val expensesDelete: Boolean = false,
    @SerialName("cash_adjust")     val cashAdjust: Boolean     = false,

    // ── المدفوعات ─────────────────────────────────────────────
    @SerialName("payments_reverse") val paymentsReverse: Boolean = false,

    // ── العمولات ولوحات المسوّقين ─────────────────────────────
    @SerialName("commission_manage")    val commissionManage: Boolean    = false,
    @SerialName("marketing_dashboards") val marketingDashboards: Boolean = false,

    // ── الشحنات ───────────────────────────────────────────────
    @SerialName("shipments_view")    val shipmentsView: Boolean    = true,
    @SerialName("shipments_manage")  val shipmentsManage: Boolean  = false,
    @SerialName("shipments_confirm") val shipmentsConfirm: Boolean = false,

    // ── الإعدادات ─────────────────────────────────────────────
    @SerialName("settings_password") val settingsPassword: Boolean = false,
    @SerialName("settings_org_data") val settingsOrgData: Boolean  = false,
    @SerialName("settings_reset")    val settingsReset: Boolean    = false,

    // ── الإدارة وOptimal (محجوزة؛ لا تظهر في شاشة منح الموظفين) ──
    @SerialName("VIEW_MANAGEMENT") val viewManagement: Boolean = false,
    @SerialName("VIEW_OPTIMAL") val viewOptimal: Boolean = false,
    @SerialName("VIEW_OPTIMAL_COMPANIES") val viewOptimalCompanies: Boolean = false,
    @SerialName("ISSUE_OPTIMAL_CODE") val issueOptimalCode: Boolean = false,
    @SerialName("VIEW_OPTIMAL_MESSAGES") val viewOptimalMessages: Boolean = false,
    @SerialName("SEND_OPTIMAL_MESSAGES") val sendOptimalMessages: Boolean = false,
    @SerialName("VIEW_OPTIMAL_INVOICES") val viewOptimalInvoices: Boolean = false,
    @SerialName("VIEW_OPTIMAL_MAINTENANCE") val viewOptimalMaintenance: Boolean = false,
    @SerialName("VIEW_OPTIMAL_SYNC_ISSUES") val viewOptimalSyncIssues: Boolean = false,
    @SerialName("RETRY_OPTIMAL_SYNC") val retryOptimalSync: Boolean = false
) {
    companion object {
        /** صلاحيات افتراضية لموظف جديد — عرض فقط */
        fun defaultEmployee(userId: String = "", orgId: String = "") = EmployeePermissions(
            userId = userId, orgId = orgId,
            salesView = true, purchasesView = true,
            clientsView = true, inventoryView = true,
            reportsSummary = true, expensesView = true,
            shipmentsView = true
        )

        /** صلاحيات كاملة — للأدمن فقط (لا تُخزَّن، مرجعية) */
        fun fullAccess(userId: String = "", orgId: String = "") = EmployeePermissions(
            userId = userId, orgId = orgId,
            salesView = true, salesCreate = true, salesEdit = true, salesDelete = true, salesExport = true,
            purchasesView = true, purchasesCreate = true, purchasesEdit = true, purchasesDelete = true, purchasesExport = true,
            clientsView = true, clientsEdit = true, clientsDelete = true, clientsAddPayment = true,
            suppliersAddPayment = true,
            inventoryView = true, inventoryEdit = true, inventoryPrice = true, inventoryImport = true, inventoryExport = true,
            reportsSummary = true, reportsDetails = true, reportsFull = true, reportsExport = true,
            expensesView = true, expensesCreate = true, expensesDelete = true, cashAdjust = true,
            paymentsReverse = true,
            commissionManage = true, marketingDashboards = true,
            shipmentsView = true, shipmentsManage = true, shipmentsConfirm = true,
            settingsPassword = true, settingsOrgData = true, settingsReset = true,
            viewManagement = true,
            viewOptimal = true,
            viewOptimalCompanies = true,
            issueOptimalCode = true,
            viewOptimalMessages = true,
            sendOptimalMessages = true,
            viewOptimalInvoices = true,
            viewOptimalMaintenance = true,
            viewOptimalSyncIssues = true,
            retryOptimalSync = true
        )
    }

    fun allows(permission: ManagementOptimalPermission): Boolean = when (permission) {
        ManagementOptimalPermission.VIEW_MANAGEMENT -> viewManagement
        ManagementOptimalPermission.VIEW_OPTIMAL -> viewOptimal
        ManagementOptimalPermission.VIEW_OPTIMAL_COMPANIES -> viewOptimalCompanies
        ManagementOptimalPermission.ISSUE_OPTIMAL_CODE -> issueOptimalCode
        ManagementOptimalPermission.VIEW_OPTIMAL_MESSAGES -> viewOptimalMessages
        ManagementOptimalPermission.SEND_OPTIMAL_MESSAGES -> sendOptimalMessages
        ManagementOptimalPermission.VIEW_OPTIMAL_INVOICES -> viewOptimalInvoices
        ManagementOptimalPermission.VIEW_OPTIMAL_MAINTENANCE -> viewOptimalMaintenance
        ManagementOptimalPermission.VIEW_OPTIMAL_SYNC_ISSUES -> viewOptimalSyncIssues
        ManagementOptimalPermission.RETRY_OPTIMAL_SYNC -> retryOptimalSync
    }

    /** يمنع أي مسار إدارة حالي من منح صلاحيات Optimal للموظفين. */
    fun withoutRestrictedManagementOptimalPermissions(): EmployeePermissions = copy(
        viewManagement = false,
        viewOptimal = false,
        viewOptimalCompanies = false,
        issueOptimalCode = false,
        viewOptimalMessages = false,
        sendOptimalMessages = false,
        viewOptimalInvoices = false,
        viewOptimalMaintenance = false,
        viewOptimalSyncIssues = false,
        retryOptimalSync = false,
    )
}

/** موظف مع صلاحياته — يُستخدم في لوحة الأدمن */
data class EmployeeWithPermissions(
    val userId: String,
    val name: String,
    val email: String = "",
    val joinedAt: String = "",
    val isActive: Boolean = true,
    val permissions: EmployeePermissions
)
