package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.ForecastCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForecastCacheDao {

    @Query("""
        SELECT * FROM forecast_cache
        WHERE forecastType = :type
          AND granularity = :granularity
          AND targetDate >= :from
          AND targetDate <= :to
        ORDER BY targetDate ASC
    """)
    fun getForecastsInRange(
        type: String,
        granularity: String,
        from: Long,
        to: Long
    ): Flow<List<ForecastCacheEntity>>

    @Query("""
        SELECT * FROM forecast_cache
        WHERE forecastType = :type
          AND granularity = :granularity
        ORDER BY targetDate ASC
    """)
    suspend fun getAllByType(type: String, granularity: String): List<ForecastCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(forecasts: List<ForecastCacheEntity>)

    @Query("DELETE FROM forecast_cache WHERE forecastType = :type AND granularity = :granularity")
    suspend fun clearByType(type: String, granularity: String)

    @Query("DELETE FROM forecast_cache WHERE computedAt < :before")
    suspend fun clearStale(before: Long)

    @Query("SELECT MAX(computedAt) FROM forecast_cache WHERE forecastType = :type")
    suspend fun getLastComputedAt(type: String): Long?
}
