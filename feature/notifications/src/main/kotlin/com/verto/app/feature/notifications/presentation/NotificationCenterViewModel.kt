package com.verto.app.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.presentation.UiEvent
import com.verto.app.core.presentation.UiEventHandler
import com.verto.app.core.presentation.UiState
import com.verto.app.core.presentation.UiStateHolder
import com.verto.app.feature.notifications.domain.model.AppNotification
import com.verto.app.feature.notifications.domain.repository.NotificationCenterGateway
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationCenterUiState(
    val isLoading: Boolean = true,
    val notifications: List<AppNotification> = emptyList(),
    val error: String? = null
) : UiState

sealed interface NotificationCenterEvent : UiEvent {
    data class MarkAsRead(val id: String) : NotificationCenterEvent
    data object MarkAllAsRead : NotificationCenterEvent
    data object SyncNow : NotificationCenterEvent
}

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val gateway: NotificationCenterGateway
) : ViewModel(),
    UiStateHolder<NotificationCenterUiState>,
    UiEventHandler<NotificationCenterEvent> {

    private val _uiState = MutableStateFlow(NotificationCenterUiState())
    override val uiState: StateFlow<NotificationCenterUiState> = _uiState.asStateFlow()

    init {
        observeNotifications()
    }

    override fun onEvent(event: NotificationCenterEvent) {
        when (event) {
            is NotificationCenterEvent.MarkAsRead -> markAsReadInternal(event.id)
            NotificationCenterEvent.MarkAllAsRead -> markAllAsReadInternal()
            NotificationCenterEvent.SyncNow -> syncNowInternal()
        }
    }

    fun markAsRead(id: String) = onEvent(NotificationCenterEvent.MarkAsRead(id))
    fun markAllAsRead() = onEvent(NotificationCenterEvent.MarkAllAsRead)
    fun syncNow() = onEvent(NotificationCenterEvent.SyncNow)

    private fun observeNotifications() = viewModelScope.launch {
        runCatching { gateway.observeCurrentUserNotifications() }
            .onSuccess { flow ->
                flow.collectLatest { notifications ->
                    _uiState.update {
                        it.copy(isLoading = false, notifications = notifications, error = null)
                    }
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ErrorHumanizer.humanize(error, "تحميل الإشعارات")
                    )
                }
            }
    }

    private fun markAsReadInternal(id: String) = viewModelScope.launch {
        runCatching { gateway.markAsRead(id) }.onFailure { error ->
            _uiState.update { it.copy(error = ErrorHumanizer.humanize(error, "تحديث حالة الإشعار")) }
        }
    }

    private fun markAllAsReadInternal() = viewModelScope.launch {
        runCatching { gateway.markAllAsRead() }.onFailure { error ->
            _uiState.update { it.copy(error = ErrorHumanizer.humanize(error, "تحديث حالة الإشعارات")) }
        }
    }

    private fun syncNowInternal() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        gateway.sync()
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ErrorHumanizer.humanize(error, "تحديث الإشعارات")
                    )
                }
            }
            .onSuccess {
                _uiState.update { it.copy(isLoading = false, error = null) }
            }
    }

}
