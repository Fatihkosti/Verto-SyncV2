package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Tenant-scoped conversation storage owned by the Messages feature.
 *
 * The table is intentionally generic. Optimal only binds a company to a conversation id and never
 * creates a parallel conversation, message, or media table.
 */
@Entity(
    tableName = "message_conversations",
    primaryKeys = ["organization_id", "conversation_id"],
    indices = [
        Index(
            value = ["organization_id", "client_id", "last_activity_at"],
            name = "index_message_conversations_org_client_activity",
        ),
        Index(
            value = ["organization_id", "is_archived", "last_activity_at"],
            name = "index_message_conversations_org_archived_activity",
        ),
    ],
)
data class MessageConversationEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    val subject: String,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long = createdAt,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)

/** A durable message row with sender name and role frozen at the time of the message. */
@Entity(
    tableName = "messages",
    primaryKeys = ["organization_id", "message_id"],
    indices = [
        Index(
            value = ["organization_id", "conversation_id", "created_at"],
            name = "index_messages_org_conversation_created_at",
        ),
        Index(
            value = ["organization_id", "conversation_id", "is_read"],
            name = "index_messages_org_conversation_read",
        ),
        Index(
            value = ["organization_id", "client_id", "created_at"],
            name = "index_messages_org_client_created_at",
        ),
        Index(
            value = ["organization_id", "remote_id"],
            unique = true,
            name = "index_messages_org_remote_id",
        ),
    ],
)
data class MessageEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name_snapshot") val senderNameSnapshot: String,
    @ColumnInfo(name = "sender_role_snapshot") val senderRoleSnapshot: String,
    val kind: String,
    val body: String,
    @ColumnInfo(name = "delivery_status") val deliveryStatus: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
)

/** Metadata for media that belongs to a message. Bytes remain in app-private storage. */
@Entity(
    tableName = "message_media",
    primaryKeys = ["organization_id", "media_id"],
    indices = [
        Index(
            value = ["organization_id", "message_id"],
            name = "index_message_media_org_message",
        ),
    ],
)
data class MessageMediaEntity(
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "local_uri") val localUri: String? = null,
    @ColumnInfo(name = "remote_url") val remoteUrl: String? = null,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
