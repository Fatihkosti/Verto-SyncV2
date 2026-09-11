package com.verto.app.feature.integration.optimal.navigation

object OptimalNavigation {
    const val INTEGRATION_ID: String = "optimal"
    const val ROUTE: String = "management/optimal"

    const val COMPANIES_ROUTE: String = "$ROUTE/companies"
    const val COMPANY_ID_ARG: String = "companyId"
    const val COMPANY_DETAILS_ROUTE: String = "$COMPANIES_ROUTE/{$COMPANY_ID_ARG}"

    fun companyDetailsRoute(clientId: String): String = "$COMPANIES_ROUTE/$clientId"
    const val MESSAGES_ROUTE: String = "$ROUTE/messages"
    const val CHAT_CLIENT_ID_ARG: String = "clientId"
    const val CHAT_ROUTE: String = "$MESSAGES_ROUTE/{$CHAT_CLIENT_ID_ARG}"

    fun chatRoute(clientId: String): String = "$MESSAGES_ROUTE/$clientId"
    const val INVOICES_ROUTE: String = "$ROUTE/invoices"
    const val COMPANY_INVOICE_ORGANIZATION_ID_ARG: String = "organizationId"
    const val COMPANY_INVOICE_ID_ARG: String = "invoiceId"
    const val COMPANY_INVOICE_ROUTE: String =
        "$INVOICES_ROUTE/open/{$COMPANY_INVOICE_ORGANIZATION_ID_ARG}/{$COMPANY_INVOICE_ID_ARG}"

    fun companyInvoiceRoute(organizationId: String, invoiceId: String): String =
        "$INVOICES_ROUTE/open/${safeRouteSegment("organizationId", organizationId)}/${safeRouteSegment("invoiceId", invoiceId)}"

    const val MAINTENANCE_ROUTE: String = "$ROUTE/maintenance"
    const val MAINTENANCE_RECORD_ID_ARG: String = "recordId"
    const val MAINTENANCE_DETAILS_ROUTE: String = "$MAINTENANCE_ROUTE/{$MAINTENANCE_RECORD_ID_ARG}"

    fun maintenanceDetailsRoute(recordId: String): String =
        "$MAINTENANCE_ROUTE/${recordId.trim()}"
    const val CODES_ROUTE: String = "$ROUTE/codes"
    const val SYNC_ISSUES_ROUTE: String = "$ROUTE/sync-issues"

    private fun safeRouteSegment(name: String, rawValue: String): String {
        val value = rawValue.trim()
        require(value.isNotEmpty()) { "$name is required" }
        require(SAFE_ROUTE_SEGMENT.matches(value)) {
            "$name is not a safe route segment"
        }
        return value
    }

    private val SAFE_ROUTE_SEGMENT = Regex("[A-Za-z0-9_-]+")

    val sectionRoutes: Set<String> = setOf(
        COMPANIES_ROUTE,
        MESSAGES_ROUTE,
        INVOICES_ROUTE,
        MAINTENANCE_ROUTE,
        CODES_ROUTE,
        SYNC_ISSUES_ROUTE,
    )
}
