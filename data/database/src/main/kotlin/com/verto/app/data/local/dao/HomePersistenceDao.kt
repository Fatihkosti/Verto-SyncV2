package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.HomeEventStateEntity
import com.verto.app.data.local.entity.HomeQuickActionOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeQuickActionOrderDao {
    @Query(
        """
        SELECT * FROM home_quick_action_order
        WHERE organization_id = :organizationId AND user_id = :userId
        ORDER BY position ASC
        """,
    )
    fun observeOrder(
        organizationId: String,
        userId: String,
    ): Flow<List<HomeQuickActionOrderEntity>>

    @Query(
        """
        SELECT * FROM home_quick_action_order
        WHERE organization_id = :organizationId AND user_id = :userId
        ORDER BY position ASC
        """,
    )
    suspend fun getOrder(
        organizationId: String,
        userId: String,
    ): List<HomeQuickActionOrderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(rows: List<HomeQuickActionOrderEntity>)

    @Query(
        """
        DELETE FROM home_quick_action_order
        WHERE organization_id = :organizationId AND user_id = :userId
        """,
    )
    suspend fun deleteOrder(organizationId: String, userId: String): Int

    @Transaction
    suspend fun replaceOrder(
        organizationId: String,
        userId: String,
        rows: List<HomeQuickActionOrderEntity>,
    ) {
        deleteOrder(organizationId, userId)
        if (rows.isNotEmpty()) insertOrder(rows)
    }
}

@Dao
interface HomeEventStateDao {
    @Query(
        """
        SELECT * FROM home_event_states
        WHERE organization_id = :organizationId AND user_id = :userId
        ORDER BY event_key ASC
        """,
    )
    fun observeStates(
        organizationId: String,
        userId: String,
    ): Flow<List<HomeEventStateEntity>>

    @Query(
        """
        SELECT * FROM home_event_states
        WHERE organization_id = :organizationId AND user_id = :userId
        ORDER BY event_key ASC
        """,
    )
    suspend fun getStates(
        organizationId: String,
        userId: String,
    ): List<HomeEventStateEntity>

    @Query(
        """
        SELECT * FROM home_event_states
        WHERE organization_id = :organizationId
          AND user_id = :userId
          AND event_key = :eventKey
        LIMIT 1
        """,
    )
    suspend fun getState(
        organizationId: String,
        userId: String,
        eventKey: String,
    ): HomeEventStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: HomeEventStateEntity)

    @Transaction
    suspend fun markSeen(
        organizationId: String,
        userId: String,
        eventKey: String,
        seenAt: Long,
    ) {
        val current = getState(organizationId, userId, eventKey)
        upsert(
            (current ?: HomeEventStateEntity(organizationId, userId, eventKey)).copy(
                seenAt = current?.seenAt ?: seenAt,
            ),
        )
    }

    @Transaction
    suspend fun snooze(
        organizationId: String,
        userId: String,
        eventKey: String,
        snoozedUntil: Long,
    ) {
        val current = getState(organizationId, userId, eventKey)
        upsert(
            (current ?: HomeEventStateEntity(organizationId, userId, eventKey)).copy(
                snoozedUntil = snoozedUntil,
            ),
        )
    }

    @Transaction
    suspend fun dismiss(
        organizationId: String,
        userId: String,
        eventKey: String,
        dismissedAt: Long,
    ) {
        val current = getState(organizationId, userId, eventKey)
        upsert(
            (current ?: HomeEventStateEntity(organizationId, userId, eventKey)).copy(
                snoozedUntil = null,
                dismissedAt = dismissedAt,
            ),
        )
    }

    @Query(
        """
        DELETE FROM home_event_states
        WHERE organization_id = :organizationId
          AND user_id = :userId
          AND snoozed_until IS NOT NULL
          AND snoozed_until <= :nowEpochMillis
          AND seen_at IS NULL
          AND dismissed_at IS NULL
        """,
    )
    suspend fun deleteExpiredSnoozeOnlyStates(
        organizationId: String,
        userId: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE home_event_states
        SET snoozed_until = NULL
        WHERE organization_id = :organizationId
          AND user_id = :userId
          AND snoozed_until IS NOT NULL
          AND snoozed_until <= :nowEpochMillis
        """,
    )
    suspend fun clearExpiredSnoozeFromHistoricalStates(
        organizationId: String,
        userId: String,
        nowEpochMillis: Long,
    ): Int

    @Transaction
    suspend fun clearExpiredSnoozes(
        organizationId: String,
        userId: String,
        nowEpochMillis: Long,
    ): Int {
        val deleted = deleteExpiredSnoozeOnlyStates(organizationId, userId, nowEpochMillis)
        val cleared = clearExpiredSnoozeFromHistoricalStates(organizationId, userId, nowEpochMillis)
        return deleted + cleared
    }
}
