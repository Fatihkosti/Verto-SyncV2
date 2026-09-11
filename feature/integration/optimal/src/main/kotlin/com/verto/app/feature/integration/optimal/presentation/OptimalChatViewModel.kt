package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.core.session.model.ManagementOptimalPermission
import com.verto.app.feature.integration.optimal.application.ArchiveOptimalConversationUseCase
import com.verto.app.feature.integration.optimal.application.MarkOptimalConversationReadUseCase
import com.verto.app.feature.integration.optimal.application.ObserveOptimalChatUseCase
import com.verto.app.feature.integration.optimal.application.SendOptimalTextMessageUseCase
import com.verto.app.feature.integration.optimal.application.PrepareOptimalCameraCaptureUseCase
import com.verto.app.feature.integration.optimal.application.SendOptimalAttachmentUseCase
import com.verto.app.feature.integration.optimal.application.DiscardOptimalCameraCaptureUseCase
import com.verto.app.feature.integration.optimal.application.AudioRecordingController
import com.verto.app.feature.integration.optimal.application.SendOptimalAudioMessageUseCase
import com.verto.app.feature.integration.optimal.application.DiscardOptimalAudioPreviewUseCase
import com.verto.app.feature.integration.optimal.domain.model.ArchiveOptimalConversationResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalChatLoadResult
import com.verto.app.feature.integration.optimal.domain.model.SendOptimalTextResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentSelection
import com.verto.app.feature.integration.optimal.domain.model.OptimalCameraCaptureTarget
import com.verto.app.feature.integration.optimal.domain.model.PrepareOptimalCameraResult
import com.verto.app.feature.integration.optimal.domain.model.SendOptimalAttachmentResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalStoredAttachment
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import com.verto.app.feature.integration.optimal.domain.repository.OptimalHomeAccessSource
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OptimalChatUiState(
    val isLoading: Boolean = true,
    val companyName: String = "",
    val currentUserId: String = "",
    val messages: List<OptimalMessage> = emptyList(),
    val draftText: String = "",
    val canSend: Boolean = false,
    val isSending: Boolean = false,
    val isSendingAttachment: Boolean = false,
    val cameraCaptureRequest: OptimalCameraCaptureTarget? = null,
    val isStartingAudio: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val isStoppingAudio: Boolean = false,
    val isSendingAudio: Boolean = false,
    val audioRecordingElapsedMs: Long = 0L,
    val audioPreview: OptimalStoredAttachment? = null,
    val isArchived: Boolean = false,
    val accessDenied: Boolean = false,
    val notFound: Boolean = false,
    val feedback: String? = null,
)

private data class PendingText(val messageId: String, val body: String)

