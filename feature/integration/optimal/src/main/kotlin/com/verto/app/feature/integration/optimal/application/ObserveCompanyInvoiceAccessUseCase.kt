package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyInvoicesRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Revalidates both the expected tenant and invoice ownership for as long as the screen is open. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveCompanyInvoiceAccessUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalCompanyInvoicesRepository,
) {
    operator fun invoke(
        expectedOrganizationId: String,
        invoiceId: String,
    ): Flow<Boolean> {
        val expected = expectedOrganizationId.trim()
        val invoice = invoiceId.trim()
        if (expected.isBlank() || invoice.isBlank()) return flowOf(false)

        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { activeOrganizationId ->
                if (activeOrganizationId != expected) {
                    flowOf(false)
                } else {
                    repository.observeInvoiceAccess(
                        organizationId = expected,
                        invoiceId = invoice,
                    )
                }
            }
            .distinctUntilChanged()
    }
}
