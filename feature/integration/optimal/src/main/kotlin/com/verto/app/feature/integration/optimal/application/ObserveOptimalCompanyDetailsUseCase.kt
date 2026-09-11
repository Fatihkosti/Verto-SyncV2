package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveOptimalCompanyDetailsUseCase @Inject constructor(
    private val observeCompanies: ObserveOptimalCompaniesUseCase,
) {
    operator fun invoke(clientId: String): Flow<OptimalCompany?> {
        val normalizedClientId = clientId.trim()
        return observeCompanies(OptimalCompanyQuery())
            .map { companies -> companies.firstOrNull { it.clientId == normalizedClientId } }
    }
}
