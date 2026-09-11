package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OptimalCompanyReadDao
import com.verto.app.data.local.dao.OptimalCompanyReadRow
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkFilter
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkStatus
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomOptimalCompanyRepository @Inject constructor(
    private val dao: OptimalCompanyReadDao,
) : OptimalCompanyRepository {
    override fun observeCompanies(
        organizationId: String,
        searchTerm: String,
        linkFilter: OptimalCompanyLinkFilter,
    ): Flow<List<OptimalCompany>> = dao.observeCompanies(
        organizationId = organizationId,
        searchTerm = searchTerm,
        linkFilter = linkFilter.toDatabaseValue(),
    ).map { rows -> rows.map(OptimalCompanyReadRow::toDomain) }
}

private fun OptimalCompanyLinkFilter.toDatabaseValue(): Int = when (this) {
    OptimalCompanyLinkFilter.ALL -> 0
    OptimalCompanyLinkFilter.LINKED -> 1
    OptimalCompanyLinkFilter.UNLINKED -> 2
}

private fun OptimalCompanyReadRow.toDomain(): OptimalCompany = OptimalCompany(
    organizationId = organizationId,
    clientId = clientId,
    name = clientName,
    customerSegment = customerSegment.trim().uppercase(),
    linkStatus = if (isLinked) {
        OptimalCompanyLinkStatus.LINKED
    } else {
        OptimalCompanyLinkStatus.UNLINKED
    },
    optimalCompanyId = optimalCompanyId,
    linkedAt = linkedAt,
    invoiceCount = invoiceCount.coerceAtLeast(0),
)
