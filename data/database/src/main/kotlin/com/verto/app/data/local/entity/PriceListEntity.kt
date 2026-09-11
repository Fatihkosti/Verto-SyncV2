package com.verto.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Durable price-list template metadata.
 * Prices, names and stock are deliberately NOT copied here; inventory stays the source of truth.
 */
@Serializable
@Entity(
    tableName = "price_list_templates",
    indices = [
        Index(value = ["organization_id"]),
        Index(value = ["organization_id", "name"]),
    ],
)
data class PriceListTemplateEntity(
    @androidx.room.PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "organization_id") val organizationId: String,
    val name: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * A template contains inventory references only. Cascades prevent orphaned template rows.
 */
@Serializable
@Entity(
    tableName = "price_list_template_items",
    primaryKeys = ["template_id", "inventory_item_id"],
    foreignKeys = [
        ForeignKey(
            entity = PriceListTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventory_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("template_id"), Index("inventory_item_id")],
)
data class PriceListTemplateItemEntity(
    @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)
