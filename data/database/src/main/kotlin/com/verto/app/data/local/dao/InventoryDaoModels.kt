package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

data class LowStockSupplierRow(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val supplierName: String,
)

data class InventoryPendingItemRow(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val minQuantity: Int,
    val isService: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSaleAt: Long?,
)

data class InventoryPriceBatchRow(
    val batchId: String,
    val itemCount: Int,
    val occurredAt: Long,
)

data class InventoryActivityMovementRow(
    @Embedded val movement: InventoryMovementEntity,
    val itemName: String,
)

data class InventoryCanonicalReadRow(
    val itemId: String,
    val itemName: String,
    val baseQuantity: Long,
    val approvedUnitCostMinor: Long,
    val inventoryValueMinor: Long,
)

data class InventoryDriftRow(
    val itemId: String,
    val snapshotQuantity: Long,
    val ledgerQuantity: Long,
    val driftQuantity: Long,
)

data class InventoryOperationsMetrics(
    val pendingOutbox: Long,
    val oldestOutboxAt: Long?,
    val openConflicts: Long,
    val openQuarantine: Long,
    val driftedItems: Long,
)

data class InventoryFastMoverRow(val itemId: String, val soldBaseQuantity: Long)

data class InventoryBaseStockTarget(
    val item: InventoryItemEntity,
    val baseQuantity: Int,
    val factor: Long,
    val baseUnitPrice: Money,
)

data class InventoryStockTarget(
    val itemId: String,
    val quantity: Int,
)

data class InventoryStockParty(
    val documentId: String,
    val counterpartyId: String,
)

data class InventoryStockWriteMetadata(
    val note: String,
    val allowNegativeStock: Boolean = false,
    val sourceWriteId: String,
    val sourceLineId: String?,
    val postingGroupId: String?,
)

data class InventoryStockDeductionCommand(
    val target: InventoryStockTarget,
    val party: InventoryStockParty,
    val unitPrice: Double,
    val metadata: InventoryStockWriteMetadata,
)

data class InventoryStockAdditionCommand(
    val target: InventoryStockTarget,
    val party: InventoryStockParty,
    val unitPrice: Double,
    val metadata: InventoryStockWriteMetadata,
)

data class InventoryPurchaseSourceIdentity(
    val invoiceId: String,
    val supplierId: String,
    val sourceType: String,
    val sourceId: String,
    val sourceLineId: String?,
    val postingGroupId: String?,
)

data class InventoryPurchaseSourceWrite(
    val writeId: String,
    val eventId: String,
)

data class InventoryPurchaseSource(
    val identity: InventoryPurchaseSourceIdentity,
    val write: InventoryPurchaseSourceWrite,
)

data class InventoryPurchasePricing(
    val buyPriceMinor: Long,
    val sellPriceMinor: Long?,
)

data class InventoryWriteAudit(
    val actorId: String,
    val actorName: String,
    val occurredAt: Long,
    val writeId: String,
)

data class InventoryPurchasePostingCommand(
    val organizationId: String,
    val itemId: String,
    val quantity: Int,
    val source: InventoryPurchaseSource,
    val pricing: InventoryPurchasePricing,
    val audit: InventoryWriteAudit,
)

data class InventorySalesReturnIdentity(
    val itemId: String,
    val quantity: Int,
    val returnId: String,
    val returnLineId: String,
    val clientId: String,
)

data class InventorySalesReturnAudit(
    val historicalUnitCostMinor: Long,
    val occurredAt: Long,
    val writeId: String,
)

data class InventorySalesReturnCommand(
    val identity: InventorySalesReturnIdentity,
    val audit: InventorySalesReturnAudit,
)

data class InventoryPurchaseReturnTarget(
    val itemId: String,
    val quantity: Int,
    val supplierId: String,
    val originalUnitCostMinor: Long,
)

data class InventoryPurchaseReturnSource(
    val returnId: String,
    val returnLineId: String,
    val originalInvoiceId: String,
    val originalInvoiceItemId: String,
)

data class InventoryPurchaseReturnPolicy(
    val internationalPurchase: Boolean,
    val sourceStillValidForItem: Boolean,
)

data class InventoryPurchaseReturnCommand(
    val target: InventoryPurchaseReturnTarget,
    val source: InventoryPurchaseReturnSource,
    val policy: InventoryPurchaseReturnPolicy,
    val audit: InventoryWriteAudit,
)

data class InventoryShipmentReceiptIdentity(
    val organizationId: String,
    val actorId: String,
    val postingId: String,
    val shipmentId: String,
    val receivingBatchId: String,
    val receivingLineId: String,
)

data class InventoryShipmentReceiptDetails(
    val itemId: String,
    val quantity: Int,
    val supplierId: String,
    val unitPrice: Double,
    val note: String,
)

data class InventoryShipmentReceiptCommand(
    val identity: InventoryShipmentReceiptIdentity,
    val details: InventoryShipmentReceiptDetails,
)
