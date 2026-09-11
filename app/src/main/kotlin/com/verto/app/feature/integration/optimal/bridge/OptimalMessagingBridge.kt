package com.verto.app.feature.integration.optimal.bridge

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.session.model.SessionState
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.OptimalCompanyLinkDao
import com.verto.app.data.local.dao.OptimalConversationBindingDao
import com.verto.app.data.local.dao.OptimalOutboxDao
import com.verto.app.data.local.entity.OptimalConversationBindingEntity
import com.verto.app.data.local.entity.OptimalOutboxEntity
import com.verto.app.data.local.entity.OptimalOutboxStatus
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationActivity
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationIdentity
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationState
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessage
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageIdentity
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageDeliveryStatus
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageKind
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMedia
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMediaLocation
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageMediaDraft
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageState
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessageTiming
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingAccessException
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdGenerator
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingIdentity
import com.verto.app.feature.integration.optimal.domain.port.OptimalMessagingPort
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutgoingMessageDraft
import com.verto.app.feature.integration.optimal.domain.port.OptimalSenderSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import com.verto.app.feature.messages.domain.port.EnsureOwnedConversationCommand
import com.verto.app.feature.messages.domain.port.MessageOwnerPort
import com.verto.app.feature.messages.domain.port.OwnedConversation
import com.verto.app.feature.messages.domain.port.OwnedMessage
import com.verto.app.feature.messages.domain.port.OwnedMessageDeliveryStatus
import com.verto.app.feature.messages.domain.port.OwnedMessageKind
import com.verto.app.feature.messages.domain.port.OwnedMessageMedia
import com.verto.app.feature.messages.domain.port.OwnedMessageMediaDraft
import com.verto.app.feature.messages.domain.port.OwnedSenderSnapshot
import com.verto.app.feature.messages.domain.port.PersistOwnedMessageCommand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OptimalMessagingBridge @Inject constructor(
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outboxDao: OptimalOutboxDao,
    private val messageOwner: MessageOwnerPort,
    private val clock: OptimalClock,
    private val operationGuard: OptimalOperationGuard,
    private val conversationBinder: OptimalConversationBinder
) : OptimalMessagingPort {

    override suspend fun getOrCreateConversation(
        clientId: String,
        companyName: String,
    ): Result<OptimalConversationState> = runCatching {
        val session = requireSession()
        require(clientId.isNotBlank()) { "clientId is required" }
        requireAccess(OptimalOperation.VIEW_MESSAGES, "clientId=$clientId")
        database.withTransaction {
            conversationBinder.ensure(session, clientId, companyName, clock.nowMillis())
        }
    }

    override suspend fun getConversation(clientId: String): Result<OptimalConversationState?> = runCatching {
        val session = requireSession()
        require(clientId.isNotBlank()) { "clientId is required" }
        requireAccess(OptimalOperation.VIEW_MESSAGES, "clientId=$clientId")
        val binding = conversationBinder.binding(session.organization.id, clientId) ?: return@runCatching null
        val conversation = messageOwner.getConversation(
            organizationId = session.organization.id,
            conversationId = binding.conversationId,
        ) ?: error("binding points to a missing conversation")
        conversation.toOptimalState(binding.boundAt)
    }

    override fun observeMessages(
        organizationId: String,
        conversationId: String,
    ): Flow<List<OptimalMessage>> = messageOwner.observeMessages(
        organizationId = organizationId,
        conversationId = conversationId,
    ).map { rows -> rows.map(OwnedMessage::toOptimalMessage) }

    override suspend fun saveOutgoingMessage(
        draft: OptimalOutgoingMessageDraft,
    ): Result<OptimalMessage> = runCatching {
        require(draft.messageId.isNotBlank()) { "messageId is required" }
        val session = requireSession()
        requireAccess(OptimalOperation.SEND_MESSAGES, "clientId=${draft.clientId}")
        val organizationId = session.organization.id
        val now = clock.nowMillis()
        database.withTransaction {
            val conversation = conversationBinder.ensure(session, draft.clientId, draft.companyName, now)
            val saved = messageOwner.persistMessage(
                PersistOwnedMessageCommand(
                    organizationId = organizationId,
                    messageId = draft.messageId,
                    conversationId = conversation.conversationId,
                    clientId = draft.clientId,
                    sender = OwnedSenderSnapshot(
                        senderId = session.user.id,
                        senderName = session.user.name,
                        senderRole = session.role,
                    ),
                    kind = draft.kind.toOwnedKind(),
                    body = draft.body,
                    deliveryStatus = OwnedMessageDeliveryStatus.PENDING,
                    isRead = true,
                    createdAt = now,
                    media = draft.media.map(OptimalMessageMediaDraft::toOwnedDraft),
                ),
            )

            val payloadVersion = if (saved.media.isEmpty()) 1 else 2
            val key = OptimalMessagingIdentity.sendIdempotencyKey(
                organizationId = organizationId,
                conversationId = conversation.conversationId,
                messageId = draft.messageId,
                operationVersion = payloadVersion,
            )
            if (outboxDao.getByIdempotencyKey(organizationId, key) == null) {
                outboxDao.insert(
                    OptimalOutboxEntity(
                        organizationId = organizationId,
                        eventId = OptimalMessagingIdentity.sendEventId(draft.messageId),
                        aggregateType = AGGREGATE_CONVERSATION,
                        aggregateId = conversation.conversationId,
                        operation = OPERATION_SEND_MESSAGE,
                        payloadJson = sendPayload(conversation.conversationId, draft.clientId, saved),
                        payloadVersion = payloadVersion,
                        idempotencyKey = key,
                        sequence = outboxDao.nextSequence(
                            organizationId = organizationId,
                            aggregateType = AGGREGATE_CONVERSATION,
                            aggregateId = conversation.conversationId,
                        ),
                        status = OptimalOutboxStatus.PENDING,
                        createdAt = now,
                    ),
                )
            }
            saved.toOptimalMessage()
        }
    }

    override suspend fun markConversationRead(clientId: String): Result<Int> = runCatching {
        val session = requireSession()
        requireAccess(OptimalOperation.VIEW_MESSAGES, "clientId=$clientId")
        val binding = conversationBinder.binding(session.organization.id, clientId)
            ?: error("company has no conversation binding")
        messageOwner.markConversationRead(
            organizationId = session.organization.id,
            conversationId = binding.conversationId,
            updatedAt = clock.nowMillis(),
        )
    }

    override suspend fun setConversationArchived(
        clientId: String,
        archived: Boolean,
    ): Result<OptimalConversationState> = runCatching {
        val session = requireSession()
        requireAccess(OptimalOperation.ARCHIVE_CONVERSATION, "clientId=$clientId")
        val organizationId = session.organization.id
        val now = clock.nowMillis()
        database.withTransaction {
            val binding = conversationBinder.binding(organizationId, clientId)
                ?: error("company has no conversation binding")
            val current = messageOwner.getConversation(organizationId, binding.conversationId)
                ?: error("binding points to a missing conversation")
            if (current.isArchived == archived) return@withTransaction current.toOptimalState(binding.boundAt)

            check(
                messageOwner.setConversationArchived(
                    organizationId = organizationId,
                    conversationId = binding.conversationId,
                    archived = archived,
                    updatedAt = now,
                ),
            ) { "conversation does not exist" }

            val sequence = outboxDao.nextSequence(
                organizationId = organizationId,
                aggregateType = AGGREGATE_CONVERSATION,
                aggregateId = binding.conversationId,
            )
            val key = OptimalMessagingIdentity.archiveIdempotencyKey(
                organizationId = organizationId,
                conversationId = binding.conversationId,
                archived = archived,
                sequence = sequence,
                operationVersion = 1,
            )
            outboxDao.insert(
                OptimalOutboxEntity(
                    organizationId = organizationId,
                    eventId = OptimalMessagingIdentity.archiveEventId(binding.conversationId, archived, sequence),
                    aggregateType = AGGREGATE_CONVERSATION,
                    aggregateId = binding.conversationId,
                    operation = if (archived) OPERATION_ARCHIVE else OPERATION_UNARCHIVE,
                    payloadJson = archivePayload(binding.conversationId, archived),
                    payloadVersion = 1,
                    idempotencyKey = key,
                    sequence = sequence,
                    status = OptimalOutboxStatus.PENDING,
                    createdAt = now,
                ),
            )
            val conversation = messageOwner.getConversation(organizationId, binding.conversationId)
                ?: error("conversation disappeared after archive")
            conversation.toOptimalState(binding.boundAt)
        }
    }

    private suspend fun requireAccess(operation: OptimalOperation, details: String) {
        when (val decision = operationGuard.check(operation, OptimalGuardLayer.REPOSITORY, details)) {
            OptimalAccessDecision.Granted -> Unit
            else -> throw OptimalMessagingAccessException(decision)
        }
    }

    private suspend fun requireSession(): SessionState {
        val session = sessionReader.snapshot()
        require(session.organization.id.isNotBlank()) { "organization session is unavailable" }
        require(session.user.id.isNotBlank()) { "user session is unavailable" }
        require(session.user.name.isNotBlank()) { "sender name snapshot is unavailable" }
        require(session.role.isNotBlank()) { "sender role snapshot is unavailable" }
        return session
    }

    private companion object {
        const val AGGREGATE_CONVERSATION = "CONVERSATION"
        const val OPERATION_SEND_MESSAGE = "SEND_MESSAGE"
        const val OPERATION_ARCHIVE = "ARCHIVE_CONVERSATION"
        const val OPERATION_UNARCHIVE = "UNARCHIVE_CONVERSATION"
    }
}


