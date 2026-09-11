package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.JoinCodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JoinCodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(joinCode: JoinCodeEntity)

    @Query("SELECT * FROM join_codes WHERE clientId = :clientId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForClient(clientId: String): JoinCodeEntity?

    @Query("SELECT * FROM join_codes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<JoinCodeEntity>>

    @Query("UPDATE join_codes SET used = 1, usedAt = :usedAt, usedByUserId = :usedByUserId WHERE id = :id")
    suspend fun markAsUsed(id: String, usedAt: Long = System.currentTimeMillis(), usedByUserId: String? = null)

    @Query("DELETE FROM join_codes WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long)
}
