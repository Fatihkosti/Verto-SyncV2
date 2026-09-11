package com.verto.app.feature.organization.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.organization.domain.model.OrganizationSettings
import com.verto.app.feature.organization.domain.repository.OrganizationSettingsGateway
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OrganizationSaveState {
    data object Idle : OrganizationSaveState
    data object Loading : OrganizationSaveState
    data object Success : OrganizationSaveState
    data class Error(val message: String) : OrganizationSaveState
}

data class OrganizationSettingsUiState(
    val settings: OrganizationSettings = OrganizationSettings(),
    val saveState: OrganizationSaveState = OrganizationSaveState.Idle
) : UiState

sealed interface OrganizationSettingsEvent : UiEvent {
    data class Save(val settings: OrganizationSettings) : OrganizationSettingsEvent
}

/** يملك قراءة وحفظ بيانات المؤسسة فقط. */
@HiltViewModel
class OrganizationSettingsViewModel @Inject constructor(
    private val gateway: OrganizationSettingsGateway
) : ViewModel(),
    UiStateHolder<OrganizationSettingsUiState>,
    UiEventHandler<OrganizationSettingsEvent> {

    val settings: StateFlow<OrganizationSettings> = gateway.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OrganizationSettings()
    )

    private val _saveState = MutableStateFlow<OrganizationSaveState>(OrganizationSaveState.Idle)
    val saveState: StateFlow<OrganizationSaveState> = _saveState.asStateFlow()

    override val uiState: StateFlow<OrganizationSettingsUiState> =
        combine(settings, saveState) { currentSettings, currentSaveState ->
            OrganizationSettingsUiState(
                settings = currentSettings,
                saveState = currentSaveState
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            OrganizationSettingsUiState()
        )

    override fun onEvent(event: OrganizationSettingsEvent) {
        when (event) {
            is OrganizationSettingsEvent.Save -> saveInternal(event.settings)
        }
    }

    fun save(settings: OrganizationSettings) = onEvent(OrganizationSettingsEvent.Save(settings))

    private fun saveInternal(settings: OrganizationSettings) = viewModelScope.launch {
        _saveState.value = OrganizationSaveState.Loading
        runCatching { gateway.save(settings) }
            .onSuccess {
                _saveState.value = OrganizationSaveState.Success
                delay(2_000)
                _saveState.value = OrganizationSaveState.Idle
            }
            .onFailure { error ->
                _saveState.value = OrganizationSaveState.Error(ErrorHumanizer.humanize(error))
            }
    }
}
