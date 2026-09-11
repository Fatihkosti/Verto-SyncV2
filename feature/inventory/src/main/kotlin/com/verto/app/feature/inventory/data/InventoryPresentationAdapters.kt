package com.verto.app.feature.inventory.data

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.UnifiedOutboxWriter
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import com.verto.app.data.local.dao.PriceListDao
import com.verto.app.data.local.entity.CategoryEntity
import com.verto.app.data.local.entity.InventoryItemEntity
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryUnitEntity
import com.verto.app.data.local.entity.ItemCategoryEntity
import com.verto.app.data.local.entity.MovementType as RoomMovementType
import com.verto.app.data.local.entity.PriceListTemplateEntity
import com.verto.app.data.local.entity.PriceListTemplateItemEntity
import com.verto.app.data.local.entity.UnitType
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.repository.InventoryRepository
import com.verto.app.feature.inventory.application.model.*
import com.verto.app.feature.inventory.application.port.*
import com.verto.app.utils.PreferencesManager
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class InventoryPresentationAdapter @Inject constructor(
    private val repository: InventoryRepository,
) : InventoryPresentationQuery, InventoryPresentationCommand {

    override fun getAllItems() = repository.getAllItems().map { rows -> rows.map(InventoryItemEntity::toViewData) }

    override fun searchItems(query: String) = repository.searchItems(query).map { rows -> rows.map(InventoryItemEntity::toViewData) }

    override fun getFilteredItemsPaged(query: String, stockFilter: String, category: String, sort: String) =
        Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
            repository.getFilteredItemsPaged(query, stockFilter, category, sort)
        }.flow.map { pagingData -> pagingData.map(InventoryItemEntity::toViewData) }

    override fun getLowStockItems() = repository.getLowStockItems().map { rows -> rows.map(InventoryItemEntity::toViewData) }
    override fun getLowStockCount() = repository.getLowStockCount()
    override fun getLowStockWithSuppliers() = repository.getLowStockWithSuppliers().map { rows ->
        rows.map { row -> LowStockSupplierViewData(row.itemId, row.itemName, row.quantity, row.supplierName) }
    }
    override fun getMovementsForItem(itemId: String) = repository.getMovementsForItem(itemId).map { rows -> rows.map(InventoryMovementEntity::toViewData) }
    override fun getAllMovements() = repository.getAllMovements().map { rows -> rows.map(InventoryMovementEntity::toViewData) }
    override suspend fun getItemByIdSync(id: String) = repository.getItemByIdSync(id)?.toViewData()
    override suspend fun getAllItemsSync() = repository.getAllItemsSync().map(InventoryItemEntity::toViewData)
    override suspend fun getSlowMovingItemsSync(sinceTimestamp: Long) = repository.getSlowMovingItemsSync(sinceTimestamp).map(InventoryItemEntity::toViewData)
    override fun getAllUnits() = repository.getAllUnits().map { rows -> rows.map(InventoryUnitEntity::toViewData) }
    override fun getAllItemCategories() = repository.getAllItemCategories().map { rows -> rows.map(ItemCategoryEntity::toViewData) }
    override fun getAllDistinctCategories() = repository.getAllDistinctCategories()
    override fun getAllMasterCategories() = repository.getAllMasterCategories().map { rows -> rows.map(CategoryEntity::toViewData) }
    override suspend fun isNameTaken(name: String, excludeId: String?) = repository.isNameTaken(name, excludeId)

    override suspend fun saveItem(item: InventoryItemViewData) = repository.saveItem(item.toEntity())
    override suspend fun saveItemWithDetails(item: InventoryItemViewData, categories: List<String>, unit: InventoryUnitViewData?) =
        repository.saveItemWithDetails(item.toEntity(), categories, unit?.toEntity()).toViewData()
    override suspend fun syncUnitItemPrice(baseItem: InventoryItemViewData) = repository.syncUnitItemPrice(baseItem.toEntity())
    override suspend fun saveAsUnitItem(item: InventoryItemViewData, unitName: String, unitQuantity: Double, linkedPieceItemId: String, categories: List<String>) =
        repository.saveAsUnitItem(item.toEntity(), unitName, unitQuantity, linkedPieceItemId, categories).toViewData()
    override suspend fun updatePrices(item: InventoryItemViewData, buyPrice: Double, sellPrice: Double) =
        repository.updatePrices(item.toEntity(), buyPrice, sellPrice).toViewData()
    override suspend fun updatePriceBatch(items: List<InventoryItemViewData>) =
        repository.updatePriceBatch(items.map(InventoryItemViewData::toEntity)).map(InventoryItemEntity::toViewData)
    override suspend fun deleteItem(id: String) = repository.deleteItem(id)
    override suspend fun adjustStock(itemId: String, newQuantity: Int, note: String) = repository.adjustStock(itemId, newQuantity, note)
    override suspend fun saveUnit(unit: InventoryUnitViewData) = repository.saveUnit(unit.toEntity())
    override suspend fun deleteUnit(id: String) = repository.deleteUnit(id)
    override suspend fun addMasterCategory(name: String) = repository.addMasterCategory(name)
    override suspend fun deleteMasterCategory(id: String) = repository.deleteMasterCategory(id)
    override suspend fun updateMasterCategory(id: String, newName: String) = repository.updateMasterCategory(id, newName)
}

