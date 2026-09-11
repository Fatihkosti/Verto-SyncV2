package com.verto.app.ui.screens.home

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.model.EmployeePermissions
import com.verto.app.data.remote.PermissionProvider
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomePermissionKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun observeHomePermissionContext(
    sessionReader: SessionReader,
    permissionProvider: PermissionProvider,
): Flow<HomePermissionContext?> = combine(
    sessionReader.organizationId,
    sessionReader.userId,
    sessionReader.role,
    permissionProvider.permissions,
) { organizationId, userId, role, permissions ->
    if (organizationId.isBlank() || userId.isBlank()) {
        null
    } else {
        HomePermissionContext(
            organizationId = organizationId,
            userId = userId,
            grantedPermissions = permissions.toHomePermissionKeys(role),
        )
    }
}.distinctUntilChanged()

internal fun EmployeePermissions?.toHomePermissionKeys(role: String): Set<String> = buildSet {
    val permissions = this@toHomePermissionKeys
    val isAdmin = role.equals("admin", ignoreCase = true)

    if (isAdmin) add(HomePermissionKeys.ADMIN)
    if (permissions == null) return@buildSet

    if (permissions.clientsView || isAdmin) add(HomePermissionKeys.CLIENTS_VIEW)
    if (permissions.clientsEdit || isAdmin) add(HomePermissionKeys.CLIENTS_EDIT)
    if (permissions.clientsAddPayment || isAdmin) add(HomePermissionKeys.CLIENTS_ADD_PAYMENT)
    if (permissions.suppliersAddPayment || isAdmin) add(HomePermissionKeys.SUPPLIERS_ADD_PAYMENT)

    if (permissions.salesView || isAdmin) add(HomePermissionKeys.SALES_VIEW)
    if (permissions.salesCreate || isAdmin) add(HomePermissionKeys.SALES_CREATE)
    if (permissions.salesEdit || isAdmin) add(HomePermissionKeys.SALES_EDIT)
    if (permissions.purchasesView || isAdmin) add(HomePermissionKeys.PURCHASES_VIEW)
    if (permissions.purchasesCreate || isAdmin) add(HomePermissionKeys.PURCHASES_CREATE)
    if (permissions.purchasesEdit || isAdmin) add(HomePermissionKeys.PURCHASES_EDIT)

    if (permissions.inventoryView || isAdmin) add(HomePermissionKeys.INVENTORY_VIEW)
    if (permissions.inventoryEdit || isAdmin) add(HomePermissionKeys.INVENTORY_EDIT)
    if (permissions.inventoryPrice || isAdmin) add(HomePermissionKeys.INVENTORY_PRICE)

    if (permissions.reportsSummary || permissions.reportsDetails || permissions.reportsFull || isAdmin) {
        add(HomePermissionKeys.REPORTS_VIEW)
    }
    if (permissions.expensesView || isAdmin) add(HomePermissionKeys.EXPENSES_VIEW)
    if (permissions.expensesCreate || isAdmin) add(HomePermissionKeys.EXPENSES_CREATE)
    if (permissions.commissionManage || isAdmin) add(HomePermissionKeys.COMMISSION_MANAGE)
    if (permissions.shipmentsView || isAdmin) add(HomePermissionKeys.SHIPMENTS_VIEW)
    if (permissions.marketingDashboards || isAdmin) add(HomePermissionKeys.MARKETING_DASHBOARDS)
    if (permissions.settingsPassword || isAdmin) add(HomePermissionKeys.SETTINGS_PASSWORD)
    if (permissions.settingsOrgData || isAdmin) add(HomePermissionKeys.SETTINGS_ORG_DATA)
    if (permissions.paymentsReverse || isAdmin) add(HomePermissionKeys.PAYMENTS_REVERSE)

    if (permissions.viewManagement || isAdmin) add(HomePermissionKeys.VIEW_MANAGEMENT)
    if (permissions.viewOptimal || isAdmin) add(HomePermissionKeys.VIEW_OPTIMAL)
    if (permissions.viewOptimalCompanies || isAdmin) add(HomePermissionKeys.VIEW_OPTIMAL_COMPANIES)
    if (permissions.viewOptimalMessages || isAdmin) add(HomePermissionKeys.VIEW_OPTIMAL_MESSAGES)
    if (permissions.viewOptimalInvoices || isAdmin) add(HomePermissionKeys.VIEW_OPTIMAL_INVOICES)
    if (permissions.viewOptimalMaintenance || isAdmin) add(HomePermissionKeys.VIEW_OPTIMAL_MAINTENANCE)
    if (permissions.issueOptimalCode || isAdmin) add(HomePermissionKeys.ISSUE_OPTIMAL_CODE)
    if (permissions.viewOptimalSyncIssues || isAdmin) add(HomePermissionKeys.VIEW_OPTIMAL_SYNC_ISSUES)
}
