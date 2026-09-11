package com.verto.app.feature.settings.presentation.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.settings.domain.repository.AppearanceSettingsGateway
import com.verto.app.utils.AppFontSize
import com.verto.app.utils.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppearanceSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val appFontSize: AppFontSize = AppFontSize.MEDIUM
) : UiState

sealed interface AppearanceSettingsEvent : UiEvent {
    data class ThemeModeChanged(val value: ThemeMode) : AppearanceSettingsEvent
    data class FontSizeChanged(val value: AppFontSize) : AppearanceSettingsEvent
}

/** يملك إعدادات مظهر التطبيق فقط. */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val gateway: AppearanceSettingsGateway
) : ViewModel(),
    UiStateHolder<AppearanceSettingsUiState>,
    UiEventHandler<AppearanceSettingsEvent> {

    val themeMode: StateFlow<ThemeMode> = gateway.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThemeMode.AUTO
    )

    val appFontSize: StateFlow<AppFontSize> = gateway.appFontSize.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppFontSize.MEDIUM
    )

    override val uiState: StateFlow<AppearanceSettingsUiState> =
        combine(themeMode, appFontSize) { mode, size ->
            AppearanceSettingsUiState(themeMode = mode, appFontSize = size)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppearanceSettingsUiState()
        )

    override fun onEvent(event: AppearanceSettingsEvent) {
        when (event) {
            is AppearanceSettingsEvent.ThemeModeChanged -> setThemeModeInternal(event.value)
            is AppearanceSettingsEvent.FontSizeChanged -> setAppFontSizeInternal(event.value)
        }
    }

    fun setThemeMode(value: ThemeMode) =
        onEvent(AppearanceSettingsEvent.ThemeModeChanged(value))

    fun setAppFontSize(value: AppFontSize) =
        onEvent(AppearanceSettingsEvent.FontSizeChanged(value))

    private fun setThemeModeInternal(value: ThemeMode) = viewModelScope.launch {
        gateway.setThemeMode(value)
    }

    private fun setAppFontSizeInternal(value: AppFontSize) = viewModelScope.launch {
        gateway.setAppFontSize(value)
    }
}
