package com.verto.app.feature.inventory.data.sync

import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.pullCategories
import com.verto.app.data.sync.pullCostAllocations
import com.verto.app.data.sync.pullInventoryItems
import com.verto.app.data.sync.pullInventoryMovements
import com.verto.app.data.sync.pullInventoryCostRevisions
import com.verto.app.data.sync.pullInventoryUnits
import com.verto.app.data.sync.pullItemCategories
import com.verto.app.data.sync.pushCategories
import com.verto.app.data.sync.pushCategoryDeletions
import com.verto.app.data.sync.pushCostAllocations
import com.verto.app.data.sync.pushInventoryDeletions
import com.verto.app.data.sync.pushInventoryItems
import com.verto.app.data.sync.pushInventoryMovements
import com.verto.app.data.sync.pushInventoryCostRevisions
import com.verto.app.data.sync.pushInventoryUnits
import com.verto.app.data.sync.pushItemCategories
import com.verto.app.data.sync.pushUnitDeletions

class InventorySyncParticipant(
    private val runtime: SyncRuntime
) : SyncParticipant {
    override val key: String = "inventory"

    override fun operations(context: SyncRunContext): List<SyncOperation> = listOf(
        SyncOperation(SyncOperationSlot.PUSH_INVENTORY_UNITS, "push الوحدات", SyncFailureMode.COLLECT, execute = {
            runtime.pushUnitDeletions(context.organizationId)
            runtime.pushInventoryUnits(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_INVENTORY, "push المخزون", SyncFailureMode.COLLECT, execute = {
            runtime.pushInventoryDeletions(context.organizationId)
            runtime.pushInventoryItems(context.organizationId, context.userId)
            runtime.pushInventoryMovements(context.organizationId, context.userId)
            runtime.pushInventoryCostRevisions(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_ITEM_CATEGORIES, "push تصنيفات الأصناف", SyncFailureMode.COLLECT, execute = {
            runtime.pushItemCategories(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_COST_ALLOCATIONS, "push تخصيص التكلفة", SyncFailureMode.COLLECT, execute = {
            runtime.pushCostAllocations(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PUSH_INVENTORY_CATEGORIES, "push التصنيفات", SyncFailureMode.COLLECT, execute = {
            runtime.pushCategories(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.DELETE_INVENTORY_CATEGORIES, "حذف التصنيفات", SyncFailureMode.COLLECT, execute = {
            runtime.pushCategoryDeletions(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVENTORY_UNITS, "pull الوحدات", SyncFailureMode.COLLECT, execute = {
            runtime.pullInventoryUnits(context.organizationId, context.deletions.unitIds)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVENTORY, "pull المخزون", SyncFailureMode.COLLECT, execute = {
            runtime.pullInventoryItems(
                context.organizationId,
                runtime.pendingInventoryDeletions() + context.deletions.inventoryIds
            )
        }),
        SyncOperation(SyncOperationSlot.PULL_ITEM_CATEGORIES, "pull تصنيفات الأصناف", SyncFailureMode.COLLECT, execute = {
            runtime.pullItemCategories(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_COST_ALLOCATIONS, "pull تخصيص التكلفة", SyncFailureMode.COLLECT, execute = {
            runtime.pullCostAllocations(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVENTORY_MOVEMENTS, "pull حركات المخزون", SyncFailureMode.COLLECT, execute = {
            runtime.pullInventoryMovements(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVENTORY_COST_REVISIONS, "pull تكاليف المخزون", SyncFailureMode.COLLECT, execute = {
            runtime.pullInventoryCostRevisions(context.organizationId)
        }),
        SyncOperation(SyncOperationSlot.PULL_INVENTORY_CATEGORIES, "pull التصنيفات", SyncFailureMode.COLLECT, execute = {
            runtime.pullCategories(context.organizationId, context.deletions.categoryIds)
        })
    )
}
