package com.verto.app.data.sync

import androidx.room.withTransaction
import com.verto.app.data.local.entity.NotificationAudience
import com.verto.app.data.local.entity.NotificationEntity
import com.verto.app.data.local.entity.NotificationType
import com.verto.app.data.remote.dto.NotificationPageRequest
import com.verto.app.data.remote.dto.RemoteNotificationDto
import com.verto.app.utils.SupabaseDateParser
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

/**
 * Pulls the current user's notification read model through the paginated server RPC.
 * The complete cache replacement is committed atomically so a process death cannot leave Room
 * empty between clear and insert.
 */
suspend fun SyncRuntime.pullNotifications(orgId: String) {
    require(orgId.isNotBlank()) { "organizationId is required for notification sync" }

    val remoteNotifications = mutableListOf<RemoteNotificationDto>()
    var beforeCreatedAt: String? = null
    var beforeId: String? = null
    var previousCursor: String? = null

    while (true) {
        val page = supabase.postgrest
            .rpc(
                "get_my_notifications_page_v1",
                NotificationPageRequest(
                    limit = PAGE_SIZE,
                    beforeCreatedAt = beforeCreatedAt,
                    beforeId = beforeId,
                ),
            )
            .decodeList<RemoteNotificationDto>()

        if (page.isEmpty()) break
        page.forEach { dto ->
            require(dto.orgId.isBlank() || dto.orgId == orgId) {
                "notification tenant mismatch"
            }
        }
        remoteNotifications += page

        if (page.size < PAGE_SIZE) break
        val last = page.last()
        val nextCreatedAt = last.createdAt
            ?: error("notification page cursor is missing created_at")
        val nextId = last.id.takeIf { it.isNotBlank() }
            ?: error("notification page cursor is missing id")
        val cursor = "$nextCreatedAt|$nextId"
        check(cursor != previousCursor) { "notification pagination did not advance" }
        previousCursor = cursor
        beforeCreatedAt = nextCreatedAt
        beforeId = nextId
    }

    val localNotifications = remoteNotifications.map { dto ->
        NotificationEntity(
            id = dto.id,
            organizationId = dto.orgId.ifBlank { orgId },
            branchId = dto.branchId,
            targetUserId = dto.targetUserId,
            audience = runCatching { NotificationAudience.valueOf(dto.audience) }
                .getOrDefault(NotificationAudience.DIRECT_EMPLOYEE),
            type = runCatching { NotificationType.valueOf(dto.type) }
                .getOrDefault(NotificationType.UNKNOWN),
            title = dto.title,
            body = dto.body,
            relatedEntityId = dto.relatedEntityId,
            relatedEntityType = dto.relatedEntityType,
            navigationRoute = dto.navigationRoute,
            isRead = dto.isRead,
            createdAt = SupabaseDateParser.parseOrNow(dto.createdAt),
            createdBy = dto.createdBy,
        )
    }

    db.withTransaction {
        db.notificationDao().clearForOrganization(orgId)
        db.notificationDao().upsertNotifications(localNotifications)
    }
}

private const val PAGE_SIZE = 100
