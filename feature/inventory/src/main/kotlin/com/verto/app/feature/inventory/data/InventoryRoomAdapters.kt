package com.verto.app.feature.inventory.data

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.data.local.dao.InventoryUnitDao
import com.verto.app.data.local.dao.ItemCategoryDao
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.MovementType
import com.verto.app.data.local.entity.InventoryUnitEntity
import com.verto.app.data.local.entity.ItemCategoryEntity
import com.verto.app.data.local.entity.UnitType
import com.verto.app.feature.inventory.domain.model.InventoryItem
import com.verto.app.feature.inventory.domain.model.InventoryPriceBatchChangeRecord
import com.verto.app.feature.inventory.domain.model.InventoryCostRevaluationRecord
import com.verto.app.feature.inventory.domain.model.InventoryPurchaseReceiptCommand
import com.verto.app.feature.inventory.domain.model.InventoryPostingIdentity
import com.verto.app.feature.inventory.domain.model.InventorySaleStockMutation
import com.verto.app.feature.inventory.domain.model.InventoryUnit
import com.verto.app.feature.inventory.domain.model.InventoryUnitType
import com.verto.app.feature.inventory.domain.port.InventoryDeletionQueuePort
import com.verto.app.feature.inventory.domain.port.InventoryIdentityPort
import com.verto.app.feature.inventory.domain.port.InventoryPriceBatchEventPort
import com.verto.app.feature.inventory.domain.port.InventoryStockPort
import com.verto.app.feature.inventory.domain.port.InventoryLinePostingStockPort
import com.verto.app.feature.inventory.domain.port.InventoryStorePort
import com.verto.app.utils.PreferencesManager
import com.verto.app.data.sync.UnifiedOutboxWriter
import java.util.UUID
import javax.inject.Inject

class RoomInventoryStoreAdapter @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val unitDao: InventoryUnitDao,
    private val categoryDao: ItemCategoryDao,
    private val stockWriter: InventoryStockWriter,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : InventoryStorePort {
    override suspend fun getItem(itemId: String): InventoryItem? =
        inventoryDao.getItemByIdSync(itemId)?.toDomain()

    override suspend fun getAllItems(): List<InventoryItem> =
        inventoryDao.getAllItemsSync().map(InventoryItemEntity::toDomain)

    override suspend fun saveItem(item: InventoryItem) {
        require(!item.isUnitItem || item.quantity == 0) { "رصيد الوحدة يحفظ على الصنف الأساسي فقط" }
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            val entity = item.toEntity().copy(isDirty = true)
            val current = inventoryDao.getItemByIdSync(item.id)
            if (current == null) {
                inventoryDao.insertItem(entity.copy(quantity = 0))
                if (item.quantity > 0) stockWriter.open(item.id, item.quantity).getOrThrow()
            } else {
                inventoryDao.updateItem(entity.copy(quantity = current.quantity))
            }
            outbox.enqueue(
                organizationId, "INVENTORY_ITEM", item.id, "UPSERT", inventoryItemPayload(item),
            )
        }
    }

    override suspend fun deleteItem(itemId: String) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            check(inventoryDao.archiveItem(itemId) == 1) { "inventory item not found or already archived" }
            outbox.enqueue(
                organizationId, "INVENTORY_ITEM", itemId, "ARCHIVE",
                mapOf("archived" to true),
            )
        }
    }

    override suspend fun replaceCategories(itemId: String, categories: List<String>) {
        val organizationId = trustedOrganizationId()
        val normalized = categories.map(String::trim).filter(String::isNotBlank).distinct().sorted()
        database.withTransaction {
            categoryDao.deleteCategoriesForItem(itemId)
            normalized.forEach { category -> categoryDao.insertCategory(ItemCategoryEntity(itemId = itemId, category = category)) }
            outbox.enqueue(
                organizationId, "ITEM_CATEGORY", itemId, "UPSERT",
                mapOf("categories" to normalized.joinToString("\u001F"), "itemId" to itemId),
            )
        }
    }

    override suspend fun getUnit(unitId: String): InventoryUnit? =
        unitDao.getUnitById(unitId)?.toDomain()

    override suspend fun saveUnit(unit: InventoryUnit) {
        require(unit.quantityPerUnit.isFinite() && unit.quantityPerUnit > 0.0) { "عامل تحويل الوحدة يجب أن يكون موجباً" }
        require(unit.quantityPerUnit % 1.0 == 0.0) { "الأصناف الكسرية غير مدعومة؛ استخدم عاملاً صحيحاً" }
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            unitDao.insertUnit(unit.toEntity())
            outbox.enqueue(
                organizationId, "INVENTORY_UNIT", unit.id, "UPSERT",
                mapOf("name" to unit.name, "quantityPerUnit" to unit.quantityPerUnit, "unitType" to unit.unitType.name),
            )
        }
    }

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }

    private fun inventoryItemPayload(item: InventoryItem) = mapOf(
        "barcode" to item.barcode,
        "buyPrice" to item.buyPrice,
        "isService" to item.isService,
        "isUnitItem" to item.isUnitItem,
        "linkedUnitItemId" to item.linkedUnitItemId,
        "location" to item.location,
        "minQuantity" to item.minQuantity,
        "name" to item.name,
        "note" to item.note,
        "partNumber" to item.partNumber,
        "quantityPerUnit" to item.quantityPerUnit,
        "sellPrice" to item.sellPrice,
        "unitId" to item.unitId,
        "updatedAt" to item.updatedAt,
    )
}

