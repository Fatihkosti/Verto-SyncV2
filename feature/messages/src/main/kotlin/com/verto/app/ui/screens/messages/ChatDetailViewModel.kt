package com.verto.app.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.messages.application.MessageItem
import com.verto.app.feature.messages.application.MessagesGateway
import com.verto.app.feature.messages.application.SendMessageCommand
import com.verto.app.feature.messages.application.appendIfMissing
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

data class ChatDetailUiState(
    val messages: List<MessageItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val messagesGateway: MessagesGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    private var conversationId: String = ""
    private var clientId: String = ""
    private var realtimeJob: Job? = null

    fun init(convId: String, cliId: String) {
        val needsRealtime = conversationId != convId || realtimeJob?.isActive != true
        conversationId = convId
        clientId = cliId
        if (needsRealtime) {
            loadMessages()
            startRealtime()
        }
    }

    fun loadMessages() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        messagesGateway.getMessages(conversationId)
            .onSuccess { messages ->
                _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                messagesGateway.markConversationRead(conversationId)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorHumanizer.humanize(error, "تحميل المحادثة")
                )
            }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        send(SendMessageCommand.text(conversationId, clientId, text))
    }

    fun sendImage(bytes: ByteArray, mimeType: String) {
        send(SendMessageCommand.image(conversationId, clientId, bytes, mimeType))
    }

    fun sendVoice(bytes: ByteArray, durationMs: Long) {
        send(SendMessageCommand.voice(conversationId, clientId, bytes, durationMs))
    }

    fun deleteMessage(messageId: String) = viewModelScope.launch {
        messagesGateway.deleteMessage(messageId)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filter { it.id != messageId }
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = ErrorHumanizer.humanize(error, "الحذف")
                )
            }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun send(command: SendMessageCommand) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isSending = true)
        messagesGateway.sendMessage(command)
            .onSuccess { message ->
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.appendIfMissing(message),
                    isSending = false
                )
            }
            .onFailure {
                val operation = when (command.kind.name) {
                    "IMAGE" -> "الصورة"
                    "VOICE" -> "التسجيل الصوتي"
                    else -> "الرسالة"
                }
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = "تعذّر إرسال $operation، تحقق من الاتصال وحاول مرة أخرى"
                )
            }
    }

    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = messagesGateway.observeMessages(conversationId)
            .onEach { message ->
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.appendIfMissing(message)
                )
            }
            .catch { /* FCM and manual refresh remain available if realtime disconnects. */ }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        realtimeJob?.cancel()
        super.onCleared()
    }
}
