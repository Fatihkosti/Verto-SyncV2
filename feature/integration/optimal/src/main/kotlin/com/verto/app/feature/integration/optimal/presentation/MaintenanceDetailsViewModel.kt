package com.verto.app.feature.integration.optimal.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveMaintenanceDetailsUseCase
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceDetails
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

internal data class MaintenanceDetailsUiState(
    val isLoading: Boolean = true,
    val details: MaintenanceDetails? = null,
    val isNotFound: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class MaintenanceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMaintenanceDetails: ObserveMaintenanceDetailsUseCase,
) : ViewModel() {
    private val recordId: String = savedStateHandle
        .get<String>(OptimalNavigation.MAINTENANCE_RECORD_ID_ARG)
        .orEmpty()
        .trim()

    internal val uiState: StateFlow<MaintenanceDetailsUiState> = observeMaintenanceDetails(recordId)
        .map { details ->
            MaintenanceDetailsUiState(
                isLoading = false,
                details = details,
                isNotFound = details == null,
            )
        }
        .onStart { emit(MaintenanceDetailsUiState(isLoading = true)) }
        .catch {
            emit(
                MaintenanceDetailsUiState(
                    isLoading = false,
                    errorMessage = "تعذّر تحميل تفاصيل الصيانة",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MaintenanceDetailsUiState(),
        )
}
