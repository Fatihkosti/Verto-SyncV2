package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.RfmCacheEntity
import com.verto.app.data.local.entity.RfmSegment
import kotlinx.coroutines.flow.Flow

@Dao
interface RfmCacheDao {

    @Query("SELECT * FROM client_rfm_cache ORDER BY totalSpent DESC")
    fun getAll(): Flow<List<RfmCacheEntity>>

    @Query("SELECT * FROM client_rfm_cache WHERE clientId = :clientId")
    suspend fun getByClientId(clientId: String): RfmCacheEntity?

    @Query("SELECT * FROM client_rfm_cache WHERE segment = :segment ORDER BY totalSpent DESC")
    fun getBySegment(segment: RfmSegment): Flow<List<RfmCacheEntity>>

    @Query("SELECT segment, COUNT(*) as count FROM client_rfm_cache GROUP BY segment")
    fun getSegmentDistribution(): Flow<List<RfmSegmentCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rfm: RfmCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rfms: List<RfmCacheEntity>)

    @Query("DELETE FROM client_rfm_cache WHERE clientId = :clientId")
    suspend fun deleteByClientId(clientId: String)

    @Query("DELETE FROM client_rfm_cache")
    suspend fun clearAll()

    @Query("SELECT MAX(calculatedAt) FROM client_rfm_cache")
    suspend fun getLastCalculatedAt(): Long?
}

data class RfmSegmentCount(
    val segment: RfmSegment,
    val count: Int
)
