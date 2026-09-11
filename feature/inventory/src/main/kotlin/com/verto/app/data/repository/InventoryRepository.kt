package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.data.local.dao.CategoryDao
import com.verto.app.data.local.dao.InventoryDao
import com.verto.app.data.local.dao.InventoryUnitDao
import com.verto.app.data.local.dao.ItemCategoryDao
import com.verto.app.data.local.dao.LowStockSupplierRow
import com.verto.app.data.local.entity.CategoryEntity
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryUnitEntity
import com.verto.app.data.local.entity.MovementType
import com.verto.app.data.local.entity.ItemCategoryEntity
import com.verto.app.feature.inventory.application.DeleteInventoryCoordinator
import com.verto.app.feature.inventory.application.InventoryPriceCoordinator
import com.verto.app.feature.inventory.application.SaveInventoryItemCoordinator
import com.verto.app.feature.inventory.application.SaveInventoryUnitItemCoordinator
import com.verto.app.feature.inventory.data.toDomain
import com.verto.app.feature.inventory.data.toEntity
import com.verto.app.feature.inventory.domain.model.InventoryPriceChange
import com.verto.app.feature.inventory.domain.model.SaveInventoryItemCommand
import com.verto.app.feature.inventory.domain.model.SaveInventoryItemResult
import com.verto.app.feature.inventory.domain.model.SaveInventoryUnitItemCommand
import com.verto.app.feature.inventory.domain.port.InventoryDeletionQueuePort
import com.verto.app.feature.inventory.domain.port.InventoryStockPort
import com.verto.app.feature.inventory.domain.port.InventoryStorePort
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    private val dao: InventoryDao,
    private val unitDao: InventoryUnitDao,
    private val categoryDao: ItemCategoryDao,
    private val categoryMasterDao: CategoryDao,
    private val store: InventoryStorePort,
    private val stock: InventoryStockPort,
    private val deletionQueue: InventoryDeletionQueuePort,
    private val saveItemCoordinator: SaveInventoryItemCoordinator,
    private val saveUnitItemCoordinator: SaveInventoryUnitItemCoordinator,
    private val priceCoordinator: InventoryPriceCoordinator,
    private val deleteCoordinator: DeleteInventoryCoordinator,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {

    // ─── قراءة الأصناف ───────────────────────────────

    fun getAllItems(): Flow<List<InventoryItemEntity>> = dao.getAllItems()

    fun searchItems(query: String): Flow<List<InventoryItemEntity>> =
        if (query.isBlank()) dao.getAllItems() else dao.searchItems(query)

    fun getFilteredItemsPaged(query: String, stockFilter: String, category: String, sort: String) =
        dao.getFilteredItemsPaged(
            query = query,
            stockFilter = stockFilter,
            category = category,
            sort = sort,
            slowSince = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L,
        )

    fun getLowStockItems(): Flow<List<InventoryItemEntity>> = dao.getLowStockItems()

    fun getLowStockCount(): Flow<Int> = dao.getLowStockCount()

    fun getLowStockWithSuppliers(): Flow<List<LowStockSupplierRow>> = dao.getLowStockWithSuppliers()

    fun getMovementsForItem(itemId: String) = dao.getMovementsForItem(itemId)

    fun getAllMovements() = dao.getAllMovements()

    fun getMovementsByTypeInRange(type: MovementType, from: Long, to: Long) =
        dao.getMovementsByTypeInRange(type.name, from, to)

    suspend fun getItemByIdSync(id: String) = dao.getItemByIdSync(id)

    suspend fun getAllItemsSync(): List<InventoryItemEntity> = dao.getAllItemsSync()

    suspend fun getSlowMovingItemsSync(sinceTimestamp: Long): List<InventoryItemEntity> =
        dao.getSlowMovingItemsSync(sinceTimestamp)

    // ─── إضافة / تعديل / حذف صنف ────────────────────

    /**
     * يتحقق إذا كان اسم الصنف موجوداً مسبقاً (بتجاهل الحالة)
     * @param name اسم الصنف المراد فحصه
     * @param excludeId ID الصنف الحالي عند التعديل (لتجاهله في البحث)
     */
    suspend fun isNameTaken(name: String, excludeId: String? = null): Boolean =
        store.getAllItems().any { item ->
            item.name.trim().equals(name.trim(), ignoreCase = true) && item.id != excludeId
        }

    suspend fun saveItem(item: InventoryItemEntity) = store.saveItem(item.toDomain())

    suspend fun deleteItem(id: String) = deleteCoordinator.deleteItem(id)

    suspend fun updateBuyPrice(id: String, price: Double) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            val current = requireNotNull(dao.getItemByIdSync(id)) { "Inventory item not found" }
            dao.updateBuyPrice(id, price)
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "INVENTORY_ITEM",
                aggregateId = id,
                operationType = "UPSERT",
                payload = mapOf(
                    "buyPrice" to price,
                    "name" to current.name,
                    "partNumber" to current.partNumber,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
        }
    }

    // ─── حفظ صنف مع وحداته وتصنيفاته ────────────────

    /**
     * يحفظ الصنف الأساسي، ينشئ/يحدّث الصنف المرتبط بالوحدة تلقائياً،
     * ويحدّث التصنيفات في جدول الوصل.
     *
     * @return ID الصنف المحفوظ
     */
    suspend fun saveItemWithDetails(
        item: InventoryItemEntity,
        categories: List<String>,
        unit: InventoryUnitEntity?
    ): InventoryItemEntity {
        return when (val result = saveItemCoordinator.save(
            SaveInventoryItemCommand(
                item = item.toDomain(),
                categories = categories,
                unit = unit?.toDomain(),
                now = System.currentTimeMillis()
            )
        )) {
            is SaveInventoryItemResult.Success -> result.item.toEntity()
            is SaveInventoryItemResult.DuplicateName ->
                throw IllegalArgumentException("الصنف \"${result.name}\" موجود مسبقاً في المخزون")
        }
    }

    /**
     * عند تغيير سعر الصنف الأساسي، يُحدّث سعر الصنف المرتبط بالوحدة تلقائياً.
     */
    suspend fun syncUnitItemPrice(baseItem: InventoryItemEntity) {
        priceCoordinator.syncLinkedPrice(baseItem.toDomain(), System.currentTimeMillis())
    }

    /**
     * يحفظ صنف جديد كوحدة (كرتونة/دستة...) ويربطه بصنف القطعة المحددة.
     * - اسم الصنف يصبح: "${item.name} (${unitName})"
     * - يُنشئ InventoryUnitEntity إذا لم يكن موجوداً بنفس الاسم
     * - يُحدّث سعر الشراء لصنف القطعة تلقائياً
     */
    suspend fun saveAsUnitItem(
        item: InventoryItemEntity,
        unitName: String,
        unitQuantity: Double,
        linkedPieceItemId: String,
        categories: List<String>
    ): InventoryItemEntity = saveUnitItemCoordinator.save(
        SaveInventoryUnitItemCommand(
            item = item.toDomain(),
            unitName = unitName,
            unitQuantity = unitQuantity,
            linkedPieceItemId = linkedPieceItemId,
            categories = categories,
            now = System.currentTimeMillis()
        )
    ).toEntity()

    suspend fun updatePrices(
        item: InventoryItemEntity,
        buyPrice: Double,
        sellPrice: Double
    ): InventoryItemEntity = priceCoordinator.update(
        InventoryPriceChange(
            item = item.toDomain(),
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            now = System.currentTimeMillis()
        )
    ).toEntity()


    suspend fun updatePriceBatch(items: List<InventoryItemEntity>): List<InventoryItemEntity> {
        val now = System.currentTimeMillis()
        val changes = items.distinctBy(InventoryItemEntity::id).mapNotNull { proposed ->
            val current = dao.getItemByIdSync(proposed.id) ?: return@mapNotNull null
            InventoryPriceChange(
                item = current.toDomain(),
                buyPrice = proposed.buyPrice,
                sellPrice = proposed.sellPrice,
                now = now,
            )
        }
        return priceCoordinator.updateBatch(
            batchId = UUID.randomUUID().toString(),
            changes = changes,
        ).map { it.toEntity() }
    }

    // ─── الحركات — atomic بالكامل ────────────────────

    suspend fun deductStock(
        itemId: String, quantity: Int, invoiceId: String,
        clientId: String, unitPrice: Double, note: String = "",
        allowNegativeStock: Boolean = false
    ): Result<Unit> = stock.deductStock(
        itemId, quantity, invoiceId, clientId, unitPrice, note, allowNegativeStock
    )

    suspend fun addStock(
        itemId: String, quantity: Int, invoiceId: String,
        supplierId: String, unitPrice: Double, note: String = ""
    ): Result<Unit> = stock.addStock(
        itemId, quantity, invoiceId, supplierId, unitPrice, note
    )

    suspend fun adjustStock(
        itemId: String, newQuantity: Int, note: String = "تعديل يدوي"
    ): Result<Unit> = stock.adjustStock(itemId, newQuantity, note)

    suspend fun reverseInvoiceMovements(invoiceId: String, sourceWriteId: String = "") =
        stock.reverseInvoiceMovements(invoiceId, sourceWriteId)

    suspend fun deleteMovementsByInvoiceId(invoiceId: String) =
        stock.deleteMovementsByInvoiceId(invoiceId)

    // ─── الوحدات ─────────────────────────────────────

    fun getAllUnits(): Flow<List<InventoryUnitEntity>> = unitDao.getAllUnits()

    suspend fun getUnitById(id: String): InventoryUnitEntity? = unitDao.getUnitById(id)

    suspend fun saveUnit(unit: InventoryUnitEntity) = store.saveUnit(unit.toDomain())

    suspend fun deleteUnit(id: String) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            unitDao.deleteUnit(id)
            outbox.enqueue(organizationId, "INVENTORY_UNIT", id, "DELETE", mapOf("deleted" to true))
        }
        runCatching { deletionQueue.enqueueUnitDeletion(id) } // compatibility-only after durable commit
    }

    // ─── التصنيفات ───────────────────────────────────

    fun getAllItemCategories(): Flow<List<ItemCategoryEntity>> = categoryDao.getAllItemCategories()

    fun getAllDistinctCategories(): Flow<List<String>> = categoryDao.getAllDistinctCategories()

    fun getCategoriesForItem(itemId: String): Flow<List<ItemCategoryEntity>> =
        categoryDao.getCategoriesForItem(itemId)

    suspend fun setItemCategories(itemId: String, categories: List<String>) {
        val organizationId = trustedOrganizationId()
        val normalized = categories.map(String::trim).filter(String::isNotBlank).distinct().sorted()
        database.withTransaction {
            categoryDao.deleteCategoriesForItem(itemId)
            normalized.forEach { cat -> categoryDao.insertCategory(ItemCategoryEntity(itemId = itemId, category = cat)) }
            outbox.enqueue(
                organizationId, "ITEM_CATEGORY", itemId, "UPSERT",
                mapOf("categories" to normalized.joinToString("\u001F"), "itemId" to itemId),
            )
        }
    }

    // ─── التصنيفات المستقلة (قائمة رئيسية) ──────────────

    fun getAllMasterCategories(): Flow<List<CategoryEntity>> = categoryMasterDao.getAllCategories()

    suspend fun addMasterCategory(name: String) {
        val organizationId = trustedOrganizationId()
        val entity = CategoryEntity(name = name.trim())
        database.withTransaction {
            categoryMasterDao.insertCategory(entity)
            outbox.enqueue(organizationId, "CATEGORY", entity.id, "UPSERT", mapOf("name" to entity.name))
        }
    }

    suspend fun deleteMasterCategory(id: String) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            categoryMasterDao.deleteCategory(id)
            outbox.enqueue(organizationId, "CATEGORY", id, "DELETE", mapOf("deleted" to true))
        }
        runCatching { deletionQueue.enqueueCategoryDeletion(id) } // compatibility-only
    }

    suspend fun updateMasterCategory(id: String, newName: String) {
        val organizationId = trustedOrganizationId()
        val entity = CategoryEntity(id = id, name = newName.trim())
        database.withTransaction {
            categoryMasterDao.updateCategory(entity)
            outbox.enqueue(organizationId, "CATEGORY", id, "UPSERT", mapOf("name" to entity.name))
        }
    }

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }
}
