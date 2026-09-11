package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotifications(notifications: List<NotificationEntity>)

    @Query("""
        SELECT * FROM notifications
        WHERE organizationId = :organizationId
          AND (
            audience = 'ALL_EMPLOYEES'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
        ORDER BY createdAt DESC
    """)
    fun getMyNotifications(userId: String, organizationId: String): Flow<List<NotificationEntity>>

    @Query("""
        SELECT * FROM notifications
        WHERE organizationId = :organizationId
          AND (
            audience = 'ALL_EMPLOYEES'
            OR audience = 'MANAGER_ONLY'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
        ORDER BY createdAt DESC
    """)
    fun getManagerNotifications(userId: String, organizationId: String): Flow<List<NotificationEntity>>

    @Query("""
        SELECT COUNT(*) FROM notifications
        WHERE organizationId = :organizationId
          AND isRead = 0
          AND (
            audience = 'ALL_EMPLOYEES'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
    """)
    fun getMyUnreadCount(userId: String, organizationId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM notifications
        WHERE organizationId = :organizationId
          AND isRead = 0
          AND (
            audience = 'ALL_EMPLOYEES'
            OR audience = 'MANAGER_ONLY'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
    """)
    fun getManagerUnreadCount(userId: String, organizationId: String): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id AND organizationId = :organizationId")
    suspend fun markAsRead(id: String, organizationId: String)

    @Query("""
        SELECT id FROM notifications
        WHERE organizationId = :organizationId
          AND isRead = 0
          AND (
            audience = 'ALL_EMPLOYEES'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
    """)
    suspend fun getUnreadIdsForUser(userId: String, organizationId: String): List<String>

    @Query("""
        SELECT id FROM notifications
        WHERE organizationId = :organizationId
          AND isRead = 0
          AND (
            audience = 'ALL_EMPLOYEES'
            OR audience = 'MANAGER_ONLY'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
    """)
    suspend fun getUnreadIdsForManager(userId: String, organizationId: String): List<String>

    @Query("""
        UPDATE notifications
        SET isRead = 1
        WHERE organizationId = :organizationId
          AND (
            audience = 'ALL_EMPLOYEES'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
    """)
    suspend fun markAllAsReadForUser(userId: String, organizationId: String)

    @Query("""
        UPDATE notifications
        SET isRead = 1
        WHERE organizationId = :organizationId
          AND (
            audience = 'ALL_EMPLOYEES'
            OR audience = 'MANAGER_ONLY'
            OR (audience = 'DIRECT_EMPLOYEE' AND targetUserId = :userId)
          )
    """)
    suspend fun markAllAsReadForManager(userId: String, organizationId: String)

    @Query("DELETE FROM notifications WHERE organizationId = :organizationId")
    suspend fun clearForOrganization(organizationId: String)

    /** Session 308 server-owned explicit delete only. */
    @Query("DELETE FROM notifications WHERE organizationId = :organizationId AND id = :id")
    suspend fun deleteNotificationById(organizationId: String, id: String): Int
}
