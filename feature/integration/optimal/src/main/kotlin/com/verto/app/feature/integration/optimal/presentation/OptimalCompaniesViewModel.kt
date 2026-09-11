package com.verto.app.feature.integration.optimal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveOptimalCompaniesUseCase
import com.verto.app.feature.integration.optimal.data.OptimalCompaniesRemoteRefresher
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyLinkFilter
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompanyQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class OptimalCompaniesUiState(
    val isLoading: Boolean = true,
    val companies: List<OptimalCompany> = emptyList(),
    val searchTerm: String = "",
    val linkFilter: OptimalCompanyLinkFilter = OptimalCompanyLinkFilter.ALL,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OptimalCompaniesViewModel @Inject constructor(
    observeCompanies: ObserveOptimalCompaniesUseCase,
    private val remoteRefresher: OptimalCompaniesRemoteRefresher,
) : ViewModel() {
    private val query = MutableStateFlow(OptimalCompanyQuery())

    internal val uiState: StateFlow<OptimalCompaniesUiState> = query
        .flatMapLatest { currentQuery ->
            observeCompanies(currentQuery)
                .map { companies ->
                    OptimalCompaniesUiState(
                        isLoading = false,
                        companies = companies,
                        searchTerm = currentQuery.searchTerm,
                        linkFilter = currentQuery.linkFilter,
                    )
                }
                .onStart {
                    emit(OptimalCompaniesUiState(
                        isLoading = true,
                        searchTerm = currentQuery.searchTerm,
                        linkFilter = currentQuery.linkFilter,
                    ))
                }
                .catch {
                    emit(OptimalCompaniesUiState(
                        isLoading = false,
                        searchTerm = currentQuery.searchTerm,
                        linkFilter = currentQuery.linkFilter,
                        errorMessage = "تعذّر تحميل شركات Optimal",
                    ))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OptimalCompaniesUiState(),
        )

    fun refresh() {
        viewModelScope.launch {
            remoteRefresher.refresh()
        }
    }

    fun updateSearch(searchTerm: String) {
        query.value = query.value.copy(searchTerm = searchTerm)
    }

    fun updateFilter(linkFilter: OptimalCompanyLinkFilter) {
        query.value = query.value.copy(linkFilter = linkFilter)
    }
}
