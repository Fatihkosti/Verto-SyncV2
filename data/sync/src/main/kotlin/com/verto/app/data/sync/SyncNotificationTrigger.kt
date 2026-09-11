package com.verto.app.data.sync

import com.verto.app.data.repository.NotificationSyncTrigger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotificationTrigger @Inject constructor(
    private val syncManager: SyncManager
) : NotificationSyncTrigger {
    override suspend fun pullNotifications(organizationId: String): Result<Unit> =
        syncManager.pullNotifications(organizationId)
}
