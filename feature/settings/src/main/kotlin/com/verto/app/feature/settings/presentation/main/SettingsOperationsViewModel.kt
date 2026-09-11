package com.verto.app.feature.settings.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.settings.domain.model.SettingsBackupTarget
import com.verto.app.feature.settings.domain.model.SettingsDataScope
import com.verto.app.feature.settings.domain.repository.SettingsOperationsGateway
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SectionState {
    object Idle : SectionState()
    object Loading : SectionState()
    object Success : SectionState()
    data class Error(val msg: String) : SectionState()
}

data class SettingsOperationsUiState(
    val isAdmin: Boolean = false,
    val canViewManagement: Boolean = false,
    val lastSyncTime: Long? = null,
    val syncState: SectionState = SectionState.Idle,
    val resetState: SectionState = SectionState.Idle,
    val logoutState: SectionState = SectionState.Idle,
    val backupState: SectionState = SectionState.Idle,
    val backupMessage: String? = null
) : UiState

sealed interface SettingsOperationsEvent : UiEvent {
    data object SyncNow : SettingsOperationsEvent
    data class ExportBackup(val target: SettingsBackupTarget) : SettingsOperationsEvent
    data class ImportBackup(val uri: String) : SettingsOperationsEvent
    data class ResetData(val scope: SettingsDataScope) : SettingsOperationsEvent
    data object Logout : SettingsOperationsEvent
}

/** يملك فقط المزامنة والنسخ الاحتياطي والمسح والخروج في شاشة الإعدادات الرئيسية. */
@HiltViewModel
class SettingsOperationsViewModel @Inject constructor(
    private val gateway: SettingsOperationsGateway
) : ViewModel(),
    UiStateHolder<SettingsOperationsUiState>,
    UiEventHandler<SettingsOperationsEvent> {

    private val _uiState = MutableStateFlow(SettingsOperationsUiState())
    override val uiState: StateFlow<SettingsOperationsUiState> = _uiState.asStateFlow()

    val isAdmin: StateFlow<Boolean> = uiState.map { it.isAdmin }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val canViewManagement: StateFlow<Boolean> = uiState.map { it.canViewManagement }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val lastSyncTime: StateFlow<Long?> = uiState.map { it.lastSyncTime }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    init {
        viewModelScope.launch {
            val isAdmin = gateway.currentUserIsAdmin()
            val canViewManagement = gateway.currentUserCanViewManagement()
            _uiState.update {
                it.copy(
                    isAdmin = isAdmin,
                    canViewManagement = canViewManagement,
                )
            }
        }
           viewModelScope.launch {
            gateway.observeLastSuccessfulSyncAt().collect { timestamp ->
                _uiState.update { it.copy(lastSyncTime = timestamp) }
            }
        }
    }

    override fun onEvent(event: SettingsOperationsEvent) {
        when (event) {
            SettingsOperationsEvent.SyncNow -> syncNowInternal()
            is SettingsOperationsEvent.ExportBackup -> exportBackupInternal(event.target)
            is SettingsOperationsEvent.ImportBackup -> importBackupInternal(event.uri)
            is SettingsOperationsEvent.ResetData -> resetDataInternal(event.scope)
            SettingsOperationsEvent.Logout -> logoutInternal()
        }
    }

    fun syncNow() = onEvent(SettingsOperationsEvent.SyncNow)
    fun exportBackup(target: SettingsBackupTarget) =
        onEvent(SettingsOperationsEvent.ExportBackup(target))
    fun importBackup(uri: String) = onEvent(SettingsOperationsEvent.ImportBackup(uri))
    fun resetData(scope: SettingsDataScope) = onEvent(SettingsOperationsEvent.ResetData(scope))
    fun logout() = onEvent(SettingsOperationsEvent.Logout)

    private fun syncNowInternal() = viewModelScope.launch {
        _uiState.update { it.copy(syncState = SectionState.Loading) }
        gateway.syncNow()
            .onSuccess {
                _uiState.update {
                    it.copy(syncState = SectionState.Success)
                }
                delay(4_000)
                _uiState.update { it.copy(syncState = SectionState.Idle) }
            }
            .onFailure { error ->
                val message = if (ErrorHumanizer.isNetworkError(error)) {
                    "تعذّر الاتصال بالإنترنت، تحقق من الشبكة وأعد المحاولة"
                } else {
                    "تعذّرت المزامنة، ستُعاد المحاولة تلقائياً"
                }
                _uiState.update { it.copy(syncState = SectionState.Error(message)) }
            }
    }

    private fun exportBackupInternal(target: SettingsBackupTarget) = viewModelScope.launch {
        _uiState.update { it.copy(backupState = SectionState.Loading, backupMessage = null) }
        gateway.exportBackup(target)
            .onSuccess {
                _uiState.update {
                    it.copy(
                        backupState = SectionState.Success,
                        backupMessage = "تم تصدير النسخة الاحتياطية"
                    )
                }
                delay(3_000)
                _uiState.update { it.copy(backupState = SectionState.Idle, backupMessage = null) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        backupState = SectionState.Error(
                            ErrorHumanizer.humanize(error, "تصدير النسخة الاحتياطية")
                        ),
                        backupMessage = null
                    )
                }
            }
    }

    private fun importBackupInternal(uri: String) = viewModelScope.launch {
        _uiState.update { it.copy(backupState = SectionState.Loading, backupMessage = null) }
        gateway.importBackup(uri)
            .onSuccess { summary ->
                _uiState.update {
                    it.copy(backupState = SectionState.Success, backupMessage = summary)
                }
                delay(5_000)
                _uiState.update { it.copy(backupState = SectionState.Idle, backupMessage = null) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        backupState = SectionState.Error(
                            ErrorHumanizer.humanize(error, "استيراد النسخة الاحتياطية")
                        ),
                        backupMessage = null
                    )
                }
            }
    }

    private fun resetDataInternal(scope: SettingsDataScope) = viewModelScope.launch {
        withSection(set = { copy(resetState = it) }) {
            gateway.resetData(scope).getOrThrow()
        }
    }

    private fun logoutInternal() = viewModelScope.launch {
        withSection(set = { copy(logoutState = it) }) {
            gateway.logout().getOrThrow()
        }
    }

    private suspend fun withSection(
        set: SettingsOperationsUiState.(SectionState) -> SettingsOperationsUiState,
        block: suspend () -> Unit
    ) {
        _uiState.update { it.set(SectionState.Loading) }
        runCatching { block() }
            .onSuccess {
                _uiState.update { it.set(SectionState.Success) }
                delay(2_000)
                _uiState.update { it.set(SectionState.Idle) }
            }
            .onFailure { error ->
                _uiState.update { it.set(SectionState.Error(ErrorHumanizer.humanize(error))) }
            }
    }
}
