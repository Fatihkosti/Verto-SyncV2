package com.verto.app.data.local.dao

import androidx.room.ColumnInfo

data class SupplierPurchaseReturnRow(
    @ColumnInfo(name = "return_id") val returnId: String,
    @ColumnInfo(name = "supplier_id") val supplierId: String,
    @ColumnInfo(name = "purchase_order_id") val purchaseOrderId: String,
    @ColumnInfo(name = "inventory_item_id") val inventoryItemId: String,
    val quantity: Int,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)
