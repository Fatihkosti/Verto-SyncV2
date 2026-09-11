package com.verto.app.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "client_rfm_cache",
    foreignKeys = [ForeignKey(
        entity = PartyIdentityEntity::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("segment"), Index("calculatedAt")]
)
data class RfmCacheEntity(
    @PrimaryKey val clientId: String,

    val recencyScore: Int = 0,
    val frequencyScore: Int = 0,
    val monetaryScore: Int = 0,

    val daysSinceLastPurchase: Int = 0,
    val totalInvoiceCount: Int = 0,
    val totalSpent: Double = 0.0,
    val avgInvoiceValue: Double = 0.0,

    val segment: RfmSegment = RfmSegment.NEW_CUSTOMERS,

    val totalProfit: Double = 0.0,
    val firstPurchaseAt: Long = 0L,
    val lastPurchaseAt: Long = 0L,
    val customerLifespanDays: Int = 0,

    val calculatedAt: Long = System.currentTimeMillis()
)
