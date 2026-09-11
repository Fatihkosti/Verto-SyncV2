package com.verto.app.feature.messages.data

import androidx.room.withTransaction
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.MessageConversationDao
import com.verto.app.data.local.dao.MessageConversationSummaryRow
import com.verto.app.data.local.dao.MessageDao
import com.verto.app.data.local.dao.MessageMediaDao
import com.verto.app.data.local.entity.MessageConversationEntity
import com.verto.app.data.local.entity.MessageEntity
import com.verto.app.data.local.entity.MessageMediaEntity
import com.verto.app.feature.messages.domain.port.EnsureOwnedConversationCommand
import com.verto.app.feature.messages.domain.port.MessageOwnerPolicy
import com.verto.app.feature.messages.domain.port.MessageOwnerPort
import com.verto.app.feature.messages.domain.port.OwnedConversation
import com.verto.app.feature.messages.domain.port.OwnedConversationSummary
import com.verto.app.feature.messages.domain.port.OwnedMessage
import com.verto.app.feature.messages.domain.port.OwnedMessageDeliveryStatus
import com.verto.app.feature.messages.domain.port.OwnedMessageKind
import com.verto.app.feature.messages.domain.port.OwnedMessageMedia
import com.verto.app.feature.messages.domain.port.PersistOwnedMessageCommand
import com.verto.app.feature.messages.domain.port.OwnedSenderSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomMessageOwnerAdapter @Inject constructor(
    private val database: AppDatabase,
    private val conversationDao: MessageConversationDao,
    private val messageDao: MessageDao,
    private val mediaDao: MessageMediaDao,
) : MessageOwnerPort {

    override suspend fun getOrCreateConversation(
        command: EnsureOwnedConversationCommand,
    ): OwnedConversation {
        MessageOwnerPolicy.validateTenantIdentity(command.organizationId, command.clientId)
        MessageOwnerPolicy.validateConversationIdentity(command.proposedConversationId)
        return database.withTransaction {
            conversationDao.get(command.organizationId, command.proposedConversationId)
                ?.also { require(it.clientId == command.clientId) { "conversation belongs to another client" } }
                ?.toDomain()
                ?: conversationDao.getLatestForClient(command.organizationId, command.clientId)
                    ?.toDomain()
                ?: MessageConversationEntity(
                    organizationId = command.organizationId,
                    conversationId = command.proposedConversationId,
                    clientId = command.clientId,
                    subject = command.subject.trim().ifBlank { "محادثة" },
                    createdAt = command.createdAt,
                ).also { conversationDao.insert(it) }.toDomain()
        }
    }

    override suspend fun getConversation(
        organizationId: String,
        conversationId: String,
    ): OwnedConversation? {
        MessageOwnerPolicy.validateConversationIdentity(conversationId)
        require(organizationId.isNotBlank()) { "organizationId is required" }
        return conversationDao.get(organizationId, conversationId)?.toDomain()
    }

    override suspend fun countConversationsForClient(
        organizationId: String,
        clientId: String,
    ): Int {
        MessageOwnerPolicy.validateTenantIdentity(organizationId, clientId)
        return conversationDao.countForClient(organizationId, clientId)
    }

    override fun observeConversations(organizationId: String): Flow<List<OwnedConversation>> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        return conversationDao.observeForOrganization(organizationId)
            .map { rows -> rows.map(MessageConversationEntity::toDomain) }
    }

    override fun observeConversationSummaries(
        organizationId: String,
    ): Flow<List<OwnedConversationSummary>> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        return conversationDao.observeSummariesForOrganization(organizationId)
            .map { rows -> rows.map(MessageConversationSummaryRow::toDomain) }
    }

    override fun observeMessages(
        organizationId: String,
        conversationId: String,
    ): Flow<List<OwnedMessage>> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        MessageOwnerPolicy.validateConversationIdentity(conversationId)
        return combine(
            messageDao.observeConversation(organizationId, conversationId),
            mediaDao.observeForConversation(organizationId, conversationId),
        ) { messages, media -> messages.attachMedia(media) }
    }

    override fun observeMessagesForClient(
        organizationId: String,
        clientId: String,
    ): Flow<List<OwnedMessage>> {
        MessageOwnerPolicy.validateTenantIdentity(organizationId, clientId)
        return combine(
            messageDao.observeForClient(organizationId, clientId),
            mediaDao.observeForClient(organizationId, clientId),
        ) { messages, media -> messages.attachMedia(media) }
    }

    override suspend fun persistMessage(command: PersistOwnedMessageCommand): OwnedMessage {
        MessageOwnerPolicy.validateMessage(command)
        return database.withTransaction {
            val conversation = conversationDao.get(command.organizationId, command.conversationId)
                ?: error("conversation does not exist")
            require(conversation.clientId == command.clientId) {
                "conversation belongs to another client"
            }

            val candidate = command.toEntity()
            val inserted = messageDao.insertIgnore(candidate)
            val resolved = if (inserted == -1L) {
                messageDao.get(command.organizationId, command.messageId)
                    ?: command.remoteId?.let { messageDao.getByRemoteId(command.organizationId, it) }
                    ?: error("message identity conflict")
            } else {
                candidate
            }

            command.media.forEach { media ->
                val candidateMedia = MessageMediaEntity(
                    organizationId = command.organizationId,
                    mediaId = media.mediaId,
                    messageId = resolved.messageId,
                    localUri = media.localUri,
                    remoteUrl = media.remoteUrl,
                    mimeType = media.mimeType,
                    sizeBytes = media.sizeBytes,
                    durationMs = media.durationMs,
                    createdAt = command.createdAt,
                )
                val mediaInserted = mediaDao.insertIgnore(candidateMedia)
                if (mediaInserted == -1L) {
                    val existingMedia = mediaDao.get(command.organizationId, media.mediaId)
                        ?: error("media identity conflict")
                    require(existingMedia.sameIdentityAndContent(candidateMedia)) {
                        "media identity belongs to another attachment"
                    }
                }
            }

            if (inserted != -1L) {
                check(
                    conversationDao.recordActivity(
                        organizationId = command.organizationId,
                        conversationId = command.conversationId,
                        preview = MessageOwnerPolicy.preview(command.kind, command.body),
                        activityAt = command.createdAt,
                        unreadDelta = if (command.isRead) 0 else 1,
                        updatedAt = command.createdAt,
                    ) == 1,
                ) { "conversation disappeared while saving message" }
            }
            resolved.toDomain(
                mediaDao.getForMessage(command.organizationId, resolved.messageId)
                    .map(MessageMediaEntity::toDomain),
            )
        }
    }

    override suspend fun getMedia(
        organizationId: String,
        messageId: String,
    ): List<OwnedMessageMedia> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(messageId.isNotBlank()) { "messageId is required" }
        return mediaDao.getForMessage(organizationId, messageId).map(MessageMediaEntity::toDomain)
    }

    override suspend fun markConversationRead(
        organizationId: String,
        conversationId: String,
        updatedAt: Long,
    ): Int {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        MessageOwnerPolicy.validateConversationIdentity(conversationId)
        return database.withTransaction {
            val changed = messageDao.markConversationRead(organizationId, conversationId, updatedAt)
            if (conversationDao.clearUnread(organizationId, conversationId, updatedAt) != 1) {
                error("conversation does not exist")
            }
            changed
        }
    }

    override suspend fun setConversationArchived(
        organizationId: String,
        conversationId: String,
        archived: Boolean,
        updatedAt: Long,
    ): Boolean {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        MessageOwnerPolicy.validateConversationIdentity(conversationId)
        return conversationDao.setArchived(
            organizationId = organizationId,
            conversationId = conversationId,
            archived = archived,
            updatedAt = updatedAt,
        ) == 1
    }
}