class RoomInventoryStockAdapter @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val stockWriter: InventoryStockWriter,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : InventoryStockPort, InventoryLinePostingStockPort {
    override suspend fun getItem(itemId: String): InventoryItem? =
        inventoryDao.getItemByIdSync(itemId)?.toDomain()

    override suspend fun getAllItems(): List<InventoryItem> =
        inventoryDao.getAllItemsSync().map(InventoryItemEntity::toDomain)

    override suspend fun saveItem(item: InventoryItem) {
        require(!item.isUnitItem || item.quantity == 0) { "رصيد الوحدة يحفظ على الصنف الأساسي فقط" }
        val organizationId = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
        }
        database.withTransaction {
            val entity = item.toEntity().copy(isDirty = true)
            val current = inventoryDao.getItemByIdSync(item.id)
            if (current == null) {
                inventoryDao.insertItem(entity.copy(quantity = 0))
                if (item.quantity > 0) stockWriter.open(item.id, item.quantity).getOrThrow()
            } else {
                inventoryDao.updateItem(entity.copy(quantity = current.quantity))
            }
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "INVENTORY_ITEM",
                aggregateId = item.id,
                operationType = "UPSERT",
                payload = mapOf(
                    "barcode" to item.barcode,
                    "buyPrice" to item.buyPrice,
                    "isService" to item.isService,
                    "isUnitItem" to item.isUnitItem,
                    "linkedUnitItemId" to item.linkedUnitItemId,
                    "location" to item.location,
                    "minQuantity" to item.minQuantity,
                    "name" to item.name,
                    "note" to item.note,
                    "partNumber" to item.partNumber,
                    "quantityPerUnit" to item.quantityPerUnit,
                    "sellPrice" to item.sellPrice,
                    "unitId" to item.unitId,
                    "updatedAt" to item.updatedAt,
                ),
            )
        }
    }

    override suspend fun deductStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        clientId: String,
        unitPrice: Double,
        note: String,
        allowNegativeStock: Boolean,
        sourceWriteId: String,
    ): Result<Unit> = stockWriter.issue(
        request = InventoryIssueRequest(itemId, quantity, invoiceId, clientId, unitPrice, allowNegativeStock),
        meta = InventoryWriteMeta(sourceWriteId.ifBlank { UUID.randomUUID().toString() }, note),
    )

    override suspend fun deductPostingLine(
        mutation: InventorySaleStockMutation,
        identity: InventoryPostingIdentity,
    ): Result<Unit> = stockWriter.issue(
        request = InventoryIssueRequest(
            mutation.itemId,
            mutation.quantity,
            mutation.invoiceId,
            mutation.clientId,
            mutation.unitPrice,
            mutation.allowNegativeStock,
        ),
        meta = InventoryWriteMeta(
            commandId = identity.writeId.ifBlank { UUID.randomUUID().toString() },
            sourceLineId = identity.sourceLineId,
            postingGroupId = identity.postingGroupId,
        ),
    )

    override suspend fun addStock(
        itemId: String,
        quantity: Int,
        invoiceId: String,
        supplierId: String,
        unitPrice: Double,
        note: String,
        sourceWriteId: String,
    ): Result<Unit> = stockWriter.receive(
        request = InventoryReceiveRequest(itemId, quantity, invoiceId, supplierId, unitPrice),
        meta = InventoryWriteMeta(sourceWriteId.ifBlank { UUID.randomUUID().toString() }, note),
    )

    override suspend fun receivePurchaseAtLatestPrice(
        command: InventoryPurchaseReceiptCommand,
    ): InventoryCostRevaluationRecord? = stockWriter.receivePurchaseAtLatestPrice(command)

    override suspend fun adjustStock(itemId: String, newQuantity: Int, note: String): Result<Unit> =
        stockWriter.adjust(itemId, newQuantity, note)

    override suspend fun reverseInvoiceMovements(invoiceId: String, sourceWriteId: String) {
        stockWriter.reverseInvoice(invoiceId, sourceWriteId.ifBlank { "invoice-reverse:$invoiceId" }).getOrThrow()
    }

    override suspend fun deleteMovementsByInvoiceId(invoiceId: String) {
        stockWriter.reverseInvoice(invoiceId, "legacy-delete-replaced-by-reversal:$invoiceId").getOrThrow()
    }
}