class PriceListPresentationAdapter @Inject constructor(
    private val dao: PriceListDao,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : PriceListPresentationQuery, PriceListPresentationCommand {

    override fun observeTemplates(organizationId: String) = combine(
        dao.observeTemplates(organizationId),
        dao.observeTemplateItems(organizationId),
    ) { templates, links ->
        val byTemplate = links.groupBy { it.templateId }
        templates.map { template ->
            template.toViewData(byTemplate[template.id].orEmpty())
        }
    }

    override suspend fun saveTemplate(template: PriceListTemplateViewData): PriceListTemplateViewData {
        val organizationId = trustedOrganizationId()
        val now = System.currentTimeMillis()
        val normalized = template.copy(
            name = template.name.trim(),
            inventoryItemIds = template.inventoryItemIds.distinct(),
            updatedAt = now,
        )
        require(normalized.name.isNotBlank()) { "EMPTY_TEMPLATE_NAME" }
        require(normalized.inventoryItemIds.isNotEmpty()) { "EMPTY_TEMPLATE_ITEMS" }

        val entity = PriceListTemplateEntity(
            id = normalized.id,
            organizationId = organizationId,
            name = normalized.name,
            isFavorite = normalized.isFavorite,
            createdAt = normalized.createdAt,
            updatedAt = normalized.updatedAt,
        )
        val links = normalized.inventoryItemIds.mapIndexed { index, itemId ->
            PriceListTemplateItemEntity(entity.id, itemId, index)
        }

        database.withTransaction {
            require(dao.findConflictingTemplateId(organizationId, normalized.name, normalized.id) == null) {
                "DUPLICATE_TEMPLATE_NAME"
            }
            dao.upsertTemplate(entity)
            dao.deleteTemplateItems(entity.id)
            dao.upsertTemplateItems(links)
            val baseVersion = predictedPriceListBaseVersion(organizationId, entity.id)
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "PRICE_LIST",
                aggregateId = entity.id,
                operationType = "UPSERT",
                baseVersion = baseVersion,
                payload = mapOf(
                    "kind" to "TEMPLATE",
                    "id" to entity.id,
                    "organizationId" to organizationId,
                    "name" to entity.name,
                    "isFavorite" to entity.isFavorite,
                    "itemIds" to normalized.inventoryItemIds.joinToString(","),
                    "createdAt" to entity.createdAt,
                    "updatedAt" to entity.updatedAt,
                ),
            )
        }
        return normalized
    }

    override suspend fun deleteTemplate(templateId: String) {
        val organizationId = trustedOrganizationId()
        database.withTransaction {
            dao.deleteTemplate(templateId, organizationId)
            val baseVersion = predictedPriceListBaseVersion(organizationId, templateId)
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "PRICE_LIST",
                aggregateId = templateId,
                operationType = "DELETE",
                baseVersion = baseVersion,
                payload = mapOf(
                    "kind" to "TEMPLATE",
                    "id" to templateId,
                    "organizationId" to organizationId,
                    "deleted" to true,
                ),
            )
        }
    }

    private suspend fun predictedPriceListBaseVersion(organizationId: String, aggregateId: String): Long? {
        val syncDao = database.unifiedSyncDao()
        val known = syncDao.latestKnownServerVersion(organizationId, "PRICE_LIST", aggregateId)
        val queuedBeforeThisWrite = syncDao.countActiveOrderedMutations(organizationId, "PRICE_LIST", aggregateId)
        return when {
            queuedBeforeThisWrite == 0 -> known
            known == null -> queuedBeforeThisWrite.toLong()
            else -> known + queuedBeforeThisWrite
        }
    }

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }
}

