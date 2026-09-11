package com.verto.app.feature.invoice.data

import com.verto.app.data.local.dao.PurchaseCycleDao
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
import com.verto.app.feature.invoice.domain.model.GoodsReceiptLineRecord
import com.verto.app.feature.invoice.domain.model.GoodsReceiptRecord
import com.verto.app.feature.invoice.domain.model.PurchaseAttachmentOwnerType
import com.verto.app.feature.invoice.domain.model.PurchaseCycleAttachmentDraft
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchAssessment
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchRecord
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchStatus
import com.verto.app.feature.invoice.domain.model.PurchaseOrderLineRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderStatus
import com.verto.app.feature.invoice.domain.model.PurchasePaymentOverrideRecord
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.invoice.domain.port.PurchaseCycleStorePort
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

class RoomPurchaseCycleStore @Inject constructor(
    private val dao: PurchaseCycleDao,
) : PurchaseCycleStorePort {
    override suspend fun getOrder(orderId: String): PurchaseOrderRecord? = dao.getOrder(orderId)?.toDomain()

    override suspend fun getOrderByWriteId(organizationId: String, writeId: String): PurchaseOrderRecord? =
        dao.getOrderByWriteId(organizationId, writeId)?.toDomain()

    override suspend fun getOrderLines(orderId: String): List<PurchaseOrderLineRecord> =
        dao.getOrderLines(orderId).map { it.toDomain() }

    override suspend fun insertOrder(
        order: PurchaseOrderRecord,
        lines: List<PurchaseOrderLineRecord>,
        attachments: List<PurchaseCycleAttachmentDraft>,
    ) = dao.insertOrder(
        order = order.toEntity(),
        lines = lines.map { it.toEntity() },
        attachments = attachments.toEntities(
            organizationId = order.organizationId,
            ownerType = PurchaseAttachmentOwnerType.PURCHASE_ORDER,
            ownerId = order.id,
            writeId = order.writeId,
            occurredAt = order.createdAt,
        ),
    )

    override suspend fun getReceiptByWriteId(organizationId: String, writeId: String): GoodsReceiptRecord? =
        dao.getReceiptByWriteId(organizationId, writeId)?.toDomain()

    override suspend fun getReceiptLines(receiptId: String): List<GoodsReceiptLineRecord> =
        dao.getReceiptLines(receiptId).map { it.toDomain() }

    override suspend fun getAcceptedQuantity(orderLineId: String): Int = dao.getAcceptedQuantity(orderLineId)

    override suspend fun getMatchedInvoicedQuantity(orderLineId: String): Int = dao.getMatchedInvoicedQuantity(orderLineId)

    override suspend fun getResolvedInventoryItemId(orderLineId: String): String? =
        dao.getResolvedInventoryItemId(orderLineId)?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun insertReceipt(
        receipt: GoodsReceiptRecord,
        lines: List<GoodsReceiptLineRecord>,
        attachments: List<PurchaseCycleAttachmentDraft>,
    ) {
        dao.insertReceipt(
            receipt = receipt.toEntity(),
            lines = lines.map { it.toEntity() },
            attachments = attachments.toEntities(
                organizationId = receipt.organizationId,
                ownerType = PurchaseAttachmentOwnerType.GOODS_RECEIPT,
                ownerId = receipt.id,
                writeId = receipt.writeId,
                occurredAt = receipt.receivedAt,
            ),
        )
        lines.asSequence().map { it.purchaseOrderLineId }.distinct().forEach { orderLineId ->
            allocateAcceptedReceipts(
                organizationId = receipt.organizationId,
                orderLineId = orderLineId,
                occurredAt = receipt.receivedAt,
            )
        }
    }

    override suspend fun updateOrderStatus(
        orderId: String,
        status: PurchaseOrderStatus,
        closedAt: Long?,
        closeReason: String?,
    ) {
        check(dao.updateOrderStatus(orderId, status.name, closedAt, closeReason) == 1) { "purchase order status update failed" }
    }

    override suspend fun insertInvoiceMatch(assessment: PurchaseInvoiceMatchAssessment) {
        dao.insertInvoiceMatch(
            assessment.match.toEntity(),
            assessment.lines.map { line ->
                PurchaseInvoiceMatchLineEntity(
                    id = line.id,
                    matchId = line.matchId,
                    invoiceItemId = line.invoiceItemId,
                    purchaseOrderLineId = line.purchaseOrderLineId,
                    orderedQuantity = line.orderedQuantity,
                    acceptedQuantity = line.acceptedQuantity,
                    invoicedQuantity = line.invoicedQuantity,
                    poUnitPriceMinor = line.poUnitPriceMinor,
                    invoiceUnitPriceMinor = line.invoiceUnitPriceMinor,
                    quantityVarianceUnits = line.quantityVarianceUnits,
                    priceVarianceMinor = line.priceVarianceMinor,
                    payableAmountMinor = line.payableAmountMinor,
                )
            },
        )
        assessment.lines.asSequence().map { it.purchaseOrderLineId }.distinct().forEach { orderLineId ->
            allocateAcceptedReceipts(
                organizationId = assessment.match.organizationId,
                orderLineId = orderLineId,
                occurredAt = assessment.match.matchedAt,
            )
        }
    }

    private suspend fun allocateAcceptedReceipts(
        organizationId: String,
        orderLineId: String,
        occurredAt: Long,
    ) {
        val matchLines = dao.getMatchLinesAwaitingReceipt(orderLineId)
        val receiptLines = dao.getReceiptLinesWithAvailableQuantity(orderLineId)
        if (matchLines.isEmpty() || receiptLines.isEmpty()) return

        for (matchLine in matchLines) {
            var needed = matchLine.invoicedQuantity - dao.getAllocatedQuantityForMatchLine(matchLine.id)
            if (needed <= 0) continue
            for (receiptLine in receiptLines) {
                if (needed <= 0) break
                val available = receiptLine.acceptedQuantity - dao.getAllocatedQuantityForReceiptLine(receiptLine.id)
                if (available <= 0) continue
                val quantity = minOf(needed, available)
                val allocationId = UUID.nameUUIDFromBytes(
                    "grn-invoice-allocation|$organizationId|${matchLine.id}|${receiptLine.id}".toByteArray(StandardCharsets.UTF_8)
                ).toString()
                val row = PurchaseInvoiceReceiptAllocationEntity(
                    id = allocationId,
                    organizationId = organizationId,
                    matchLineId = matchLine.id,
                    goodsReceiptLineId = receiptLine.id,
                    allocatedQuantity = quantity,
                    createdAt = occurredAt,
                    writeId = allocationId,
                )
                val inserted = dao.insertReceiptAllocation(row)
                if (inserted == -1L) {
                    val existing = dao.getReceiptAllocation(organizationId, matchLine.id, receiptLine.id)
                    require(existing != null && existing.allocatedQuantity == quantity) {
                        "تعارض تخصيص GRN مع فاتورة المورد"
                    }
                }
                needed -= quantity
            }
        }
    }

    override suspend fun getInvoiceMatch(invoiceId: String): PurchaseInvoiceMatchRecord? =
        dao.getInvoiceMatch(invoiceId)?.toDomain()

    override suspend fun insertPaymentOverride(record: PurchasePaymentOverrideRecord) {
        val row = PurchasePaymentOverrideEntity(
            id = record.id, organizationId = record.organizationId, invoiceId = record.invoiceId,
            paymentRequestId = record.paymentRequestId, requestedAmountMinor = record.requestedAmountMinor,
            payableBeforeOverrideMinor = record.payableBeforeOverrideMinor, reason = record.reason,
            approvedBy = record.approvedBy, approvedByName = record.approvedByName, createdAt = record.createdAt,
        )
        val inserted = dao.insertPaymentOverride(row)
        if (inserted == -1L) {
            val existing = dao.getPaymentOverride(record.organizationId, record.paymentRequestId)
            require(existing == row) { "تعارض هوية استثناء دفع المشتريات" }
        }
    }

    override suspend fun shipmentExists(organizationId: String, shipmentId: String): Boolean =
        dao.shipmentExists(organizationId, shipmentId)

    override suspend fun linkOrderToShipment(
        organizationId: String, orderId: String, shipmentId: String, writeId: String, addedAt: Long,
    ) {
        dao.getOrderShipmentSourceByWriteId(organizationId, writeId)?.let { existing ->
            require(existing.purchaseOrderId == orderId && existing.shipmentId == shipmentId) { "writeId مستخدم لمصدر شحنة مختلف" }
            return
        }
        dao.insertOrderShipmentSource(
            PurchaseOrderShipmentSourceEntity(
                id = UUID.nameUUIDFromBytes("po-shipment|$organizationId|$writeId".toByteArray(StandardCharsets.UTF_8)).toString(),
                organizationId = organizationId, shipmentId = shipmentId, purchaseOrderId = orderId,
                addedAt = addedAt, writeId = writeId,
            )
        )
    }
}

