package com.verto.app.data.sync

import com.verto.app.data.local.entity.GoodsReceiptEntity
import com.verto.app.data.local.entity.GoodsReceiptLineEntity
import com.verto.app.data.local.entity.PurchaseCycleAttachmentEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchEntity
import com.verto.app.data.local.entity.PurchaseInvoiceMatchLineEntity
import com.verto.app.data.local.entity.PurchaseInvoiceReceiptAllocationEntity
import com.verto.app.data.local.entity.PurchaseOrderEntity
import com.verto.app.data.local.entity.PurchaseOrderLineEntity
import com.verto.app.data.local.entity.PurchasePaymentOverrideEntity
import com.verto.app.data.remote.dto.GoodsReceiptDto
import com.verto.app.data.remote.dto.GoodsReceiptLineDto
import com.verto.app.data.remote.dto.PurchaseCycleAttachmentDto
import com.verto.app.data.remote.dto.PurchaseCyclePostPushRequest
import com.verto.app.data.remote.dto.PurchaseCyclePrePushRequest
import com.verto.app.data.remote.dto.PurchaseInvoiceMatchDto
import com.verto.app.data.remote.dto.PurchaseInvoiceMatchLineDto
import com.verto.app.data.remote.dto.PurchaseInvoiceReceiptAllocationDto
import com.verto.app.data.remote.dto.PurchaseOrderDto
import com.verto.app.data.remote.dto.PurchaseOrderLineDto
import com.verto.app.data.remote.dto.PurchasePaymentOverrideDto
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

/**
 * F253 purchase-cycle transport.
 *
 * Ordering is intentional:
 * 1) PO/GRN facts must exist remotely before invoices may reference a PO.
 * 2) invoices + invoice_items must exist before three-way-match facts.
 * 3) match/allocation/override facts must exist before payment push so the server payment guard
 *    sees the same received-quantity truth as Room.
 */
suspend fun SyncRuntime.pushPurchaseCyclePreInvoices(orgId: String) {
    require(orgId.isNotBlank()) { "purchase-cycle sync requires organization" }
    val dao = db.purchaseCycleDao()
    val orders = dao.getOrdersForOrganization(orgId)
    val orderIds = orders.mapTo(hashSetOf()) { it.id }
    val orderLines = dao.getOrderLinesForOrganization(orgId)
    val receipts = dao.getReceiptsForOrganization(orgId)
    val receiptIds = receipts.mapTo(hashSetOf()) { it.id }
    val receiptLines = dao.getReceiptLinesForOrganization(orgId)
    val attachments = dao.getAttachmentsForOrganization(orgId)

    require(orderLines.all { it.purchaseOrderId in orderIds }) { "cross-tenant PO line rejected before push" }
    require(receiptLines.all { it.goodsReceiptId in receiptIds }) { "cross-tenant GRN line rejected before push" }

    if (orders.isEmpty() && orderLines.isEmpty() && receipts.isEmpty() && receiptLines.isEmpty() && attachments.isEmpty()) return
    supabase.postgrest.rpc(
        "verto_purchase_cycle_push_pre_v253",
        PurchaseCyclePrePushRequest(
            purchaseOrders = orders.map { it.toRemote() },
            purchaseOrderLines = orderLines.map { it.toRemote(orgId) },
            goodsReceipts = receipts.map { it.toRemote() },
            goodsReceiptLines = receiptLines.map { it.toRemote(orgId) },
            attachments = attachments.map { it.toRemote() },
        ),
    )
}

suspend fun SyncRuntime.pushPurchaseCyclePostInvoices(orgId: String) {
    require(orgId.isNotBlank()) { "purchase-cycle sync requires organization" }
    val dao = db.purchaseCycleDao()
    val matches = dao.getInvoiceMatchesForOrganization(orgId)
    val matchIds = matches.mapTo(hashSetOf()) { it.id }
    val matchLines = dao.getInvoiceMatchLinesForOrganization(orgId)
    val matchLineIds = matchLines.mapTo(hashSetOf()) { it.id }
    val allocations = dao.getReceiptAllocationsForOrganization(orgId)
    val overrides = dao.getPaymentOverridesForOrganization(orgId)

    require(matchLines.all { it.matchId in matchIds }) { "cross-tenant match line rejected before push" }
    require(allocations.all { it.matchLineId in matchLineIds }) { "cross-tenant receipt allocation rejected before push" }

    if (matches.isEmpty() && matchLines.isEmpty() && allocations.isEmpty() && overrides.isEmpty()) return
    supabase.postgrest.rpc(
        "verto_purchase_cycle_push_post_v253",
        PurchaseCyclePostPushRequest(
            matches = matches.map { it.toRemote() },
            matchLines = matchLines.map { it.toRemote(orgId) },
            allocations = allocations.map { it.toRemote() },
            paymentOverrides = overrides.map { it.toRemote() },
        ),
    )
}

