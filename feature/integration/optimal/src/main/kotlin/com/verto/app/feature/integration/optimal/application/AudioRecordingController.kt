package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentException
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentFailure
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioRecordingTarget
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.model.StartOptimalAudioRecordingResult
import com.verto.app.feature.integration.optimal.domain.model.StopOptimalAudioRecordingResult
import com.verto.app.feature.integration.optimal.domain.port.AudioRecorder
import com.verto.app.feature.integration.optimal.domain.port.OptimalAttachmentStore
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioRecordingController @Inject constructor(
    private val sessionReader: SessionReader,
    private val attachmentStore: OptimalAttachmentStore,
    private val audioRecorder: AudioRecorder,
    private val idGenerator: OptimalMessagingIdGenerator,
    private val operationGuard: OptimalOperationGuard,
) {
    private val mutex = Mutex()
    private var active: ActiveAudioRecording? = null

    suspend fun start(clientId: String): StartOptimalAudioRecordingResult = mutex.withLock {
        if (active != null) return@withLock StartOptimalAudioRecordingResult.AlreadyActive
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return@withLock StartOptimalAudioRecordingResult.Failed
        when (
            operationGuard.check(
                OptimalOperation.SEND_MESSAGES,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId;attachment=voice",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> {
                return@withLock StartOptimalAudioRecordingResult.PermissionDenied
            }
            OptimalAccessDecision.BackendContractBlocked -> {
                return@withLock StartOptimalAudioRecordingResult.Failed
            }
            OptimalAccessDecision.Granted -> Unit
        }

        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (organizationId.isBlank() || userId.isBlank()) {
            return@withLock StartOptimalAudioRecordingResult.SessionUnavailable
        }
        val target = attachmentStore.prepareAudioTarget(
            organizationId = organizationId,
            userId = userId,
            mediaId = idGenerator.newMediaId(),
        ).getOrElse { return@withLock StartOptimalAudioRecordingResult.Failed }

        val started = audioRecorder.start(target)
        if (started.isFailure) {
            attachmentStore.discardAudioTarget(organizationId, userId, target)
            return@withLock StartOptimalAudioRecordingResult.Failed
        }
        active = ActiveAudioRecording(organizationId, userId, target)
        StartOptimalAudioRecordingResult.Started
    }

    suspend fun stop(): StopOptimalAudioRecordingResult = mutex.withLock {
        val current = active ?: return@withLock StopOptimalAudioRecordingResult.NotRecording
        active = null
        val recorderResult = audioRecorder.stop().getOrElse {
            attachmentStore.discardAudioTarget(
                current.organizationId,
                current.userId,
                current.target,
            )
            return@withLock StopOptimalAudioRecordingResult.Failed
        }
        val stored = attachmentStore.finalizeAudioTarget(
            organizationId = current.organizationId,
            userId = current.userId,
            target = current.target,
            elapsedDurationMs = recorderResult.elapsedDurationMs,
        ).getOrElse { error ->
            attachmentStore.discardAudioTarget(
                current.organizationId,
                current.userId,
                current.target,
            )
            return@withLock if (
                error is OptimalAttachmentException &&
                error.failure == OptimalAttachmentFailure.EMPTY_FILE
            ) {
                StopOptimalAudioRecordingResult.EmptyRecording
            } else {
                StopOptimalAudioRecordingResult.Failed
            }
        }
        StopOptimalAudioRecordingResult.Preview(stored)
    }

    suspend fun cancel() = mutex.withLock {
        val current = active ?: return@withLock
        active = null
        audioRecorder.cancel()
        attachmentStore.discardAudioTarget(
            current.organizationId,
            current.userId,
            current.target,
        )
    }

    private data class ActiveAudioRecording(
        val organizationId: String,
        val userId: String,
        val target: OptimalAudioRecordingTarget,
    )
}