private fun PurchaseOrderEntity.toDomain() = PurchaseOrderRecord(
    id = id,
    organizationId = organizationId,
    orderNumber = orderNumber,
    supplierId = supplierId,
    purchaseScope = PurchaseScope.valueOf(purchaseScope),
    currencyCode = currencyCode,
    status = PurchaseOrderStatus.valueOf(status),
    createdAt = createdAt,
    promisedDeliveryAt = promisedDeliveryAt,
    createdBy = createdBy,
    createdByName = createdByName,
    closedAt = closedAt,
    closeReason = closeReason,
    note = note,
    writeId = writeId,
)

private fun PurchaseOrderRecord.toEntity() = PurchaseOrderEntity(
    id = id,
    organizationId = organizationId,
    orderNumber = orderNumber,
    supplierId = supplierId,
    purchaseScope = purchaseScope.name,
    currencyCode = currencyCode,
    status = status.name,
    createdAt = createdAt,
    promisedDeliveryAt = promisedDeliveryAt,
    createdBy = createdBy,
    createdByName = createdByName,
    closedAt = closedAt,
    closeReason = closeReason,
    note = note,
    writeId = writeId,
)

private fun PurchaseOrderLineEntity.toDomain() = PurchaseOrderLineRecord(
    id = id,
    purchaseOrderId = purchaseOrderId,
    lineNumber = lineNumber,
    inventoryItemId = inventoryItemId,
    itemName = itemNameSnapshot,
    orderedQuantity = orderedQuantity,
    unitPriceMinor = unitPriceMinor,
)

