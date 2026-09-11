package com.verto.app.feature.integration.optimal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveMaintenanceRecordsUseCase
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordFilterOptions
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordListItem
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordQuery
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceSyncStatus
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

internal data class MaintenanceRecordsUiState(
    val isLoading: Boolean = true,
    val records: List<MaintenanceRecordListItem> = emptyList(),
    val filterOptions: MaintenanceRecordFilterOptions = MaintenanceRecordFilterOptions(),
    val query: MaintenanceRecordQuery = MaintenanceRecordQuery(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MaintenanceRecordsViewModel @Inject constructor(
    observeMaintenanceRecords: ObserveMaintenanceRecordsUseCase,
) : ViewModel() {
    private val query = MutableStateFlow(MaintenanceRecordQuery())

    internal val uiState: StateFlow<MaintenanceRecordsUiState> = query
        .flatMapLatest { currentQuery ->
            observeMaintenanceRecords(currentQuery)
                .map { snapshot ->
                    MaintenanceRecordsUiState(
                        isLoading = false,
                        records = snapshot.records,
                        filterOptions = snapshot.filterOptions,
                        query = currentQuery,
                    )
                }
                .onStart {
                    emit(
                        MaintenanceRecordsUiState(
                            isLoading = true,
                            query = currentQuery,
                        ),
                    )
                }
                .catch {
                    emit(
                        MaintenanceRecordsUiState(
                            isLoading = false,
                            query = currentQuery,
                            errorMessage = "تعذّر تحميل سجل الصيانة",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MaintenanceRecordsUiState(),
        )

    fun updateFreeText(value: String) = update { it.copy(freeText = value) }

    fun updateCompanySearch(value: String) = update { it.copy(companySearch = value) }

    fun updateVehicleSearch(value: String) = update { it.copy(vehicleSearch = value) }

    fun updateDriver(value: String?) = update { it.copy(driver = value) }

    fun updateSyncStatus(value: MaintenanceSyncStatus?) = update { it.copy(syncStatus = value) }

    fun updateFromDate(value: Long?) = update { current ->
        current.copy(
            fromDateInclusive = value,
            toDateExclusive = current.toDateExclusive?.takeIf { end -> value == null || value < end },
        )
    }

    fun updateToDateExclusive(value: Long?) = update { current ->
        current.copy(
            fromDateInclusive = current.fromDateInclusive?.takeIf { start -> value == null || start < value },
            toDateExclusive = value,
        )
    }

    fun clearFilters() {
        query.value = MaintenanceRecordQuery(freeText = query.value.freeText)
    }

    fun clearAll() {
        query.value = MaintenanceRecordQuery()
    }

    private inline fun update(transform: (MaintenanceRecordQuery) -> MaintenanceRecordQuery) {
        query.value = transform(query.value)
    }
}
