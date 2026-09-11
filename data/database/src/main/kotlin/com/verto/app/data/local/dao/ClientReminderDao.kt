package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.ClientReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientReminderDao {

    @Query("SELECT * FROM client_reminders WHERE clientId = :clientId ORDER BY reminderAt ASC")
    fun getRemindersForClient(clientId: String): Flow<List<ClientReminderEntity>>

    @Query("SELECT * FROM client_reminders WHERE isDone = 0 ORDER BY reminderAt ASC")
    fun getPendingReminders(): Flow<List<ClientReminderEntity>>

    @Query("SELECT * FROM client_reminders WHERE isDone = 0 AND reminderAt <= :now")
    suspend fun getDueReminders(now: Long): List<ClientReminderEntity>

    @Query("SELECT * FROM client_reminders")
    suspend fun getAllRemindersSync(): List<ClientReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ClientReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ClientReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ClientReminderEntity)

    @Query("UPDATE client_reminders SET isDone = 1 WHERE id = :id")
    suspend fun markAsDone(id: String)

    /** SYNC-013: إدراج من السيرفر دون استبدال الموجود محلياً. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReminderFromRemote(reminder: ClientReminderEntity)

    /** Session 308 remote explicit delete. */
    @Query("DELETE FROM client_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: String): Int
}
