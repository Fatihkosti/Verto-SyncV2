package com.verto.app.feature.integration.optimal.domain.model

enum class CompanyInvoiceLifecycle {
    ACTIVE,
    VOIDED,
}

enum class CompanyInvoiceSettlement {
    CASH,
    CREDIT,
}

enum class CompanyInvoiceSyncStatus {
    PENDING,
    SYNCED,
}

/** Independent filters over the original Verto invoices owned by linked Optimal companies. */
data class CompanyInvoiceFilters(
    val companyId: String? = null,
    val fromDateInclusive: Long? = null,
    val toDateExclusive: Long? = null,
    val lifecycle: CompanyInvoiceLifecycle? = null,
    val settlement: CompanyInvoiceSettlement? = null,
    val vehicleSearch: String = "",
    val syncStatus: CompanyInvoiceSyncStatus? = null,
) {
    fun normalized(): CompanyInvoiceFilters {
        val from = fromDateInclusive
        val to = toDateExclusive
        require(from == null || from >= 0L) { "fromDateInclusive cannot be negative" }
        require(to == null || to >= 0L) { "toDateExclusive cannot be negative" }
        require(from == null || to == null || from < to) {
            "fromDateInclusive must precede toDateExclusive"
        }
        return copy(
            companyId = companyId?.trim()?.takeIf(String::isNotEmpty),
            vehicleSearch = vehicleSearch.trim(),
        )
    }

    fun hasActiveFilters(): Boolean =
        companyId != null ||
            fromDateInclusive != null ||
            toDateExclusive != null ||
            lifecycle != null ||
            settlement != null ||
            vehicleSearch.isNotBlank() ||
            syncStatus != null
}

data class CompanyInvoiceListItem(
    val organizationId: String,
    val invoiceId: String,
    val invoiceNumber: Int,
    val companyId: String,
    val companyName: String,
    val optimalCompanyId: String,
    val description: String,
    val totalAmount: Double,
    val createdAt: Long,
    val dueDate: Long,
    val lifecycle: CompanyInvoiceLifecycle,
    val settlement: CompanyInvoiceSettlement,
    val syncStatus: CompanyInvoiceSyncStatus,
    val vehicleName: String,
    val vehicleType: String,
    val plateNumber: String,
)

data class CompanyInvoiceCompanyOption(
    val companyId: String,
    val companyName: String,
)

data class CompanyInvoiceFilterOptions(
    val companies: List<CompanyInvoiceCompanyOption> = emptyList(),
    val earliestDate: Long? = null,
    val latestDate: Long? = null,
) {
    val hasDateValues: Boolean
        get() = earliestDate != null && latestDate != null
}

data class CompanyInvoicesSnapshot(
    val invoices: List<CompanyInvoiceListItem> = emptyList(),
    val filterOptions: CompanyInvoiceFilterOptions = CompanyInvoiceFilterOptions(),
)
