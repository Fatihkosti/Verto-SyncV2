package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.data.local.dao.OptimalConversationBindingDao
import com.verto.app.feature.integration.optimal.domain.model.OptimalConversationListItem
import com.verto.app.feature.integration.optimal.domain.port.OptimalConversationQueryPort
import com.verto.app.feature.messages.domain.port.MessageOwnerPort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class OptimalConversationQueryBridge @Inject constructor(
    private val messageOwner: MessageOwnerPort,
    private val bindingDao: OptimalConversationBindingDao,
) : OptimalConversationQueryPort {
    override fun observeConversations(organizationId: String): Flow<List<OptimalConversationListItem>> {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        return combine(
            messageOwner.observeConversationSummaries(organizationId),
            bindingDao.observeForOrganization(organizationId),
        ) { summaries, bindings ->
            val bindingsByConversation = bindings
                .asSequence()
                .filter { it.organizationId == organizationId }
                .associateBy { it.conversationId }
            summaries
                .asSequence()
                .filter { it.organizationId == organizationId }
                .mapNotNull { summary ->
                    val binding = bindingsByConversation[summary.conversationId] ?: return@mapNotNull null
                    if (binding.clientId != summary.clientId) return@mapNotNull null
                    OptimalConversationListItem(
                        organizationId = organizationId,
                        clientId = summary.clientId,
                        conversationId = summary.conversationId,
                        companyName = summary.subject,
                        lastMessagePreview = summary.lastMessagePreview,
                        lastSenderName = summary.lastSender?.senderName,
                        lastSenderRole = summary.lastSender?.senderRole,
                        unreadCount = summary.unreadCount.coerceAtLeast(0),
                        isArchived = summary.isArchived,
                        lastActivityAt = summary.lastActivityAt,
                    )
                }
                .sortedWith(compareByDescending<OptimalConversationListItem> { it.lastActivityAt }.thenBy { it.conversationId })
                .toList()
        }
    }
}