class InventoryPreferencesAdapter @Inject constructor(
    private val preferences: PreferencesManager,
) : InventoryPreferencesPort {
    override val inventoryTemplate = preferences.inventoryTemplate
    override val inventoryFont = preferences.inventoryFont
    override val inventoryFontSize = preferences.inventoryFontSize
    override val priceListFont = preferences.priceListFont
    override val priceListFontSize = preferences.priceListFontSize
}

class InventoryPermissionAdapter @Inject constructor(
    private val provider: PermissionProvider,
) : InventoryPermissionPort {
    override val permissions = provider.permissions.map { permissions ->
        permissions?.let {
            InventoryPermissionsViewData(
                inventoryEdit = it.inventoryEdit,
                inventoryPrice = it.inventoryPrice,
                inventoryImport = it.inventoryImport,
                inventoryExport = it.inventoryExport,
                shipmentsView = it.shipmentsView,
            )
        }
    }

    override fun can(permission: InventoryPermission): Boolean = when (permission) {
        InventoryPermission.EDIT -> provider.can { it.inventoryEdit }
        InventoryPermission.PRICE -> provider.can { it.inventoryPrice }
        InventoryPermission.IMPORT -> provider.can { it.inventoryImport }
        InventoryPermission.EXPORT -> provider.can { it.inventoryExport }
    }

    override suspend fun canNow(permission: InventoryPermission): Boolean = when (permission) {
        InventoryPermission.EDIT -> provider.canNow { it.inventoryEdit }
        InventoryPermission.PRICE -> provider.canNow { it.inventoryPrice }
        InventoryPermission.IMPORT -> provider.canNow { it.inventoryImport }
        InventoryPermission.EXPORT -> provider.canNow { it.inventoryExport }
    }
}

private fun InventoryItemEntity.toViewData() = InventoryItemViewData(
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
    isDirty = isDirty,
)

private fun InventoryItemViewData.toEntity() = InventoryItemEntity(
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
    isDirty = isDirty,
)

private fun InventoryUnitEntity.toViewData() = InventoryUnitViewData(
    id = id,
    name = name,
    quantityPerUnit = quantityPerUnit,
    unitType = if (unitType == UnitType.LENGTH) InventoryUnitTypeViewData.LENGTH else InventoryUnitTypeViewData.COUNT,
)

private fun InventoryUnitViewData.toEntity() = InventoryUnitEntity(
    id = id,
    name = name,
    quantityPerUnit = quantityPerUnit,
    unitType = if (unitType == InventoryUnitTypeViewData.LENGTH) UnitType.LENGTH else UnitType.COUNT,
)

private fun CategoryEntity.toViewData() = CategoryViewData(id = id, name = name)
private fun ItemCategoryEntity.toViewData() = InventoryCategoryLinkViewData(itemId = itemId, category = category)

private fun InventoryMovementEntity.toViewData() = InventoryMovementViewData(
    id = id,
    itemId = itemId,
    invoiceId = invoiceId,
    clientId = clientId,
    movementType = when (movementType) {
        RoomMovementType.IN -> MovementTypeViewData.IN
        RoomMovementType.OUT -> MovementTypeViewData.OUT
        RoomMovementType.ADJUST -> MovementTypeViewData.ADJUST
        RoomMovementType.RETURN -> MovementTypeViewData.RETURN
    },
    quantity = quantity,
    quantityBefore = quantityBefore,
    quantityAfter = quantityAfter,
    unitPrice = unitPrice,
    note = note,
    shipmentId = shipmentId,
    createdAt = createdAt,
)

private fun PriceListTemplateEntity.toViewData(items: List<PriceListTemplateItemEntity>) = PriceListTemplateViewData(
    id = id,
    name = name,
    inventoryItemIds = items.sortedBy { it.sortOrder }.map { it.inventoryItemId },
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
