package com.verto.app.feature.dashboard.bridge

import com.verto.app.data.local.dao.HomeEventStateDao
import com.verto.app.data.local.dao.HomeQuickActionOrderDao
import com.verto.app.data.local.entity.HomeEventStateEntity
import com.verto.app.data.local.entity.HomeQuickActionOrderEntity
import com.verto.feature.dashboard.api.HomeEventState
import com.verto.feature.dashboard.api.HomeEventStateStore
import com.verto.feature.dashboard.api.HomeStorageScope
import com.verto.feature.dashboard.api.QuickActionOrderStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomQuickActionOrderStore @Inject constructor(
    private val dao: HomeQuickActionOrderDao,
) : QuickActionOrderStore {
    override fun observeOrder(scope: HomeStorageScope): Flow<List<String>> =
        dao.observeOrder(scope.organizationId, scope.userId)
            .map { rows -> rows.map(HomeQuickActionOrderEntity::actionId) }

    override suspend fun saveOrder(
        scope: HomeStorageScope,
        orderedActionIds: List<String>,
        updatedAtEpochMillis: Long,
    ) {
        require(updatedAtEpochMillis >= 0L) { "updatedAtEpochMillis must not be negative" }
        val normalizedIds = orderedActionIds.map(String::trim)
        require(normalizedIds.none(String::isBlank)) { "quick action ids must not be blank" }
        require(normalizedIds.size == normalizedIds.toSet().size) {
            "quick action ids must be unique"
        }
        dao.replaceOrder(
            organizationId = scope.organizationId,
            userId = scope.userId,
            rows = normalizedIds.mapIndexed { position, actionId ->
                HomeQuickActionOrderEntity(
                    organizationId = scope.organizationId,
                    userId = scope.userId,
                    actionId = actionId,
                    position = position,
                    updatedAt = updatedAtEpochMillis,
                )
            },
        )
    }

    override suspend fun clearOrder(scope: HomeStorageScope) {
        dao.deleteOrder(scope.organizationId, scope.userId)
    }
}

@Singleton
class RoomHomeEventStateStore @Inject constructor(
    private val dao: HomeEventStateDao,
) : HomeEventStateStore {
    override fun observeStates(scope: HomeStorageScope): Flow<List<HomeEventState>> =
        dao.observeStates(scope.organizationId, scope.userId)
            .map { rows -> rows.map(HomeEventStateEntity::toApi) }

    override suspend fun markSeen(
        scope: HomeStorageScope,
        eventKey: String,
        seenAtEpochMillis: Long,
    ) {
        dao.markSeen(
            scope.organizationId,
            scope.userId,
            normalizeEventKey(eventKey),
            requireTimestamp(seenAtEpochMillis),
        )
    }

    override suspend fun snooze(
        scope: HomeStorageScope,
        eventKey: String,
        snoozedUntilEpochMillis: Long,
    ) {
        dao.snooze(
            scope.organizationId,
            scope.userId,
            normalizeEventKey(eventKey),
            requireTimestamp(snoozedUntilEpochMillis),
        )
    }

    override suspend fun dismiss(
        scope: HomeStorageScope,
        eventKey: String,
        dismissedAtEpochMillis: Long,
    ) {
        dao.dismiss(
            scope.organizationId,
            scope.userId,
            normalizeEventKey(eventKey),
            requireTimestamp(dismissedAtEpochMillis),
        )
    }

    override suspend fun clearExpiredSnoozes(
        scope: HomeStorageScope,
        nowEpochMillis: Long,
    ): Int = dao.clearExpiredSnoozes(
        scope.organizationId,
        scope.userId,
        requireTimestamp(nowEpochMillis),
    )

    private fun normalizeEventKey(eventKey: String): String =
        eventKey.trim().also { require(it.isNotEmpty()) { "eventKey must not be blank" } }

    private fun requireTimestamp(value: Long): Long =
        value.also { require(it >= 0L) { "event timestamp must not be negative" } }
}

private fun HomeEventStateEntity.toApi(): HomeEventState = HomeEventState(
    eventKey = eventKey,
    seenAtEpochMillis = seenAt,
    snoozedUntilEpochMillis = snoozedUntil,
    dismissedAtEpochMillis = dismissedAt,
)
