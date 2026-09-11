package com.verto.app.feature.dashboard.application

import kotlinx.coroutines.flow.Flow

/** Read boundary for the only numeric indicator allowed in the Home header. */
interface HomeNotificationBadgeQuery {
    fun observeUnreadCount(): Flow<Int>
}
