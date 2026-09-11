package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMediaDraft
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentCategory
import com.verto.app.feature.integration.optimal.domain.model.OptimalAudioPolicy
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.model.OptimalStoredAttachment
import com.verto.app.feature.integration.optimal.domain.model.SendOptimalAudioResult
import com.verto.app.feature.integration.optimal.domain.port.OptimalAttachmentStore
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingAccessException
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutgoingMessageDraft
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SendOptimalAudioMessageUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val attachmentStore: OptimalAttachmentStore,
    private val messagingPort: OptimalMessagingPort,
    private val operationGuard: OptimalOperationGuard,
) {
    suspend operator fun invoke(
        clientId: String,
        companyName: String,
        messageId: String,
        attachment: OptimalStoredAttachment,
    ): SendOptimalAudioResult {
        val normalizedClientId = clientId.trim()
        val normalizedMessageId = messageId.trim()
        if (normalizedClientId.isBlank() || normalizedMessageId.isBlank() || companyName.isBlank()) {
            return SendOptimalAudioResult.InvalidConversation
        }
        if (
            attachment.category != OptimalAttachmentCategory.AUDIO ||
            attachment.mimeType != OptimalAudioPolicy.MIME_TYPE ||
            attachment.sizeBytes <= 0L ||
            !OptimalAudioPolicy.isValidDuration(attachment.durationMs)
        ) {
            return SendOptimalAudioResult.InvalidPreview
        }
        when (
            operationGuard.check(
                OptimalOperation.SEND_MESSAGES,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId;attachment=voice",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> return SendOptimalAudioResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> return SendOptimalAudioResult.PersistenceFailed
            OptimalAccessDecision.Granted -> Unit
        }
        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (organizationId.isBlank() || userId.isBlank()) {
            return SendOptimalAudioResult.SessionUnavailable
        }
        if (!attachmentStore.owns(organizationId, userId, attachment.privateUri)) {
            return SendOptimalAudioResult.SecurityViolation
        }

        val draft = OptimalOutgoingMessageDraft(
            messageId = normalizedMessageId,
            clientId = normalizedClientId,
            companyName = companyName.trim(),
            kind = OptimalMessageKind.VOICE,
            body = "تسجيل صوتي",
            media = listOf(
                OptimalMessageMediaDraft(
                    mediaId = attachment.mediaId,
                    localUri = attachment.privateUri,
                    remoteUrl = null,
                    mimeType = attachment.mimeType,
                    sizeBytes = attachment.sizeBytes,
                    durationMs = attachment.durationMs,
                ),
            ),
        )
        val persistence = try {
            messagingPort.saveOutgoingMessage(draft)
        } catch (cancellation: CancellationException) {
            deleteAudio(organizationId, userId, attachment)
            throw cancellation
        } catch (_: Exception) {
            deleteAudio(organizationId, userId, attachment)
            return SendOptimalAudioResult.PersistenceFailed
        }
        return persistence.fold(
            onSuccess = { SendOptimalAudioResult.Saved(it) },
            onFailure = { error ->
                deleteAudio(organizationId, userId, attachment)
                when (error) {
                    is OptimalMessagingAccessException -> when (error.decision) {
                        OptimalAccessDecision.PermissionDenied -> SendOptimalAudioResult.PermissionDenied
                        OptimalAccessDecision.BackendContractBlocked,
                        OptimalAccessDecision.Granted,
                        -> SendOptimalAudioResult.PersistenceFailed
                    }
                    else -> SendOptimalAudioResult.PersistenceFailed
                }
            },
        )
    }

    private suspend fun deleteAudio(
        organizationId: String,
        userId: String,
        attachment: OptimalStoredAttachment,
    ) = withContext(NonCancellable) {
        attachmentStore.deleteStoredAttachment(organizationId, userId, attachment)
    }
}

class DiscardOptimalAudioPreviewUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val attachmentStore: OptimalAttachmentStore,
) {
    suspend operator fun invoke(attachment: OptimalStoredAttachment) {
        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (
            organizationId.isNotBlank() &&
            userId.isNotBlank() &&
            attachmentStore.owns(organizationId, userId, attachment.privateUri)
        ) {
            attachmentStore.deleteStoredAttachment(organizationId, userId, attachment)
        }
    }
}
