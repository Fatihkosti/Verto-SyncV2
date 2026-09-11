package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkFilter
import kotlinx.coroutines.flow.Flow

interface OptimalCompanyRepository {
    fun observeCompanies(
        organizationId: String,
        searchTerm: String,
        linkFilter: OptimalCompanyLinkFilter,
    ): Flow<List<OptimalCompany>>
}
