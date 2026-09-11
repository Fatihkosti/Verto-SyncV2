package com.verto.app.feature.inventory.application.model

import java.util.UUID

/** Pure Inventory presentation models. No Room/DAO/transport types cross this boundary. */
data class InventoryItemViewData(
    val id: String = UUID.randomUUID().toString(),
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
)

enum class InventoryUnitTypeViewData { COUNT, LENGTH }

data class InventoryUnitViewData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantityPerUnit: Double,
    val unitType: InventoryUnitTypeViewData = InventoryUnitTypeViewData.COUNT,
)

data class CategoryViewData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
)

data class InventoryCategoryLinkViewData(
    val itemId: String,
    val category: String,
)

enum class MovementTypeViewData(val label: String) {
    IN("وارد"),
    OUT("صادر"),
    ADJUST("تعديل"),
    RETURN("مرتجع"),
}

data class InventoryMovementViewData(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val invoiceId: String = "",
    val clientId: String = "",
    val movementType: MovementTypeViewData,
    val quantity: Int,
    val quantityBefore: Int,
    val quantityAfter: Int,
    val unitPrice: Double = 0.0,
    val note: String = "",
    val shipmentId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class LowStockSupplierViewData(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val supplierName: String,
)

data class PriceListDraftItemViewData(
    val inventoryItemId: String,
    val name: String,
    val price: Double,
    val quantity: Int,
    val partNumber: String = "",
    val priceOverridden: Boolean = false,
)

data class PriceListTemplateViewData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val inventoryItemIds: List<String>,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class InventoryPermissionsViewData(
    val inventoryEdit: Boolean = false,
    val inventoryPrice: Boolean = false,
    val inventoryImport: Boolean = false,
    val inventoryExport: Boolean = false,
    val shipmentsView: Boolean = true,
)

enum class InventoryPermission { EDIT, PRICE, IMPORT, EXPORT }

// Compatibility names retained inside the feature so UI signatures do not change.
typealias InventoryItemView = InventoryItemViewData
typealias InventoryUnitView = InventoryUnitViewData
typealias CategoryItem = CategoryViewData
typealias InventoryMovementItem = InventoryMovementViewData
typealias LowStockSupplierItem = LowStockSupplierViewData
typealias PriceListDraftItemView = PriceListDraftItemViewData
typealias PriceListTemplateView = PriceListTemplateViewData
typealias MovementType = MovementTypeViewData
