package com.verto.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.MessageConversationEntity
import kotlinx.coroutines.flow.Flow

data class MessageConversationSummaryRow(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    val subject: String,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_sender_id") val lastSenderId: String?,
    @ColumnInfo(name = "last_sender_name") val lastSenderName: String?,
    @ColumnInfo(name = "last_sender_role") val lastSenderRole: String?,
)

@Dao
interface MessageConversationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(conversation: MessageConversationEntity)

    @Query(
        """
        SELECT * FROM message_conversations
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        LIMIT 1
        """,
    )
    suspend fun get(
        organizationId: String,
        conversationId: String,
    ): MessageConversationEntity?

    @Query(
        """
        SELECT * FROM message_conversations
        WHERE organization_id = :organizationId AND client_id = :clientId
        ORDER BY last_activity_at DESC, conversation_id ASC
        LIMIT 1
        """,
    )
    suspend fun getLatestForClient(
        organizationId: String,
        clientId: String,
    ): MessageConversationEntity?

    @Query(
        """
        SELECT COUNT(*) FROM message_conversations
        WHERE organization_id = :organizationId AND client_id = :clientId
        """,
    )
    suspend fun countForClient(
        organizationId: String,
        clientId: String,
    ): Int

    @Query(
        """
        SELECT * FROM message_conversations
        WHERE organization_id = :organizationId
        ORDER BY last_activity_at DESC, conversation_id ASC
        """,
    )
    fun observeForOrganization(organizationId: String): Flow<List<MessageConversationEntity>>

    @Query(
        """
        SELECT
            conversation.organization_id,
            conversation.conversation_id,
            conversation.client_id,
            conversation.subject,
            conversation.is_archived,
            conversation.unread_count,
            conversation.last_message_preview,
            conversation.created_at,
            conversation.last_activity_at,
            conversation.updated_at,
            (
                SELECT message.sender_id
                FROM messages AS message
                WHERE message.organization_id = conversation.organization_id
                  AND message.conversation_id = conversation.conversation_id
                ORDER BY message.created_at DESC, message.message_id DESC
                LIMIT 1
            ) AS last_sender_id,
            (
                SELECT message.sender_name_snapshot
                FROM messages AS message
                WHERE message.organization_id = conversation.organization_id
                  AND message.conversation_id = conversation.conversation_id
                ORDER BY message.created_at DESC, message.message_id DESC
                LIMIT 1
            ) AS last_sender_name,
            (
                SELECT message.sender_role_snapshot
                FROM messages AS message
                WHERE message.organization_id = conversation.organization_id
                  AND message.conversation_id = conversation.conversation_id
                ORDER BY message.created_at DESC, message.message_id DESC
                LIMIT 1
            ) AS last_sender_role
        FROM message_conversations AS conversation
        WHERE conversation.organization_id = :organizationId
        ORDER BY conversation.last_activity_at DESC, conversation.conversation_id ASC
        """,
    )
    fun observeSummariesForOrganization(
        organizationId: String,
    ): Flow<List<MessageConversationSummaryRow>>

    @Query(
        """
        SELECT * FROM message_conversations
        WHERE organization_id = :organizationId AND client_id = :clientId
        ORDER BY last_activity_at DESC, conversation_id ASC
        """,
    )
    fun observeForClient(
        organizationId: String,
        clientId: String,
    ): Flow<List<MessageConversationEntity>>

    @Query(
        """
        UPDATE message_conversations
        SET last_message_preview = CASE
                WHEN :activityAt >= last_activity_at THEN :preview
                ELSE last_message_preview
            END,
            last_activity_at = CASE
                WHEN :activityAt >= last_activity_at THEN :activityAt
                ELSE last_activity_at
            END,
            unread_count = unread_count + :unreadDelta,
            updated_at = CASE
                WHEN :updatedAt >= updated_at THEN :updatedAt
                ELSE updated_at
            END
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        """,
    )
    suspend fun recordActivity(
        organizationId: String,
        conversationId: String,
        preview: String,
        activityAt: Long,
        unreadDelta: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE message_conversations
        SET unread_count = 0, updated_at = :updatedAt
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        """,
    )
    suspend fun clearUnread(
        organizationId: String,
        conversationId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE message_conversations
        SET is_archived = :archived, updated_at = :updatedAt
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        """,
    )
    suspend fun setArchived(
        organizationId: String,
        conversationId: String,
        archived: Boolean,
        updatedAt: Long,
    ): Int
}
