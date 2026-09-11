package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OptimalCompanyInvoiceReadDao
import com.verto.app.data.local.dao.OptimalCompanyInvoiceReadRow
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceCompanyOption
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilterOptions
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilters
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceLifecycle
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceListItem
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceSettlement
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceSyncStatus
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoicesSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalCompanyInvoicesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomOptimalCompanyInvoicesRepository @Inject constructor(
    private val dao: OptimalCompanyInvoiceReadDao,
) : OptimalCompanyInvoicesRepository {
    override fun observeInvoices(
        organizationId: String,
        filters: CompanyInvoiceFilters,
    ): Flow<CompanyInvoicesSnapshot> {
        val organization = organizationId.trim()
        require(organization.isNotEmpty()) { "organizationId is required" }
        val normalized = filters.normalized()

        return combine(
            dao.observeInvoices(
                organizationId = organization,
                companyId = normalized.companyId.orEmpty(),
                fromDateInclusive = normalized.fromDateInclusive,
                toDateExclusive = normalized.toDateExclusive,
                lifecycle = normalized.lifecycle?.name.orEmpty(),
                settlement = normalized.settlement?.name.orEmpty(),
                vehicleSearch = normalized.vehicleSearch,
                syncStatus = normalized.syncStatus?.name.orEmpty(),
            ),
            dao.observeCompanies(organization),
            dao.observeDateBounds(organization),
        ) { rows, companies, bounds ->
            CompanyInvoicesSnapshot(
                invoices = rows.map(OptimalCompanyInvoiceReadRow::toDomain),
                filterOptions = CompanyInvoiceFilterOptions(
                    companies = companies
                        .map {
                            CompanyInvoiceCompanyOption(
                                companyId = it.companyId.trim(),
                                companyName = it.companyName.trim().ifBlank { "شركة غير معروفة" },
                            )
                        }
                        .distinctBy(CompanyInvoiceCompanyOption::companyId)
                        .sortedBy { it.companyName.lowercase() },
                    earliestDate = bounds.earliestDate,
                    latestDate = bounds.latestDate,
                ),
            )
        }
    }

    override fun observeInvoiceAccess(
        organizationId: String,
        invoiceId: String,
    ): Flow<Boolean> {
        val organization = organizationId.trim()
        val invoice = invoiceId.trim()
        require(organization.isNotEmpty()) { "organizationId is required" }
        require(invoice.isNotEmpty()) { "invoiceId is required" }
        return dao.observeInvoiceOwnedByOrganization(
            organizationId = organization,
            invoiceId = invoice,
        )
    }
}

private fun OptimalCompanyInvoiceReadRow.toDomain(): CompanyInvoiceListItem =
    CompanyInvoiceListItem(
        organizationId = organizationId.trim(),
        invoiceId = invoiceId.trim(),
        invoiceNumber = invoiceNumber,
        companyId = companyId.trim(),
        companyName = companyName.trim().ifBlank { "شركة غير معروفة" },
        optimalCompanyId = optimalCompanyId.trim(),
        description = description.trim(),
        totalAmount = totalAmount,
        createdAt = createdAt,
        dueDate = dueDate,
        lifecycle = if (voided) CompanyInvoiceLifecycle.VOIDED else CompanyInvoiceLifecycle.ACTIVE,
        settlement = when (invoiceStatus) {
            "CLOSED_CASH" -> CompanyInvoiceSettlement.CASH
            "CLOSED_CREDIT" -> CompanyInvoiceSettlement.CREDIT
            else -> error("Unsupported invoice status: $invoiceStatus")
        },
        syncStatus = if (isDirty) CompanyInvoiceSyncStatus.PENDING else CompanyInvoiceSyncStatus.SYNCED,
        vehicleName = vehicleName.trim(),
        vehicleType = vehicleType.trim(),
        plateNumber = plateNumber.trim(),
    )
