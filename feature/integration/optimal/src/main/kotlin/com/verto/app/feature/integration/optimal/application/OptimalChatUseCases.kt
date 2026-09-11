package com.verto.app.feature.integration.optimal.application

import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageKind

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingAccessException
import com.verto.app.feature.integration.optimal.domain.model.ArchiveOptimalConversationResult
import com.verto.app.feature.integration.optimal.domain.model.MarkOptimalReadResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalChatLoadResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalChatSnapshot
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.model.SendOptimalTextResult
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationQueryPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutgoingMessageDraft
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOptimalChatUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val conversationQuery: OptimalConversationQueryPort,
    private val messagingPort: OptimalMessagingPort,
    private val operationGuard: OptimalOperationGuard,
) {
    operator fun invoke(clientId: String): Flow<OptimalChatLoadResult> = flow {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) {
            emit(OptimalChatLoadResult.NotFound)
            return@flow
        }
        when (operationGuard.check(OptimalOperation.VIEW_MESSAGES, OptimalGuardLayer.USE_CASE)) {
            OptimalAccessDecision.Granted -> Unit
            OptimalAccessDecision.PermissionDenied -> {
                emit(OptimalChatLoadResult.PermissionDenied)
                return@flow
            }
            OptimalAccessDecision.BackendContractBlocked -> {
                emit(OptimalChatLoadResult.PermissionDenied)
                return@flow
            }
        }

        emitAll(
            combine(
                sessionReader.organizationId.map(String::trim).distinctUntilChanged(),
                sessionReader.userId.map(String::trim).distinctUntilChanged(),
            ) { organizationId, userId -> organizationId to userId }
                .flatMapLatest { (organizationId, userId) ->
                    if (organizationId.isBlank() || userId.isBlank()) {
                        flowOf(OptimalChatLoadResult.SessionUnavailable)
                    } else {
                        conversationQuery.observeConversations(organizationId)
                            .flatMapLatest { conversations ->
                                val conversation = conversations.firstOrNull {
                                    it.organizationId == organizationId &&
                                        it.clientId == normalizedClientId
                                }
                                if (conversation == null) {
                                    flowOf(OptimalChatLoadResult.NotFound)
                                } else {
                                    messagingPort.observeMessages(
                                        organizationId = organizationId,
                                        conversationId = conversation.conversationId,
                                    ).map { messages ->
                                        val safeMessages = messages
                                            .asSequence()
                                            .filter { it.organizationId == organizationId }
                                            .filter { it.clientId == normalizedClientId }
                                            .filter { it.conversationId == conversation.conversationId }
                                            .sortedWith(compareBy({ it.createdAt }, { it.messageId }))
                                            .toList()
                                        OptimalChatLoadResult.Ready(
                                            OptimalChatSnapshot(
                                                organizationId = organizationId,
                                                clientId = normalizedClientId,
                                                conversationId = conversation.conversationId,
                                                companyName = conversation.companyName,
                                                currentUserId = userId,
                                                unreadCount = conversation.unreadCount.coerceAtLeast(0),
                                                isArchived = conversation.isArchived,
                                                messages = safeMessages,
                                            ),
                                        )
                                    }
                                }
                            }
                    }
                },
        )
    }
}

class SendOptimalTextMessageUseCase @Inject constructor(
    private val messagingPort: OptimalMessagingPort,
    private val operationGuard: OptimalOperationGuard,
) {
    suspend operator fun invoke(
        clientId: String,
        companyName: String,
        messageId: String,
        text: String,
    ): SendOptimalTextResult {
        val normalizedClientId = clientId.trim()
        val normalizedMessageId = messageId.trim()
        val body = text.trim()
        if (body.isBlank()) return SendOptimalTextResult.EmptyText
        if (normalizedClientId.isBlank() || normalizedMessageId.isBlank()) {
            return SendOptimalTextResult.InvalidConversation
        }
        when (
            operationGuard.check(
                OptimalOperation.SEND_MESSAGES,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> return SendOptimalTextResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> {
                return SendOptimalTextResult.BackendContractBlocked
            }
            OptimalAccessDecision.Granted -> Unit
        }

        return messagingPort.saveOutgoingMessage(
            OptimalOutgoingMessageDraft(
                messageId = normalizedMessageId,
                clientId = normalizedClientId,
                companyName = companyName.trim(),
                kind = OptimalMessageKind.TEXT,
                body = body,
            ),
        ).fold(
            onSuccess = { SendOptimalTextResult.Saved(it) },
            onFailure = { error -> error.toSendResult() },
        )
    }
}

class MarkOptimalConversationReadUseCase @Inject constructor(
    private val messagingPort: OptimalMessagingPort,
    private val operationGuard: OptimalOperationGuard,
) {
    suspend operator fun invoke(clientId: String): MarkOptimalReadResult {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return MarkOptimalReadResult.ConversationUnavailable
        if (
            operationGuard.check(
                OptimalOperation.VIEW_MESSAGES,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId",
            ) != OptimalAccessDecision.Granted
        ) {
            return MarkOptimalReadResult.PermissionDenied
        }
        return messagingPort.markConversationRead(normalizedClientId).fold(
            onSuccess = { MarkOptimalReadResult.Marked(it) },
            onFailure = { MarkOptimalReadResult.ConversationUnavailable },
        )
    }
}

class ArchiveOptimalConversationUseCase @Inject constructor(
    private val messagingPort: OptimalMessagingPort,
    private val operationGuard: OptimalOperationGuard,
) {
    suspend operator fun invoke(clientId: String): ArchiveOptimalConversationResult {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isBlank()) return ArchiveOptimalConversationResult.Failed
        return when (
            operationGuard.check(
                OptimalOperation.ARCHIVE_CONVERSATION,
                OptimalGuardLayer.USE_CASE,
                "clientId=$normalizedClientId",
            )
        ) {
            OptimalAccessDecision.PermissionDenied -> ArchiveOptimalConversationResult.PermissionDenied
            OptimalAccessDecision.BackendContractBlocked -> {
                ArchiveOptimalConversationResult.BackendContractBlocked
            }
            OptimalAccessDecision.Granted -> messagingPort
                .setConversationArchived(normalizedClientId, archived = true)
                .fold(
                    onSuccess = { ArchiveOptimalConversationResult.Archived },
                    onFailure = { ArchiveOptimalConversationResult.Failed },
                )
        }
    }
}

private fun Throwable.toSendResult(): SendOptimalTextResult = when (this) {
    is OptimalMessagingAccessException -> when (decision) {
        OptimalAccessDecision.PermissionDenied -> SendOptimalTextResult.PermissionDenied
        OptimalAccessDecision.BackendContractBlocked -> SendOptimalTextResult.BackendContractBlocked
        OptimalAccessDecision.Granted -> SendOptimalTextResult.PersistenceFailed
    }
    else -> SendOptimalTextResult.PersistenceFailed
}
