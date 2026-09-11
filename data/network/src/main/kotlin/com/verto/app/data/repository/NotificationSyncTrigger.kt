package com.verto.app.data.repository

interface NotificationSyncTrigger {
    suspend fun pullNotifications(organizationId: String): Result<Unit>
}
