package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.utils.ErrorHumanizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveCompanyInvoicesUseCase
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilterOptions
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceFilters
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceLifecycle
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceListItem
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceSettlement
import com.verto.app.feature.integration.optimal.domain.model.CompanyInvoiceSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class CompanyInvoicesViewModel @Inject constructor(
    private val observeCompanyInvoices: ObserveCompanyInvoicesUseCase,
) : ViewModel() {
    private val filters = MutableStateFlow(CompanyInvoiceFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CompanyInvoicesUiState> = filters
        .flatMapLatest { current ->
            observeCompanyInvoices(current)
                .map { snapshot ->
                    CompanyInvoicesUiState(
                        isLoading = false,
                        filters = current,
                        invoices = snapshot.invoices,
                        filterOptions = snapshot.filterOptions,
                    )
                }
                .catch { error ->
                    emit(
                        CompanyInvoicesUiState(
                            isLoading = false,
                            filters = current,
                            errorMessage = ErrorHumanizer.humanize(error, "تحميل فواتير الشركات"),
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CompanyInvoicesUiState(),
        )

    fun updateCompany(companyId: String?) = update { copy(companyId = companyId) }
    fun updateLifecycle(value: CompanyInvoiceLifecycle?) = update { copy(lifecycle = value) }
    fun updateSettlement(value: CompanyInvoiceSettlement?) = update { copy(settlement = value) }
    fun updateVehicleSearch(value: String) = update { copy(vehicleSearch = value) }
    fun updateSyncStatus(value: CompanyInvoiceSyncStatus?) = update { copy(syncStatus = value) }

    fun updateFromDate(value: Long?) = update {
        copy(
            fromDateInclusive = value,
            toDateExclusive = toDateExclusive?.takeIf { value == null || value < it },
        )
    }

    fun updateToDateExclusive(value: Long?) = update {
        copy(
            toDateExclusive = value,
            fromDateInclusive = fromDateInclusive?.takeIf { value == null || it < value },
        )
    }

    fun clearFilters() {
        filters.value = CompanyInvoiceFilters()
    }

    fun retry() {
        filters.value = filters.value.copy()
    }

    private inline fun update(transform: CompanyInvoiceFilters.() -> CompanyInvoiceFilters) {
        filters.update { it.transform() }
    }
}

data class CompanyInvoicesUiState(
    val isLoading: Boolean = true,
    val filters: CompanyInvoiceFilters = CompanyInvoiceFilters(),
    val invoices: List<CompanyInvoiceListItem> = emptyList(),
    val filterOptions: CompanyInvoiceFilterOptions = CompanyInvoiceFilterOptions(),
    val errorMessage: String? = null,
)
