package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryCatalogReadDao {
// ─── القطع ───────────────────────────────────────

@Query("SELECT * FROM inventory_items WHERE is_archived = 0 ORDER BY name ASC")
fun getAllItems(): Flow<List<InventoryItemEntity>>

@Query("SELECT * FROM inventory_items WHERE is_archived = 0 ORDER BY name ASC")
suspend fun getAllItemsSync(): List<InventoryItemEntity>

@Query("SELECT * FROM inventory_items ORDER BY name ASC")
suspend fun getAllItemsIncludingArchivedSync(): List<InventoryItemEntity>

@Query("SELECT * FROM inventory_items WHERE id = :id AND is_archived = 0")
fun getItemById(id: String): Flow<InventoryItemEntity?>

@Query("SELECT * FROM inventory_items WHERE id = :id AND is_archived = 0")
suspend fun getItemByIdSync(id: String): InventoryItemEntity?

@Query("""
    SELECT * FROM inventory_items
    WHERE TRIM(name) = TRIM(:name) COLLATE NOCASE
    ORDER BY updatedAt DESC
    LIMIT 1
""")
suspend fun getItemByExactNameSync(name: String): InventoryItemEntity?

@Query("""
    SELECT * FROM inventory_items
    WHERE is_archived = 0 AND (name LIKE '%' || :query || '%'
       OR partNumber LIKE '%' || :query || '%')
    ORDER BY name ASC
""")
fun searchItems(query: String): Flow<List<InventoryItemEntity>>

@Query("""
    SELECT i.* FROM inventory_items i
    WHERE i.is_archived = 0
      AND (
          EXISTS (
              SELECT 1 FROM inventory_movements movement
              WHERE movement.itemId = i.id AND movement.organization_id = :organizationId
          )
          OR EXISTS (
              SELECT 1 FROM inventory_cost_revisions cost
              WHERE cost.item_id = i.id AND cost.organization_id = :organizationId
          )
          OR EXISTS (
              SELECT 1 FROM invoice_items line
              INNER JOIN invoices inv ON inv.id = line.invoiceId
              WHERE line.inventoryItemId = i.id AND inv.organization_id = :organizationId
          )
          OR EXISTS (
              SELECT 1 FROM logistics_shipment_lines shipment_line
              WHERE shipment_line.inventory_item_id = i.id
                AND shipment_line.organization_id = :organizationId
          )
      )
      AND (
          (length(:textQuery) >= 2 AND i.nameSearch >= :textQuery AND i.nameSearch < (:textQuery || char(1114111)))
          OR (length(:identifierQuery) >= 2 AND i.partNumberSearch >= :identifierQuery AND i.partNumberSearch < (:identifierQuery || char(1114111)))
          OR (length(:identifierQuery) >= 2 AND i.barcodeSearch >= :identifierQuery AND i.barcodeSearch < (:identifierQuery || char(1114111)))
      )
    ORDER BY
        CASE
            WHEN i.nameSearch = :textQuery
              OR i.partNumberSearch = :identifierQuery
              OR i.barcodeSearch = :identifierQuery THEN 0
            ELSE 1
        END,
        i.nameSearch ASC,
        i.updatedAt DESC,
        i.id ASC
    LIMIT :limit
""")
suspend fun searchItemsByPrefix(
    organizationId: String,
    textQuery: String,
    identifierQuery: String,
    limit: Int
): List<InventoryItemEntity>

@Query("SELECT * FROM inventory_items WHERE is_archived = 0 AND quantity <= minQuantity ORDER BY quantity ASC")
fun getLowStockItems(): Flow<List<InventoryItemEntity>>

@Query("SELECT COUNT(*) FROM inventory_items WHERE is_archived = 0 AND quantity <= minQuantity")
fun getLowStockCount(): Flow<Int>


@Query("""
    SELECT i.id AS itemId,
           i.name AS itemName,
           i.quantity AS quantity,
           i.minQuantity AS minQuantity,
           i.isService AS isService,
           i.createdAt AS createdAt,
           i.updatedAt AS updatedAt,
           MAX(
               CASE
                   WHEN m.movement_kind = 'SALE'
                    AND (m.organization_id = :organizationId OR m.organization_id IS NULL)
                    AND inv.organization_id = :organizationId
                    AND inv.category = 'SALE'
                    AND inv.voided = 0
                   THEN COALESCE(m.occurred_at, m.createdAt)
                   ELSE NULL
               END
           ) AS lastSaleAt
    FROM inventory_items i
    LEFT JOIN inventory_movements m
           ON m.itemId = i.id
          AND (m.organization_id = :organizationId OR m.organization_id IS NULL)
    LEFT JOIN invoices inv
           ON inv.id = m.invoiceId
          AND inv.organization_id = :organizationId
    WHERE i.isService = 0
      AND i.is_archived = 0
      AND (
          EXISTS (
              SELECT 1 FROM inventory_movements own
              WHERE own.itemId = i.id
                AND own.organization_id = :organizationId
          )
          OR NOT EXISTS (
              SELECT 1 FROM inventory_movements scoped
              WHERE scoped.itemId = i.id
                AND scoped.organization_id IS NOT NULL
          )
      )
    GROUP BY i.id
    HAVING i.quantity <= i.minQuantity
        OR (
            i.quantity > 0
            AND COALESCE(
                MAX(
                    CASE
                        WHEN m.movement_kind = 'SALE'
                         AND (m.organization_id = :organizationId OR m.organization_id IS NULL)
                         AND inv.organization_id = :organizationId
                         AND inv.category = 'SALE'
                         AND inv.voided = 0
                        THEN COALESCE(m.occurred_at, m.createdAt)
                        ELSE NULL
                    END
                ),
                i.createdAt
            ) <= :staleCutoffEpochMillis
        )
    ORDER BY i.updatedAt DESC, i.name ASC
""")
fun observePendingActionItems(
    organizationId: String,
    staleCutoffEpochMillis: Long,
): Flow<List<InventoryPendingItemRow>>

@Query("""
    SELECT m.invoiceId AS batchId,
           COUNT(DISTINCT m.itemId) AS itemCount,
           MAX(m.createdAt) AS occurredAt
    FROM inventory_movements m
    WHERE m.movementType = 'ADJUST'
      AND m.organization_id = :organizationId
      AND m.invoiceId LIKE 'price-batch:%'
    GROUP BY m.invoiceId
    HAVING COUNT(DISTINCT m.itemId) > 1
    ORDER BY occurredAt ASC, m.invoiceId ASC
""")
fun observePriceBatches(organizationId: String): Flow<List<InventoryPriceBatchRow>>

@Query("""
    SELECT DISTINCT i.id AS itemId, i.name AS itemName, i.quantity,
           COALESCE(c.name, '') AS supplierName
    FROM inventory_items i
    LEFT JOIN inventory_movements m ON m.itemId = i.id AND m.movementType = 'IN' AND m.clientId != ''
    LEFT JOIN clients c ON c.id = m.clientId
                       AND EXISTS (SELECT 1 FROM party_roles pr WHERE pr.party_id=c.id AND pr.role='SUPPLIER' AND pr.status='ACTIVE')
                       AND c.id NOT IN ('00000000-0000-0000-0000-000000000002','cash_supplier_main')
    WHERE i.is_archived = 0 AND i.quantity <= i.minQuantity
    ORDER BY supplierName ASC, i.name ASC
""")
fun getLowStockWithSuppliers(): Flow<List<LowStockSupplierRow>>

// ── Pagination ───────────────────────────────────────────────────────────

@Query("""
    SELECT DISTINCT i.* FROM inventory_items i
    LEFT JOIN item_categories ic ON ic.itemId = i.id
    WHERE i.is_archived = 0 AND (:query = '' OR i.name LIKE '%' || :query || '%' OR i.partNumber LIKE '%' || :query || '%')
      AND (
          :stockFilter = 'ALL'
          OR (:stockFilter = 'LOW' AND i.quantity > 0 AND i.quantity <= i.minQuantity)
          OR (:stockFilter = 'OUT' AND i.quantity <= 0)
          OR (
              :stockFilter = 'SLOW'
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_movements slow_m
                  WHERE slow_m.itemId = i.id AND slow_m.movement_kind = 'SALE'
                    AND COALESCE(slow_m.occurred_at, slow_m.createdAt) >= :slowSince
              )
          )
      )
      AND (:category = '' OR ic.category = :category)
    ORDER BY
        CASE WHEN :sort = 'NAME'       THEN i.name                     END ASC,
        CASE WHEN :sort = 'QUANTITY'   THEN i.quantity                 END ASC,
        CASE WHEN :sort = 'BUY_PRICE'  THEN (0 - i.buyPrice)          END ASC,
        CASE WHEN :sort = 'SELL_PRICE' THEN (0 - i.sellPrice)         END ASC,
        CASE WHEN :sort = 'CATEGORY'   THEN
            (SELECT MIN(ic2.category) FROM item_categories ic2 WHERE ic2.itemId = i.id)
        END ASC,
        i.name ASC
""")
fun getFilteredItemsPaged(
    query: String,
    stockFilter: String,
    category: String,
    sort: String,
    slowSince: Long,
): PagingSource<Int, InventoryItemEntity>

}