class RoomInventoryPriceBatchEventAdapter @Inject constructor(
    private val inventoryDao: InventoryDao,
) : InventoryPriceBatchEventPort {
    override suspend fun recordBatch(
        batchId: String,
        changes: List<InventoryPriceBatchChangeRecord>,
    ) {
        if (changes.size < 2) return
        inventoryDao.insertMovements(
            changes.map { change ->
                InventoryMovementEntity(
                    itemId = change.itemId,
                    invoiceId = PRICE_BATCH_PREFIX + batchId,
                    movementType = MovementType.ADJUST,
                    quantity = 0,
                    quantityBefore = change.quantity,
                    quantityAfter = change.quantity,
                    unitPrice = change.newSellPrice,
                    note = buildString {
                        append(PRICE_BATCH_NOTE_PREFIX)
                        append("buy=")
                        append(change.oldBuyPrice)
                        append('>')
                        append(change.newBuyPrice)
                        append(";sell=")
                        append(change.oldSellPrice)
                        append('>')
                        append(change.newSellPrice)
                    },
                    createdAt = change.occurredAtEpochMillis,
                )
            },
        )
    }

    private companion object {
        const val PRICE_BATCH_PREFIX = "price-batch:"
        const val PRICE_BATCH_NOTE_PREFIX = "PRICE_BATCH:"
    }
}

class PreferencesInventoryDeletionQueueAdapter @Inject constructor(
    private val preferences: PreferencesManager
) : InventoryDeletionQueuePort {
    override suspend fun enqueueItemDeletion(itemId: String) = preferences.addPendingInventoryDeletion(itemId)
    override suspend fun enqueueUnitDeletion(unitId: String) = preferences.addPendingUnitDeletion(unitId)
    override suspend fun enqueueCategoryDeletion(categoryId: String) = preferences.addPendingCategoryDeletion(categoryId)
}

class UuidInventoryIdentityAdapter @Inject constructor() : InventoryIdentityPort {
    override fun newId(): String = UUID.randomUUID().toString()
}

fun InventoryItemEntity.toDomain() = InventoryItem(
    id = id,
    partNumber = partNumber,
    name = name,
    barcode = barcode,
    unitId = unitId,
    linkedUnitItemId = linkedUnitItemId,
    isUnitItem = isUnitItem,
    quantityPerUnit = quantityPerUnit,
    isService = isService,
    buyPrice = buyPrice,
    sellPrice = sellPrice,
    quantity = quantity,
    minQuantity = minQuantity,
    location = location,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDirty = isDirty
)

fun InventoryItem.toEntity() = InventoryItemEntity(
    id = id,
    partNumber = partNumber,
    name = name,
    barcode = barcode,
    unitId = unitId,
    linkedUnitItemId = linkedUnitItemId,
    isUnitItem = isUnitItem,
    quantityPerUnit = quantityPerUnit,
    isService = isService,
    buyPrice = buyPrice,
    sellPrice = sellPrice,
    quantity = quantity,
    minQuantity = minQuantity,
    location = location,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDirty = isDirty
)

fun InventoryUnitEntity.toDomain() = InventoryUnit(
    id = id,
    name = name,
    quantityPerUnit = quantityPerUnit,
    unitType = if (unitType == UnitType.LENGTH) InventoryUnitType.LENGTH else InventoryUnitType.COUNT
)

fun InventoryUnit.toEntity() = InventoryUnitEntity(
    id = id,
    name = name,
    quantityPerUnit = quantityPerUnit,
    unitType = if (unitType == InventoryUnitType.LENGTH) UnitType.LENGTH else UnitType.COUNT
)
