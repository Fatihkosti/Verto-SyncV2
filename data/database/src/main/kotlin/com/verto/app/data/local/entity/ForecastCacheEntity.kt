package com.verto.app.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "forecast_cache",
    indices = [
        Index("targetDate"),
        Index("forecastType"),
        Index("granularity")
    ]
)
data class ForecastCacheEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val forecastType: String,
    val granularity: String,
    val targetDate: Long,

    val predictedValue: Double,
    val lowerBound: Double = 0.0,
    val upperBound: Double = 0.0,
    val confidenceLevel: Float = 0.8f,

    val method: String = "MOVING_AVERAGE",
    val basedOnDataPoints: Int = 0,

    val computedAt: Long = System.currentTimeMillis()
)
