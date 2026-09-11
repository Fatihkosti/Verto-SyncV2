package com.verto.app.feature.integration.optimal.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.CompanyEventsReadModel
import com.verto.app.feature.integration.optimal.application.ObserveOptimalCompanyDetailsUseCase
import com.verto.app.feature.integration.optimal.domain.model.CompanyEvent
import com.verto.app.feature.integration.optimal.domain.model.OptimalCompany
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

internal data class OptimalCompanyDetailsUiState(
    val isLoading: Boolean = true,
    val company: OptimalCompany? = null,
    val events: List<CompanyEvent> = emptyList(),
    val canViewInvoices: Boolean = false,
    val canViewMessages: Boolean = false,
    val accessDenied: Boolean = false,
)

@HiltViewModel
class OptimalCompanyDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeCompanyDetails: ObserveOptimalCompanyDetailsUseCase,
    companyEvents: CompanyEventsReadModel,
) : ViewModel() {
    private val clientId: String = savedStateHandle
        .get<String>(OptimalNavigation.COMPANY_ID_ARG)
        .orEmpty()
        .trim()

    private val companyFlow = if (clientId.isBlank()) {
        flowOf(null)
    } else {
        observeCompanyDetails(clientId).catch { emit(null) }
    }
    private val eventsFlow = if (clientId.isBlank()) {
        flowOf(
            com.verto.app.feature.integration.optimal.domain.model.CompanyEventsSnapshot(
                accessDenied = true,
            ),
        )
    } else {
        companyEvents(clientId).catch {
            emit(
                com.verto.app.feature.integration.optimal.domain.model.CompanyEventsSnapshot(
                    clientId = clientId,
                    accessDenied = true,
                ),
            )
        }
    }

    internal val uiState: StateFlow<OptimalCompanyDetailsUiState> = combine(
        companyFlow,
        eventsFlow,
    ) { company, snapshot ->
        OptimalCompanyDetailsUiState(
            isLoading = false,
            company = company,
            events = snapshot.events,
            canViewInvoices = snapshot.canViewInvoices,
            canViewMessages = snapshot.canViewMessages,
            accessDenied = snapshot.accessDenied,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OptimalCompanyDetailsUiState(),
    )
}
