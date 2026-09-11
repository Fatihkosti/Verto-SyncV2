package com.verto.app.feature.integration.optimal.domain.port

import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecorderStop
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecordingTarget

/** Single-session recorder abstraction; implementations must release platform resources on every path. */
interface AudioRecorder {
    suspend fun start(target: OptimalAudioRecordingTarget): Result<Unit>
    suspend fun stop(): Result<OptimalAudioRecorderStop>
    suspend fun cancel()
}
