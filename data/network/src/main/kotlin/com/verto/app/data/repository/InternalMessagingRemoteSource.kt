package com.verto.app.data.repository

import com.verto.app.core.security.SecureMediaPolicy
import com.verto.app.core.security.TenantIsolationPolicy
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.ConversationDto
import com.verto.app.data.remote.dto.InternalMessageDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AdminMediaMessageCommand(
    val conversationId: String?,
    val clientId: String,
    val type: String,
    val body: String,
    val mediaBytes: ByteArray?,
    val mimeType: String?,
    val durationMs: Long?
)

internal class InternalMessagingRemoteSource(
    private val authRepository: AuthRepository
) {
    private val client by lazy { VertoSupabase.client }

suspend fun getConversations(): Result<List<ConversationDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["conversations"]
                .select {
                    filter { eq("org_id", profile.organizationId) }
                    order("updated_at", Order.DESCENDING)
                }
                .decodeList<ConversationDto>()
        }
    }

suspend fun getMessages(clientId: String): Result<List<InternalMessageDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["internal_messages"]
                .select { filter {
                    eq("client_id", clientId)
                    eq("org_id", profile.organizationId)
                } }
                .decodeList<InternalMessageDto>()
                .sortedBy { it.createdAt }
        }
    }

suspend fun sendAdminMessage(clientId: String, body: String): Result<InternalMessageDto> =
    withContext(Dispatchers.IO) {
        runCatching {
            val uid     = client.auth.currentUserOrNull()?.id ?: error("غير مسجل")
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            val msgId   = java.util.UUID.randomUUID().toString()
            // نستخدم created_at الخادم بدل الوقت المحلي (توحيد timestamp)
            val inserted = client.postgrest["internal_messages"]
                .insert(AdminMessageInsert(
                    id         = msgId,
                    orgId      = profile.organizationId,
                    clientId   = clientId,
                    senderId   = uid,
                    senderType = "ADMIN",
                    body       = body
                )) { select() }
                .decodeSingleOrNull<InternalMessageDto>()
            inserted ?: InternalMessageDto(
                id         = msgId,
                orgId      = profile.organizationId,
                clientId   = clientId,
                senderId   = uid,
                senderType = "ADMIN",
                body       = body,
                isRead     = false,
                createdAt  = nowIso()
            )
        }
    }

suspend fun markMessagesRead(clientId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["internal_messages"]
                .update(MarkReadUpdate(isRead = true)) { filter {
                    eq("client_id", clientId)
                    eq("org_id", profile.organizationId)
                    eq("sender_type", "MARKETER")
                } }
            Unit
        }
    }

suspend fun markConversationRead(conversationId: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["conversations"]
                .update(ConversationUnreadUpdate(adminUnread = 0)) { filter {
                    eq("id", conversationId)
                    eq("org_id", profile.organizationId)
                } }
            client.postgrest["internal_messages"]
                .update(MarkReadUpdate(isRead = true)) { filter {
                    eq("conversation_id", conversationId)
                    eq("org_id", profile.organizationId)
                    eq("sender_type", "MARKETER")
                } }
            Unit
        }
    }

suspend fun getMessagesByConversation(conversationId: String): Result<List<InternalMessageDto>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            client.postgrest["internal_messages"]
                .select { filter {
                    eq("conversation_id", conversationId)
                    eq("org_id", profile.organizationId)
                } }
                .decodeList<InternalMessageDto>()
                .sortedBy { it.createdAt }
        }
    }

