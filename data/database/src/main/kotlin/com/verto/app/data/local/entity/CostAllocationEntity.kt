package com.verto.app.data.local.entity

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "cost_allocations",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index("sourceId"),
        Index("createdAt")
    ]
)
data class CostAllocationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val itemId: String,
    val sourceType: CostAllocationSource = CostAllocationSource.SHIPMENT_COST,
    val sourceId: String = "",

    val allocatedAmount: Double,
    val perUnitCost: Double = 0.0,
    val quantityAffected: Int = 0,

    val method: CostAllocationMethod = CostAllocationMethod.BY_QUANTITY,

    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
