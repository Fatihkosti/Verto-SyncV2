package com.verto.app.feature.integration.optimal.domain.model

data class OptimalCompany(
    val organizationId: String,
    val clientId: String,
    val name: String,
    val customerSegment: String,
    val linkStatus: OptimalCompanyLinkStatus,
    val optimalCompanyId: String?,
    val linkedAt: Long?,
    val invoiceCount: Int,
)

enum class OptimalCompanyLinkStatus {
    LINKED,
    UNLINKED,
}

enum class OptimalCompanyLinkFilter {
    ALL,
    LINKED,
    UNLINKED,
}

data class OptimalCompanyQuery(
    val searchTerm: String = "",
    val linkFilter: OptimalCompanyLinkFilter = OptimalCompanyLinkFilter.ALL,
)
