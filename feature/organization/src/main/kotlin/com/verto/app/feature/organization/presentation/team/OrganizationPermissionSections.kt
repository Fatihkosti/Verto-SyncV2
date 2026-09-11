package com.verto.app.feature.organization.presentation.team

import com.verto.app.feature.organization.domain.model.EmployeePermissions

/** قسم الصلاحيات — للعرض في الـ UI */
data class PermissionSection(
    val key: String,
    val label: String,
    val subPermissions: List<SubPermission>
)

data class SubPermission(
    val key: String,
    val label: String,
    val getValue: (EmployeePermissions) -> Boolean,
    val setValue: (EmployeePermissions, Boolean) -> EmployeePermissions
)

/** بناء قائمة الأقسام للعرض */
fun buildPermissionSections(resolveString: (Int) -> String): List<PermissionSection> = listOf(
    PermissionSection(
        key = "sales", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_sales),
        subPermissions = listOf(
            SubPermission("sales_view",   "عرض الفواتير",  { it.salesView },   { p, v -> p.copy(salesView = v) }),
            SubPermission("sales_create", "إنشاء فاتورة",  { it.salesCreate }, { p, v -> p.copy(salesCreate = v) }),
            SubPermission("sales_edit",   "تعديل فاتورة",  { it.salesEdit },   { p, v -> p.copy(salesEdit = v) }),
            SubPermission("sales_delete", "حذف فاتورة",    { it.salesDelete }, { p, v -> p.copy(salesDelete = v) }),
            SubPermission("sales_export", "تصدير PDF",     { it.salesExport }, { p, v -> p.copy(salesExport = v) }),
        )
    ),
    PermissionSection(
        key = "purchases", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_purchases),
        subPermissions = listOf(
            SubPermission("purchases_view",   "عرض الفواتير",  { it.purchasesView },   { p, v -> p.copy(purchasesView = v) }),
            SubPermission("purchases_create", "إنشاء فاتورة",  { it.purchasesCreate }, { p, v -> p.copy(purchasesCreate = v) }),
            SubPermission("purchases_edit",   "تعديل فاتورة",  { it.purchasesEdit },   { p, v -> p.copy(purchasesEdit = v) }),
            SubPermission("purchases_delete", "حذف فاتورة",    { it.purchasesDelete }, { p, v -> p.copy(purchasesDelete = v) }),
            SubPermission("purchases_export", "تصدير PDF",     { it.purchasesExport }, { p, v -> p.copy(purchasesExport = v) }),
        )
    ),
    PermissionSection(
        key = "clients", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_clients),
        subPermissions = listOf(
            SubPermission("clients_view",          "عرض العملاء والموردين", { it.clientsView },         { p, v -> p.copy(clientsView = v) }),
            SubPermission("clients_edit",          "إضافة / تعديل عميل",   { it.clientsEdit },         { p, v -> p.copy(clientsEdit = v) }),
            SubPermission("clients_delete",        "حذف عميل نهائياً",     { it.clientsDelete },       { p, v -> p.copy(clientsDelete = v) }),
            SubPermission("suppliers_add_payment", "سداد مورد",            { it.suppliersAddPayment }, { p, v -> p.copy(suppliersAddPayment = v) }),
        )
    ),
    PermissionSection(
        key = "inventory", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_inventory),
        subPermissions = listOf(
            SubPermission("inventory_view",   "عرض المخزون",       { it.inventoryView },   { p, v -> p.copy(inventoryView = v) }),
            SubPermission("inventory_edit",   "إضافة / تعديل صنف", { it.inventoryEdit },   { p, v -> p.copy(inventoryEdit = v) }),
            SubPermission("inventory_price",  "تعديل الأسعار",     { it.inventoryPrice },  { p, v -> p.copy(inventoryPrice = v) }),
            SubPermission("inventory_import", "استيراد CSV",        { it.inventoryImport }, { p, v -> p.copy(inventoryImport = v) }),
            SubPermission("inventory_export", "تصدير",             { it.inventoryExport }, { p, v -> p.copy(inventoryExport = v) }),
        )
    ),
    PermissionSection(
        key = "reports", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_reports),
        subPermissions = listOf(
            SubPermission("reports_summary", "عرض الملخص",    { it.reportsSummary }, { p, v -> p.copy(reportsSummary = v) }),
            SubPermission("reports_details", "عرض التفاصيل",  { it.reportsDetails }, { p, v -> p.copy(reportsDetails = v) }),
            SubPermission("reports_full",    "تقارير كاملة",  { it.reportsFull },    { p, v -> p.copy(reportsFull = v) }),
            SubPermission("reports_export",  "تصدير PDF",     { it.reportsExport },  { p, v -> p.copy(reportsExport = v) }),
        )
    ),
    PermissionSection(
        key = "expenses", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_expenses),
        subPermissions = listOf(
            SubPermission("expenses_view",   "عرض المصاريف", { it.expensesView },   { p, v -> p.copy(expensesView = v) }),
            SubPermission("expenses_create", "إضافة مصروف",  { it.expensesCreate }, { p, v -> p.copy(expensesCreate = v) }),
            SubPermission("expenses_delete", "حذف مصروف",    { it.expensesDelete }, { p, v -> p.copy(expensesDelete = v) }),
        )
    ),
    PermissionSection(
        key = "payments", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_payments),
        subPermissions = listOf(
            SubPermission("clients_add_payment", "إضافة دفعة عميل", { it.clientsAddPayment }, { p, v -> p.copy(clientsAddPayment = v) }),
            SubPermission("payments_reverse",    "عكس دفعة",         { it.paymentsReverse },   { p, v -> p.copy(paymentsReverse = v) }),
            SubPermission("cash_adjust",         "تسوية الصندوق",    { it.cashAdjust },        { p, v -> p.copy(cashAdjust = v) }),
        )
    ),
    PermissionSection(
        key = "commission", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_commission),
        subPermissions = listOf(
            SubPermission("commission_manage", "إدارة العمولات", { it.commissionManage }, { p, v -> p.copy(commissionManage = v) }),
        )
    ),
    PermissionSection(
        key = "marketing", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_marketing),
        subPermissions = listOf(
            SubPermission("marketing_dashboards", "عرض اللوحات", { it.marketingDashboards }, { p, v -> p.copy(marketingDashboards = v) }),
        )
    ),
    PermissionSection(
        key = "shipments", label = resolveString(com.verto.feature.organization.R.string.organization_v298_section_shipments),
        subPermissions = listOf(
            SubPermission("shipments_view", "عرض الشحنات", { it.shipmentsView }, { p, v -> p.copy(shipmentsView = v) }),
            SubPermission("shipments_manage", "إدارة الشحنات", { it.shipmentsManage }, { p, v -> p.copy(shipmentsManage = v) }),
            SubPermission("shipments_confirm", "تأكيد الشحنات", { it.shipmentsConfirm }, { p, v -> p.copy(shipmentsConfirm = v) }),
        )
    ),
)
