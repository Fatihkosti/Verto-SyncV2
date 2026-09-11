package com.verto.app.feature.notifications.bridge
import com.verto.app.data.local.entity.NotificationAudience
import com.verto.app.data.local.entity.NotificationEntity
import com.verto.app.data.repository.NotificationRepository
import com.verto.app.feature.notifications.domain.model.AppNotification
import com.verto.app.feature.notifications.domain.model.AppNotificationAudience
import com.verto.app.feature.notifications.domain.repository.NotificationCenterGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** يحول مخزن الإشعارات الحالي إلى نماذج وعقد مملوكة للميزة. */
@Singleton
class NotificationCenterGatewayAdapter @Inject constructor(
    private val repository: NotificationRepository
) : NotificationCenterGateway {

    override suspend fun observeCurrentUserNotifications(): Flow<List<AppNotification>> =
        repository.getCurrentUserNotifications().map { notifications ->
            notifications.map { it.toDomain() }
        }

    override suspend fun markAsRead(id: String) {
        repository.markAsRead(id)
    }

    override suspend fun markAllAsRead() {
        repository.markAllAsReadForCurrentUser()
    }

    override suspend fun sync(): Result<Unit> = repository.syncCurrentUserNotifications()


    private fun NotificationEntity.toDomain(): AppNotification = AppNotification(
        id = id,
        audience = when (audience) {
            NotificationAudience.DIRECT_EMPLOYEE -> AppNotificationAudience.DIRECT_EMPLOYEE
            NotificationAudience.ALL_EMPLOYEES -> AppNotificationAudience.ALL_EMPLOYEES
            NotificationAudience.MANAGER_ONLY -> AppNotificationAudience.MANAGER_ONLY
        },
        title = title,
        body = body,
        navigationRoute = navigationRoute,
        isRead = isRead,
        createdAt = createdAt,
        type = type.name,
        relatedEntityId = relatedEntityId,
        relatedEntityType = relatedEntityType,
    )
}
