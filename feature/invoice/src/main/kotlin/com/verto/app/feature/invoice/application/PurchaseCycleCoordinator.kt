package com.verto.app.feature.invoice.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.invoice.domain.model.ClosePurchaseOrderCommand
import com.verto.app.feature.invoice.domain.model.CreatePurchaseOrderCommand
import com.verto.app.feature.invoice.domain.model.GoodsReceiptLineRecord
import com.verto.app.feature.invoice.domain.model.GoodsReceiptRecord
import com.verto.app.feature.invoice.domain.model.GoodsReceiptResult
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoicePurchaseStockCommand
import com.verto.app.feature.invoice.domain.model.InvoiceStockItem
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceCandidateLine
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchAssessment
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchLineRecord
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchRecord
import com.verto.app.feature.invoice.domain.model.PurchaseInvoiceMatchStatus
import com.verto.app.feature.invoice.domain.model.PurchaseOrderLineRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderRecord
import com.verto.app.feature.invoice.domain.model.PurchaseOrderResult
import com.verto.app.feature.invoice.domain.model.PurchaseOrderStatus
import com.verto.app.feature.invoice.domain.model.PurchasePaymentOverrideRecord
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.invoice.domain.model.RecordGoodsReceiptCommand
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import com.verto.app.feature.invoice.domain.port.PurchaseCycleInvoicePort
import com.verto.app.feature.invoice.domain.port.PurchaseCycleStorePort
import com.verto.app.money.Money
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/** F253 owner for PO/GRN/three-way-match transitions. */
class PurchaseCycleCoordinator @Inject constructor(
    private val transaction: InvoiceTransactionPort,
    private val store: PurchaseCycleStorePort,
    private val stock: InvoiceStockPort,
    private val policy: PurchaseCyclePolicy,
    private val sessionReader: SessionReader,
    private val unifiedOutboxWriter: UnifiedOutboxWriter,
) : PurchaseCycleInvoicePort {

    suspend fun createOrder(raw: CreatePurchaseOrderCommand): PurchaseOrderResult {
        val command = raw.normalized()
        require(policy.canPostPurchase()) { "لا تملك صلاحية إنشاء أمر شراء" }
        val session = sessionReader.snapshot()
        val organizationId = command.organizationId.ifBlank { session.organization.id.trim() }
        require(organizationId.isNotBlank()) { "تعذر تحديد الشركة لأمر الشراء" }
        store.getOrderByWriteId(organizationId, command.writeId)?.let {
            return PurchaseOrderResult(it.id, duplicate = true)
        }

        val recommendationWarnings = supplierRecommendationWarnings(command)
        val orderId = stableId("po", organizationId, command.writeId)
        val order = PurchaseOrderRecord(
            id = orderId,
            organizationId = organizationId,
            orderNumber = command.orderNumber,
            supplierId = command.supplierId,
            purchaseScope = command.purchaseScope,
            currencyCode = command.currencyCode,
            status = PurchaseOrderStatus.OPEN,
            createdAt = command.createdAt,
            promisedDeliveryAt = command.promisedDeliveryAt,
            createdBy = session.user.id,
            createdByName = session.user.name,
            note = command.note,
            writeId = command.writeId,
        )
        val lines = command.lines.mapIndexed { index, line ->
            PurchaseOrderLineRecord(
                id = stableId("po-line", orderId, (index + 1).toString()),
                purchaseOrderId = orderId,
                lineNumber = index + 1,
                inventoryItemId = line.inventoryItemId?.trim()?.takeIf(String::isNotEmpty),
                itemName = line.itemName.trim(),
                orderedQuantity = line.orderedQuantity,
                unitPriceMinor = line.unitPrice.amountMinor,
            )
        }
        transaction.inTransaction {
            store.getOrderByWriteId(organizationId, command.writeId)?.let {
                return@inTransaction
            }
            store.insertOrder(order, lines, command.attachments)
            unifiedOutboxWriter.enqueue(
                organizationId = organizationId,
                aggregateType = "PURCHASE_ORDER",
                aggregateId = orderId,
                operationType = "UPSERT",
                mutationId = command.writeId,
                commandBatchId = command.writeId,
                commandOrder = 0,
                createdAt = command.createdAt,
                payload = mapOf(
                    "id" to order.id,
                    "orderNumber" to order.orderNumber,
                    "supplierId" to order.supplierId,
                    "purchaseScope" to order.purchaseScope.name,
                    "currencyCode" to order.currencyCode,
                    "status" to order.status.name,
                    "promisedDeliveryAt" to order.promisedDeliveryAt,
                    "note" to order.note,
                    "lines" to lines.sortedBy { it.lineNumber }.joinToString("|") { line ->
                        listOf(line.id, line.inventoryItemId.orEmpty(), line.itemName, line.orderedQuantity.toString(), line.unitPriceMinor.toString()).joinToString("~")
                    },
                ),
            )
        }
        return PurchaseOrderResult(orderId, duplicate = false, recommendationWarnings = recommendationWarnings)
    }

    suspend fun receive(raw: RecordGoodsReceiptCommand): GoodsReceiptResult {
        val command = raw.normalized()
        require(policy.canPostPurchase()) { "لا تملك صلاحية تسجيل استلام مشتريات" }
        val session = sessionReader.snapshot()
        val organizationId = command.organizationId.ifBlank { session.organization.id.trim() }
        require(organizationId.isNotBlank()) { "تعذر تحديد الشركة للاستلام" }
        store.getReceiptByWriteId(organizationId, command.writeId)?.let { existing ->
            val order = requireNotNull(store.getOrder(existing.purchaseOrderId))
            return GoodsReceiptResult(existing.id, duplicate = true, orderStatus = order.status)
        }

        val receiptId = stableId("grn", organizationId, command.writeId)
        var finalStatus = PurchaseOrderStatus.OPEN
        transaction.inTransaction {
            store.getReceiptByWriteId(organizationId, command.writeId)?.let { existing ->
                finalStatus = requireNotNull(store.getOrder(existing.purchaseOrderId)).status
                return@inTransaction
            }
            val order = requireNotNull(store.getOrder(command.purchaseOrderId)) { "أمر الشراء غير موجود" }
            require(order.organizationId == organizationId) { "أمر الشراء يتبع منشأة أخرى" }
            require(order.status !in setOf(PurchaseOrderStatus.CLOSED, PurchaseOrderStatus.CANCELLED)) {
                "أمر الشراء مغلق ولا يقبل استلاماً جديداً"
            }
            val orderLines = store.getOrderLines(order.id).associateBy { it.id }
            require(command.lines.map { it.purchaseOrderLineId }.distinct().size == command.lines.size) {
                "لا يمكن تكرار بند أمر الشراء داخل نفس GRN"
            }

            val receiptLines = command.lines.mapIndexed { index, requested ->
                val orderLine = requireNotNull(orderLines[requested.purchaseOrderLineId]) { "بند أمر الشراء غير موجود" }
                val acceptedBefore = store.getAcceptedQuantity(orderLine.id)
                require(Math.addExact(acceptedBefore, requested.acceptedQuantity) <= orderLine.orderedQuantity) {
                    "الكمية المقبولة تتجاوز الكمية المطلوبة للبند ${orderLine.itemName}"
                }
                val resolvedInventoryId = if (order.purchaseScope == PurchaseScope.LOCAL && requested.acceptedQuantity > 0) {
                    resolveLocalInventoryItem(orderLine)
                } else {
                    orderLine.inventoryItemId ?: store.getResolvedInventoryItemId(orderLine.id)
                }
                GoodsReceiptLineRecord(
                    id = stableId("grn-line", receiptId, (index + 1).toString()),
                    goodsReceiptId = receiptId,
                    purchaseOrderLineId = orderLine.id,
                    inventoryItemId = resolvedInventoryId,
                    receivedQuantity = requested.receivedQuantity,
                    acceptedQuantity = requested.acceptedQuantity,
                    rejectedQuantity = requested.rejectedQuantity,
                    unitCostMinor = requested.unitCostMinor ?: orderLine.unitPriceMinor,
                )
            }
            val receipt = GoodsReceiptRecord(
                id = receiptId,
                organizationId = organizationId,
                purchaseOrderId = order.id,
                receiptNumber = command.receiptNumber,
                receivedAt = command.receivedAt,
                receivedBy = session.user.id,
                receivedByName = session.user.name,
                note = command.note,
                writeId = command.writeId,
            )
            store.insertReceipt(receipt, receiptLines, command.attachments)

            // Local-cycle inventory is owned by GRN. International stock remains logistics-owned.
            if (order.purchaseScope == PurchaseScope.LOCAL) {
                for (line in receiptLines.filter { it.acceptedQuantity > 0 }) {
                    val itemId = requireNotNull(line.inventoryItemId) { "تعذر ربط بند الاستلام بالمخزون" }
                    val lineWriteId = stableId("grn-post", receiptId, line.id)
                    stock.receivePurchaseAtLatestPrice(
                        InvoicePurchaseStockCommand(
                            itemId = itemId,
                            quantity = line.acceptedQuantity,
                            invoiceId = "",
                            supplierId = order.supplierId,
                            buyPriceMinor = line.unitCostMinor,
                            actorId = session.user.id,
                            actorName = session.user.name,
                            occurredAt = command.receivedAt,
                            writeId = lineWriteId,
                            eventId = stableId("grn-cost", receiptId, line.id),
                            sourceType = "GOODS_RECEIPT",
                            sourceId = receiptId,
                            sourceLineId = line.id,
                            postingGroupId = receiptId,
                        )
                    )
                }
            }

            var allAccepted = 0
            for (orderLine in orderLines.values) {
                allAccepted = Math.addExact(allAccepted, store.getAcceptedQuantity(orderLine.id))
            }
            val allOrdered = orderLines.values.sumOf { it.orderedQuantity }
            finalStatus = when {
                allAccepted == 0 -> PurchaseOrderStatus.OPEN
                allAccepted < allOrdered -> PurchaseOrderStatus.PARTIALLY_RECEIVED
                else -> PurchaseOrderStatus.RECEIVED
            }
            store.updateOrderStatus(order.id, finalStatus)
            unifiedOutboxWriter.enqueue(
                organizationId = organizationId,
                aggregateType = "GOODS_RECEIPT",
                aggregateId = receiptId,
                operationType = "COMMAND",
                mutationId = command.writeId,
                commandBatchId = command.writeId,
                commandOrder = 0,
                createdAt = command.receivedAt,
                payload = mapOf("purchaseRequest" to mapOf(
                    "purchaseOrders" to listOf(mapOf(
                        "id" to order.id, "organizationId" to order.organizationId,
                        "orderNumber" to order.orderNumber, "supplierId" to order.supplierId,
                        "purchaseScope" to order.purchaseScope.name, "currencyCode" to order.currencyCode,
                        "status" to finalStatus.name, "createdAt" to order.createdAt,
                        "promisedDeliveryAt" to order.promisedDeliveryAt, "createdBy" to order.createdBy,
                        "createdByName" to order.createdByName, "closedAt" to order.closedAt,
                        "closeReason" to order.closeReason, "note" to order.note, "writeId" to order.writeId,
                    )),
                    "purchaseOrderLines" to orderLines.values.sortedBy { it.id }.map { line -> mapOf(
                        "id" to line.id, "purchaseOrderId" to line.purchaseOrderId,
                        "lineNumber" to line.lineNumber, "inventoryItemId" to line.inventoryItemId,
                        "itemNameSnapshot" to line.itemName, "orderedQuantity" to line.orderedQuantity,
                        "unitPriceMinor" to line.unitPriceMinor,
                    ) },
                    "goodsReceipts" to listOf(mapOf(
                        "id" to receipt.id, "organizationId" to receipt.organizationId,
                        "purchaseOrderId" to receipt.purchaseOrderId, "receiptNumber" to receipt.receiptNumber,
                        "receivedAt" to receipt.receivedAt, "receivedBy" to receipt.receivedBy,
                        "receivedByName" to receipt.receivedByName, "note" to receipt.note, "writeId" to receipt.writeId,
                    )),
                    "goodsReceiptLines" to receiptLines.sortedBy { it.id }.map { line -> mapOf(
                        "id" to line.id, "goodsReceiptId" to line.goodsReceiptId,
                        "purchaseOrderLineId" to line.purchaseOrderLineId, "inventoryItemId" to line.inventoryItemId,
                        "receivedQuantity" to line.receivedQuantity, "acceptedQuantity" to line.acceptedQuantity,
                        "rejectedQuantity" to line.rejectedQuantity, "unitCostMinor" to line.unitCostMinor,
                    ) },
                    "attachments" to command.attachments.map { attachment -> mapOf(
                        "mimeType" to attachment.mimeType, "displayName" to attachment.displayName,
                        "transferState" to "LOCAL_PENDING"
                    ) },
                    "matches" to emptyList<Map<String, Any?>>(),
                    "matchLines" to emptyList<Map<String, Any?>>(),
                    "allocations" to emptyList<Map<String, Any?>>(),
                    "paymentOverrides" to emptyList<Map<String, Any?>>(),
                )),
            )
            unifiedOutboxWriter.enqueue(
                organizationId = organizationId,
                aggregateType = "PURCHASE_ORDER",
                aggregateId = order.id,
                operationType = "COMMAND",
                mutationId = stableId("po-status", order.id, command.writeId),
                commandBatchId = command.writeId,
                commandOrder = 1,
                createdAt = command.receivedAt,
                payload = mapOf("command" to "RECEIPT_STATUS", "status" to finalStatus.name, "receiptId" to receiptId),
            )
        }
        return GoodsReceiptResult(receiptId, duplicate = false, orderStatus = finalStatus)
    }

    suspend fun close(command: ClosePurchaseOrderCommand) {
        require(policy.canPostPurchase()) { "لا تملك صلاحية إغلاق أمر الشراء" }
        val reason = command.reason.trim()
        require(reason.isNotEmpty()) { "سبب إغلاق أمر الشراء مطلوب" }
        transaction.inTransaction {
            val order = requireNotNull(store.getOrder(command.purchaseOrderId.trim())) { "أمر الشراء غير موجود" }
            require(order.status !in setOf(PurchaseOrderStatus.CLOSED, PurchaseOrderStatus.CANCELLED)) { "أمر الشراء مغلق بالفعل" }
            store.updateOrderStatus(order.id, PurchaseOrderStatus.CLOSED, command.closedAt, reason)
            val mutationId = stableId("po-close", order.id, command.closedAt.toString())
            unifiedOutboxWriter.enqueue(
                organizationId = order.organizationId,
                aggregateType = "PURCHASE_ORDER",
                aggregateId = order.id,
                operationType = "COMMAND",
                mutationId = mutationId,
                createdAt = command.closedAt,
                payload = mapOf(
                    "command" to "CLOSE",
                    "id" to order.id,
                    "status" to PurchaseOrderStatus.CLOSED.name,
                    "reason" to reason,
                    "closedAt" to command.closedAt,
                ),
            )
        }
    }

    override suspend fun assessSupplierInvoice(
        organizationId: String,
        purchaseOrderId: String,
        supplierId: String,
        purchaseScope: PurchaseScope,
        invoiceId: String,
        invoiceAmountMinor: Long,
        lines: List<PurchaseInvoiceCandidateLine>,
        quantityToleranceUnits: Int,
        priceToleranceMinor: Long,
        varianceReason: String?,
        approvedBy: String?,
        approvedByName: String?,
        writeId: String,
        matchedAt: Long,
    ): PurchaseInvoiceMatchAssessment {
        require(quantityToleranceUnits >= 0) { "حد سماح الكمية لا يمكن أن يكون سالباً" }
        require(priceToleranceMinor >= 0L) { "حد سماح السعر لا يمكن أن يكون سالباً" }
        val order = requireNotNull(store.getOrder(purchaseOrderId)) { "أمر الشراء غير موجود" }
        require(order.organizationId == organizationId) { "أمر الشراء يتبع منشأة أخرى" }
        require(order.supplierId == supplierId) { "فاتورة المورد لا تخص مورد أمر الشراء" }
        require(order.purchaseScope == purchaseScope) { "نطاق الشراء لا يطابق أمر الشراء" }
        require(order.status != PurchaseOrderStatus.CANCELLED) { "أمر الشراء ملغي" }

        val orderLines = store.getOrderLines(order.id)
        val usedOrderLines = mutableSetOf<String>()
        val matchId = stableId("three-way", invoiceId, writeId)
        val matchedLines = lines.map { invoiceLine ->
            val poLine = matchOrderLine(invoiceLine, orderLines, usedOrderLines)
            usedOrderLines += poLine.id
            val acceptedTotal = store.getAcceptedQuantity(poLine.id)
            val alreadyInvoiced = store.getMatchedInvoicedQuantity(poLine.id)
            val acceptedAvailable = (acceptedTotal - alreadyInvoiced).coerceAtLeast(0)
            val quantityVariance = (invoiceLine.quantity - acceptedAvailable).coerceAtLeast(0)
            val unitPriceDelta = absExact(invoiceLine.unitPriceMinor, poLine.unitPriceMinor)
            val priceVariance = Math.multiplyExact(unitPriceDelta, invoiceLine.quantity.toLong())
            val payableQuantity = minOf(invoiceLine.quantity, acceptedAvailable)
            val payable = Math.multiplyExact(invoiceLine.unitPriceMinor, payableQuantity.toLong())
            PurchaseInvoiceMatchLineRecord(
                id = stableId("three-way-line", matchId, invoiceLine.invoiceItemId),
                matchId = matchId,
                invoiceItemId = invoiceLine.invoiceItemId,
                purchaseOrderLineId = poLine.id,
                orderedQuantity = poLine.orderedQuantity,
                acceptedQuantity = acceptedTotal,
                invoicedQuantity = invoiceLine.quantity,
                poUnitPriceMinor = poLine.unitPriceMinor,
                invoiceUnitPriceMinor = invoiceLine.unitPriceMinor,
                quantityVarianceUnits = quantityVariance,
                priceVarianceMinor = priceVariance,
                payableAmountMinor = payable,
            )
        }
        val quantityVariance = matchedLines.sumOf { it.quantityVarianceUnits }
        val priceVariance = matchedLines.fold(0L) { acc, row -> Math.addExact(acc, row.priceVarianceMinor) }
        val payable = matchedLines.fold(0L) { acc, row -> Math.addExact(acc, row.payableAmountMinor) }
        val hasVariance = quantityVariance > 0 || priceVariance > 0L
        val exceedsTolerance = quantityVariance > quantityToleranceUnits || priceVariance > priceToleranceMinor
        val normalizedReason = varianceReason?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedApprover = approvedBy?.trim()?.takeIf { it.isNotEmpty() }
        val status = when {
            !hasVariance -> PurchaseInvoiceMatchStatus.MATCHED
            !exceedsTolerance -> PurchaseInvoiceMatchStatus.WITHIN_TOLERANCE
            else -> {
                require(normalizedReason != null && normalizedApprover != null) {
                    "فرق المطابقة يتجاوز حدود السماح؛ يلزم سبب وصلاحية Override"
                }
                PurchaseInvoiceMatchStatus.OVERRIDDEN
            }
        }
        val match = PurchaseInvoiceMatchRecord(
            id = matchId,
            organizationId = organizationId,
            invoiceId = invoiceId,
            purchaseOrderId = order.id,
            status = status,
            quantityVarianceUnits = quantityVariance,
            priceVarianceMinor = priceVariance,
            quantityToleranceUnits = quantityToleranceUnits,
            priceToleranceMinor = priceToleranceMinor,
            invoiceAmountMinor = invoiceAmountMinor,
            payableAmountMinor = payable.coerceAtMost(invoiceAmountMinor),
            varianceReason = normalizedReason,
            approvedBy = normalizedApprover,
            approvedByName = approvedByName?.trim()?.takeIf { it.isNotEmpty() },
            matchedAt = matchedAt,
            writeId = writeId,
        )
        return PurchaseInvoiceMatchAssessment(match, matchedLines, hasVariance, exceedsTolerance)
    }

    override suspend fun persistSupplierInvoiceMatch(assessment: PurchaseInvoiceMatchAssessment) {
        val match = assessment.match
        require(match.organizationId.isNotBlank() && match.writeId.isNotBlank()) { "FAIL_ORG_SCOPE" }
        transaction.inTransaction {
            store.insertInvoiceMatch(assessment)
            unifiedOutboxWriter.enqueue(
                organizationId = match.organizationId,
                aggregateType = "PURCHASE_MATCH",
                aggregateId = match.id,
                operationType = "COMMAND",
                mutationId = match.writeId,
                createdAt = match.matchedAt,
                payload = mapOf("purchaseRequest" to mapOf(
                    "purchaseOrders" to emptyList<Map<String, Any?>>(),
                    "purchaseOrderLines" to emptyList<Map<String, Any?>>(),
                    "goodsReceipts" to emptyList<Map<String, Any?>>(),
                    "goodsReceiptLines" to emptyList<Map<String, Any?>>(),
                    "attachments" to emptyList<Map<String, Any?>>(),
                    "matches" to listOf(mapOf(
                        "id" to match.id, "organizationId" to match.organizationId,
                        "invoiceId" to match.invoiceId, "purchaseOrderId" to match.purchaseOrderId,
                        "status" to match.status.name, "quantityVarianceUnits" to match.quantityVarianceUnits,
                        "priceVarianceMinor" to match.priceVarianceMinor,
                        "quantityToleranceUnits" to match.quantityToleranceUnits,
                        "priceToleranceMinor" to match.priceToleranceMinor,
                        "invoiceAmountMinor" to match.invoiceAmountMinor,
                        "payableAmountMinor" to match.payableAmountMinor,
                        "varianceReason" to match.varianceReason, "approvedBy" to match.approvedBy,
                        "approvedByName" to match.approvedByName, "matchedAt" to match.matchedAt,
                        "writeId" to match.writeId,
                    )),
                    "matchLines" to assessment.lines.sortedBy { it.id }.map { line -> mapOf(
                        "id" to line.id, "matchId" to line.matchId, "invoiceItemId" to line.invoiceItemId,
                        "purchaseOrderLineId" to line.purchaseOrderLineId,
                        "orderedQuantity" to line.orderedQuantity, "acceptedQuantity" to line.acceptedQuantity,
                        "invoicedQuantity" to line.invoicedQuantity, "poUnitPriceMinor" to line.poUnitPriceMinor,
                        "invoiceUnitPriceMinor" to line.invoiceUnitPriceMinor,
                        "quantityVarianceUnits" to line.quantityVarianceUnits,
                        "priceVarianceMinor" to line.priceVarianceMinor,
                        "payableAmountMinor" to line.payableAmountMinor,
                    ) },
                    "allocations" to emptyList<Map<String, Any?>>(),
                    "paymentOverrides" to emptyList<Map<String, Any?>>(),
                )),
            )
        }
    }

    override suspend fun recordPaymentOverride(record: PurchasePaymentOverrideRecord) {
        require(record.organizationId.isNotBlank() && record.invoiceId.isNotBlank() && record.paymentRequestId.isNotBlank()) {
            "هوية استثناء الدفع غير مكتملة"
        }
        require(record.requestedAmountMinor > record.payableBeforeOverrideMinor) {
            "استثناء الدفع مطلوب فقط عند تجاوز القيمة القابلة للدفع"
        }
        require(record.reason.isNotBlank() && record.approvedBy.isNotBlank()) { "سبب واعتماد استثناء الدفع مطلوبان" }
        transaction.inTransaction {
            store.insertPaymentOverride(record)
            unifiedOutboxWriter.enqueue(
                organizationId = record.organizationId,
                aggregateType = "PURCHASE_PAYMENT_OVERRIDE",
                aggregateId = record.id,
                operationType = "COMMAND",
                mutationId = record.paymentRequestId,
                createdAt = record.createdAt,
                payload = mapOf("purchaseRequest" to mapOf(
                    "purchaseOrders" to emptyList<Map<String, Any?>>(),
                    "purchaseOrderLines" to emptyList<Map<String, Any?>>(),
                    "goodsReceipts" to emptyList<Map<String, Any?>>(),
                    "goodsReceiptLines" to emptyList<Map<String, Any?>>(),
                    "attachments" to emptyList<Map<String, Any?>>(),
                    "matches" to emptyList<Map<String, Any?>>(),
                    "matchLines" to emptyList<Map<String, Any?>>(),
                    "allocations" to emptyList<Map<String, Any?>>(),
                    "paymentOverrides" to listOf(mapOf(
                        "id" to record.id, "organizationId" to record.organizationId,
                        "invoiceId" to record.invoiceId, "paymentRequestId" to record.paymentRequestId,
                        "requestedAmountMinor" to record.requestedAmountMinor,
                        "payableBeforeOverrideMinor" to record.payableBeforeOverrideMinor,
                        "reason" to record.reason, "approvedBy" to record.approvedBy,
                        "approvedByName" to record.approvedByName, "createdAt" to record.createdAt,
                    )),
                )),
            )
        }
    }

    private suspend fun resolveLocalInventoryItem(orderLine: PurchaseOrderLineRecord): String {
        orderLine.inventoryItemId?.takeIf { it.isNotBlank() }?.let { id ->
            requireNotNull(stock.getItem(id)) { "صنف المخزون المرتبط بأمر الشراء غير موجود: ${orderLine.itemName}" }
            return id
        }
        store.getResolvedInventoryItemId(orderLine.id)?.let { return it }
        val sameName = stock.getAllItems().filter { it.name.trim().equals(orderLine.itemName.trim(), ignoreCase = true) }
        require(sameName.isEmpty()) {
            "الصنف ${orderLine.itemName} موجود في المخزون؛ يجب ربطه صراحةً في أمر الشراء"
        }
        val item = InvoiceStockItem(
            name = orderLine.itemName.trim(),
            buyPrice = Money.ofMinor(orderLine.unitPriceMinor).toLegacyDouble(),
            buyPriceMinor = orderLine.unitPriceMinor,
            quantity = 0,
        )
        stock.saveItem(item)
        return item.id
    }

    private suspend fun matchOrderLine(
        invoiceLine: PurchaseInvoiceCandidateLine,
        orderLines: List<PurchaseOrderLineRecord>,
        used: Set<String>,
    ): PurchaseOrderLineRecord {
        val explicitId = invoiceLine.inventoryItemId.trim()
        val candidates = if (explicitId.isNotEmpty()) {
            orderLines.filter { line ->
                if (line.id in used) return@filter false
                val linked = line.inventoryItemId ?: store.getResolvedInventoryItemId(line.id)
                linked == explicitId
            }
        } else {
            orderLines.filter {
                it.id !in used && it.itemName.trim().equals(invoiceLine.itemName.trim(), ignoreCase = true)
            }
        }
        require(candidates.size == 1) {
            "تعذر مطابقة بند الفاتورة ${invoiceLine.itemName} مع بند واحد في أمر الشراء"
        }
        return candidates.single()
    }

    private suspend fun supplierRecommendationWarnings(command: CreatePurchaseOrderCommand): List<String> =
        command.lines
            .mapNotNull { it.inventoryItemId?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .mapNotNull { itemId ->
                val recommendation = policy.recommendationForItem(itemId)
                val best = recommendation.bestSupplierId?.trim().orEmpty()
                if (best.isBlank() || best == command.supplierId) null
                else listOf(
                    "BETTER_SUPPLIER_HISTORY",
                    itemId,
                    best,
                    recommendation.scoreBps?.toString().orEmpty(),
                    recommendation.orderCount?.toString().orEmpty(),
                ).joinToString(":")
            }

    private fun CreatePurchaseOrderCommand.normalized(): CreatePurchaseOrderCommand = copy(
        organizationId = organizationId.trim(),
        orderNumber = orderNumber.trim().also { require(it.isNotEmpty()) { "رقم أمر الشراء مطلوب" } },
        supplierId = supplierId.trim().also { require(it.isNotEmpty()) { "المورد مطلوب" } },
        currencyCode = currencyCode.trim().uppercase().also { require(it.isNotEmpty()) { "عملة أمر الشراء مطلوبة" } },
        note = note.trim(),
        writeId = writeId.trim().also { require(it.isNotEmpty()) { "writeId مطلوب" } },
        createdAt = createdAt.also { require(it > 0L) { "createdAt مطلوب" } },
        promisedDeliveryAt = promisedDeliveryAt?.also {
            require(it >= createdAt) { "موعد التسليم الموعود لا يمكن أن يسبق إنشاء أمر الشراء" }
        },
        lines = lines.also { require(it.isNotEmpty()) { "أمر الشراء يحتاج بنداً واحداً على الأقل" } }.map { line ->
            line.copy(
                inventoryItemId = line.inventoryItemId?.trim()?.takeIf(String::isNotEmpty),
                itemName = line.itemName.trim().also { require(it.isNotEmpty()) { "اسم بند أمر الشراء مطلوب" } },
                orderedQuantity = line.orderedQuantity.also { require(it > 0) { "كمية أمر الشراء يجب أن تكون موجبة" } },
                unitPrice = line.unitPrice.also { require(it.amountMinor >= 0L) { "سعر أمر الشراء غير صالح" } },
            )
        },
    )

    private fun RecordGoodsReceiptCommand.normalized(): RecordGoodsReceiptCommand = copy(
        organizationId = organizationId.trim(),
        purchaseOrderId = purchaseOrderId.trim().also { require(it.isNotEmpty()) { "purchaseOrderId مطلوب" } },
        receiptNumber = receiptNumber.trim().also { require(it.isNotEmpty()) { "رقم GRN مطلوب" } },
        note = note.trim(),
        writeId = writeId.trim().also { require(it.isNotEmpty()) { "writeId مطلوب" } },
        receivedAt = receivedAt.also { require(it > 0L) { "receivedAt مطلوب" } },
        lines = lines.also { require(it.isNotEmpty()) { "GRN يحتاج بنداً واحداً على الأقل" } }.map { line ->
            line.copy(
                purchaseOrderLineId = line.purchaseOrderLineId.trim().also { require(it.isNotEmpty()) { "بند أمر الشراء مطلوب" } },
                receivedQuantity = line.receivedQuantity.also { require(it > 0) { "الكمية المستلمة يجب أن تكون موجبة" } },
                acceptedQuantity = line.acceptedQuantity.also { require(it >= 0) { "الكمية المقبولة غير صالحة" } },
                rejectedQuantity = line.rejectedQuantity.also { require(it >= 0) { "الكمية المرفوضة غير صالحة" } },
                unitCostMinor = line.unitCostMinor?.also { require(it >= 0L) { "تكلفة الاستلام غير صالحة" } },
            ).also { require(it.acceptedQuantity + it.rejectedQuantity == it.receivedQuantity) { "المقبول + المرفوض يجب أن يساوي المستلم" } }
        },
    )

    private fun absExact(a: Long, b: Long): Long {
        val delta = Math.subtractExact(a, b)
        return if (delta == Long.MIN_VALUE) throw ArithmeticException("price variance overflow") else abs(delta)
    }

    private fun stableId(prefix: String, a: String, b: String): String =
        UUID.nameUUIDFromBytes("$prefix|$a|$b".toByteArray(StandardCharsets.UTF_8)).toString()
}