class OptimalConversationBinder @Inject constructor(
    private val bindingDao: OptimalConversationBindingDao,
    private val companyLinkDao: OptimalCompanyLinkDao,
    private val messageOwner: MessageOwnerPort,
    private val idGenerator: OptimalMessagingIdGenerator,
) {
    suspend fun binding(organizationId: String, clientId: String) = bindingDao.get(organizationId, clientId)

    suspend fun ensure(session: SessionState, clientId: String, companyName: String, now: Long): OptimalConversationState {
        val organizationId = session.organization.id
        require(companyLinkDao.get(organizationId, clientId) != null) {
            "company is not linked to Optimal in this organization"
        }
        bindingDao.get(organizationId, clientId)?.let { binding ->
            val conversation = messageOwner.getConversation(organizationId, binding.conversationId)
                ?: error("binding points to a missing conversation")
            require(conversation.clientId == clientId) { "binding client mismatch" }
            return conversation.toOptimalState(binding.boundAt)
        }
        val conversation = messageOwner.getOrCreateConversation(
            EnsureOwnedConversationCommand(
                organizationId = organizationId,
                proposedConversationId = idGenerator.newConversationId(),
                clientId = clientId,
                subject = companyName.trim().ifBlank { "محادثة Optimal" },
                createdAt = now,
            ),
        )
        bindingDao.insert(OptimalConversationBindingEntity(organizationId, clientId, conversation.conversationId, now))
        return conversation.toOptimalState(now)
    }
}