private fun PurchaseOrderLineRecord.toEntity() = PurchaseOrderLineEntity(
    id = id,
    purchaseOrderId = purchaseOrderId,
    lineNumber = lineNumber,
    inventoryItemId = inventoryItemId,
    itemNameSnapshot = itemName,
    orderedQuantity = orderedQuantity,
    unitPriceMinor = unitPriceMinor,
)

private fun GoodsReceiptEntity.toDomain() = GoodsReceiptRecord(
    id = id,
    organizationId = organizationId,
    purchaseOrderId = purchaseOrderId,
    receiptNumber = receiptNumber,
    receivedAt = receivedAt,
    receivedBy = receivedBy,
    receivedByName = receivedByName,
    note = note,
    writeId = writeId,
)

private fun GoodsReceiptRecord.toEntity() = GoodsReceiptEntity(
    id = id,
    organizationId = organizationId,
    purchaseOrderId = purchaseOrderId,
    receiptNumber = receiptNumber,
    receivedAt = receivedAt,
    receivedBy = receivedBy,
    receivedByName = receivedByName,
    note = note,
    writeId = writeId,
)

private fun GoodsReceiptLineEntity.toDomain() = GoodsReceiptLineRecord(
    id = id,
    goodsReceiptId = goodsReceiptId,
    purchaseOrderLineId = purchaseOrderLineId,
    inventoryItemId = inventoryItemId,
    receivedQuantity = receivedQuantity,
    acceptedQuantity = acceptedQuantity,
    rejectedQuantity = rejectedQuantity,
    unitCostMinor = unitCostMinor,
)

private fun GoodsReceiptLineRecord.toEntity() = GoodsReceiptLineEntity(
    id = id,
    goodsReceiptId = goodsReceiptId,
    purchaseOrderLineId = purchaseOrderLineId,
    inventoryItemId = inventoryItemId,
    receivedQuantity = receivedQuantity,
    acceptedQuantity = acceptedQuantity,
    rejectedQuantity = rejectedQuantity,
    unitCostMinor = unitCostMinor,
)

private fun PurchaseInvoiceMatchRecord.toEntity() = PurchaseInvoiceMatchEntity(
    id = id,
    organizationId = organizationId,
    invoiceId = invoiceId,
    purchaseOrderId = purchaseOrderId,
    status = status.name,
    quantityVarianceUnits = quantityVarianceUnits,
    priceVarianceMinor = priceVarianceMinor,
    quantityToleranceUnits = quantityToleranceUnits,
    priceToleranceMinor = priceToleranceMinor,
    invoiceAmountMinor = invoiceAmountMinor,
    payableAmountMinor = payableAmountMinor,
    varianceReason = varianceReason,
    approvedBy = approvedBy,
    approvedByName = approvedByName,
    matchedAt = matchedAt,
    writeId = writeId,
)

private fun PurchaseInvoiceMatchEntity.toDomain() = PurchaseInvoiceMatchRecord(
    id = id,
    organizationId = organizationId,
    invoiceId = invoiceId,
    purchaseOrderId = purchaseOrderId,
    status = PurchaseInvoiceMatchStatus.valueOf(status),
    quantityVarianceUnits = quantityVarianceUnits,
    priceVarianceMinor = priceVarianceMinor,
    quantityToleranceUnits = quantityToleranceUnits,
    priceToleranceMinor = priceToleranceMinor,
    invoiceAmountMinor = invoiceAmountMinor,
    payableAmountMinor = payableAmountMinor,
    varianceReason = varianceReason,
    approvedBy = approvedBy,
    approvedByName = approvedByName,
    matchedAt = matchedAt,
    writeId = writeId,
)

private fun List<PurchaseCycleAttachmentDraft>.toEntities(
    organizationId: String,
    ownerType: PurchaseAttachmentOwnerType,
    ownerId: String,
    writeId: String,
    occurredAt: Long,
): List<PurchaseCycleAttachmentEntity> = mapIndexed { index, attachment ->
    PurchaseCycleAttachmentEntity(
        id = stableAttachmentId(ownerType.name, ownerId, "$writeId:$index:${attachment.uri}"),
        organizationId = organizationId,
        ownerType = ownerType.name,
        ownerId = ownerId,
        uri = attachment.uri.trim().also { require(it.isNotEmpty()) { "attachment uri is required" } },
        mimeType = attachment.mimeType.trim(),
        displayName = attachment.displayName.trim(),
        createdAt = occurredAt,
        writeId = writeId,
    )
}

private fun stableAttachmentId(type: String, owner: String, identity: String): String =
    UUID.nameUUIDFromBytes("purchase-attachment|$type|$owner|$identity".toByteArray(StandardCharsets.UTF_8)).toString()
