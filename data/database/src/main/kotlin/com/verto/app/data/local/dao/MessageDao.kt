package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: MessageEntity): Long

    @Query(
        """
        SELECT * FROM messages
        WHERE organization_id = :organizationId AND message_id = :messageId
        LIMIT 1
        """,
    )
    suspend fun get(
        organizationId: String,
        messageId: String,
    ): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE organization_id = :organizationId AND remote_id = :remoteId
        LIMIT 1
        """,
    )
    suspend fun getByRemoteId(
        organizationId: String,
        remoteId: String,
    ): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        ORDER BY created_at ASC, message_id ASC
        """,
    )
    fun observeConversation(
        organizationId: String,
        conversationId: String,
    ): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE organization_id = :organizationId AND client_id = :clientId
        ORDER BY created_at ASC, message_id ASC
        """,
    )
    fun observeForClient(
        organizationId: String,
        clientId: String,
    ): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE organization_id = :organizationId
          AND conversation_id = :conversationId
          AND is_read = 0
        """,
    )
    suspend fun unreadCount(
        organizationId: String,
        conversationId: String,
    ): Int

    @Query(
        """
        UPDATE messages
        SET is_read = 1, updated_at = :updatedAt
        WHERE organization_id = :organizationId
          AND conversation_id = :conversationId
          AND is_read = 0
        """,
    )
    suspend fun markConversationRead(
        organizationId: String,
        conversationId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE organization_id = :organizationId AND conversation_id = :conversationId
        """,
    )
    suspend fun countForConversation(
        organizationId: String,
        conversationId: String,
    ): Int
}
