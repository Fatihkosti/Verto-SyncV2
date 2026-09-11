package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.verto.app.data.local.entity.GoodsReceiptEntity
import com.verto.app.data.local.entity.GoodsReceiptLineEntity
import com.verto.app.data.local.entity.PurchaseCycleAttachmentEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceReceiptAllocationEntity
import com.verto.app.data.local.entity.PurchaseOrderEntity
import com.verto.app.data.local.entity.PurchaseOrderLineEntity
import com.verto.app.data.local.entity.PurchasePaymentOverrideEntity
import com.verto.app.data.local.entity.PurchaseOrderShipmentSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PurchaseCycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOrderRaw(order: PurchaseOrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOrderLinesRaw(lines: List<PurchaseOrderLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertReceiptRaw(receipt: GoodsReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertReceiptLinesRaw(lines: List<GoodsReceiptLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOrderLineRaw(line: PurchaseOrderLineEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertReceiptLineRaw(line: GoodsReceiptLineEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttachmentRaw(row: PurchaseCycleAttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttachmentsRaw(rows: List<PurchaseCycleAttachmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInvoiceMatchRaw(match: PurchaseInvoiceMatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInvoiceMatchLinesRaw(lines: List<PurchaseInvoiceMatchLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertInvoiceMatchLineRaw(line: PurchaseInvoiceMatchLineEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertOrderShipmentSource(row: PurchaseOrderShipmentSourceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertReceiptAllocation(row: PurchaseInvoiceReceiptAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPaymentOverride(row: PurchasePaymentOverrideEntity): Long

    @Query("SELECT * FROM purchase_orders WHERE id = :orderId LIMIT 1")
    abstract suspend fun getOrder(orderId: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_orders WHERE organization_id = :organizationId ORDER BY created_at, id")
    abstract suspend fun getOrdersForOrganization(organizationId: String): List<PurchaseOrderEntity>

    /** Session 346 read-model inputs. These queries are read-only and invalidate with Room. */
    @Query("SELECT * FROM purchase_orders WHERE supplier_id = :supplierId ORDER BY created_at, id")
    abstract fun observeOrdersForSupplierIntelligence(supplierId: String): Flow<List<PurchaseOrderEntity>>

    @Query(
        """
        SELECT pol.* FROM purchase_order_lines pol
        INNER JOIN purchase_orders po ON po.id = pol.purchase_order_id
        WHERE po.supplier_id = :supplierId
        ORDER BY po.created_at, pol.purchase_order_id, pol.line_number
        """
    )
    abstract fun observeOrderLinesForSupplierIntelligence(supplierId: String): Flow<List<PurchaseOrderLineEntity>>

    @Query(
        """
        SELECT gr.* FROM goods_receipts gr
        INNER JOIN purchase_orders po ON po.id = gr.purchase_order_id
        WHERE po.supplier_id = :supplierId
        ORDER BY gr.received_at, gr.id
        """
    )
    abstract fun observeReceiptsForSupplierIntelligence(supplierId: String): Flow<List<GoodsReceiptEntity>>

    @Query(
        """
        SELECT grl.* FROM goods_receipt_lines grl
        INNER JOIN goods_receipts gr ON gr.id = grl.goods_receipt_id
        INNER JOIN purchase_orders po ON po.id = gr.purchase_order_id
        WHERE po.supplier_id = :supplierId
        ORDER BY gr.received_at, gr.id, grl.id
        """
    )
    abstract fun observeReceiptLinesForSupplierIntelligence(supplierId: String): Flow<List<GoodsReceiptLineEntity>>

    @Query(
        """
        SELECT ml.* FROM purchase_invoice_match_lines ml
        INNER JOIN purchase_order_lines pol ON pol.id = ml.purchase_order_line_id
        INNER JOIN purchase_orders po ON po.id = pol.purchase_order_id
        WHERE po.supplier_id = :supplierId
        ORDER BY po.created_at, ml.id
        """
    )
    abstract fun observeMatchLinesForSupplierIntelligence(supplierId: String): Flow<List<PurchaseInvoiceMatchLineEntity>>

    @Query(
        """
        SELECT DISTINCT po.* FROM purchase_orders po
        INNER JOIN purchase_order_lines pol ON pol.purchase_order_id = po.id
        WHERE pol.inventory_item_id = :inventoryItemId
        ORDER BY po.created_at, po.id
        """
    )
    abstract fun observeOrdersForSupplierItemIntelligence(inventoryItemId: String): Flow<List<PurchaseOrderEntity>>

    @Query(
        """
        SELECT pol.* FROM purchase_order_lines pol
        INNER JOIN purchase_orders po ON po.id = pol.purchase_order_id
        WHERE pol.inventory_item_id = :inventoryItemId
        ORDER BY po.created_at, pol.purchase_order_id, pol.line_number
        """
    )
    abstract fun observeOrderLinesForSupplierItemIntelligence(inventoryItemId: String): Flow<List<PurchaseOrderLineEntity>>

    @Query(
        """
        SELECT DISTINCT gr.* FROM goods_receipts gr
        INNER JOIN goods_receipt_lines grl ON grl.goods_receipt_id = gr.id
        INNER JOIN purchase_order_lines pol ON pol.id = grl.purchase_order_line_id
        WHERE pol.inventory_item_id = :inventoryItemId
        ORDER BY gr.received_at, gr.id
        """
    )
    abstract fun observeReceiptsForSupplierItemIntelligence(inventoryItemId: String): Flow<List<GoodsReceiptEntity>>

    @Query(
        """
        SELECT grl.* FROM goods_receipt_lines grl
        INNER JOIN goods_receipts gr ON gr.id = grl.goods_receipt_id
        INNER JOIN purchase_order_lines pol ON pol.id = grl.purchase_order_line_id
        WHERE pol.inventory_item_id = :inventoryItemId
        ORDER BY gr.received_at, gr.id, grl.id
        """
    )
    abstract fun observeReceiptLinesForSupplierItemIntelligence(inventoryItemId: String): Flow<List<GoodsReceiptLineEntity>>

    @Query(
        """
        SELECT ml.* FROM purchase_invoice_match_lines ml
        INNER JOIN purchase_order_lines pol ON pol.id = ml.purchase_order_line_id
        INNER JOIN purchase_orders po ON po.id = pol.purchase_order_id
        WHERE pol.inventory_item_id = :inventoryItemId
        ORDER BY po.created_at, ml.id
        """
    )
    abstract fun observeMatchLinesForSupplierItemIntelligence(inventoryItemId: String): Flow<List<PurchaseInvoiceMatchLineEntity>>

    @Query(
        """
        SELECT d.id AS return_id,
               d.client_id AS supplier_id,
               i.purchase_order_id AS purchase_order_id,
               l.inventory_item_id AS inventory_item_id,
               l.quantity AS quantity,
               d.occurred_at AS occurred_at
        FROM invoice_return_documents d
        INNER JOIN invoice_return_lines l ON l.return_id = d.id
        INNER JOIN invoices i ON i.id = d.original_invoice_id
        WHERE d.document_type = 'PURCHASE_RETURN_DEBIT_NOTE'
          AND d.client_id = :supplierId
          AND i.purchase_order_id IS NOT NULL
        ORDER BY d.occurred_at, d.id, l.id
        """
    )
    abstract fun observePurchaseReturnsForSupplierIntelligence(
        supplierId: String,
    ): Flow<List<SupplierPurchaseReturnRow>>

    @Query(
        """
        SELECT d.id AS return_id,
               d.client_id AS supplier_id,
               i.purchase_order_id AS purchase_order_id,
               l.inventory_item_id AS inventory_item_id,
               l.quantity AS quantity,
               d.occurred_at AS occurred_at
        FROM invoice_return_documents d
        INNER JOIN invoice_return_lines l ON l.return_id = d.id
        INNER JOIN invoices i ON i.id = d.original_invoice_id
        WHERE d.document_type = 'PURCHASE_RETURN_DEBIT_NOTE'
          AND l.inventory_item_id = :inventoryItemId
          AND i.purchase_order_id IS NOT NULL
        ORDER BY d.occurred_at, d.id, l.id
        """
    )
    abstract fun observePurchaseReturnsForSupplierItemIntelligence(
        inventoryItemId: String,
    ): Flow<List<SupplierPurchaseReturnRow>>

    @Query("SELECT * FROM purchase_orders WHERE organization_id = :organizationId AND write_id = :writeId LIMIT 1")
    abstract suspend fun getOrderByWriteId(organizationId: String, writeId: String): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_order_lines WHERE purchase_order_id = :orderId ORDER BY line_number ASC")
    abstract suspend fun getOrderLines(orderId: String): List<PurchaseOrderLineEntity>

    @Query("SELECT * FROM purchase_order_lines WHERE id = :lineId LIMIT 1")
    abstract suspend fun getOrderLine(lineId: String): PurchaseOrderLineEntity?

    @Query("SELECT pol.* FROM purchase_order_lines pol INNER JOIN purchase_orders po ON po.id = pol.purchase_order_id WHERE po.organization_id = :organizationId ORDER BY pol.purchase_order_id, pol.line_number")
    abstract suspend fun getOrderLinesForOrganization(organizationId: String): List<PurchaseOrderLineEntity>

    @Query("SELECT * FROM goods_receipts WHERE id = :receiptId LIMIT 1")
    abstract suspend fun getReceipt(receiptId: String): GoodsReceiptEntity?

    @Query("SELECT * FROM goods_receipts WHERE organization_id = :organizationId ORDER BY received_at, id")
    abstract suspend fun getReceiptsForOrganization(organizationId: String): List<GoodsReceiptEntity>

    @Query("SELECT * FROM goods_receipts WHERE organization_id = :organizationId AND write_id = :writeId LIMIT 1")
    abstract suspend fun getReceiptByWriteId(organizationId: String, writeId: String): GoodsReceiptEntity?

    @Query("SELECT * FROM goods_receipts WHERE purchase_order_id = :orderId ORDER BY received_at ASC, id ASC")
    abstract suspend fun getReceiptsForOrder(orderId: String): List<GoodsReceiptEntity>

    @Query("SELECT * FROM goods_receipt_lines WHERE goods_receipt_id = :receiptId ORDER BY id ASC")
    abstract suspend fun getReceiptLines(receiptId: String): List<GoodsReceiptLineEntity>

    @Query("SELECT * FROM goods_receipt_lines WHERE id = :lineId LIMIT 1")
    abstract suspend fun getReceiptLine(lineId: String): GoodsReceiptLineEntity?

    @Query("SELECT grl.* FROM goods_receipt_lines grl INNER JOIN goods_receipts gr ON gr.id = grl.goods_receipt_id WHERE gr.organization_id = :organizationId ORDER BY gr.received_at, gr.id, grl.id")
    abstract suspend fun getReceiptLinesForOrganization(organizationId: String): List<GoodsReceiptLineEntity>

    @Query("SELECT * FROM purchase_cycle_attachments WHERE id = :attachmentId LIMIT 1")
    abstract suspend fun getAttachment(attachmentId: String): PurchaseCycleAttachmentEntity?

    @Query("SELECT * FROM purchase_cycle_attachments WHERE organization_id = :organizationId ORDER BY created_at, id")
    abstract suspend fun getAttachmentsForOrganization(organizationId: String): List<PurchaseCycleAttachmentEntity>

    @Query("SELECT COALESCE(SUM(accepted_quantity), 0) FROM goods_receipt_lines WHERE purchase_order_line_id = :orderLineId")
    abstract suspend fun getAcceptedQuantity(orderLineId: String): Int

    @Query("SELECT COALESCE(SUM(received_quantity), 0) FROM goods_receipt_lines WHERE purchase_order_line_id = :orderLineId")
    abstract suspend fun getReceivedQuantity(orderLineId: String): Int

    @Query("SELECT inventory_item_id FROM goods_receipt_lines WHERE purchase_order_line_id = :orderLineId AND accepted_quantity > 0 AND inventory_item_id IS NOT NULL ORDER BY rowid ASC LIMIT 1")
    abstract suspend fun getResolvedInventoryItemId(orderLineId: String): String?

    @Query("SELECT * FROM purchase_invoice_matches WHERE invoice_id = :invoiceId LIMIT 1")
    abstract suspend fun getInvoiceMatch(invoiceId: String): PurchaseInvoiceMatchEntity?

    @Query("SELECT * FROM purchase_invoice_matches WHERE id = :matchId LIMIT 1")
    abstract suspend fun getInvoiceMatchById(matchId: String): PurchaseInvoiceMatchEntity?

    @Query("SELECT * FROM purchase_invoice_matches WHERE organization_id = :organizationId ORDER BY matched_at, id")
    abstract suspend fun getInvoiceMatchesForOrganization(organizationId: String): List<PurchaseInvoiceMatchEntity>

    @Query("SELECT * FROM purchase_invoice_match_lines WHERE match_id = :matchId ORDER BY id ASC")
    abstract suspend fun getInvoiceMatchLines(matchId: String): List<PurchaseInvoiceMatchLineEntity>

    @Query("SELECT * FROM purchase_invoice_match_lines WHERE id = :lineId LIMIT 1")
    abstract suspend fun getInvoiceMatchLine(lineId: String): PurchaseInvoiceMatchLineEntity?

    @Query("SELECT ml.* FROM purchase_invoice_match_lines ml INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id WHERE m.organization_id = :organizationId ORDER BY m.matched_at, m.id, ml.id")
    abstract suspend fun getInvoiceMatchLinesForOrganization(organizationId: String): List<PurchaseInvoiceMatchLineEntity>

    @Query("SELECT * FROM purchase_invoice_receipt_allocations WHERE id = :allocationId LIMIT 1")
    abstract suspend fun getReceiptAllocationById(allocationId: String): PurchaseInvoiceReceiptAllocationEntity?

    @Query("SELECT * FROM purchase_invoice_receipt_allocations WHERE organization_id = :organizationId ORDER BY created_at, id")
    abstract suspend fun getReceiptAllocationsForOrganization(organizationId: String): List<PurchaseInvoiceReceiptAllocationEntity>

    @Query("SELECT * FROM purchase_payment_overrides WHERE id = :overrideId LIMIT 1")
    abstract suspend fun getPaymentOverrideById(overrideId: String): PurchasePaymentOverrideEntity?

    @Query("SELECT * FROM purchase_payment_overrides WHERE organization_id = :organizationId ORDER BY created_at, id")
    abstract suspend fun getPaymentOverridesForOrganization(organizationId: String): List<PurchasePaymentOverrideEntity>

    @Query("SELECT COALESCE(SUM(p.supplier_amount_minor), 0) FROM payments p WHERE p.invoiceId = :invoiceId AND p.reversedPaymentId IS NULL AND NOT EXISTS (SELECT 1 FROM payments r WHERE r.reversedPaymentId = p.id)")
    abstract suspend fun getPaidMinor(invoiceId: String): Long

    @Query("SELECT EXISTS(SELECT 1 FROM logistics_shipments WHERE organization_id = :organizationId AND id = :shipmentId)")
    abstract suspend fun shipmentExists(organizationId: String, shipmentId: String): Boolean

    @Query("SELECT * FROM purchase_order_shipment_sources WHERE organization_id = :organizationId AND purchase_order_id = :orderId ORDER BY added_at ASC")
    abstract suspend fun getOrderShipmentSources(organizationId: String, orderId: String): List<PurchaseOrderShipmentSourceEntity>

    @Query("SELECT * FROM purchase_order_shipment_sources WHERE organization_id = :organizationId AND write_id = :writeId LIMIT 1")
    abstract suspend fun getOrderShipmentSourceByWriteId(organizationId: String, writeId: String): PurchaseOrderShipmentSourceEntity?

    @Query("SELECT * FROM purchase_payment_overrides WHERE organization_id = :organizationId AND payment_request_id = :requestId LIMIT 1")
    abstract suspend fun getPaymentOverride(organizationId: String, requestId: String): PurchasePaymentOverrideEntity?

    @Query("SELECT purchase_order_id FROM invoices WHERE id = :invoiceId LIMIT 1")
    abstract suspend fun getInvoicePurchaseOrderId(invoiceId: String): String?

    @Query("UPDATE purchase_orders SET status = :status, closed_at = :closedAt, close_reason = :closeReason WHERE id = :orderId")
    abstract suspend fun updateOrderStatus(orderId: String, status: String, closedAt: Long?, closeReason: String?): Int

    @Query("SELECT COALESCE(SUM(l.invoiced_quantity), 0) FROM purchase_invoice_match_lines l WHERE l.purchase_order_line_id = :orderLineId")
    abstract suspend fun getMatchedInvoicedQuantity(orderLineId: String): Int

    @Query(
        """
        SELECT COALESCE(SUM(l.invoiced_quantity), 0)
        FROM purchase_invoice_match_lines l
        INNER JOIN purchase_invoice_matches m ON m.id = l.match_id
        WHERE l.purchase_order_line_id = :orderLineId
          AND (m.matched_at < :matchedAt OR (m.matched_at = :matchedAt AND m.id < :matchId))
        """
    )
    abstract suspend fun getMatchedInvoicedQuantityBefore(orderLineId: String, matchedAt: Long, matchId: String): Int

    @Query(
        """
        SELECT grl.*
        FROM goods_receipt_lines grl
        INNER JOIN goods_receipts gr ON gr.id = grl.goods_receipt_id
        WHERE grl.purchase_order_line_id = :orderLineId
          AND grl.accepted_quantity > COALESCE((
              SELECT SUM(a.allocated_quantity)
              FROM purchase_invoice_receipt_allocations a
              WHERE a.goods_receipt_line_id = grl.id
          ), 0)
        ORDER BY gr.received_at ASC, gr.id ASC, grl.id ASC
        """
    )
    abstract suspend fun getReceiptLinesWithAvailableQuantity(orderLineId: String): List<GoodsReceiptLineEntity>

    @Query(
        """
        SELECT ml.*
        FROM purchase_invoice_match_lines ml
        INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id
        WHERE ml.purchase_order_line_id = :orderLineId
          AND ml.invoiced_quantity > COALESCE((
              SELECT SUM(a.allocated_quantity)
              FROM purchase_invoice_receipt_allocations a
              WHERE a.match_line_id = ml.id
          ), 0)
        ORDER BY m.matched_at ASC, m.id ASC, ml.id ASC
        """
    )
    abstract suspend fun getMatchLinesAwaitingReceipt(orderLineId: String): List<PurchaseInvoiceMatchLineEntity>

    @Query("SELECT COALESCE(SUM(allocated_quantity),0) FROM purchase_invoice_receipt_allocations WHERE match_line_id = :matchLineId")
    abstract suspend fun getAllocatedQuantityForMatchLine(matchLineId: String): Int

    @Query("SELECT COALESCE(SUM(allocated_quantity),0) FROM purchase_invoice_receipt_allocations WHERE goods_receipt_line_id = :receiptLineId")
    abstract suspend fun getAllocatedQuantityForReceiptLine(receiptLineId: String): Int

    @Query("SELECT * FROM purchase_invoice_receipt_allocations WHERE organization_id = :organizationId AND match_line_id = :matchLineId AND goods_receipt_line_id = :receiptLineId LIMIT 1")
    abstract suspend fun getReceiptAllocation(organizationId: String, matchLineId: String, receiptLineId: String): PurchaseInvoiceReceiptAllocationEntity?

    @Query(
        """
        SELECT COALESCE(SUM(a.allocated_quantity * ml.invoice_unit_price_minor),0)
        FROM purchase_invoice_receipt_allocations a
        INNER JOIN purchase_invoice_match_lines ml ON ml.id = a.match_line_id
        INNER JOIN purchase_invoice_matches m ON m.id = ml.match_id
        WHERE m.invoice_id = :invoiceId
        """
    )
    abstract suspend fun getCurrentReceivedPayableMinor(invoiceId: String): Long

    @Transaction
    open suspend fun applyRemotePreCycle(
        organizationId: String,
        orders: List<PurchaseOrderEntity>,
        orderLines: List<PurchaseOrderLineEntity>,
        receipts: List<GoodsReceiptEntity>,
        receiptLines: List<GoodsReceiptLineEntity>,
        attachments: List<PurchaseCycleAttachmentEntity>,
    ) {
        orders.forEach { remote ->
            require(remote.organizationId == organizationId) { "remote PO tenant mismatch" }
            val local = getOrder(remote.id)
            if (local == null) {
                insertOrderRaw(remote)
            } else {
                require(
                    local.copy(status = remote.status, closedAt = remote.closedAt, closeReason = remote.closeReason) == remote
                ) { "immutable PO facts conflict: ${remote.id}" }
                if (local.status != remote.status || local.closedAt != remote.closedAt || local.closeReason != remote.closeReason) {
                    check(updateOrderStatus(remote.id, remote.status, remote.closedAt, remote.closeReason) == 1)
                }
            }
        }
        orderLines.forEach { remote ->
            val parent = requireNotNull(getOrder(remote.purchaseOrderId)) { "remote PO line missing parent" }
            require(parent.organizationId == organizationId) { "remote PO line tenant mismatch" }
            val local = getOrderLine(remote.id)
            if (local == null) insertOrderLineRaw(remote) else require(local == remote) { "immutable PO line conflict: ${remote.id}" }
        }
        receipts.forEach { remote ->
            require(remote.organizationId == organizationId) { "remote GRN tenant mismatch" }
            val local = getReceipt(remote.id)
            if (local == null) insertReceiptRaw(remote) else require(local == remote) { "immutable GRN conflict: ${remote.id}" }
        }
        receiptLines.forEach { remote ->
            val parent = requireNotNull(getReceipt(remote.goodsReceiptId)) { "remote GRN line missing parent" }
            require(parent.organizationId == organizationId) { "remote GRN line tenant mismatch" }
            val local = getReceiptLine(remote.id)
            if (local == null) insertReceiptLineRaw(remote) else require(local == remote) { "immutable GRN line conflict: ${remote.id}" }
        }
        attachments.forEach { remote ->
            require(remote.organizationId == organizationId) { "remote purchase attachment tenant mismatch" }
            val local = getAttachment(remote.id)
            if (local == null) insertAttachmentRaw(remote) else require(local == remote) { "immutable purchase attachment conflict: ${remote.id}" }
        }
    }

    @Transaction
    open suspend fun applyRemotePostCycle(
        organizationId: String,
        matches: List<PurchaseInvoiceMatchEntity>,
        matchLines: List<PurchaseInvoiceMatchLineEntity>,
        allocations: List<PurchaseInvoiceReceiptAllocationEntity>,
        overrides: List<PurchasePaymentOverrideEntity>,
    ) {
        matches.forEach { remote ->
            require(remote.organizationId == organizationId) { "remote purchase match tenant mismatch" }
            val local = getInvoiceMatchById(remote.id)
            if (local == null) insertInvoiceMatchRaw(remote) else require(local == remote) { "immutable purchase match conflict: ${remote.id}" }
        }
        matchLines.forEach { remote ->
            val parent = requireNotNull(getInvoiceMatchById(remote.matchId)) { "remote match line missing parent" }
            require(parent.organizationId == organizationId) { "remote match line tenant mismatch" }
            val local = getInvoiceMatchLine(remote.id)
            if (local == null) insertInvoiceMatchLineRaw(remote) else require(local == remote) { "immutable match line conflict: ${remote.id}" }
        }
        allocations.forEach { remote ->
            require(remote.organizationId == organizationId) { "remote receipt allocation tenant mismatch" }
            val local = getReceiptAllocationById(remote.id)
            if (local == null) {
                check(insertReceiptAllocation(remote) != -1L) { "duplicate receipt allocation identity" }
            } else require(local == remote) { "immutable receipt allocation conflict: ${remote.id}" }
        }
        overrides.forEach { remote ->
            require(remote.organizationId == organizationId) { "remote payment override tenant mismatch" }
            val local = getPaymentOverrideById(remote.id)
            if (local == null) {
                check(insertPaymentOverride(remote) != -1L) { "duplicate payment override identity" }
            } else require(local == remote) { "immutable payment override conflict: ${remote.id}" }
        }
    }

    @Transaction
    open suspend fun insertOrder(order: PurchaseOrderEntity, lines: List<PurchaseOrderLineEntity>, attachments: List<PurchaseCycleAttachmentEntity>) {
        insertOrderRaw(order)
        insertOrderLinesRaw(lines)
        if (attachments.isNotEmpty()) insertAttachmentsRaw(attachments)
    }

    @Transaction
    open suspend fun insertReceipt(receipt: GoodsReceiptEntity, lines: List<GoodsReceiptLineEntity>, attachments: List<PurchaseCycleAttachmentEntity>) {
        insertReceiptRaw(receipt)
        insertReceiptLinesRaw(lines)
        if (attachments.isNotEmpty()) insertAttachmentsRaw(attachments)
    }

    @Transaction
    open suspend fun insertInvoiceMatch(match: PurchaseInvoiceMatchEntity, lines: List<PurchaseInvoiceMatchLineEntity>) {
        insertInvoiceMatchRaw(match)
        insertInvoiceMatchLinesRaw(lines)
    }
}
