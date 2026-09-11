package com.verto.app.feature.integration.optimal.presentation

import com.verto.app.feature.integration.optimal.application.AudioRecordingController
import com.verto.app.feature.integration.optimal.application.DiscardOptimalAudioPreviewUseCase
import com.verto.app.feature.integration.optimal.application.SendOptimalAudioMessageUseCase
import com.verto.app.feature.integration.optimal.domain.model.SendOptimalAudioResult
import com.verto.app.feature.integration.optimal.domain.model.StartOptimalAudioRecordingResult
import com.verto.app.feature.integration.optimal.domain.model.StopOptimalAudioRecordingResult
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class OptimalChatAudioCoordinator(
    private val clientId: String,
    private val scope: CoroutineScope,
    private val state: () -> OptimalChatUiState,
    private val updateState: ((OptimalChatUiState) -> OptimalChatUiState) -> Unit,
    private val recordingController: AudioRecordingController,
    private val sendAudioMessage: SendOptimalAudioMessageUseCase,
    private val discardPreview: DiscardOptimalAudioPreviewUseCase,
    private val idGenerator: OptimalMessagingIdGenerator,
) {
    private var tickerJob: Job? = null
    private var pendingMessageId: String? = null

    fun permissionDenied() {
        updateState { it.copy(feedback = "صلاحية الميكروفون مطلوبة للتسجيل الصوتي") }
    }

    fun start() {
        val current = state()
        if (
            !current.canSend || current.isArchived || current.isSending ||
            current.isSendingAttachment || current.isStartingAudio ||
            current.isRecordingAudio || current.isStoppingAudio ||
            current.isSendingAudio || current.audioPreview != null
        ) return
        scope.launch {
            updateState { it.copy(isStartingAudio = true, audioRecordingElapsedMs = 0L) }
            when (recordingController.start(clientId)) {
                StartOptimalAudioRecordingResult.Started,
                StartOptimalAudioRecordingResult.AlreadyActive,
                -> {
                    updateState { it.copy(isStartingAudio = false, isRecordingAudio = true) }
                    startTicker()
                }
                StartOptimalAudioRecordingResult.PermissionDenied -> updateState {
                    it.copy(isStartingAudio = false, feedback = "لا تملك صلاحية إرسال الرسائل الصوتية")
                }
                StartOptimalAudioRecordingResult.SessionUnavailable -> updateState {
                    it.copy(isStartingAudio = false, feedback = "الجلسة غير متاحة")
                }
                StartOptimalAudioRecordingResult.Failed -> updateState {
                    it.copy(isStartingAudio = false, feedback = "تعذّر بدء التسجيل الصوتي")
                }
            }
        }
    }

    fun stop() {
        if (!state().isRecordingAudio || state().isStoppingAudio) return
        stopTicker()
        scope.launch {
            updateState { it.copy(isRecordingAudio = false, isStoppingAudio = true) }
            when (val result = recordingController.stop()) {
                is StopOptimalAudioRecordingResult.Preview -> {
                    pendingMessageId = null
                    updateState {
                        it.copy(
                            isStoppingAudio = false,
                            audioRecordingElapsedMs = 0L,
                            audioPreview = result.attachment,
                        )
                    }
                }
                StopOptimalAudioRecordingResult.EmptyRecording -> updateState {
                    it.copy(
                        isStoppingAudio = false,
                        audioRecordingElapsedMs = 0L,
                        feedback = "التسجيل قصير أو فارغ؛ سجّل مدة أطول",
                    )
                }
                StopOptimalAudioRecordingResult.NotRecording,
                StopOptimalAudioRecordingResult.Failed,
                -> updateState {
                    it.copy(
                        isStoppingAudio = false,
                        audioRecordingElapsedMs = 0L,
                        feedback = "تعذّر حفظ التسجيل الصوتي",
                    )
                }
            }
        }
    }

    fun cancel() {
        if (!state().isRecordingAudio && !state().isStartingAudio) return
        stopTicker()
        scope.launch {
            recordingController.cancel()
            updateState {
                it.copy(
                    isStartingAudio = false,
                    isRecordingAudio = false,
                    isStoppingAudio = false,
                    audioRecordingElapsedMs = 0L,
                )
            }
        }
    }

    fun discardPreview() {
        val attachment = state().audioPreview ?: return
        pendingMessageId = null
        updateState { it.copy(audioPreview = null) }
        scope.launch { discardPreview(attachment) }
    }

    fun sendPreview() {
        val current = state()
        val preview = current.audioPreview ?: return
        if (current.isSendingAudio || !current.canSend || current.isArchived) return
        val messageId = pendingMessageId ?: idGenerator.newMessageId().also { pendingMessageId = it }
        scope.launch {
            updateState { it.copy(isSendingAudio = true) }
            when (
                sendAudioMessage(
                    clientId = clientId,
                    companyName = current.companyName,
                    messageId = messageId,
                    attachment = preview,
                )
            ) {
                is SendOptimalAudioResult.Saved -> clearCommittedPreview()
                SendOptimalAudioResult.PermissionDenied -> updateState {
                    it.copy(isSendingAudio = false, feedback = "لا تملك صلاحية الإرسال")
                }
                SendOptimalAudioResult.InvalidConversation -> updateState {
                    it.copy(isSendingAudio = false, feedback = "المحادثة غير متاحة")
                }
                SendOptimalAudioResult.InvalidPreview -> updateState {
                    it.copy(isSendingAudio = false, feedback = "التسجيل غير صالح للإرسال")
                }
                SendOptimalAudioResult.SessionUnavailable -> updateState {
                    it.copy(isSendingAudio = false, feedback = "الجلسة غير متاحة")
                }
                SendOptimalAudioResult.SecurityViolation -> updateState {
                    it.copy(isSendingAudio = false, feedback = "رُفض التسجيل لأسباب أمنية")
                }
                SendOptimalAudioResult.PersistenceFailed -> clearCommittedPreview(
                    feedback = "تعذّر حفظ التسجيل؛ أعد التسجيل",
                )
            }
        }
    }

    fun onLeaving() {
        stopTicker()
        val current = state()
        val preview = current.audioPreview
        updateState {
            it.copy(
                isStartingAudio = false,
                isRecordingAudio = false,
                isStoppingAudio = false,
                audioRecordingElapsedMs = 0L,
                audioPreview = null,
            )
        }
        scope.launch {
            recordingController.cancel()
            if (preview != null && !current.isSendingAudio) discardPreview(preview)
        }
    }

    private fun clearCommittedPreview(feedback: String? = null) {
        pendingMessageId = null
        updateState {
            it.copy(isSendingAudio = false, audioPreview = null, feedback = feedback)
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                delay(1_000L)
                updateState { current ->
                    if (current.isRecordingAudio) {
                        current.copy(audioRecordingElapsedMs = current.audioRecordingElapsedMs + 1_000L)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
