package com.verto.app.data.repository

import com.verto.app.data.local.dao.ForecastCacheDao
import com.verto.app.data.local.entity.ForecastCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastRepository @Inject constructor(
    private val dao: ForecastCacheDao
) {
    fun getForecastsInRange(type: String, granularity: String, from: Long, to: Long) =
        dao.getForecastsInRange(type, granularity, from, to)

    suspend fun getAllByType(type: String, granularity: String) =
        dao.getAllByType(type, granularity)

    suspend fun saveForecasts(forecasts: List<ForecastCacheEntity>) {
        if (forecasts.isEmpty()) return
        dao.clearByType(forecasts.first().forecastType, forecasts.first().granularity)
        dao.insertAll(forecasts)
    }

    suspend fun clearStale(olderThanMillis: Long) = dao.clearStale(olderThanMillis)
    suspend fun getLastComputedAt(type: String) = dao.getLastComputedAt(type)
}