private fun MessageConversationEntity.toDomain(): OwnedConversation = OwnedConversation(
    organizationId = organizationId,
    conversationId = conversationId,
    clientId = clientId,
    subject = subject,
    isArchived = isArchived,
    unreadCount = unreadCount,
    lastMessagePreview = lastMessagePreview,
    createdAt = createdAt,
    lastActivityAt = lastActivityAt,
    updatedAt = updatedAt,
)

private fun MessageConversationSummaryRow.toDomain(): OwnedConversationSummary =
    run {
        val senderId = lastSenderId
        val senderName = lastSenderName
        val senderRole = lastSenderRole

        OwnedConversationSummary(
        organizationId = organizationId,
        conversationId = conversationId,
        clientId = clientId,
        subject = subject,
        isArchived = isArchived,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        lastSender = if (
            !senderId.isNullOrBlank() &&
            !senderName.isNullOrBlank() &&
            !senderRole.isNullOrBlank()
        ) {
            OwnedSenderSnapshot(
                senderId = senderId,
                senderName = senderName,
                senderRole = senderRole,
            )
        } else {
            null
        },
        createdAt = createdAt,
        lastActivityAt = lastActivityAt,
        updatedAt = updatedAt,
        )
    }

private fun PersistOwnedMessageCommand.toEntity(): MessageEntity = MessageEntity(
    organizationId = organizationId,
    messageId = messageId,
    conversationId = conversationId,
    clientId = clientId,
    remoteId = remoteId,
    senderId = sender.senderId,
    senderNameSnapshot = sender.senderName,
    senderRoleSnapshot = sender.senderRole,
    kind = kind.name,
    body = body,
    deliveryStatus = deliveryStatus.name,
    isRead = isRead,
    createdAt = createdAt,
)

private fun MessageEntity.toDomain(media: List<OwnedMessageMedia> = emptyList()): OwnedMessage = OwnedMessage(
    organizationId = organizationId,
    messageId = messageId,
    conversationId = conversationId,
    clientId = clientId,
    remoteId = remoteId,
    sender = OwnedSenderSnapshot(
        senderId = senderId,
        senderName = senderNameSnapshot,
        senderRole = senderRoleSnapshot,
    ),
    kind = enumValueOrDefault(kind, OwnedMessageKind.TEXT),
    body = body,
    deliveryStatus = enumValueOrDefault(deliveryStatus, OwnedMessageDeliveryStatus.FAILED),
    isRead = isRead,
    createdAt = createdAt,
    updatedAt = updatedAt,
    media = media,
)

private fun List<MessageEntity>.attachMedia(
    media: List<MessageMediaEntity>,
): List<OwnedMessage> {
    val byMessage = media.groupBy(MessageMediaEntity::messageId)
    return map { message ->
        message.toDomain(byMessage[message.messageId].orEmpty().map(MessageMediaEntity::toDomain))
    }
}

private fun MessageMediaEntity.toDomain(): OwnedMessageMedia = OwnedMessageMedia(
    mediaId = mediaId,
    messageId = messageId,
    localUri = localUri,
    remoteUrl = remoteUrl,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    createdAt = createdAt,
)

private fun MessageMediaEntity.sameIdentityAndContent(other: MessageMediaEntity): Boolean =
    organizationId == other.organizationId &&
        mediaId == other.mediaId &&
        messageId == other.messageId &&
        localUri == other.localUri &&
        remoteUrl == other.remoteUrl &&
        mimeType == other.mimeType &&
        sizeBytes == other.sizeBytes &&
        durationMs == other.durationMs

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value.uppercase() } ?: fallback
