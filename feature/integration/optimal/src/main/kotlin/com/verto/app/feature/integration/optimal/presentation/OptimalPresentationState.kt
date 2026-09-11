package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.application.OptimalHomeSnapshot
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation

data class OptimalPresentationState(
    val isLoading: Boolean = true,
    val sections: List<OptimalHomeSection> = emptyList(),
    val errorMessage: String? = null,
)

data class OptimalHomeSection(
    val id: OptimalHomeSectionId,
    val title: String,
    val description: String,
    val route: String,
    val icon: OptimalHomeSectionIcon,
    val badgeCount: Int? = null,
)

enum class OptimalHomeSectionId {
    COMPANIES,
    MESSAGES,
    INVOICES,
    MAINTENANCE,
    CODES,
    SYNC_ISSUES,
}

enum class OptimalHomeSectionIcon {
    COMPANIES,
    MESSAGES,
    INVOICES,
    MAINTENANCE,
    CODES,
    SYNC_ISSUES,
}

internal fun OptimalHomeSnapshot.toPresentationState(resolveString: (Int) -> String): OptimalPresentationState {
    val permissions = grantedPermissions
    val sections = buildList {
        addIfAllowed(
            permission = ManagementOptimalPermission.VIEW_OPTIMAL_COMPANIES,
            permissions = permissions,
            section = OptimalHomeSection(
                id = OptimalHomeSectionId.COMPANIES,
                title = resolveString(com.verto.feature.integration.optimal.R.string.optimal_v298_home_companies),
                description = "الشركات المرتبطة وحالة الربط",
                route = OptimalNavigation.COMPANIES_ROUTE,
                icon = OptimalHomeSectionIcon.COMPANIES,
            ),
        )
        addIfAllowed(
            permission = ManagementOptimalPermission.VIEW_OPTIMAL_MESSAGES,
            permissions = permissions,
            section = OptimalHomeSection(
                id = OptimalHomeSectionId.MESSAGES,
                title = resolveString(com.verto.feature.integration.optimal.R.string.optimal_v298_home_messages),
                description = "محادثات الشركات والرسائل الواردة",
                route = OptimalNavigation.MESSAGES_ROUTE,
                icon = OptimalHomeSectionIcon.MESSAGES,
                badgeCount = unreadMessages.takeIf { it > 0 },
            ),
        )
        addIfAllowed(
            permission = ManagementOptimalPermission.VIEW_OPTIMAL_INVOICES,
            permissions = permissions,
            section = OptimalHomeSection(
                id = OptimalHomeSectionId.INVOICES,
                title = resolveString(com.verto.feature.integration.optimal.R.string.optimal_v298_home_invoices),
                description = "فواتير Optimal وحالة المزامنة",
                route = OptimalNavigation.INVOICES_ROUTE,
                icon = OptimalHomeSectionIcon.INVOICES,
            ),
        )
        addIfAllowed(
            permission = ManagementOptimalPermission.VIEW_OPTIMAL_MAINTENANCE,
            permissions = permissions,
            section = OptimalHomeSection(
                id = OptimalHomeSectionId.MAINTENANCE,
                title = resolveString(com.verto.feature.integration.optimal.R.string.optimal_v298_home_maintenance),
                description = "طلبات الصيانة المرتبطة بالفواتير",
                route = OptimalNavigation.MAINTENANCE_ROUTE,
                icon = OptimalHomeSectionIcon.MAINTENANCE,
            ),
        )
        addIfAllowed(
            permission = ManagementOptimalPermission.ISSUE_OPTIMAL_CODE,
            permissions = permissions,
            section = OptimalHomeSection(
                id = OptimalHomeSectionId.CODES,
                title = resolveString(com.verto.feature.integration.optimal.R.string.optimal_v298_home_codes),
                description = "توليد كود لانضمام شركة Optimal إلى Verto",
                route = OptimalNavigation.CODES_ROUTE,
                icon = OptimalHomeSectionIcon.CODES,
            ),
        )
        addIfAllowed(
            permission = ManagementOptimalPermission.VIEW_OPTIMAL_SYNC_ISSUES,
            permissions = permissions,
            section = OptimalHomeSection(
                id = OptimalHomeSectionId.SYNC_ISSUES,
                title = resolveString(com.verto.feature.integration.optimal.R.string.optimal_v298_home_sync_issues),
                description = "العمليات المحلية التي تحتاج متابعة",
                route = OptimalNavigation.SYNC_ISSUES_ROUTE,
                icon = OptimalHomeSectionIcon.SYNC_ISSUES,
                badgeCount = syncIssues.takeIf { it > 0 },
            ),
        )
    }
    return OptimalPresentationState(
        isLoading = false,
        sections = sections,
    )
}

private fun MutableList<OptimalHomeSection>.addIfAllowed(
    permission: ManagementOptimalPermission,
    permissions: Set<ManagementOptimalPermission>,
    section: OptimalHomeSection,
) {
    if (permission in permissions) add(section)
}