private fun OwnedConversation.toOptimalState(boundAt: Long): OptimalConversationState = OptimalConversationState(
    identity = OptimalConversationIdentity(organizationId, clientId, conversationId),
    activity = OptimalConversationActivity(subject, boundAt, isArchived, unreadCount, lastMessagePreview, lastActivityAt),
)

private fun sendPayload(conversationId: String, clientId: String, message: OwnedMessage): String {
    val attachments = message.media.joinToString(prefix = "[", postfix = "]") { media ->
        val duration = media.durationMs?.toString() ?: "null"
        """{"media_id":"${jsonEscape(media.mediaId)}","mime_type":"${jsonEscape(media.mimeType)}","size_bytes":${media.sizeBytes},"duration_ms":$duration}"""
    }
    return """{"conversation_id":"${jsonEscape(conversationId)}","client_id":"${jsonEscape(clientId)}","message_id":"${jsonEscape(message.messageId)}","kind":"${message.kind.name}","body":"${jsonEscape(message.body)}","sender_id":"${jsonEscape(message.sender.senderId)}","sender_name":"${jsonEscape(message.sender.senderName)}","sender_role":"${jsonEscape(message.sender.senderRole)}","created_at":${message.createdAt},"attachments":$attachments}"""
}

private fun archivePayload(conversationId: String, archived: Boolean): String =
    """{"conversation_id":"${jsonEscape(conversationId)}","archived":$archived}"""

private fun jsonEscape(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t"); else -> append(character)
        }
    }
}

internal fun OptimalMessageKind.toOwnedKind(): OwnedMessageKind = when (this) {
    OptimalMessageKind.TEXT -> OwnedMessageKind.TEXT
    OptimalMessageKind.IMAGE -> OwnedMessageKind.IMAGE
    OptimalMessageKind.VOICE -> OwnedMessageKind.VOICE
    OptimalMessageKind.VIDEO -> OwnedMessageKind.VIDEO
    OptimalMessageKind.DOCUMENT -> OwnedMessageKind.DOCUMENT
}

internal fun OptimalMessageMediaDraft.toOwnedDraft(): OwnedMessageMediaDraft = OwnedMessageMediaDraft(
    mediaId = mediaId,
    localUri = localUri,
    remoteUrl = remoteUrl,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
)

internal fun OwnedMessage.toOptimalMessage(): OptimalMessage = OptimalMessage(
    identity = OptimalMessageIdentity(organizationId, messageId, conversationId, clientId, remoteId),
    state = OptimalMessageState(
        sender = OptimalSenderSnapshot(sender.senderId, sender.senderName, sender.senderRole),
        kind = when (kind) {
            OwnedMessageKind.TEXT -> OptimalMessageKind.TEXT
            OwnedMessageKind.IMAGE -> OptimalMessageKind.IMAGE
            OwnedMessageKind.VOICE -> OptimalMessageKind.VOICE
            OwnedMessageKind.VIDEO -> OptimalMessageKind.VIDEO
            OwnedMessageKind.DOCUMENT -> OptimalMessageKind.DOCUMENT
        },
        body = body,
        deliveryStatus = when (deliveryStatus) {
            OwnedMessageDeliveryStatus.PENDING -> OptimalMessageDeliveryStatus.PENDING
            OwnedMessageDeliveryStatus.SENT -> OptimalMessageDeliveryStatus.SENT
            OwnedMessageDeliveryStatus.RECEIVED -> OptimalMessageDeliveryStatus.RECEIVED
            OwnedMessageDeliveryStatus.FAILED -> OptimalMessageDeliveryStatus.FAILED
        },
        isRead = isRead,
    ),
    timing = OptimalMessageTiming(createdAt, updatedAt),
    media = media.map(OwnedMessageMedia::toOptimalMedia),
)

internal fun OwnedMessageMedia.toOptimalMedia(): OptimalMessageMedia = OptimalMessageMedia(
    mediaId = mediaId,
    messageId = messageId,
    location = OptimalMessageMediaLocation(localUri, remoteUrl),
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    createdAt = createdAt
)
