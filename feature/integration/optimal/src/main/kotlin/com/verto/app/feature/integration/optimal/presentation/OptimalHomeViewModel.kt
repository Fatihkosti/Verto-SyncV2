package com.verto.app.feature.integration.optimal.presentation

import dagger.hilt.android.qualifiers.ApplicationContext

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.integration.optimal.application.ObserveOptimalHomeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class OptimalHomeViewModel @Inject constructor(
    private val observeOptimalHome: ObserveOptimalHomeUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OptimalPresentationState())
    val uiState: StateFlow<OptimalPresentationState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        observe()
    }

    private fun observe() {
        observationJob?.cancel()
        _uiState.value = OptimalPresentationState()
        observationJob = viewModelScope.launch {
            observeOptimalHome()
                .catch {
                    _uiState.value = OptimalPresentationState(
                        isLoading = false,
                        errorMessage = "تعذّر تحميل أقسام Optimal",
                    )
                }
                .collect { snapshot ->
                    _uiState.value = snapshot.toPresentationState(context::getString)
                }
        }
    }
}
