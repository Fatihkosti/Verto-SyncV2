package com.verto.app.feature.inventory.domain.port

import com.verto.app.feature.inventory.domain.model.InventoryItem
import com.verto.app.feature.inventory.domain.model.InventoryPriceBatchChangeRecord
import com.verto.app.feature.inventory.domain.model.InventorySaleStockMutation
import com.verto.app.feature.inventory.domain.model.InventoryPostingIdentity
import com.verto.app.feature.inventory.domain.model.InventoryPurchaseReceiptCommand
import com.verto.app.feature.inventory.domain.model.InventoryCostRevaluationRecord
import com.verto.app.feature.inventory.domain.model.InventoryUnit

interface InventoryStorePort {
    suspend fun getItem(itemId: String): InventoryItem?
    suspend fun getAllItems(): List<InventoryItem>
    suspend fun saveItem(item: InventoryItem)
    suspend fun deleteItem(itemId: String)
    suspend fun replaceCategories(itemId: String, categories: List<String>)
    suspend fun getUnit(unitId: String): InventoryUnit?
    suspend fun saveUnit(unit: InventoryUnit)
}

interface InventoryStockPort {
    suspend fun getItem(itemId: String): InventoryItem?
    suspend fun getAllItems(): List<InventoryItem>
    suspend fun saveItem(item: InventoryItem)
    suspend fun deductStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        clientId: String,
        unitPrice: Double,
        note: String = "",
        allowNegativeStock: Boolean = false,
        sourceWriteId: String = "",
    ): Result<Unit>

    suspend fun addStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        supplierId: String,
        unitPrice: Double,
        note: String = "",
        sourceWriteId: String = "",
    ): Result<Unit>
    suspend fun receivePurchaseAtLatestPrice(command: InventoryPurchaseReceiptCommand): InventoryCostRevaluationRecord?
    suspend fun adjustStock(itemId: String, newQuantity: Int, note: String = "تعديل يدوي"): Result<Unit>
    suspend fun reverseInvoiceMovements(invoiceId: String, sourceWriteId: String = "")
    suspend fun deleteMovementsByInvoiceId(invoiceId: String)
}

interface InventoryLinePostingStockPort {
    suspend fun deductPostingLine(mutation: InventorySaleStockMutation, identity: InventoryPostingIdentity): Result<Unit>
}

interface InventoryDeletionQueuePort {
    suspend fun enqueueItemDeletion(itemId: String)
    suspend fun enqueueUnitDeletion(unitId: String)
    suspend fun enqueueCategoryDeletion(categoryId: String)
}

interface InventoryIdentityPort {
    fun newId(): String
}

interface InventoryPriceBatchEventPort {
    suspend fun recordBatch(
        batchId: String,
        changes: List<InventoryPriceBatchChangeRecord>,
    )
}
