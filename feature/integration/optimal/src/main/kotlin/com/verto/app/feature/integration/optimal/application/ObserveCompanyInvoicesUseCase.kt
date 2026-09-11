package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilters
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoicesSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyInvoicesRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCompanyInvoicesUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalCompanyInvoicesRepository,
) {
    operator fun invoke(
        filters: CompanyInvoiceFilters = CompanyInvoiceFilters(),
    ): Flow<CompanyInvoicesSnapshot> {
        val normalizedFilters = filters.normalized()
        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                if (organizationId.isBlank()) {
                    flowOf(CompanyInvoicesSnapshot())
                } else {
                    repository.observeInvoices(
                        organizationId = organizationId,
                        filters = normalizedFilters,
                    )
                }
            }
    }
}
