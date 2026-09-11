package com.verto.app.data.sync

import android.database.sqlite.SQLiteConstraintException
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.AppDatabase.Companion.CASH_CLIENT_UUID
import com.verto.app.data.local.AppDatabase.Companion.CASH_SUPPLIER_UUID
import com.verto.app.data.local.entity.*
import java.util.UUID
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.remote.VertoSupabase
import com.verto.app.data.remote.dto.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import com.verto.app.utils.PreferencesManager
import com.verto.app.utils.SupabaseDateParser
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

    suspend fun SyncRuntime.pushInventoryItems(orgId: String, userId: String) {
        reconcileLegacyInventoryIfRequired(orgId)
        val blockedInvoices = financiallyBlockedAggregateIds(orgId)
        val blockedItemIds = if (blockedInvoices.isEmpty()) emptySet()
            else db.inventoryDao().getItemIdsTouchedByInvoices(blockedInvoices.toList()).toSet()
        val list = db.inventoryDao().getDirtyItemsSync().filter { it.id !in blockedItemIds }  // F249
        if (list.isEmpty()) return

        supabase.postgrest["inventory_items"].upsert(
            list.map { item ->
                InventoryItemMetadataDto(
                    id               = item.id,
                    organizationId   = orgId,
                    createdBy        = userId,
                    partNumber       = item.partNumber,
                    name             = item.name,
                    unitId           = item.unitId,
                    linkedUnitItemId = item.linkedUnitItemId,
                    isUnitItem       = item.isUnitItem,
                    quantityPerUnit  = item.quantityPerUnit,
                    isService        = item.isService,
                    buyPrice         = item.buyPrice.toRemoteDecimal(),
                    sellPrice        = item.sellPrice.toRemoteDecimal(),
                    minQuantity      = item.minQuantity,
                    location         = item.location,
                    note             = item.note,
                    createdAt        = SupabaseDateParser.format(item.createdAt),
                    updatedAt        = SupabaseDateParser.format(item.updatedAt)
                )
            }
        ) { onConflict = "id" }
        db.inventoryDao().markItemsClean(list.map { it.id })  // SYNC-012
    }

    suspend fun SyncRuntime.pushInventoryDeletions(orgId: String) {
        reconcileLegacyInventoryIfRequired(orgId)
        val pendingIds = userPrefs.getPendingInventoryDeletions() + db.inventoryDao().getDirtyArchivedItemIds()
        if (pendingIds.isEmpty()) return

        pendingIds.forEach { id ->
            val archived = runCatching {
                supabase.postgrest.rpc("archive_inventory_item_v2", ArchiveInventoryItemRequest(id))
            }.onFailure { e ->
                android.util.Log.e("SyncManager", "Remote deletion failed")
            }.isSuccess
            if (archived) {
                userPrefs.removePendingInventoryDeletion(id)
                db.inventoryDao().markItemsClean(listOf(id))
            }
        }
    }

    suspend fun SyncRuntime.pullInventoryItems(
        orgId: String,
        pendingDeletions: Set<String> = emptySet(),
        forceFull: Boolean = false,
        preserveQuantity: Boolean = false,
    ) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_INVENTORY)
        val pullStartedAt = System.currentTimeMillis()
        val inventoryDao = db.inventoryDao()
        val blockedInvoices = financiallyBlockedAggregateIds(orgId)
        val blockedItemIds = if (blockedInvoices.isEmpty()) emptySet()
            else inventoryDao.getItemIdsTouchedByInvoices(blockedInvoices.toList()).toSet()

        val remote = supabase.postgrest["inventory_items"].select {
            filter {
                eq("organization_id", orgId)
                if (!forceFull && lastPulledAt > 0L) gte("updated_at", SupabaseDateParser.format(lastPulledAt))
            }
        }.decodeList<InventoryItemDto>()
        if (remote.isEmpty()) {
            if (blockedInvoices.isEmpty()) {
                userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_INVENTORY, pullStartedAt)
            }
            return
        }

        val blockedReturned = remote.any { it.id in blockedItemIds }
        // FIX-5.5: جلب كل الأصناف المحلية مرة واحدة بدل N+1 query لكل صنف
        val localItemsMap = inventoryDao.getAllItemsIncludingArchivedSync().associateBy { it.id }

        remote.filter { it.isArchived }.forEach { dto ->
            val existing = localItemsMap[dto.id]
            if (existing == null) {
                inventoryDao.insertItem(
                    InventoryItemEntity(
                        id = dto.id, partNumber = dto.partNumber, name = dto.name,
                        quantity = dto.quantity, isDirty = false, isArchived = true,
                        archivedAt = dto.archivedAt?.let { SupabaseDateParser.parse(it) }, archivedBy = dto.archivedBy,
                    )
                )
            } else if (!existing.isArchived) {
                inventoryDao.archiveItem(
                    dto.id,
                    dto.archivedBy ?: "REMOTE",
                    dto.archivedAt?.let { SupabaseDateParser.parse(it) } ?: System.currentTimeMillis(),
                )
                inventoryDao.markItemsClean(listOf(dto.id))
            }
        }

        remote.filter { !it.isArchived && it.id !in pendingDeletions && it.id !in blockedItemIds }.forEach { dto ->
            val existing = localItemsMap[dto.id]
            if (existing == null) {
                // صنف جديد من جهاز آخر — أدرجه كاملاً (FIX-5.1: مع حقول نظام الوحدات)
                inventoryDao.insertItem(
                    InventoryItemEntity(
                        id               = dto.id,
                        partNumber       = dto.partNumber,
                        name             = dto.name,
                        unitId           = dto.unitId,
                        linkedUnitItemId = dto.linkedUnitItemId,
                        isUnitItem       = dto.isUnitItem,
                        quantityPerUnit  = dto.quantityPerUnit,
                        isService        = dto.isService,
                        buyPrice         = dto.buyPrice.toRemoteDouble(),
                        sellPrice        = dto.sellPrice.toRemoteDouble(),
                        quantity         = if (preserveQuantity) 0 else dto.quantity,
                        minQuantity      = dto.minQuantity,
                        location         = dto.location,
                        note             = dto.note,
                        createdAt        = SupabaseDateParser.parse(dto.createdAt),
                        updatedAt        = SupabaseDateParser.parse(dto.updatedAt),
                        isDirty          = false   // SYNC-012: مسحوب = نظيف
                    )
                )
            } else {
                val remoteUpdatedAt = SupabaseDateParser.parse(dto.updatedAt)
                val resolution = SyncConflictPolicy.resolve(
                    localDirty = existing.isDirty,
                    localUpdatedAt = existing.updatedAt,
                    remoteUpdatedAt = remoteUpdatedAt
                )
                if (resolution == SyncConflictResolution.APPLY_REMOTE) {
                    // السيرفر أحدث والمحلي نظيف — حدّث كل شيء بما فيه الكمية.
                    inventoryDao.updateItem(
                        existing.copy(
                            partNumber       = dto.partNumber,
                            name             = dto.name,
                            unitId           = dto.unitId,
                            linkedUnitItemId = dto.linkedUnitItemId,
                            isUnitItem       = dto.isUnitItem,
                            quantityPerUnit  = dto.quantityPerUnit,
                            isService        = dto.isService,
                            buyPrice         = dto.buyPrice.toRemoteDouble(),
                            sellPrice        = dto.sellPrice.toRemoteDouble(),
                            quantity         = if (preserveQuantity) existing.quantity else dto.quantity,
                            minQuantity      = dto.minQuantity,
                            location         = dto.location,
                            note             = dto.note,
                            updatedAt        = remoteUpdatedAt,
                            isDirty          = false   // SYNC-012: مسحوب = نظيف
                        )
                    )
                }
                // المحلي أحدث أو متساوي — لا تلمس الكمية المحلية
            }
        }

        if (blockedInvoices.isEmpty() && !blockedReturned) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_INVENTORY, pullStartedAt)
        }
    }

    suspend fun SyncRuntime.pushInventoryUnits(orgId: String) {
        val list = db.inventoryUnitDao().getAllUnitsSync()
        if (list.isEmpty()) return

        supabase.postgrest["inventory_units"].upsert(
            list.map { u ->
                InventoryUnitDto(
                    id              = u.id,
                    organizationId  = orgId,
                    name            = u.name,
                    quantityPerUnit = u.quantityPerUnit,
                    unitType        = u.unitType.name
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pushUnitDeletions(orgId: String) {
        val pendingIds = userPrefs.getPendingUnitDeletions()
        if (pendingIds.isEmpty()) return

        val failed = mutableListOf<String>()
        pendingIds.forEach { id ->
            runCatching {
                supabase.postgrest["inventory_units"].delete {
                    filter {
                        eq("id", id)
                        eq("organization_id", orgId)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("SyncManager", "Remote deletion failed")
                failed += "$id (${e.message?.take(80)})"
            }
            if (isGoneFromSupabase("inventory_units", id, orgId)) {
                userPrefs.removePendingUnitDeletion(id)
            }
        }
        if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
    }

    suspend fun SyncRuntime.pullInventoryUnits(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
        val remote = supabase.postgrest["inventory_units"].select {
            filter { eq("organization_id", orgId) }
        }.decodeList<InventoryUnitDto>()

        val pendingDeletions = userPrefs.getPendingUnitDeletions() + alreadyDeletedIds
        val valid = remote.filter { it.id !in pendingDeletions }

        if (valid.isNotEmpty()) {
            valid.forEach { dto ->
                db.inventoryUnitDao().insertUnit(
                    InventoryUnitEntity(
                        id              = dto.id,
                        name            = dto.name,
                        quantityPerUnit = dto.quantityPerUnit,
                        unitType        = runCatching { UnitType.valueOf(dto.unitType) }.getOrDefault(UnitType.COUNT)
                    )
                )
            }
            // v141: absence from this pull is UNKNOWN_NOT_RETURNED, never physical deletion.
            // Cross-device deletion waits for the authoritative server tombstone/deletion feed.
        }
        // السيرفر فارغ تماماً ⇒ لا نمسح محلياً (حماية من فشل push محتمل)
    }

    suspend fun SyncRuntime.pushItemCategories(orgId: String) {
        val localCats    = db.itemCategoryDao().getAllItemCategoriesSync()
        val localItemIds = db.inventoryDao().getAllItemsSync().mapTo(mutableSetOf()) { it.id }
        val localCatIds  = localCats.mapTo(mutableSetOf()) { it.id }

        // احذف من السيرفر تصنيفات أصنافنا التي لم تَعُد محلياً (نتيجة استبدال تصنيفات الصنف)
        runCatching {
            val remote = supabase.postgrest["item_categories"]
                .select { filter { eq("organization_id", orgId) } }
                .decodeList<ItemCategoryDto>()
            val stale = remote.filter { it.itemId in localItemIds && it.id !in localCatIds }.map { it.id }
            if (stale.isNotEmpty()) {
                supabase.postgrest["item_categories"].delete { filter { isIn("id", stale) } }
            }
        }.onFailure { e ->
            android.util.Log.e("SyncManager", "Orphan inventory category cleanup failed")
        }

        if (localCats.isEmpty()) return
        supabase.postgrest["item_categories"].upsert(
            localCats.map { c ->
                ItemCategoryDto(
                    id             = c.id,
                    organizationId = orgId,
                    itemId         = c.itemId,
                    category       = c.category
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pullItemCategories(orgId: String) {
        val remote = supabase.postgrest["item_categories"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeList<ItemCategoryDto>()

        // FK guard: احتفظ فقط بتصنيفات الأصناف الموجودة محلياً
        val existingItemIds = db.inventoryDao().getAllItemsSync().mapTo(mutableSetOf()) { it.id }
        val valid = remote.filter { it.itemId in existingItemIds }
        if (valid.isEmpty()) return

        db.itemCategoryDao().upsertCategoriesFromRemote(
            valid.map { dto ->
                ItemCategoryEntity(id = dto.id, itemId = dto.itemId, category = dto.category)
            }
        )

        // احذف اليتيمة محلياً للأصناف المسحوبة فقط (تصنيفات أُزيلت على جهاز آخر)
        val pulledItemIds = valid.mapTo(mutableSetOf()) { it.itemId }
        val remoteIds     = valid.mapTo(mutableSetOf()) { it.id }
        val staleLocal = db.itemCategoryDao().getAllItemCategoriesSync()
            .filter { it.itemId in pulledItemIds && it.id !in remoteIds }
            .map { it.id }
        if (staleLocal.isNotEmpty()) {
            db.itemCategoryDao().deleteCategoriesByIds(staleLocal)
        }
    }

    suspend fun SyncRuntime.pushCategories(orgId: String) {
        val list = db.categoryDao().getAllCategoriesSync()
        if (list.isEmpty()) return

        supabase.postgrest["categories"].upsert(
            list.map { c ->
                CategoryDto(
                    id             = c.id,
                    organizationId = orgId,
                    name           = c.name
                )
            }
        ) { onConflict = "id" }
    }

    suspend fun SyncRuntime.pushCategoryDeletions(orgId: String) {
        val pendingIds = userPrefs.getPendingCategoryDeletions()
        if (pendingIds.isEmpty()) return

        val failed = mutableListOf<String>()
        pendingIds.forEach { id ->
            runCatching {
                supabase.postgrest["categories"].delete {
                    filter {
                        eq("id", id)
                        eq("organization_id", orgId)
                    }
                }
            }.onFailure { e ->
                android.util.Log.e("SyncManager", "Remote deletion failed")
                failed += "$id (${e.message?.take(80)})"
            }
            if (isGoneFromSupabase("categories", id, orgId)) {
                userPrefs.removePendingCategoryDeletion(id)
            }
        }
        if (failed.isNotEmpty()) error("لم يُحذف من Supabase: ${failed.joinToString(", ")}")
    }

    suspend fun SyncRuntime.pullCategories(orgId: String, alreadyDeletedIds: Set<String> = emptySet()) {
        val lastPulledAt = userPrefs.getLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CATEGORIES)
        val pullStartedAt = System.currentTimeMillis()

        val remote = supabase.postgrest["categories"].select {
            filter { eq("organization_id", orgId) }
        }.decodeList<CategoryDto>()
        if (remote.isEmpty()) {
            userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CATEGORIES, pullStartedAt)
            return
        }

        // SYNC-008 guard: لا تُعِد إدراج تصنيف محذوف محلياً لم يُحذف من Supabase بعد (فشل push)
        val pendingDeletions = userPrefs.getPendingCategoryDeletions() + alreadyDeletedIds

        val existingIds = db.categoryDao().getAllCategoriesSync().map { it.id }.toSet()
        remote.filter { it.id !in pendingDeletions }.forEach { dto ->
            if (dto.id !in existingIds) {
                db.categoryDao().insertCategory(
                    com.verto.app.data.local.entity.CategoryEntity(
                        id   = dto.id,
                        name = dto.name
                    )
                )
            } else {
                db.categoryDao().updateCategory(
                    com.verto.app.data.local.entity.CategoryEntity(
                        id   = dto.id,
                        name = dto.name
                    )
                )
            }
        }

        userPrefs.setLastPulledAt(com.verto.app.utils.KEY_LAST_PULLED_CATEGORIES, pullStartedAt)
    }
