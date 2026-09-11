package com.verto.app.feature.organization.domain.model

/**
 * نموذج صلاحيات الموظف المملوك لميزة المؤسسة.
 * لا يحتوي على تفاصيل التخزين أو عميل الشبكة.
 */
data class EmployeePermissions(
    val userId: String = "",
    val orgId: String = "",
    val salesView: Boolean = true,
    val salesCreate: Boolean = false,
    val salesEdit: Boolean = false,
    val salesDelete: Boolean = false,
    val salesExport: Boolean = false,
    val purchasesView: Boolean = true,
    val purchasesCreate: Boolean = false,
    val purchasesEdit: Boolean = false,
    val purchasesDelete: Boolean = false,
    val purchasesExport: Boolean = false,
    val clientsView: Boolean = true,
    val clientsEdit: Boolean = false,
    val clientsDelete: Boolean = false,
    val clientsAddPayment: Boolean = false,
    val suppliersAddPayment: Boolean = false,
    val inventoryView: Boolean = true,
    val inventoryEdit: Boolean = false,
    val inventoryPrice: Boolean = false,
    val inventoryImport: Boolean = false,
    val inventoryExport: Boolean = false,
    val reportsSummary: Boolean = true,
    val reportsDetails: Boolean = false,
    val reportsFull: Boolean = false,
    val reportsExport: Boolean = false,
    val expensesView: Boolean = true,
    val expensesCreate: Boolean = false,
    val expensesDelete: Boolean = false,
    val cashAdjust: Boolean = false,
    val paymentsReverse: Boolean = false,
    val commissionManage: Boolean = false,
    val marketingDashboards: Boolean = false,
    val shipmentsView: Boolean = true,
    val shipmentsManage: Boolean = false,
    val shipmentsConfirm: Boolean = false,
    val settingsPassword: Boolean = false,
    val settingsOrgData: Boolean = false,
    val settingsReset: Boolean = false,
    val viewManagement: Boolean = false,
    val viewOptimal: Boolean = false,
    val viewOptimalCompanies: Boolean = false,
    val issueOptimalCode: Boolean = false,
    val viewOptimalMessages: Boolean = false,
    val sendOptimalMessages: Boolean = false,
    val viewOptimalInvoices: Boolean = false,
    val viewOptimalMaintenance: Boolean = false,
    val viewOptimalSyncIssues: Boolean = false,
    val retryOptimalSync: Boolean = false
) {
    companion object {
        fun defaultEmployee(userId: String = "", orgId: String = "") = EmployeePermissions(
            userId = userId,
            orgId = orgId,
            salesView = true,
            purchasesView = true,
            clientsView = true,
            inventoryView = true,
            reportsSummary = true,
            expensesView = true,
            shipmentsView = true
        )
    }

    fun hasAnyEnabledPermission(): Boolean = listOf(
        salesView, salesCreate, salesEdit, salesDelete, salesExport,
        purchasesView, purchasesCreate, purchasesEdit, purchasesDelete, purchasesExport,
        clientsView, clientsEdit, clientsDelete, clientsAddPayment, suppliersAddPayment,
        inventoryView, inventoryEdit, inventoryPrice, inventoryImport, inventoryExport,
        reportsSummary, reportsDetails, reportsFull, reportsExport,
        expensesView, expensesCreate, expensesDelete, cashAdjust,
        paymentsReverse, commissionManage, marketingDashboards,
        shipmentsView, shipmentsManage, shipmentsConfirm,
        settingsPassword, settingsOrgData, settingsReset,
        viewManagement, viewOptimal, viewOptimalCompanies, issueOptimalCode,
        viewOptimalMessages, sendOptimalMessages, viewOptimalInvoices,
        viewOptimalMaintenance, viewOptimalSyncIssues, retryOptimalSync
    ).any { it }
}

data class OrganizationEmployee(
    val userId: String,
    val name: String,
    val email: String = "",
    val joinedAt: String = "",
    val isActive: Boolean = true,
    val permissions: EmployeePermissions
)

data class CreateOrganizationInviteRequest(
    val employeeName: String,
    val jobTitle: String,
    val actualJoinDate: String,
    val permissions: EmployeePermissions
)