@HiltViewModel
class OptimalChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeChat: ObserveOptimalChatUseCase,
    private val sendTextMessage: SendOptimalTextMessageUseCase,
    private val prepareCameraCapture: PrepareOptimalCameraCaptureUseCase,
    private val sendAttachment: SendOptimalAttachmentUseCase,
    private val discardCameraCapture: DiscardOptimalCameraCaptureUseCase,
    audioRecordingController: AudioRecordingController,
    sendAudioMessage: SendOptimalAudioMessageUseCase,
    discardAudioPreviewUseCase: DiscardOptimalAudioPreviewUseCase,
    private val markRead: MarkOptimalConversationReadUseCase,
    private val archiveConversation: ArchiveOptimalConversationUseCase,
    private val idGenerator: OptimalMessagingIdGenerator,
    accessSource: OptimalHomeAccessSource,
) : ViewModel() {
    private val clientId = savedStateHandle.get<String>(OptimalNavigation.CHAT_CLIENT_ID_ARG).orEmpty().trim()
    private val _uiState = MutableStateFlow(OptimalChatUiState())
    internal val uiState: StateFlow<OptimalChatUiState> = _uiState.asStateFlow()
    private var pendingText: PendingText? = null
    private var activeCameraCapture: OptimalCameraCaptureTarget? = null
    private var markReadJob: kotlinx.coroutines.Job? = null
    private val audioCoordinator = OptimalChatAudioCoordinator(
        clientId = clientId,
        scope = viewModelScope,
        state = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        recordingController = audioRecordingController,
        sendAudioMessage = sendAudioMessage,
        discardPreview = discardAudioPreviewUseCase,
        idGenerator = idGenerator,
    )

    init {
        accessSource.grantedPermissions
            .onEach { granted ->
                _uiState.update {
                    it.copy(canSend = ManagementOptimalPermission.SEND_OPTIMAL_MESSAGES in granted)
                }
            }
            .launchIn(viewModelScope)

        observeChat(clientId)
            .onEach(::applyChatResult)
            .catch {
                _uiState.update { state -> state.copy(isLoading = false, feedback = "تعذّر تحميل المحادثة") }
            }
            .launchIn(viewModelScope)
    }

    fun updateDraft(value: String) {
        if (pendingText?.body != value.trim()) pendingText = null
        _uiState.update { it.copy(draftText = value) }
    }

    fun sendText() {
        val state = _uiState.value
        if (state.isSending) return
        val body = state.draftText.trim()
        if (body.isBlank()) {
            _uiState.update { it.copy(feedback = "اكتب رسالة أولًا") }
            return
        }
        if (!state.canSend) {
            _uiState.update { it.copy(feedback = "لا تملك صلاحية إرسال رسائل Optimal") }
            return
        }
        if (state.isArchived || clientId.isBlank() || state.companyName.isBlank()) return

        val pending = pendingText?.takeIf { it.body == body }
            ?: PendingText(idGenerator.newMessageId(), body).also { pendingText = it }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            when (
                sendTextMessage(
                    clientId = clientId,
                    companyName = state.companyName,
                    messageId = pending.messageId,
                    text = pending.body,
                )
            ) {
                is SendOptimalTextResult.Saved -> {
                    pendingText = null
                    _uiState.update { it.copy(isSending = false, draftText = "") }
                }
                SendOptimalTextResult.EmptyText -> _uiState.update {
                    it.copy(isSending = false, feedback = "اكتب رسالة أولًا")
                }
                SendOptimalTextResult.PermissionDenied -> _uiState.update {
                    it.copy(isSending = false, feedback = "لا تملك صلاحية الإرسال")
                }
                SendOptimalTextResult.BackendContractBlocked -> _uiState.update {
                    it.copy(isSending = false, feedback = "حماية الخادم غير مثبتة")
                }
                SendOptimalTextResult.InvalidConversation,
                SendOptimalTextResult.PersistenceFailed,
                -> _uiState.update {
                    it.copy(isSending = false, feedback = "تعذّر حفظ الرسالة؛ أعد المحاولة")
                }
            }
        }
    }

    fun requestCameraCapture() {
        val state = _uiState.value
        if (
            !state.canSend || state.isArchived || state.isSendingAttachment ||
            activeCameraCapture != null
        ) return
        viewModelScope.launch {
            when (val result = prepareCameraCapture(clientId)) {
                is PrepareOptimalCameraResult.Ready -> {
                    activeCameraCapture = result.target
                    _uiState.update { it.copy(cameraCaptureRequest = result.target) }
                }
                PrepareOptimalCameraResult.PermissionDenied -> _uiState.update {
                    it.copy(feedback = "لا تملك صلاحية إرسال المرفقات")
                }
                PrepareOptimalCameraResult.SessionUnavailable,
                PrepareOptimalCameraResult.Failed,
                -> _uiState.update { it.copy(feedback = "تعذّر تجهيز الكاميرا") }
            }
        }
    }

    fun consumeCameraCaptureRequest() {
        _uiState.update { it.copy(cameraCaptureRequest = null) }
    }

    fun cameraPermissionDenied() {
        _uiState.update { it.copy(feedback = "صلاحية الكاميرا مطلوبة لالتقاط صورة") }
    }

    fun finishCameraCapture(success: Boolean) {
        val target = activeCameraCapture ?: return
        activeCameraCapture = null
        if (!success) {
            viewModelScope.launch { discardCameraCapture(target) }
            return
        }
        sendAttachmentSelection(
            selection = OptimalAttachmentSelection(
                sourceUri = target.captureUri,
                mimeType = target.mimeType,
                displayName = target.displayName,
            ),
            mediaId = target.mediaId,
            cameraTarget = target,
        )
    }

    fun sendPickedAttachment(
        sourceUri: String?,
        mimeType: String?,
        displayName: String? = null,
    ) {
        if (sourceUri.isNullOrBlank()) return
        sendAttachmentSelection(
            selection = OptimalAttachmentSelection(sourceUri, mimeType, displayName),
            mediaId = idGenerator.newMediaId(),
            cameraTarget = null,
        )
    }

    private fun sendAttachmentSelection(
        selection: OptimalAttachmentSelection,
        mediaId: String,
        cameraTarget: OptimalCameraCaptureTarget?,
    ) {
        val state = _uiState.value
        if (state.isSendingAttachment || !state.canSend || state.isArchived) return
        if (clientId.isBlank() || state.companyName.isBlank()) return
        val messageId = idGenerator.newMessageId()
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingAttachment = true) }
            val result = sendAttachment(
                clientId = clientId,
                companyName = state.companyName,
                messageId = messageId,
                mediaId = mediaId,
                selection = selection,
            )
            if (cameraTarget != null) discardCameraCapture(cameraTarget)
            _uiState.update {
                it.copy(
                    isSendingAttachment = false,
                    feedback = when (result) {
                        is SendOptimalAttachmentResult.Saved,
                        SendOptimalAttachmentResult.Cancelled,
                        -> null
                        SendOptimalAttachmentResult.PermissionDenied -> "لا تملك صلاحية إرسال المرفقات"
                        SendOptimalAttachmentResult.InvalidConversation -> "المحادثة غير متاحة"
                        SendOptimalAttachmentResult.SourceUnavailable -> "الملف غير موجود أو تعذّر فتحه"
                        SendOptimalAttachmentResult.EmptyFile -> "لا يمكن إرسال ملف فارغ"
                        SendOptimalAttachmentResult.UnsupportedMime -> "صيغة الملف غير مدعومة"
                        SendOptimalAttachmentResult.SessionUnavailable -> "الجلسة غير متاحة"
                        SendOptimalAttachmentResult.SecurityViolation -> "رُفض الملف لأسباب أمنية"
                        SendOptimalAttachmentResult.PersistenceFailed -> "تعذّر حفظ المرفق؛ أعد المحاولة"
                    },
                )
            }
        }
    }

    fun audioPermissionDenied() = audioCoordinator.permissionDenied()

    fun startAudioRecording() = audioCoordinator.start()

    fun stopAudioRecording() = audioCoordinator.stop()

    fun cancelAudioRecording() = audioCoordinator.cancel()

    fun discardAudioPreview() = audioCoordinator.discardPreview()

    fun sendAudioPreview() = audioCoordinator.sendPreview()

    fun onChatLeaving() = audioCoordinator.onLeaving()

    fun archive() {
        viewModelScope.launch {
            when (archiveConversation(clientId)) {
                ArchiveOptimalConversationResult.Archived -> Unit
                ArchiveOptimalConversationResult.PermissionDenied ->
                    _uiState.update { it.copy(feedback = "لا تملك صلاحية الأرشفة") }
                ArchiveOptimalConversationResult.BackendContractBlocked ->
                    _uiState.update { it.copy(feedback = "الأرشفة متوقفة حتى تثبيت عقد الخادم") }
                ArchiveOptimalConversationResult.Failed ->
                    _uiState.update { it.copy(feedback = "تعذّرت الأرشفة") }
            }
        }
    }

    fun clearFeedback() = _uiState.update { it.copy(feedback = null) }

    override fun onCleared() {
        onChatLeaving()
        super.onCleared()
    }

    private fun applyChatResult(result: OptimalChatLoadResult) {
        when (result) {
            is OptimalChatLoadResult.Ready -> {
                val snapshot = result.snapshot
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        companyName = snapshot.companyName,
                        currentUserId = snapshot.currentUserId,
                        messages = snapshot.messages,
                        isArchived = snapshot.isArchived,
                        accessDenied = false,
                        notFound = false,
                    )
                }
                if (snapshot.unreadCount > 0 && markReadJob?.isActive != true) {
                    markReadJob = viewModelScope.launch { markRead(clientId) }
                }
            }
            OptimalChatLoadResult.PermissionDenied -> _uiState.update {
                it.copy(isLoading = false, accessDenied = true)
            }
            OptimalChatLoadResult.NotFound,
            OptimalChatLoadResult.SessionUnavailable,
            -> _uiState.update { it.copy(isLoading = false, notFound = true) }
        }
    }
}
