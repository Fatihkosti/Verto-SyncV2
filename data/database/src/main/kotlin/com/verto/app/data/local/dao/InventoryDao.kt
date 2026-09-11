package com.verto.app.data.local.dao

import androidx.room.Dao

/**
 * Stable Room entry point for inventory persistence.
 *
 * The implementation is split by cohesion and transaction ownership in sibling
 * DAO contracts. Room still materializes one DAO instance, so every @Transaction
 * boundary remains a single database transaction.
 */
@Dao
interface InventoryDao :
    InventoryCatalogReadDao,
    InventoryItemLifecycleDao,
    InventoryCostSourceDao,
    InventorySyncDao,
    InventoryStockTargetDao,
    InventoryCostDao,
    InventoryMovementDao,
    InventoryStockCoreOperationsDao,
    InventoryPurchasePostingOperationsDao,
    InventoryPurchaseReturnOperationsDao,
    InventorySalesReturnOperationsDao,
    InventoryShipmentReceiptOperationsDao,
    InventoryShipmentOperationsDao,
    InventoryInvoiceOperationsDao,
    InventoryMaintenanceDao
