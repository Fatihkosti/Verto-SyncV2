package com.verto.app.feature.messages.bridge

import com.verto.app.feature.party.application.model.customerSegmentLabel
import com.verto.app.data.remote.dto.InternalMessageDto
import com.verto.app.data.repository.WithdrawalRepository
import com.verto.app.feature.messages.application.ConversationListItem
import com.verto.app.feature.messages.application.MessageItem
import com.verto.app.feature.messages.application.MessageKind
import com.verto.app.feature.messages.application.MessagesGateway
import com.verto.app.feature.messages.application.MessagingServerContractException
import com.verto.app.feature.messages.application.RegisteredMarketerItem
import com.verto.app.feature.messages.application.SendMessageCommand
import com.verto.app.feature.messages.data.MessagesRealtimeSource
import com.verto.app.feature.party.domain.repository.PartyDirectoryGateway
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class SupabaseMessagesGateway @Inject constructor(
    private val withdrawalRepository: WithdrawalRepository,
    private val partyDirectory: PartyDirectoryGateway,
    private val realtimeSource: MessagesRealtimeSource
) : MessagesGateway {

    override suspend fun getConversations(): Result<List<ConversationListItem>> =
        withdrawalRepository.getConversations().map { conversations ->
            val clientMap = partyDirectory.getAllClientsSync().associateBy { it.id }
            conversations.map { conversation ->
                ConversationListItem(
                    conversationId = conversation.id,
                    clientId = conversation.clientId,
                    clientName = clientMap[conversation.clientId]?.name ?: conversation.clientId.take(8),
                    subject = conversation.subject.ifBlank { "محادثة" },
                    lastMessage = conversation.lastMessage.orEmpty(),
                    lastAt = formatTime(conversation.lastMessageAt),
                    unreadCount = conversation.adminUnread
                )
            }
        }

    override suspend fun getRegisteredMarketers(): Result<List<RegisteredMarketerItem>> = runCatching {
        val allClients = partyDirectory.getAllClientsSync().associateBy { it.id }
        val remote = withdrawalRepository.getRegisteredMarketers().getOrNull()
            ?.mapNotNull { marketer ->
                val client = allClients[marketer.clientId] ?: return@mapNotNull null
                RegisteredMarketerItem(
                    clientId = client.id,
                    clientName = client.name,
                    typeLabel = client.customerSegment.customerSegmentLabel()
                )
            }
            .orEmpty()

        remote.ifEmpty {
            allClients.values
                .filter { client ->
                    client.customerSegment?.name == "MARKETER" ||
                        client.customerSegment?.name == "WORKSHOP_OWNER"
                }
                .map { client ->
                    RegisteredMarketerItem(
                        clientId = client.id,
                        clientName = client.name,
                        typeLabel = client.customerSegment.customerSegmentLabel()
                    )
                }
        }
    }

    /**
     * Single Verto entry point for opening a conversation. The server owns the
     * conversation identity; the client never synthesizes an id or relationship.
     */
    override suspend fun openConversation(clientId: String): Result<String> {
        val subject = "محادثة " + SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date())
        return withdrawalRepository.openConversation(clientId, subject).map { it.id }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> =
        Result.failure(MessagingServerContractException(
            "حذف المحادثة غير متاح حتى يوفر الخادم عقد حذف آمن للمؤسسة"
        ))

    override fun observeConversationListChanges(): Flow<Unit> = flow {
        val orgId = withdrawalRepository.currentOrgId()
        if (!orgId.isNullOrBlank()) {
            emitAll(realtimeSource.observeConversationListChanges(orgId))
        }
    }

    override suspend fun getMessages(conversationId: String): Result<List<MessageItem>> =
        withdrawalRepository.getMessagesByConversation(conversationId)
            .map { messages -> messages.map { it.toMessageItem() } }

    override suspend fun markConversationRead(conversationId: String): Result<Unit> =
        withdrawalRepository.markConversationRead(conversationId)

    override suspend fun sendMessage(command: SendMessageCommand): Result<MessageItem> =
        withdrawalRepository.sendAdminMediaMessage(
            conversationId = command.conversationId,
            clientId = command.clientId,
            type = command.kind.name,
            body = command.body,
            mediaBytes = command.mediaBytes,
            mimeType = command.mimeType,
            durationMs = command.durationMs
        ).map { it.toMessageItem() }

    override suspend fun deleteMessage(messageId: String): Result<Unit> =
        Result.failure(MessagingServerContractException(
            "حذف الرسالة غير متاح حتى يوفر الخادم عقد حذف آمن للمؤسسة"
        ))

    override fun observeMessages(conversationId: String): Flow<MessageItem> = flow {
        val orgId = withdrawalRepository.currentOrgId()
        if (!orgId.isNullOrBlank()) {
            emitAll(realtimeSource.observeMessages(orgId, conversationId).map { it.toMessageItem() })
        }
    }

    private fun InternalMessageDto.toMessageItem(): MessageItem = MessageItem(
        id = id,
        conversationId = conversationId,
        clientId = clientId,
        senderType = senderType,
        kind = MessageKind.fromRemote(type),
        body = body,
        mediaUrl = mediaUrl,
        mediaMime = mediaMime,
        mediaDurationMs = mediaDurationMs,
        isRead = isRead,
        createdAt = createdAt
    )

    private fun formatTime(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val date = iso.take(10)
            val time = iso.substring(11, 16)
            "$date $time"
        } catch (_: Exception) {
            iso.take(10)
        }
    }
}