suspend fun sendAdminMediaMessage(command: AdminMediaMessageCommand): Result<InternalMessageDto> =
    withContext(Dispatchers.IO) {
        runCatching {
            val conversationId = command.conversationId
            val clientId = command.clientId
            val type = command.type
            val body = command.body
            val mediaBytes = command.mediaBytes
            val mimeType = command.mimeType
            val durationMs = command.durationMs
            val uid = TenantIsolationPolicy.requireIdentifier(
                client.auth.currentUserOrNull()?.id ?: error("غير مسجل"),
                "userId"
            )
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)
            val targetClientId = TenantIsolationPolicy.requireIdentifier(clientId, "clientId")
            val targetConversationId = TenantIsolationPolicy.requireIdentifier(
                conversationId ?: error("المحادثة مطلوبة لإرسال الوسائط"),
                "conversationId"
            )
            requireConversationAccess(orgId, targetConversationId, targetClientId)

            val msgId = java.util.UUID.randomUUID().toString()
            var uploadedUrl: String? = null
            var safeMimeType: String? = null
            if (mediaBytes != null || mimeType != null) {
                val bytes = mediaBytes ?: error("بيانات الوسائط مفقودة")
                val mime = mimeType ?: error("نوع الوسائط مفقود")
                val media = SecureMediaPolicy.validate(type, mime, bytes.size)
                val bucket = when (media.kind) {
                    com.verto.app.core.security.ChatMediaKind.IMAGE -> "chat-images"
                    com.verto.app.core.security.ChatMediaKind.AUDIO -> "chat-audio"
                }
                val path = SecureMediaPolicy.storagePath(
                    organizationId = orgId,
                    userId = uid,
                    conversationId = targetConversationId,
                    mediaId = msgId,
                    extension = media.extension
                )
                client.storage.from(bucket).upload(path, bytes)
                uploadedUrl = client.storage.from(bucket).createSignedUrl(path, 1.hours)
                safeMimeType = media.mimeType
            }

            val safeBody = body.trim().take(4_000)
            val inserted = client.postgrest["internal_messages"]
                .insert(AdminMediaMessageInsert(
                    id = msgId,
                    orgId = orgId,
                    clientId = targetClientId,
                    conversationId = targetConversationId,
                    senderId = uid,
                    senderType = "ADMIN",
                    type = type.trim().uppercase(Locale.US),
                    body = safeBody,
                    mediaUrl = uploadedUrl,
                    mediaMime = safeMimeType,
                    mediaDurationMs = durationMs?.coerceIn(0L, 60L * 60L * 1000L)
                )) { select() }
                .decodeSingleOrNull<InternalMessageDto>()
                ?.also { TenantIsolationPolicy.requireSameTenant(orgId, it.orgId) }
            inserted ?: InternalMessageDto(
                id = msgId,
                orgId = orgId,
                clientId = targetClientId,
                conversationId = targetConversationId,
                senderId = uid,
                senderType = "ADMIN",
                type = type.trim().uppercase(Locale.US),
                body = safeBody,
                mediaUrl = uploadedUrl,
                mediaMime = safeMimeType,
                mediaDurationMs = durationMs?.coerceIn(0L, 60L * 60L * 1000L),
                isRead = false,
                createdAt = nowIso()
            )
        }
    }

/**
 * Remote delete is intentionally not issued by Verto until the server exposes
 * an authenticated, tenant-safe deletion contract. UI callers must surface failure.
 */
suspend fun deleteMessage(messageId: String): Result<Unit> =
    Result.failure(IllegalStateException("SERVER_PLAN: secure message deletion contract is unavailable"))

suspend fun deleteConversation(conversationId: String): Result<Unit> =
    Result.failure(IllegalStateException("SERVER_PLAN: secure conversation deletion contract is unavailable"))

/**
 * Canonical Verto conversation-opening command. Identity is returned by the server.
 * The current server RPC creates a new conversation; canonical race-safe get-or-create
 * remains a SERVER_PLAN dependency and must not be emulated client-side.
 */
suspend fun openConversation(clientId: String, subject: String = ""): Result<ConversationDto> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: error("غير مسجل")
            val orgId = TenantIsolationPolicy.requireTenantId(profile.organizationId)
            val targetClientId = TenantIsolationPolicy.requireIdentifier(clientId, "clientId")
            client.postgrest.rpc(
                "create_new_conversation",
                CreateNewConversationParams(targetClientId, subject)
            ).decodeAs<ConversationDto>().also { conversation ->
                TenantIsolationPolicy.requireSameTenant(orgId, conversation.orgId)
                require(conversation.clientId == targetClientId) { "الخادم أعاد محادثة لعميل مختلف" }
            }
        }
    }

suspend fun getTotalUnreadCount(): Result<Int> =
    withContext(Dispatchers.IO) {
        runCatching {
            val profile = authRepository.getMyProfile() ?: return@runCatching 0
            val convs = client.postgrest["conversations"]
                .select { filter { eq("org_id", profile.organizationId) } }
                .decodeList<ConversationDto>()
            convs.sumOf { it.adminUnread }
        }
    }

private suspend fun requireConversationAccess(
    organizationId: String,
    conversationId: String,
    clientId: String
): ConversationDto {
    val conversation = client.postgrest["conversations"]
        .select {
            filter {
                eq("id", conversationId)
                eq("org_id", organizationId)
                eq("client_id", clientId)
            }
        }
        .decodeList<ConversationDto>()
        .singleOrNull()
        ?: throw SecurityException("المحادثة غير موجودة في هذه المؤسسة")
    TenantIsolationPolicy.requireSameTenant(organizationId, conversation.orgId)
    return conversation
}
}
