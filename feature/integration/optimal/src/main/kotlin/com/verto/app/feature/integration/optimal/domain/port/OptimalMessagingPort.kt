package com.verto.app.feature.integration.optimal.domain.port

import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalIdempotencyKeyFactory
import kotlinx.coroutines.flow.Flow

data class OptimalConversationIdentity(
    val organizationId: String,
    val clientId: String,
    val conversationId: String,
)

data class OptimalConversationActivity(
    val subject: String,
    val boundAt: Long,
    val isArchived: Boolean,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val lastActivityAt: Long,
)

data class OptimalConversationState(
    val identity: OptimalConversationIdentity,
    val activity: OptimalConversationActivity,
) {
    val organizationId: String get() = identity.organizationId
    val clientId: String get() = identity.clientId
    val conversationId: String get() = identity.conversationId
    val subject: String get() = activity.subject
    val boundAt: Long get() = activity.boundAt
    val isArchived: Boolean get() = activity.isArchived
    val unreadCount: Int get() = activity.unreadCount
    val lastMessagePreview: String get() = activity.lastMessagePreview
    val lastActivityAt: Long get() = activity.lastActivityAt
}

data class OptimalOutgoingMessageDraft(
    /** Stable id generated once by the caller and reused on retries. */
    val messageId: String,
    val clientId: String,
    val companyName: String,
    val kind: OptimalMessageKind,
    val body: String,
    val media: List<OptimalMessageMediaDraft> = emptyList(),
)

/** Optimal-owned messaging boundary; provider-specific Messages models never cross into this module. */
interface OptimalMessagingPort {
    suspend fun getOrCreateConversation(clientId: String, companyName: String): Result<OptimalConversationState>
    suspend fun getConversation(clientId: String): Result<OptimalConversationState?>
    fun observeMessages(organizationId: String, conversationId: String): Flow<List<OptimalMessage>>
    suspend fun saveOutgoingMessage(draft: OptimalOutgoingMessageDraft): Result<OptimalMessage>
    suspend fun markConversationRead(clientId: String): Result<Int>
    suspend fun setConversationArchived(clientId: String, archived: Boolean): Result<OptimalConversationState>
}

interface OptimalMessagingIdGenerator {
    fun newConversationId(): String
    fun newMessageId(): String
    fun newMediaId(): String
}
object OptimalMessagingIdentity {
    fun sendIdempotencyKey(
        organizationId: String,
        conversationId: String,
        messageId: String,
        operationVersion: Int = 1,
    ): String = OptimalIdempotencyKeyFactory.create(
        organizationId = organizationId,
        operation = "SEND_MESSAGE",
        localIdentity = "$conversationId|$messageId",
        operationVersion = operationVersion,
    )

    fun archiveIdempotencyKey(
        organizationId: String,
        conversationId: String,
        archived: Boolean,
        sequence: Long,
        operationVersion: Int = 1,
    ): String {
        require(sequence > 0L)
        return OptimalIdempotencyKeyFactory.create(
            organizationId = organizationId,
            operation = if (archived) "ARCHIVE_CONVERSATION" else "UNARCHIVE_CONVERSATION",
            localIdentity = "$conversationId|$sequence",
            operationVersion = operationVersion,
        )
    }

    fun sendEventId(messageId: String): String = "optimal-send-$messageId"

    fun archiveEventId(conversationId: String, archived: Boolean, sequence: Long): String {
        require(conversationId.isNotBlank())
        require(sequence > 0L)
        return "optimal-archive-$conversationId-$archived-$sequence"
    }
}

class OptimalMessagingAccessException(
    val decision: OptimalAccessDecision,
) : IllegalStateException("Optimal messaging access denied: $decision")
