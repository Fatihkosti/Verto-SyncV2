package com.verto.app.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.messages.application.ConversationListItem
import com.verto.app.feature.messages.application.MessagesGateway
import com.verto.app.feature.messages.application.RegisteredMarketerItem
import com.verto.app.utils.ErrorHumanizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MessagesUiState(
    val conversations: List<ConversationListItem> = emptyList(),
    val registeredMarketers: List<RegisteredMarketerItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesGateway: MessagesGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        loadConversations()
        startRealtime()
    }

    fun loadConversations() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        messagesGateway.getConversations()
            .onSuccess { conversations ->
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorHumanizer.humanize(error, "تحميل المحادثات")
                )
            }
    }

    fun loadRegisteredMarketers() = viewModelScope.launch {
        messagesGateway.getRegisteredMarketers()
            .onSuccess { marketers ->
                _uiState.value = _uiState.value.copy(registeredMarketers = marketers)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = ErrorHumanizer.humanize(error, "تحميل المسوقين")
                )
            }
    }

    fun deleteConversation(conversationId: String) = viewModelScope.launch {
        messagesGateway.deleteConversation(conversationId)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    conversations = _uiState.value.conversations.filter {
                        it.conversationId != conversationId
                    }
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = ErrorHumanizer.humanize(error, "الحذف")
                )
            }
    }

    fun startConversation(clientId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        messagesGateway.openConversation(clientId)
            .onSuccess { conversationId ->
                loadConversations()
                onCreated(conversationId)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = ErrorHumanizer.humanize(error, "فتح المحادثة")
                )
            }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = messagesGateway.observeConversationListChanges()
            .onEach { loadConversations() }
            .catch { /* FCM and manual refresh remain available if realtime disconnects. */ }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        realtimeJob?.cancel()
        super.onCleared()
    }
}
