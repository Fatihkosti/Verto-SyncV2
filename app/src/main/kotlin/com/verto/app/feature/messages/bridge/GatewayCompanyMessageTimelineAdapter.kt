package com.verto.app.feature.messages.bridge

import com.verto.app.feature.messages.domain.port.CompanyMessageTimelineItem
import com.verto.app.feature.messages.domain.port.CompanyMessageTimelineKind
import com.verto.app.feature.messages.domain.port.CompanyMessageTimelinePort
import com.verto.app.feature.messages.domain.port.MessageOwnerPort
import com.verto.app.feature.messages.domain.port.OwnedMessageKind
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Read-only timeline projection over the Messages-owned Room store. */
class GatewayCompanyMessageTimelineAdapter @Inject constructor(
    private val messageOwner: MessageOwnerPort,
) : CompanyMessageTimelinePort {
    override fun observeCompanyMessages(
        organizationId: String,
        clientId: String,
    ): Flow<List<CompanyMessageTimelineItem>> = messageOwner
        .observeMessagesForClient(organizationId, clientId)
        .map { messages ->
            messages.map { message ->
                CompanyMessageTimelineItem(
                    organizationId = message.organizationId,
                    clientId = message.clientId,
                    messageId = message.messageId,
                    conversationId = message.conversationId,
                    senderType = message.sender.senderRole,
                    kind = when (message.kind) {
                        OwnedMessageKind.TEXT -> CompanyMessageTimelineKind.TEXT
                        OwnedMessageKind.IMAGE -> CompanyMessageTimelineKind.IMAGE
                        OwnedMessageKind.VOICE -> CompanyMessageTimelineKind.VOICE
                        OwnedMessageKind.VIDEO -> CompanyMessageTimelineKind.VIDEO
                        OwnedMessageKind.DOCUMENT -> CompanyMessageTimelineKind.DOCUMENT
                    },
                    body = message.body,
                    occurredAt = message.createdAt,
                )
            }
        }
        .catch { emit(emptyList()) }
        .distinctUntilChanged()
}
