package com.verto.app.feature.inventory.domain.model

enum class InventoryUnitType { COUNT, LENGTH }

data class InventoryUnit(
    val id: String,
    val name: String,
    val quantityPerUnit: Double,
    val unitType: InventoryUnitType = InventoryUnitType.COUNT
)

data class InventoryItem(
    val id: String,
    val partNumber: String = "",
    val name: String,
    val barcode: String = "",
    val unitId: String? = null,
    val linkedUnitItemId: String? = null,
    val isUnitItem: Boolean = false,
    val quantityPerUnit: Double = 0.0,
    val isService: Boolean = false,
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val quantity: Int = 0,
    val minQuantity: Int = 5,
    val location: String = "",
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isDirty: Boolean = true
)

data class SaveInventoryItemCommand(
    val item: InventoryItem,
    val categories: List<String>,
    val unit: InventoryUnit?,
    val now: Long
)

data class SaveInventoryUnitItemCommand(
    val item: InventoryItem,
    val unitName: String,
    val unitQuantity: Double,
    val linkedPieceItemId: String,
    val categories: List<String>,
    val now: Long
)

data class InventoryPriceChange(
    val item: InventoryItem,
    val buyPrice: Double,
    val sellPrice: Double,
    val now: Long
)

/** Structured record for one item inside a user-triggered bulk price operation. */
data class InventoryPriceBatchChangeRecord(
    val itemId: String,
    val quantity: Int,
    val oldBuyPrice: Double,
    val oldSellPrice: Double,
    val newBuyPrice: Double,
    val newSellPrice: Double,
    val occurredAtEpochMillis: Long,
)

sealed interface SaveInventoryItemResult {
    data class Success(val item: InventoryItem) : SaveInventoryItemResult
    data class DuplicateName(val name: String) : SaveInventoryItemResult
}


data class InventoryPurchaseReceiptCommand(
    val itemId: String,
    val quantity: Int,
    val invoiceId: String,
    val supplierId: String,
    val buyPriceMinor: Long,
    val sellPriceMinor: Long? = null,
    val actorId: String,
    val actorName: String,
    val occurredAt: Long,
    val writeId: String,
    val eventId: String,
    val sourceType: String = "INVOICE",
    val sourceId: String = invoiceId,
    val sourceLineId: String? = null,
    val postingGroupId: String? = null,
)

data class InventorySaleStockMutation(
    val itemId: String,
    val quantity: Int,
    val invoiceId: String,
    val clientId: String,
    val unitPrice: Double,
    val allowNegativeStock: Boolean,
)

data class InventoryPostingIdentity(
    val writeId: String,
    val sourceLineId: String,
    val postingGroupId: String,
)

data class InventoryCostRevaluationRecord(
    val itemId: String,
    val quantityBefore: Int,
    val oldUnitCostMinor: Long,
    val newUnitCostMinor: Long,
    val revaluationDifferenceMinor: Long,
)
