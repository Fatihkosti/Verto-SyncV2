package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.MessageMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageMediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(media: MessageMediaEntity): Long

    @Query(
        """
        SELECT media.* FROM message_media AS media
        INNER JOIN messages AS message
            ON message.organization_id = media.organization_id
           AND message.message_id = media.message_id
        WHERE media.organization_id = :organizationId
          AND message.conversation_id = :conversationId
        ORDER BY media.created_at ASC, media.media_id ASC
        """,
    )
    fun observeForConversation(
        organizationId: String,
        conversationId: String,
    ): Flow<List<MessageMediaEntity>>

    @Query(
        """
        SELECT media.* FROM message_media AS media
        INNER JOIN messages AS message
            ON message.organization_id = media.organization_id
           AND message.message_id = media.message_id
        WHERE media.organization_id = :organizationId
          AND message.client_id = :clientId
        ORDER BY media.created_at ASC, media.media_id ASC
        """,
    )
    fun observeForClient(
        organizationId: String,
        clientId: String,
    ): Flow<List<MessageMediaEntity>>

    @Query(
        """
        SELECT * FROM message_media
        WHERE organization_id = :organizationId AND message_id = :messageId
        ORDER BY created_at ASC, media_id ASC
        """,
    )
    fun observeForMessage(
        organizationId: String,
        messageId: String,
    ): Flow<List<MessageMediaEntity>>

    @Query(
        """
        SELECT * FROM message_media
        WHERE organization_id = :organizationId AND media_id = :mediaId
        LIMIT 1
        """,
    )
    suspend fun get(
        organizationId: String,
        mediaId: String,
    ): MessageMediaEntity?

    @Query(
        """
        SELECT * FROM message_media
        WHERE organization_id = :organizationId AND message_id = :messageId
        ORDER BY created_at ASC, media_id ASC
        """,
    )
    suspend fun getForMessage(
        organizationId: String,
        messageId: String,
    ): List<MessageMediaEntity>
}
