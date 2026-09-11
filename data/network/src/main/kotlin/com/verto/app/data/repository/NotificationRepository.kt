package com.verto.app.data.repository

import com.verto.app.data.local.dao.NotificationDao
import com.verto.app.data.local.entity.NotificationAudience
import com.verto.app.data.local.entity.NotificationEntity
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.CreateAudienceNotificationRequest
import com.verto.app.data.remote.dto.CreateDirectEmployeeNotificationRequest
import com.verto.app.data.remote.dto.MarkNotificationReadRequest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification read model for Verto.
 *
 * Business/system notifications are created by authoritative server events. Android only creates
 * explicit admin-authored notifications through the narrow RPCs below, then consumes the server
 * read model into Room. Read receipts are optimistic locally but durably queued before mutation so
 * an offline read is reconciled on the next successful sync.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val dao: NotificationDao,
    private val authRepository: AuthRepository,
    private val syncTrigger: NotificationSyncTrigger,
    private val readMutations: NotificationReadMutationStore,
) {
    private val supabase by lazy { VertoSupabase.client }

    private suspend fun createManualNotification(
        audience: NotificationAudience,
        title: String,
        body: String,
        targetUserId: String? = null,
    ): Result<Unit> = runCatching {
        require(title.isNotBlank()) { "عنوان الإشعار مطلوب" }
        require(body.isNotBlank()) { "نص الإشعار مطلوب" }

        when (audience) {
            NotificationAudience.DIRECT_EMPLOYEE -> {
                require(!targetUserId.isNullOrBlank()) { "الإشعار المباشر يحتاج موظفاً مستهدفاً" }
                supabase.postgrest.rpc(
                    "create_direct_employee_notification",
                    CreateDirectEmployeeNotificationRequest(
                        employeeId = targetUserId,
                        title = title.trim(),
                        body = body.trim(),
                    ),
                )
            }
            NotificationAudience.ALL_EMPLOYEES -> {
                supabase.postgrest.rpc(
                    "create_all_employees_notification",
                    CreateAudienceNotificationRequest(
                        title = title.trim(),
                        body = body.trim(),
                    ),
                )
            }
            NotificationAudience.MANAGER_ONLY -> {
                supabase.postgrest.rpc(
                    "create_manager_only_notification",
                    CreateAudienceNotificationRequest(
                        title = title.trim(),
                        body = body.trim(),
                    ),
                )
            }
        }

        // Delivery is queued by the database trigger. The client never invokes FCM directly.
        syncCurrentUserNotifications().getOrThrow()
    }

    /**
     * المدير يرى MANAGER_ONLY + ALL_EMPLOYEES + DIRECT_EMPLOYEE الموجهة له.
     * الموظف يرى ALL_EMPLOYEES + DIRECT_EMPLOYEE الموجهة له فقط.
     */
    suspend fun getCurrentUserNotifications(): Flow<List<NotificationEntity>> {
        val profile = authRepository.getMyProfile() ?: return flowOf(emptyList())
        // Network failure must not hide the existing Room cache.
        syncCurrentUserNotifications()
        return if (profile.role == "admin") {
            dao.getManagerNotifications(profile.id, profile.organizationId)
        } else {
            dao.getMyNotifications(profile.id, profile.organizationId)
        }
    }

    suspend fun getCurrentUserUnreadCount(): Flow<Int> {
        val profile = authRepository.getMyProfile() ?: return flowOf(0)
        return if (profile.role == "admin") {
            dao.getManagerUnreadCount(profile.id, profile.organizationId)
        } else {
            dao.getMyUnreadCount(profile.id, profile.organizationId)
        }
    }

    /** Persist intent first, then update Room; remote acknowledgement may happen now or on a later sync. */
    suspend fun markAsRead(id: String) {
        val profile = authRepository.getMyProfile() ?: return
        readMutations.enqueue(profile.organizationId, profile.id, id)
        dao.markAsRead(id = id, organizationId = profile.organizationId)
        flushPendingReadMutations(profile.organizationId, profile.id)
    }

    /** Explicit user action only; opening the screen never calls this. */
    suspend fun markAllAsReadForCurrentUser() {
        val profile = authRepository.getMyProfile() ?: return
        val ids = if (profile.role == "admin") {
            dao.getUnreadIdsForManager(profile.id, profile.organizationId)
        } else {
            dao.getUnreadIdsForUser(profile.id, profile.organizationId)
        }
        readMutations.enqueueAll(profile.organizationId, profile.id, ids)
        if (profile.role == "admin") {
            dao.markAllAsReadForManager(profile.id, profile.organizationId)
        } else {
            dao.markAllAsReadForUser(profile.id, profile.organizationId)
        }
        flushPendingReadMutations(profile.organizationId, profile.id)
    }

    suspend fun syncCurrentUserNotifications(): Result<Unit> = runCatching {
        val profile = authRepository.getMyProfile() ?: return@runCatching

        // A failed receipt write remains durable. Pull still runs so new notifications are not blocked.
        flushPendingReadMutations(profile.organizationId, profile.id)
        syncTrigger.pullNotifications(profile.organizationId).getOrThrow()

        // If the read RPC failed while pull succeeded, remote unread state must not overwrite the
        // user's already-accepted local action. Pending IDs remain the local optimistic overlay.
        readMutations.pending(profile.organizationId, profile.id).forEach { pendingId ->
            dao.markAsRead(pendingId, profile.organizationId)
        }
    }

    suspend fun createDirectEmployeeNotification(
        employeeId: String,
        title: String,
        body: String,
    ): Result<Unit> = createManualNotification(
        audience = NotificationAudience.DIRECT_EMPLOYEE,
        title = title,
        body = body,
        targetUserId = employeeId,
    )

    suspend fun createAllEmployeesNotification(title: String, body: String): Result<Unit> =
        createManualNotification(NotificationAudience.ALL_EMPLOYEES, title, body)

    suspend fun createManagerOnlyNotification(title: String, body: String): Result<Unit> =
        createManualNotification(NotificationAudience.MANAGER_ONLY, title, body)

    private suspend fun flushPendingReadMutations(organizationId: String, userId: String) {
        readMutations.pending(organizationId, userId)
            .take(MAX_READ_REPLAY_PER_PASS)
            .forEach { notificationId ->
                runCatching {
                    supabase.postgrest.rpc(
                        "mark_notification_read",
                        MarkNotificationReadRequest(notificationId),
                    )
                }.onSuccess {
                    readMutations.acknowledge(organizationId, userId, notificationId)
                }
            }
    }

    private companion object {
        const val MAX_READ_REPLAY_PER_PASS = 100
    }
}