suspend fun SyncRuntime.pullPurchaseCyclePreInvoices(orgId: String) {
    require(orgId.isNotBlank()) { "purchase-cycle sync requires organization" }
    val orders = supabase.postgrest["purchase_orders"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<PurchaseOrderDto>()
    val orderLines = supabase.postgrest["purchase_order_lines"].select { }.decodeList<PurchaseOrderLineDto>()
    val receipts = supabase.postgrest["goods_receipts"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<GoodsReceiptDto>()
    val receiptLines = supabase.postgrest["goods_receipt_lines"].select { }.decodeList<GoodsReceiptLineDto>()
    val attachments = supabase.postgrest["purchase_cycle_attachments"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<PurchaseCycleAttachmentDto>()

    db.purchaseCycleDao().applyRemotePreCycle(
        organizationId = orgId,
        orders = orders.map { it.toLocal() },
        orderLines = orderLines.map { it.toLocal() },
        receipts = receipts.map { it.toLocal() },
        receiptLines = receiptLines.map { it.toLocal() },
        attachments = attachments.map { it.toLocal() },
    )
}

suspend fun SyncRuntime.pullPurchaseCyclePostInvoices(orgId: String) {
    require(orgId.isNotBlank()) { "purchase-cycle sync requires organization" }
    val matches = supabase.postgrest["purchase_invoice_matches"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<PurchaseInvoiceMatchDto>()
    val matchLines = supabase.postgrest["purchase_invoice_match_lines"].select { }.decodeList<PurchaseInvoiceMatchLineDto>()
    val allocations = supabase.postgrest["purchase_invoice_receipt_allocations"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<PurchaseInvoiceReceiptAllocationDto>()
    val overrides = supabase.postgrest["purchase_payment_overrides"].select {
        filter { eq("organization_id", orgId) }
    }.decodeList<PurchasePaymentOverrideDto>()

    db.purchaseCycleDao().applyRemotePostCycle(
        organizationId = orgId,
        matches = matches.map { it.toLocal() },
        matchLines = matchLines.map { it.toLocal() },
        allocations = allocations.map { it.toLocal() },
        overrides = overrides.map { it.toLocal() },
    )
}

private fun PurchaseOrderEntity.toRemote() = PurchaseOrderDto(
    id = id,
    organizationId = organizationId,
    orderNumber = orderNumber,
    supplierId = supplierId,
    purchaseScope = purchaseScope,
    currencyCode = currencyCode,
    status = status,
    createdAt = createdAt,
    promisedDeliveryAt = promisedDeliveryAt,
    createdBy = createdBy,
    createdByName = createdByName,
    closedAt = closedAt,
    closeReason = closeReason,
    note = note,
    writeId = writeId,
)

private fun PurchaseOrderLineEntity.toRemote(orgId: String) = PurchaseOrderLineDto(
    id, orgId, purchaseOrderId, lineNumber, inventoryItemId, itemNameSnapshot, orderedQuantity, unitPriceMinor,
)

private fun GoodsReceiptEntity.toRemote() = GoodsReceiptDto(
    id, organizationId, purchaseOrderId, receiptNumber, receivedAt, receivedBy, receivedByName, note, writeId,
)

private fun GoodsReceiptLineEntity.toRemote(orgId: String) = GoodsReceiptLineDto(
    id, orgId, goodsReceiptId, purchaseOrderLineId, inventoryItemId, receivedQuantity,
    acceptedQuantity, rejectedQuantity, unitCostMinor,
)

private fun PurchaseCycleAttachmentEntity.toRemote() = PurchaseCycleAttachmentDto(
    id, organizationId, ownerType, ownerId, uri, mimeType, displayName, createdAt, writeId,
)

private fun PurchaseInvoiceMatchEntity.toRemote() = PurchaseInvoiceMatchDto(
    id, organizationId, invoiceId, purchaseOrderId, status, quantityVarianceUnits, priceVarianceMinor,
    quantityToleranceUnits, priceToleranceMinor, invoiceAmountMinor, payableAmountMinor, varianceReason,
    approvedBy, approvedByName, matchedAt, writeId,
)

private fun PurchaseInvoiceMatchLineEntity.toRemote(orgId: String) = PurchaseInvoiceMatchLineDto(
    id, orgId, matchId, invoiceItemId, purchaseOrderLineId, orderedQuantity, acceptedQuantity,
    invoicedQuantity, poUnitPriceMinor, invoiceUnitPriceMinor, quantityVarianceUnits, priceVarianceMinor,
    payableAmountMinor,
)

private fun PurchaseInvoiceReceiptAllocationEntity.toRemote() = PurchaseInvoiceReceiptAllocationDto(
    id, organizationId, matchLineId, goodsReceiptLineId, allocatedQuantity, createdAt, writeId,
)

private fun PurchasePaymentOverrideEntity.toRemote() = PurchasePaymentOverrideDto(
    id, organizationId, invoiceId, paymentRequestId, requestedAmountMinor, payableBeforeOverrideMinor,
    reason, approvedBy, approvedByName, createdAt,
)

private fun PurchaseOrderDto.toLocal() = PurchaseOrderEntity(
    id = id,
    organizationId = organizationId,
    orderNumber = orderNumber,
    supplierId = supplierId,
    purchaseScope = purchaseScope,
    currencyCode = currencyCode,
    status = status,
    createdAt = createdAt,
    promisedDeliveryAt = promisedDeliveryAt,
    createdBy = createdBy,
    createdByName = createdByName,
    closedAt = closedAt,
    closeReason = closeReason,
    note = note,
    writeId = writeId,
)

private fun PurchaseOrderLineDto.toLocal() = PurchaseOrderLineEntity(
    id, purchaseOrderId, lineNumber, inventoryItemId, itemNameSnapshot, orderedQuantity, unitPriceMinor,
)

private fun GoodsReceiptDto.toLocal() = GoodsReceiptEntity(
    id, organizationId, purchaseOrderId, receiptNumber, receivedAt, receivedBy, receivedByName, note, writeId,
)

private fun GoodsReceiptLineDto.toLocal() = GoodsReceiptLineEntity(
    id, goodsReceiptId, purchaseOrderLineId, inventoryItemId, receivedQuantity, acceptedQuantity,
    rejectedQuantity, unitCostMinor,
)

private fun PurchaseCycleAttachmentDto.toLocal() = PurchaseCycleAttachmentEntity(
    id, organizationId, ownerType, ownerId, uri, mimeType, displayName, createdAt, writeId,
)

private fun PurchaseInvoiceMatchDto.toLocal() = PurchaseInvoiceMatchEntity(
    id, organizationId, invoiceId, purchaseOrderId, status, quantityVarianceUnits, priceVarianceMinor,
    quantityToleranceUnits, priceToleranceMinor, invoiceAmountMinor, payableAmountMinor, varianceReason,
    approvedBy, approvedByName, matchedAt, writeId,
)

private fun PurchaseInvoiceMatchLineDto.toLocal() = PurchaseInvoiceMatchLineEntity(
    id, matchId, invoiceItemId, purchaseOrderLineId, orderedQuantity, acceptedQuantity, invoicedQuantity,
    poUnitPriceMinor, invoiceUnitPriceMinor, quantityVarianceUnits, priceVarianceMinor, payableAmountMinor,
)

private fun PurchaseInvoiceReceiptAllocationDto.toLocal() = PurchaseInvoiceReceiptAllocationEntity(
    id, organizationId, matchLineId, goodsReceiptLineId, allocatedQuantity, createdAt, writeId,
)

private fun PurchasePaymentOverrideDto.toLocal() = PurchasePaymentOverrideEntity(
    id, organizationId, invoiceId, paymentRequestId, requestedAmountMinor, payableBeforeOverrideMinor,
    reason, approvedBy, approvedByName, createdAt,
)
