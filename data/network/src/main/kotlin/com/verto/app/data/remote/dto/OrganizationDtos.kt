package com.verto.app.data.remote.dto

import com.verto.app.utils.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class EmployeePermissionsRowDto(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("org_id")  val orgId: String  = "",
    val permissions: EmployeePermissionsPayloadDto = EmployeePermissionsPayloadDto(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class EmployeePermissionsPayloadDto(
    // المبيعات
    @SerialName("sales_view")   val salesView: Boolean   = true,
    @SerialName("sales_create") val salesCreate: Boolean = false,
    @SerialName("sales_edit")   val salesEdit: Boolean   = false,
    @SerialName("sales_delete") val salesDelete: Boolean = false,
    @SerialName("sales_export") val salesExport: Boolean = false,

    // المشتريات
    @SerialName("purchases_view")   val purchasesView: Boolean   = true,
    @SerialName("purchases_create") val purchasesCreate: Boolean = false,
    @SerialName("purchases_edit")   val purchasesEdit: Boolean   = false,
    @SerialName("purchases_delete") val purchasesDelete: Boolean = false,
    @SerialName("purchases_export") val purchasesExport: Boolean = false,

    // العملاء
    @SerialName("clients_view")        val clientsView: Boolean       = true,
    @SerialName("clients_edit")        val clientsEdit: Boolean       = false,
    @SerialName("clients_delete")      val clientsDelete: Boolean     = false,
    @SerialName("clients_add_payment") val clientsAddPayment: Boolean = false,
    @SerialName("suppliers_add_payment") val suppliersAddPayment: Boolean = false,

    // المخزون
    @SerialName("inventory_view")   val inventoryView: Boolean   = true,
    @SerialName("inventory_edit")   val inventoryEdit: Boolean   = false,
    @SerialName("inventory_price")  val inventoryPrice: Boolean  = false,
    @SerialName("inventory_import") val inventoryImport: Boolean = false,
    @SerialName("inventory_export") val inventoryExport: Boolean = false,

    // التقارير
    @SerialName("reports_summary") val reportsSummary: Boolean = true,
    @SerialName("reports_details") val reportsDetails: Boolean = false,
    @SerialName("reports_full")    val reportsFull: Boolean    = false,
    @SerialName("reports_export")  val reportsExport: Boolean  = false,

    // المصاريف
    @SerialName("expenses_view")   val expensesView: Boolean   = true,
    @SerialName("expenses_create") val expensesCreate: Boolean = false,
    @SerialName("expenses_delete") val expensesDelete: Boolean = false,
    @SerialName("cash_adjust")     val cashAdjust: Boolean     = false,

    // المدفوعات
    @SerialName("payments_reverse") val paymentsReverse: Boolean = false,

    // العمولات ولوحات المسوّقين
    @SerialName("commission_manage")    val commissionManage: Boolean    = false,
    @SerialName("marketing_dashboards") val marketingDashboards: Boolean = false,

    // الشحنات
    @SerialName("shipments_view")    val shipmentsView: Boolean    = true,
    @SerialName("shipments_manage")  val shipmentsManage: Boolean  = false,
    @SerialName("shipments_confirm") val shipmentsConfirm: Boolean = false,

    // الإعدادات
    @SerialName("settings_password") val settingsPassword: Boolean = false,
    @SerialName("settings_org_data") val settingsOrgData: Boolean  = false,
    @SerialName("settings_reset")    val settingsReset: Boolean    = false,

    // الإدارة وOptimal — محجوزة وغير قابلة للمنح للموظفين حاليًا
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
)

@Serializable
data class EmployeePermissionsDto(
    @SerialName("user_id") val userId: String = "",
    @SerialName("org_id")  val orgId: String  = "",

    // المبيعات
    @SerialName("sales_view")   val salesView: Boolean   = true,
    @SerialName("sales_create") val salesCreate: Boolean = false,
    @SerialName("sales_edit")   val salesEdit: Boolean   = false,
    @SerialName("sales_delete") val salesDelete: Boolean = false,
    @SerialName("sales_export") val salesExport: Boolean = false,

    // المشتريات
    @SerialName("purchases_view")   val purchasesView: Boolean   = true,
    @SerialName("purchases_create") val purchasesCreate: Boolean = false,
    @SerialName("purchases_edit")   val purchasesEdit: Boolean   = false,
    @SerialName("purchases_delete") val purchasesDelete: Boolean = false,
    @SerialName("purchases_export") val purchasesExport: Boolean = false,

    // العملاء
    @SerialName("clients_view")        val clientsView: Boolean       = true,
    @SerialName("clients_edit")        val clientsEdit: Boolean       = false,
    @SerialName("clients_delete")      val clientsDelete: Boolean     = false,
    @SerialName("clients_add_payment") val clientsAddPayment: Boolean = false,
    @SerialName("suppliers_add_payment") val suppliersAddPayment: Boolean = false,

    // المخزون
    @SerialName("inventory_view")   val inventoryView: Boolean   = true,
    @SerialName("inventory_edit")   val inventoryEdit: Boolean   = false,
    @SerialName("inventory_price")  val inventoryPrice: Boolean  = false,
    @SerialName("inventory_import") val inventoryImport: Boolean = false,
    @SerialName("inventory_export") val inventoryExport: Boolean = false,

    // التقارير
    @SerialName("reports_summary") val reportsSummary: Boolean = true,
    @SerialName("reports_details") val reportsDetails: Boolean = false,
    @SerialName("reports_full")    val reportsFull: Boolean    = false,
    @SerialName("reports_export")  val reportsExport: Boolean  = false,

    // المصاريف
    @SerialName("expenses_view")   val expensesView: Boolean   = true,
    @SerialName("expenses_create") val expensesCreate: Boolean = false,
    @SerialName("expenses_delete") val expensesDelete: Boolean = false,
    @SerialName("cash_adjust")     val cashAdjust: Boolean     = false,

    // المدفوعات
    @SerialName("payments_reverse") val paymentsReverse: Boolean = false,

    // العمولات ولوحات المسوّقين
    @SerialName("commission_manage")    val commissionManage: Boolean    = false,
    @SerialName("marketing_dashboards") val marketingDashboards: Boolean = false,

    // الشحنات
    @SerialName("shipments_view")    val shipmentsView: Boolean    = true,
    @SerialName("shipments_manage")  val shipmentsManage: Boolean  = false,
    @SerialName("shipments_confirm") val shipmentsConfirm: Boolean = false,

    // الإعدادات
    @SerialName("settings_password") val settingsPassword: Boolean = false,
    @SerialName("settings_org_data") val settingsOrgData: Boolean  = false,
    @SerialName("settings_reset")    val settingsReset: Boolean    = false,

    // الإدارة وOptimal — محجوزة وغير قابلة للمنح للموظفين حاليًا
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
)

// ── كود الانضمام المحدّث ──────────────────────────────────────
@Serializable
data class InviteCodeDto(
    val id: String = "",
    val code: String = "",
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("created_by") val createdBy: String = "",
    val used: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,   // null = مفتوح بلا انتهاء
    val permissions: String = "{}"                              // JSON للصلاحيات المحددة مسبقاً
)

// ── طلب إنشاء مؤسسة ──────────────────────────────────────────
@Serializable
data class CreateOrgRequest(
    @SerialName("org_name")   val orgName: String,
    @SerialName("admin_name") val adminName: String,
    @SerialName("admin_uid")  val adminUid: String
)
// ── الصندوق ────────────────────────────────────────────────────────────────────
@Serializable
data class CashRegisterDto(
    val id: String = "main",
    @SerialName("organization_id") val organizationId: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val balance: BigDecimal = BigDecimal.ZERO,
    @SerialName("updated_at") val updatedAt: String? = null
)

// ── إعدادات المؤسسة ────────────────────────────────────────────────────────────
@Serializable
data class OrgSettingsDto(
    // SYNC-005: لا عمود id في جدول organization_settings (PK = organization_id).
    // encodeDefaults=true كان سيرسل id="" → PGRST204، فأُسقط الحقل.
    @SerialName("organization_id") val organizationId: String = "",
    @SerialName("shop_name") val shopName: String = "",
    @SerialName("shop_phone") val shopPhone: String = "",
    val city: String = "",
    val address: String = "",
    val currency: String = "",
    @SerialName("invoice_footer") val invoiceFooter: String = "",
    @SerialName("tax_number") val taxNumber: String = "",
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("signature_url") val signatureUrl: String = ""
    // updated_at يُدار سيرفرياً (DEFAULT now() + تريغر) — لا نرسله (NOT NULL لا يقبل null)
)

// ── مدفوعات العمولات ───────────────────────────────────────────────────────────
