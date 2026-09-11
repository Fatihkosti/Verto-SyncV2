package com.verto.app.feature.dashboard.bridge

import com.verto.app.data.repository.NotificationRepository
import com.verto.app.feature.dashboard.application.HomeNotificationBadgeQuery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@Singleton
class RoomHomeNotificationBadgeQuery @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : HomeNotificationBadgeQuery {
    override fun observeUnreadCount(): Flow<Int> = flow {
        emitAll(notificationRepository.getCurrentUserUnreadCount())
    }.catch {
        emit(0)
    }
}
