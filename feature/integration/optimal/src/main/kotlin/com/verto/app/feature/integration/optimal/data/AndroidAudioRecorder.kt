package com.verto.app.feature.integration.optimal.data

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecorderStop
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecordingTarget
import com.verto.app.feature.integration.optimal.domain.port.AudioRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioRecorder {
    private val mutex = Mutex()
    private var active: ActiveRecording? = null

    override suspend fun start(target: OptimalAudioRecordingTarget): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    check(active == null) { "audio recorder is already active" }
                    val output = target.recordingUri.toPrivateFile()
                    output.parentFile?.mkdirs()
                    val recorder = createMediaRecorder()
                    try {
                        recorder.apply {
                            setAudioSource(MediaRecorder.AudioSource.MIC)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioChannels(1)
                            setAudioSamplingRate(44_100)
                            setAudioEncodingBitRate(128_000)
                            setOutputFile(output.absolutePath)
                            prepare()
                            start()
                        }
                        active = ActiveRecording(
                            recorder = recorder,
                            target = target,
                            startedAtElapsedMs = SystemClock.elapsedRealtime(),
                        )
                    } catch (error: Exception) {
                        recorder.releaseSafely()
                        throw error
                    }
                }
            }
        }

    override suspend fun stop(): Result<OptimalAudioRecorderStop> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = active
                ?: return@withLock Result.failure(IllegalStateException("audio recorder is not active"))
            active = null
            runCatching {
                current.recorder.stop()
                OptimalAudioRecorderStop(
                    elapsedDurationMs = (SystemClock.elapsedRealtime() - current.startedAtElapsedMs)
                        .coerceAtLeast(0L),
                )
            }.also {
                current.recorder.releaseSafely()
            }
        }
    }

    override suspend fun cancel() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = active ?: return@withLock
            active = null
            current.recorder.releaseSafely()
        }
    }

    private fun createMediaRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

    private fun String.toPrivateFile(): File {
        val uri = Uri.parse(this)
        require(uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            "audio target must be a private file URI"
        }
        return File(requireNotNull(uri.path))
    }

    private fun MediaRecorder.releaseSafely() {
        runCatching { reset() }
        runCatching { release() }
    }

    private data class ActiveRecording(
        val recorder: MediaRecorder,
        val target: OptimalAudioRecordingTarget,
        val startedAtElapsedMs: Long,
    )
}
