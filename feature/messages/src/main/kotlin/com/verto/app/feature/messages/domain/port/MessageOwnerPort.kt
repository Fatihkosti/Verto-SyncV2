package com.verto.app.feature.messages.domain.port

import kotlinx.coroutines.flow.Flow

enum class OwnedMessageKind {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    DOCUMENT,
}

enum class OwnedMessageDeliveryStatus {
    PENDING,
    SENT,
    RECEIVED,
    FAILED,
}

data class OwnedSenderSnapshot(
    val senderId: String,
    val senderName: String,
    val senderRole: String,
)

data class OwnedMessageMediaDraft(
    val mediaId: String,
    val localUri: String? = null,
    val remoteUrl: String? = null,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
)

data class OwnedMessageMedia(
    val mediaId: String,
    val messageId: String,
    val localUri: String?,
    val remoteUrl: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val createdAt: Long,
)

data class OwnedConversation(
    val organizationId: String,
    val conversationId: String,
    val clientId: String,
    val subject: String,
    val isArchived: Boolean,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val createdAt: Long,
    val lastActivityAt: Long,
    val updatedAt: Long,
)

/** Tenant-scoped conversation row enriched with the latest sender snapshot for list screens. */
data class OwnedConversationSummary(
    val organizationId: String,
    val conversationId: String,
    val clientId: String,
    val subject: String,
    val isArchived: Boolean,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val lastSender: OwnedSenderSnapshot?,
    val createdAt: Long,
    val lastActivityAt: Long,
    val updatedAt: Long,
)

data class OwnedMessage(
    val organizationId: String,
    val messageId: String,
    val conversationId: String,
    val clientId: String,
    val remoteId: String?,
    val sender: OwnedSenderSnapshot,
    val kind: OwnedMessageKind,
    val body: String,
    val deliveryStatus: OwnedMessageDeliveryStatus,
    val isRead: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val media: List<OwnedMessageMedia> = emptyList(),
)

data class EnsureOwnedConversationCommand(
    val organizationId: String,
    val proposedConversationId: String,
    val clientId: String,
    val subject: String,
    val createdAt: Long,
)

data class PersistOwnedMessageCommand(
    val organizationId: String,
    val messageId: String,
    val conversationId: String,
    val clientId: String,
    val remoteId: String? = null,
    val sender: OwnedSenderSnapshot,
    val kind: OwnedMessageKind,
    val body: String,
    val deliveryStatus: OwnedMessageDeliveryStatus,
    val isRead: Boolean,
    val createdAt: Long,
    val media: List<OwnedMessageMediaDraft> = emptyList(),
)

/**
 * Local-first persistence API owned by Messages.
 *
 * Consumers must provide organization id on every read and mutation. Optimal may use this API but
 * must not access the Messages DAOs or create parallel conversation/message/media entities.
 */
interface MessageOwnerPort {
    suspend fun getOrCreateConversation(command: EnsureOwnedConversationCommand): OwnedConversation

    suspend fun getConversation(
        organizationId: String,
        conversationId: String,
    ): OwnedConversation?

    suspend fun countConversationsForClient(
        organizationId: String,
        clientId: String,
    ): Int

    fun observeConversations(organizationId: String): Flow<List<OwnedConversation>>

    fun observeConversationSummaries(
        organizationId: String,
    ): Flow<List<OwnedConversationSummary>>

    fun observeMessages(
        organizationId: String,
        conversationId: String,
    ): Flow<List<OwnedMessage>>

    fun observeMessagesForClient(
        organizationId: String,
        clientId: String,
    ): Flow<List<OwnedMessage>>

    suspend fun persistMessage(command: PersistOwnedMessageCommand): OwnedMessage

    suspend fun getMedia(
        organizationId: String,
        messageId: String,
    ): List<OwnedMessageMedia>

    suspend fun markConversationRead(
        organizationId: String,
        conversationId: String,
        updatedAt: Long,
    ): Int

    suspend fun setConversationArchived(
        organizationId: String,
        conversationId: String,
        archived: Boolean,
        updatedAt: Long,
    ): Boolean
}

object MessageOwnerPolicy {
    fun validateTenantIdentity(organizationId: String, clientId: String) {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        require(clientId.isNotBlank()) { "clientId is required" }
    }

    fun validateConversationIdentity(conversationId: String) {
        require(conversationId.isNotBlank()) { "conversationId is required" }
    }

    fun validateMessage(command: PersistOwnedMessageCommand) {
        validateTenantIdentity(command.organizationId, command.clientId)
        validateConversationIdentity(command.conversationId)
        require(command.messageId.isNotBlank()) { "messageId is required" }
        require(command.sender.senderId.isNotBlank()) { "senderId is required" }
        require(command.sender.senderName.isNotBlank()) { "senderName snapshot is required" }
        require(command.sender.senderRole.isNotBlank()) { "senderRole snapshot is required" }
        require(command.kind != OwnedMessageKind.TEXT || command.body.isNotBlank()) {
            "text body is required"
        }
        command.media.forEach { media ->
            require(media.mediaId.isNotBlank()) { "mediaId is required" }
            require(media.mimeType.isNotBlank()) { "mimeType is required" }
            require(media.sizeBytes > 0L) { "sizeBytes must be positive" }
            require(!media.localUri.isNullOrBlank() || !media.remoteUrl.isNullOrBlank()) {
                "media must have a local or remote location"
            }
        }
        if (command.kind == OwnedMessageKind.VOICE) {
            require(command.media.size == 1) { "voice message requires exactly one audio attachment" }
            require(command.media.single().durationMs?.let { it > 0L } == true) {
                "voice message requires a positive duration"
            }
        }
    }

    fun preview(kind: OwnedMessageKind, body: String): String = when (kind) {
        OwnedMessageKind.TEXT -> body.trim().take(160)
        OwnedMessageKind.IMAGE -> "📷 صورة"
        OwnedMessageKind.VOICE -> "🎤 تسجيل صوتي"
        OwnedMessageKind.VIDEO -> "🎥 فيديو"
        OwnedMessageKind.DOCUMENT -> "📎 مستند"
    }
}
