package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyQuery
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOptimalCompaniesUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalCompanyRepository,
) {
    operator fun invoke(query: OptimalCompanyQuery = OptimalCompanyQuery()): Flow<List<OptimalCompany>> {
        val normalizedSearch = query.searchTerm.trim()
        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                if (organizationId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    repository.observeCompanies(
                        organizationId = organizationId,
                        searchTerm = normalizedSearch,
                        linkFilter = query.linkFilter,
                    )
                }
            }
    }
}
