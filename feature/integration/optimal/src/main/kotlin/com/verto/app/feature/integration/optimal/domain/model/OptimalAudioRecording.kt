package com.verto.app.feature.integration.optimal.domain.model

/** App-private output prepared for a single tenant/user recording session. */
data class OptimalAudioRecordingTarget(
    val mediaId: String,
    val recordingUri: String,
    val mimeType: String = "audio/mp4",
    val displayName: String = "تسجيل صوتي.m4a",
)

data class OptimalAudioRecorderStop(
    val elapsedDurationMs: Long,
)

sealed interface StartOptimalAudioRecordingResult {
    data object Started : StartOptimalAudioRecordingResult
    data object AlreadyActive : StartOptimalAudioRecordingResult
    data object PermissionDenied : StartOptimalAudioRecordingResult
    data object SessionUnavailable : StartOptimalAudioRecordingResult
    data object Failed : StartOptimalAudioRecordingResult
}

sealed interface StopOptimalAudioRecordingResult {
    data class Preview(val attachment: OptimalStoredAttachment) : StopOptimalAudioRecordingResult
    data object NotRecording : StopOptimalAudioRecordingResult
    data object EmptyRecording : StopOptimalAudioRecordingResult
    data object Failed : StopOptimalAudioRecordingResult
}

sealed interface SendOptimalAudioResult {
    data class Saved(val message: com.verto.app.feature.integration.optimal.domain.port.OptimalMessage) : SendOptimalAudioResult
    data object InvalidPreview : SendOptimalAudioResult
    data object PermissionDenied : SendOptimalAudioResult
    data object InvalidConversation : SendOptimalAudioResult
    data object SessionUnavailable : SendOptimalAudioResult
    data object SecurityViolation : SendOptimalAudioResult
    data object PersistenceFailed : SendOptimalAudioResult
}

object OptimalAudioPolicy {
    const val MIME_TYPE: String = "audio/mp4"
    const val FILE_EXTENSION: String = "m4a"
    const val MIN_DURATION_MS: Long = 300L

    fun isValidDuration(durationMs: Long?): Boolean = durationMs != null && durationMs >= MIN_DURATION_MS
}
