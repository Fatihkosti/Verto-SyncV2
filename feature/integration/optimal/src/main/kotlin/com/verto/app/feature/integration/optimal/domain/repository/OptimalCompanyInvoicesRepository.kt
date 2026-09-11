package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilters
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoicesSnapshot
import kotlinx.coroutines.flow.Flow

interface OptimalCompanyInvoicesRepository {
    fun observeInvoices(
        organizationId: String,
        filters: CompanyInvoiceFilters,
    ): Flow<CompanyInvoicesSnapshot>

    fun observeInvoiceAccess(
        organizationId: String,
        invoiceId: String,
    ): Flow<Boolean>
}
