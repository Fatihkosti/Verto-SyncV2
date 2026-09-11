package com.verto.app.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEffect
import com.verto.app.core.presentation.UiEffectSource
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.profile.domain.model.UserProfile
import com.verto.app.feature.profile.domain.repository.ProfileGateway
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileOperationState {
    data object Idle : ProfileOperationState
    data object Loading : ProfileOperationState
    data object Success : ProfileOperationState
    data class Error(val message: String) : ProfileOperationState
}

data class ProfileSettingsUiState(
    val profile: UserProfile = UserProfile(),
    val canEditName: Boolean = false,
    val profileState: ProfileOperationState = ProfileOperationState.Idle,
    val passwordState: ProfileOperationState = ProfileOperationState.Idle
) : UiState

sealed interface ProfileSettingsEvent : UiEvent {
    data class SaveProfile(val name: String, val phone: String) : ProfileSettingsEvent
    data class ChangePassword(
        val currentPassword: String,
        val newPassword: String
    ) : ProfileSettingsEvent
    data object ClearPasswordState : ProfileSettingsEvent
}

sealed interface ProfileSettingsEffect : UiEffect {
    data object ProfileSaved : ProfileSettingsEffect
    data object PasswordChanged : ProfileSettingsEffect
}

/** يملك عمليات الملف الشخصي وكلمة المرور فقط. */
@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val gateway: ProfileGateway
) : ViewModel(),
    UiStateHolder<ProfileSettingsUiState>,
    UiEventHandler<ProfileSettingsEvent>,
    UiEffectSource<ProfileSettingsEffect> {

    val profile: StateFlow<UserProfile> = gateway.profile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserProfile()
    )

    private val _canEditName = MutableStateFlow(false)
    val canEditName: StateFlow<Boolean> = _canEditName.asStateFlow()

    private val _operationState = MutableStateFlow(ProfileSettingsUiState())

    override val uiState: StateFlow<ProfileSettingsUiState> =
        combine(profile, canEditName, _operationState) { currentProfile, editable, operation ->
            operation.copy(profile = currentProfile, canEditName = editable)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProfileSettingsUiState()
        )

    private val _effects = MutableSharedFlow<ProfileSettingsEffect>(extraBufferCapacity = 1)
    override val effects: SharedFlow<ProfileSettingsEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            _canEditName.value = gateway.canEditName()
        }
    }

    override fun onEvent(event: ProfileSettingsEvent) {
        when (event) {
            is ProfileSettingsEvent.SaveProfile -> saveProfileInternal(event.name, event.phone)
            is ProfileSettingsEvent.ChangePassword -> changePasswordInternal(
                event.currentPassword,
                event.newPassword
            )
            ProfileSettingsEvent.ClearPasswordState -> clearPasswordStateInternal()
        }
    }

    fun saveProfile(name: String, phone: String) =
        onEvent(ProfileSettingsEvent.SaveProfile(name, phone))

    fun changePassword(currentPassword: String, newPassword: String) =
        onEvent(ProfileSettingsEvent.ChangePassword(currentPassword, newPassword))

    fun clearPasswordState() = onEvent(ProfileSettingsEvent.ClearPasswordState)

    private fun saveProfileInternal(name: String, phone: String) = viewModelScope.launch {
        _operationState.update { it.copy(profileState = ProfileOperationState.Loading) }
        runCatching {
            if (_canEditName.value) gateway.updateName(name)
            gateway.updatePhone(phone)
        }.onSuccess {
            _operationState.update { it.copy(profileState = ProfileOperationState.Success) }
            _effects.emit(ProfileSettingsEffect.ProfileSaved)
            delay(2_000)
            _operationState.update { it.copy(profileState = ProfileOperationState.Idle) }
        }.onFailure { error ->
            _operationState.update {
                it.copy(profileState = ProfileOperationState.Error(ErrorHumanizer.humanize(error)))
            }
        }
    }

    private fun changePasswordInternal(
        currentPassword: String,
        newPassword: String
    ) = viewModelScope.launch {
        _operationState.update { it.copy(passwordState = ProfileOperationState.Loading) }
        gateway.changePassword(currentPassword, newPassword)
            .onSuccess {
                _operationState.update { it.copy(passwordState = ProfileOperationState.Success) }
                _effects.emit(ProfileSettingsEffect.PasswordChanged)
                delay(2_000)
                _operationState.update { it.copy(passwordState = ProfileOperationState.Idle) }
            }
            .onFailure { error ->
                _operationState.update {
                    it.copy(
                        passwordState = ProfileOperationState.Error(
                            ErrorHumanizer.humanize(error, "تغيير كلمة السر")
                        )
                    )
                }
            }
    }

    private fun clearPasswordStateInternal() {
        _operationState.update { it.copy(passwordState = ProfileOperationState.Idle) }
    }
}
