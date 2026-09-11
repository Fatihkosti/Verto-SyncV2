package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMediaDraft
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentCategory
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentException
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentFailure
import com.verto.app.feature.integration.optimal.domain.model.OptimalAttachmentSelection
import com.verto.app.feature.integration.optimal.domain.model.OptimalCameraCaptureTarget
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.model.PrepareOptimalCameraResult
import com.verto.app.feature.integration.optimal.domain.model.SendOptimalAttachmentResult
import com.verto.app.feature.integration.optimal.domain.port.OptimalAttachmentStore
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingAccessException
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutgoingMessageDraft
import javax.inject.Inject

class PrepareOptimalCameraCaptureUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val attachmentStore: OptimalAttachmentStore,
    private val idGenerator: OptimalMessagingIdGenerator,
    private val operationGuard: OptimalOperationGuard,
) {
    suspend operator fun invoke(clientId: String): PrepareOptimalCameraResult {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return PrepareOptimalCameraResult.Failed
        when (
            operationGuard.check(
                OptimalOperation.SEND_MESSAGES,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId;attachment=camera",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> return PrepareOptimalCameraResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> return PrepareOptimalCameraResult.Failed
            OptimalAccessDecision.Granted -> Unit
        }
        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (organizationId.isBlank() || userId.isBlank()) {
            return PrepareOptimalCameraResult.SessionUnavailable
        }
        return attachmentStore.prepareCameraTarget(
            organizationId = organizationId,
            userId = userId,
            mediaId = idGenerator.newMediaId(),
        ).fold(
            onSuccess = { PrepareOptimalCameraResult.Ready(it) },
            onFailure = { PrepareOptimalCameraResult.Failed },
        )
    }
}

class SendOptimalAttachmentUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val attachmentStore: OptimalAttachmentStore,
    private val messagingPort: OptimalMessagingPort,
    private val operationGuard: OptimalOperationGuard,
) {
    suspend operator fun invoke(
        clientId: String,
        companyName: String,
        messageId: String,
        mediaId: String,
        selection: OptimalAttachmentSelection?,
    ): SendOptimalAttachmentResult {
        val normalizedClientId = clientId.trim()
        val normalizedMessageId = messageId.trim()
        val normalizedMediaId = mediaId.trim()
        if (selection == null || selection.sourceUri.isBlank()) {
            return SendOptimalAttachmentResult.Cancelled
        }
        if (
            normalizedClientId.isBlank() ||
            normalizedMessageId.isBlank() ||
            normalizedMediaId.isBlank()
        ) {
            return SendOptimalAttachmentResult.InvalidConversation
        }
        when (
            operationGuard.check(
                OptimalOperation.SEND_MESSAGES,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId;attachment=true",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> return SendOptimalAttachmentResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> {
                return SendOptimalAttachmentResult.PersistenceFailed
            }
            OptimalAccessDecision.Granted -> Unit
        }

        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (organizationId.isBlank() || userId.isBlank()) {
            return SendOptimalAttachmentResult.SessionUnavailable
        }

        val stored = attachmentStore.importAttachment(
            organizationId = organizationId,
            userId = userId,
            mediaId = normalizedMediaId,
            selection = selection,
        ).getOrElse { return it.toAttachmentResult() }

        if (!attachmentStore.owns(organizationId, userId, stored.privateUri)) {
            attachmentStore.deleteStoredAttachment(organizationId, userId, stored)
            return SendOptimalAttachmentResult.SecurityViolation
        }

        val kind = when (stored.category) {
            OptimalAttachmentCategory.IMAGE -> OptimalMessageKind.IMAGE
            OptimalAttachmentCategory.VIDEO -> OptimalMessageKind.VIDEO
            OptimalAttachmentCategory.DOCUMENT -> OptimalMessageKind.DOCUMENT
            OptimalAttachmentCategory.AUDIO -> {
                attachmentStore.deleteStoredAttachment(organizationId, userId, stored)
                return SendOptimalAttachmentResult.UnsupportedMime
            }
        }
        val saveResult = messagingPort.saveOutgoingMessage(
            OptimalOutgoingMessageDraft(
                messageId = normalizedMessageId,
                clientId = normalizedClientId,
                companyName = companyName.trim(),
                kind = kind,
                body = stored.displayName,
                media = listOf(
                    OptimalMessageMediaDraft(
                        mediaId = stored.mediaId,
                        localUri = stored.privateUri,
                        remoteUrl = null,
                        mimeType = stored.mimeType,
                        sizeBytes = stored.sizeBytes,
                        durationMs = stored.durationMs,
                    ),
                ),
            ),
        )
        return saveResult.fold(
            onSuccess = { SendOptimalAttachmentResult.Saved(it, stored) },
            onFailure = { error ->
                attachmentStore.deleteStoredAttachment(organizationId, userId, stored)
                when (error) {
                    is OptimalMessagingAccessException -> when (error.decision) {
                        OptimalAccessDecision.PermissionDenied -> SendOptimalAttachmentResult.PermissionDenied
                        OptimalAccessDecision.BackendContractBlocked -> SendOptimalAttachmentResult.PersistenceFailed
                        OptimalAccessDecision.Granted -> SendOptimalAttachmentResult.PersistenceFailed
                    }
                    else -> SendOptimalAttachmentResult.PersistenceFailed
                }
            },
        )
    }
}

class DiscardOptimalCameraCaptureUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val attachmentStore: OptimalAttachmentStore,
) {
    suspend operator fun invoke(target: OptimalCameraCaptureTarget) {
        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (organizationId.isBlank() || userId.isBlank()) return
        attachmentStore.discardCameraTarget(organizationId, userId, target)
    }
}

private fun Throwable.toAttachmentResult(): SendOptimalAttachmentResult = when (this) {
    is OptimalAttachmentException -> when (failure) {
        OptimalAttachmentFailure.CANCELLED -> SendOptimalAttachmentResult.Cancelled
        OptimalAttachmentFailure.SOURCE_UNAVAILABLE -> SendOptimalAttachmentResult.SourceUnavailable
        OptimalAttachmentFailure.EMPTY_FILE -> SendOptimalAttachmentResult.EmptyFile
        OptimalAttachmentFailure.UNSUPPORTED_MIME -> SendOptimalAttachmentResult.UnsupportedMime
        OptimalAttachmentFailure.SESSION_UNAVAILABLE -> SendOptimalAttachmentResult.SessionUnavailable
        OptimalAttachmentFailure.SECURITY_VIOLATION -> SendOptimalAttachmentResult.SecurityViolation
        OptimalAttachmentFailure.PERSISTENCE_FAILED -> SendOptimalAttachmentResult.PersistenceFailed
    }
    else -> SendOptimalAttachmentResult.PersistenceFailed
}
