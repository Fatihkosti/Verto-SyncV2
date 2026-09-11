package com.verto.app.feature.notifications.domain.repository

import com.verto.app.feature.notifications.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

/** حد مركز الإشعارات دون كشف قاعدة البيانات أو عميل الشبكة للواجهة. */
interface NotificationCenterGateway {
    suspend fun observeCurrentUserNotifications(): Flow<List<AppNotification>>
    suspend fun markAsRead(id: String)
    suspend fun markAllAsRead()
    suspend fun sync(): Result<Unit>
}
