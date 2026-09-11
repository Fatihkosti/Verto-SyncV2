package com.verto.app.feature.dashboard.application.pendingaction

import com.verto.feature.dashboard.api.HomeEventStateStore
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.HomeStorageScope
import javax.inject.Inject

/** Write boundary for Home-only pending action state. */
class ManagePendingActionStateUseCase @Inject constructor(
    private val stateStore: HomeEventStateStore,
) {
    suspend fun markSeen(
        context: HomePermissionContext,
        eventKey: String,
        seenAtEpochMillis: Long,
    ) {
        requireTimestamp(seenAtEpochMillis, "seenAtEpochMillis")
        stateStore.markSeen(context.storageScope(), requireEventKey(eventKey), seenAtEpochMillis)
    }

    suspend fun snooze(
        context: HomePermissionContext,
        eventKey: String,
        nowEpochMillis: Long,
        snoozedUntilEpochMillis: Long,
    ) {
        requireTimestamp(nowEpochMillis, "nowEpochMillis")
        requireTimestamp(snoozedUntilEpochMillis, "snoozedUntilEpochMillis")
        require(snoozedUntilEpochMillis > nowEpochMillis) {
            "snoozedUntilEpochMillis must be later than nowEpochMillis"
        }
        stateStore.snooze(
            context.storageScope(),
            requireEventKey(eventKey),
            snoozedUntilEpochMillis,
        )
    }

    suspend fun dismiss(
        context: HomePermissionContext,
        eventKey: String,
        dismissedAtEpochMillis: Long,
    ) {
        requireTimestamp(dismissedAtEpochMillis, "dismissedAtEpochMillis")
        stateStore.dismiss(context.storageScope(), requireEventKey(eventKey), dismissedAtEpochMillis)
    }

    suspend fun clearExpiredSnoozes(
        context: HomePermissionContext,
        nowEpochMillis: Long,
    ): Int {
        requireTimestamp(nowEpochMillis, "nowEpochMillis")
        return stateStore.clearExpiredSnoozes(context.storageScope(), nowEpochMillis)
    }

    private fun requireEventKey(eventKey: String): String =
        eventKey.trim().also { require(it.isNotEmpty()) { "eventKey must not be blank" } }

    private fun requireTimestamp(value: Long, label: String) {
        require(value >= 0L) { "$label must not be negative" }
    }
}

private fun HomePermissionContext.storageScope(): HomeStorageScope = HomeStorageScope(
    organizationId = organizationId,
    userId = userId,
)
