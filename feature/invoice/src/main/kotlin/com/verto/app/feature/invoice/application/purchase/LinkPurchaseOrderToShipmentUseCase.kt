package com.verto.app.feature.invoice.application.purchase

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.LinkPurchaseOrderToShipmentCommand
import com.verto.app.feature.invoice.domain.model.PurchaseOrderStatus
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.invoice.domain.port.InvoiceAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import com.verto.app.feature.invoice.domain.port.PurchaseCycleStorePort
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

/** F253: makes an international PO a first-class shipment source without posting inventory. */
class LinkPurchaseOrderToShipmentUseCase @Inject constructor(
    private val transaction: InvoiceTransactionPort,
    private val store: PurchaseCycleStorePort,
    private val authorization: InvoiceAuthorizationPort,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    suspend operator fun invoke(command: LinkPurchaseOrderToShipmentCommand) {
        require(authorization.canPost(InvoiceCategory.PURCHASE)) { "لا تملك صلاحية ربط أمر الشراء بالشحنة" }
        val organizationId = command.organizationId.trim().ifBlank { sessionReader.snapshot().organization.id.trim() }
        require(organizationId.isNotBlank() && command.shipmentId.isNotBlank() && command.writeId.isNotBlank()) { "هوية مصدر الشحنة غير مكتملة" }
        transaction.inTransaction {
            val order = requireNotNull(store.getOrder(command.purchaseOrderId.trim())) { "أمر الشراء غير موجود" }
            require(order.organizationId == organizationId) { "أمر الشراء يتبع منشأة أخرى" }
            require(order.purchaseScope == PurchaseScope.INTERNATIONAL) { "فقط أمر الشراء الدولي يمكن أن يكون مصدر شحنة" }
            require(order.status != PurchaseOrderStatus.CANCELLED) { "أمر الشراء ملغي" }
            require(store.shipmentExists(organizationId, command.shipmentId.trim())) { "الشحنة غير موجودة" }
            val shipmentId = command.shipmentId.trim()
            store.linkOrderToShipment(
                organizationId, order.id, shipmentId, command.writeId.trim(), command.addedAt,
            )
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "PURCHASE_ORDER",
                aggregateId = order.id,
                operationType = "COMMAND",
                mutationId = command.writeId,
                commandBatchId = command.writeId,
                commandOrder = 0,
                createdAt = command.addedAt,
                payload = mapOf("command" to "LINK_SHIPMENT", "shipmentId" to shipmentId),
            )
            val shipmentMutationId = UUID.nameUUIDFromBytes(
                "v307|link-po-shipment|${command.writeId}|$shipmentId".toByteArray(StandardCharsets.UTF_8)
            ).toString()
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "SHIPMENT",
                aggregateId = shipmentId,
                operationType = "COMMAND",
                mutationId = shipmentMutationId,
                commandBatchId = command.writeId,
                commandOrder = 1,
                dependsOnMutationId = command.writeId,
                createdAt = command.addedAt,
                payload = mapOf("command" to "LINK_PURCHASE_ORDER", "purchaseOrderId" to order.id),
            )
        }
    }
}
