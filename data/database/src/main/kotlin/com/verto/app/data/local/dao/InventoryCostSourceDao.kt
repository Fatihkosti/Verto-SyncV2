package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryCostSourceDao {
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertCostRevaluationEvent(event: InventoryCostRevaluationEventEntity): Long

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertLandedCostAdjustmentEvent(event: LandedCostAdjustmentEventEntity): Long

@Query("SELECT * FROM inventory_cost_revaluation_events WHERE source_type = :sourceType AND source_id = :sourceId ORDER BY occurred_at, id")
suspend fun getCostRevaluationEventsForSource(sourceType: String, sourceId: String): List<InventoryCostRevaluationEventEntity>

@Query("SELECT * FROM inventory_cost_revaluation_events WHERE item_id = :itemId ORDER BY occurred_at DESC, id DESC LIMIT 1")
suspend fun getLatestCostRevaluationEventForItem(itemId: String): InventoryCostRevaluationEventEntity?

@Query("SELECT * FROM inventory_cost_revaluation_events WHERE item_id = :itemId ORDER BY occurred_at DESC, id DESC")
suspend fun getCostRevaluationEventsForItemDescending(itemId: String): List<InventoryCostRevaluationEventEntity>

@Query(
    """
    SELECT CASE WHEN EXISTS (
        SELECT 1
        FROM invoices i
        WHERE i.id = :invoiceId
          AND i.category = 'PURCHASE'
          AND i.purchase_scope = 'LOCAL'
          AND i.lifecycle_status = 'POSTED'
          AND (
              SELECT COALESCE(SUM(ii.quantity), 0)
              FROM invoice_items ii
              WHERE ii.invoiceId = i.id AND ii.inventoryItemId = :itemId
          ) > (
              SELECT COALESCE(SUM(rl.quantity), 0)
              FROM invoice_return_lines rl
              INNER JOIN invoice_return_documents rd ON rd.id = rl.return_id
              WHERE rd.original_invoice_id = i.id AND rl.inventory_item_id = :itemId
          )
    ) THEN 1 ELSE 0 END
    """
)
suspend fun isPurchaseInvoiceCostSourceValid(invoiceId: String, itemId: String): Boolean

@Query(
    """
    SELECT CASE WHEN EXISTS (
        SELECT 1 FROM logistics_shipments
        WHERE id = :shipmentId
          AND cancelled_at IS NULL
          AND UPPER(state) NOT IN ('CANCELLED', 'CANCELED')
    ) THEN 1 ELSE 0 END
    """
)
suspend fun isShipmentCostSourceValid(shipmentId: String): Boolean

@Query(
    """
    SELECT CASE WHEN EXISTS (
        SELECT 1
        FROM logistics_shipment_lines line
        INNER JOIN logistics_shipments shipment
          ON shipment.organization_id = line.organization_id AND shipment.id = line.shipment_id
        WHERE line.source_invoice_id = :invoiceId
          AND line.source_invoice_item_id = :invoiceItemId
          AND line.inventory_item_id = :itemId
          AND line.shipment_id = :shipmentId
          AND shipment.cancelled_at IS NULL
          AND UPPER(shipment.state) NOT IN ('CANCELLED', 'CANCELED')
    ) THEN 1 ELSE 0 END
    """
)
suspend fun isShipmentSourceForInvoiceItem(
    shipmentId: String,
    invoiceId: String,
    invoiceItemId: String,
    itemId: String,
): Boolean

@Query(
    """
    SELECT COALESCE(SUM(posting.quantity), 0)
    FROM logistics_inventory_postings posting
    INNER JOIN logistics_receiving_lines receiving
      ON receiving.organization_id = posting.organization_id AND receiving.id = posting.receiving_line_id
    INNER JOIN logistics_shipment_lines line
      ON line.organization_id = receiving.organization_id AND line.id = receiving.shipment_line_id
    INNER JOIN logistics_shipments shipment
      ON shipment.organization_id = line.organization_id AND shipment.id = line.shipment_id
    WHERE line.source_invoice_id = :invoiceId
      AND line.source_invoice_item_id = :invoiceItemId
      AND line.inventory_item_id = :itemId
      AND posting.quantity > 0
      AND shipment.cancelled_at IS NULL
      AND UPPER(shipment.state) NOT IN ('CANCELLED', 'CANCELED')
    """
)
suspend fun getPostedShipmentQuantityForInvoiceItem(
    invoiceId: String,
    invoiceItemId: String,
    itemId: String,
): Int

@Query(
    "SELECT COALESCE(SUM(quantity), 0) FROM invoice_return_lines WHERE original_invoice_item_id = :invoiceItemId"
)
suspend fun getReturnedQuantityForInvoiceItem(invoiceItemId: String): Int


@Query("SELECT CASE WHEN purchase_order_id IS NOT NULL AND length(trim(purchase_order_id)) > 0 THEN 1 ELSE 0 END FROM invoices WHERE id = :invoiceId LIMIT 1")
suspend fun isPurchaseInvoiceLinkedToOrder(invoiceId: String): Boolean

@Query(
    """
    SELECT COALESCE(SUM(a.allocated_quantity),0)
    FROM purchase_invoice_receipt_allocations a
    INNER JOIN purchase_invoice_match_lines ml ON ml.id = a.match_line_id
    INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id
    INNER JOIN goods_receipt_lines grl ON grl.id = a.goods_receipt_line_id
    WHERE m.invoice_id = :invoiceId
      AND ml.invoice_item_id = :invoiceItemId
      AND grl.inventory_item_id = :itemId
    """
)
suspend fun getAllocatedGoodsReceiptQuantityForInvoiceItem(
    invoiceId: String,
    invoiceItemId: String,
    itemId: String,
): Int

@Query(
    """
    SELECT CASE WHEN EXISTS (
        SELECT 1
        FROM purchase_invoice_receipt_allocations a
        INNER JOIN purchase_invoice_match_lines ml ON ml.id = a.match_line_id
        INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id
        INNER JOIN goods_receipt_lines grl ON grl.id = a.goods_receipt_line_id
        INNER JOIN goods_receipts gr ON gr.id = grl.goods_receipt_id
        WHERE gr.id = :receiptId
          AND m.invoice_id = :invoiceId
          AND ml.invoice_item_id = :invoiceItemId
          AND grl.inventory_item_id = :itemId
          AND a.allocated_quantity > 0
    ) THEN 1 ELSE 0 END
    """
)
suspend fun isGoodsReceiptSourceForInvoiceItem(
    receiptId: String,
    invoiceId: String,
    invoiceItemId: String,
    itemId: String,
): Boolean

@Query(
    """
    SELECT CASE WHEN EXISTS (
        SELECT 1
        FROM goods_receipt_lines grl
        INNER JOIN goods_receipts gr ON gr.id = grl.goods_receipt_id
        WHERE gr.id = :receiptId
          AND grl.inventory_item_id = :itemId
          AND grl.accepted_quantity > 0
          AND (
              grl.accepted_quantity > COALESCE((
                  SELECT SUM(a.allocated_quantity)
                  FROM purchase_invoice_receipt_allocations a
                  WHERE a.goods_receipt_line_id = grl.id
              ),0)
              OR EXISTS (
                  SELECT 1
                  FROM purchase_invoice_receipt_allocations a
                  INNER JOIN purchase_invoice_match_lines ml ON ml.id = a.match_line_id
                  WHERE a.goods_receipt_line_id = grl.id
                    AND COALESCE((
                        SELECT SUM(rl.quantity)
                        FROM invoice_return_lines rl
                        WHERE rl.original_invoice_item_id = ml.invoice_item_id
                    ),0) < COALESCE((
                        SELECT SUM(a2.allocated_quantity)
                        FROM purchase_invoice_receipt_allocations a2
                        INNER JOIN purchase_invoice_match_lines ml2 ON ml2.id = a2.match_line_id
                        WHERE ml2.invoice_item_id = ml.invoice_item_id
                    ),0)
              )
          )
    ) THEN 1 ELSE 0 END
    """
)
suspend fun isGoodsReceiptCostSourceValid(receiptId: String, itemId: String): Boolean


@Query("SELECT * FROM landed_cost_adjustment_events WHERE shipment_id = :shipmentId ORDER BY occurred_at, id")
suspend fun getLandedCostAdjustmentEventsForShipment(shipmentId: String): List<LandedCostAdjustmentEventEntity>

@Query("UPDATE inventory_items SET buyPrice = :price, buy_price_minor = CAST(ROUND(:price * 100.0) AS INTEGER), updatedAt = :now, isDirty = 1 WHERE id = :id")
suspend fun updateBuyPrice(id: String, price: Double, now: Long = System.currentTimeMillis())
}
