package com.verto.app.feature.messages.application

import kotlinx.coroutines.flow.Flow

data class ConversationListItem(
    val conversationId: String,
    val clientId: String,
    val clientName: String,
    val subject: String,
    val lastMessage: String,
    val lastAt: String,
    val unreadCount: Int
)

data class RegisteredMarketerItem(
    val clientId: String,
    val clientName: String,
    val typeLabel: String
)

enum class MessageKind {
    TEXT,
    IMAGE,
    VOICE;

    companion object {
        fun fromRemote(value: String): MessageKind =
            entries.firstOrNull { it.name == value.uppercase() } ?: TEXT
    }
}

data class MessageItem(
    val id: String,
    val conversationId: String?,
    val clientId: String,
    val senderType: String,
    val kind: MessageKind,
    val body: String,
    val mediaUrl: String?,
    val mediaMime: String?,
    val mediaDurationMs: Long?,
    val isRead: Boolean,
    val createdAt: String
)

data class SendMessageCommand(
    val conversationId: String,
    val clientId: String,
    val kind: MessageKind,
    val body: String,
    val mediaBytes: ByteArray? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null
) {
    companion object {
        fun text(conversationId: String, clientId: String, body: String): SendMessageCommand =
            SendMessageCommand(
                conversationId = conversationId,
                clientId = clientId,
                kind = MessageKind.TEXT,
                body = body
            )

        fun image(
            conversationId: String,
            clientId: String,
            bytes: ByteArray,
            mimeType: String
        ): SendMessageCommand = SendMessageCommand(
            conversationId = conversationId,
            clientId = clientId,
            kind = MessageKind.IMAGE,
            body = "📷",
            mediaBytes = bytes,
            mimeType = mimeType
        )

        fun voice(
            conversationId: String,
            clientId: String,
            bytes: ByteArray,
            durationMs: Long
        ): SendMessageCommand = SendMessageCommand(
            conversationId = conversationId,
            clientId = clientId,
            kind = MessageKind.VOICE,
            body = "🎤",
            mediaBytes = bytes,
            mimeType = "audio/m4a",
            durationMs = durationMs
        )
    }
}

class MessagingServerContractException(message: String) : IllegalStateException(message)

interface MessagesGateway {
    suspend fun getConversations(): Result<List<ConversationListItem>>
    suspend fun getRegisteredMarketers(): Result<List<RegisteredMarketerItem>>
    suspend fun openConversation(clientId: String): Result<String>
    suspend fun deleteConversation(conversationId: String): Result<Unit>
    fun observeConversationListChanges(): Flow<Unit>

    suspend fun getMessages(conversationId: String): Result<List<MessageItem>>
    suspend fun markConversationRead(conversationId: String): Result<Unit>
    suspend fun sendMessage(command: SendMessageCommand): Result<MessageItem>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    fun observeMessages(conversationId: String): Flow<MessageItem>
}

fun List<MessageItem>.appendIfMissing(candidate: MessageItem): List<MessageItem> =
    if (any { it.id == candidate.id }) this else this + candidate
