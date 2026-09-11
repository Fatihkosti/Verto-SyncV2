package com.verto.app.feature.invoice.application

import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.invoice.domain.model.CreateInvoiceReturnCommand
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.LegacyCurrencyStatus
import com.verto.app.feature.invoice.domain.model.InvoiceLine
import com.verto.app.feature.invoice.domain.model.InvoicePayment
import com.verto.app.feature.invoice.domain.model.PurchaseScope
import com.verto.app.feature.invoice.domain.model.InvoiceReturnAggregate
import com.verto.app.feature.invoice.domain.model.InvoiceReturnDocumentType
import com.verto.app.feature.invoice.domain.model.InvoiceReturnLineRecord
import com.verto.app.feature.invoice.domain.model.InvoiceReturnPaymentAllocationRecord
import com.verto.app.feature.invoice.domain.model.InvoiceReturnRecord
import com.verto.app.feature.invoice.domain.model.InvoiceReturnResult
import com.verto.app.feature.invoice.domain.model.InvoiceReturnSettlementMode
import com.verto.app.feature.invoice.domain.port.InvoiceReturnAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnCreditPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnOutboxPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceReturnStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

/** F252 owner for Sales Return/Credit Note and Purchase Return/Debit Note. */
class InvoiceReturnCoordinator @Inject constructor(
    private val transaction: InvoiceTransactionPort,
    private val invoices: InvoiceStorePort,
    private val returns: InvoiceReturnStorePort,
    private val stock: InvoiceReturnStockPort,
    private val credits: InvoiceReturnCreditPort,
    private val cash: InvoiceReturnCashPort,
    private val authorization: InvoiceReturnAuthorizationPort,
    private val outbox: InvoiceReturnOutboxPort,
    private val audit: WriteAuditPort,
    private val sessionReader: SessionReader,
) {
    suspend fun create(command: CreateInvoiceReturnCommand): Result<InvoiceReturnResult> = runCatching {
        val normalized = command.normalized()
        val session = sessionReader.snapshot()
        val activeOrg = session.organization.id.trim()
        require(activeOrg.isNotEmpty() && activeOrg == normalized.organizationId) {
            "return organization does not match active session"
        }

        val original = requireNotNull(invoices.getInvoiceById(normalized.originalInvoiceId)) {
            "الفاتورة الأصلية غير موجودة"
        }
        require(original.organizationId.isBlank() || original.organizationId == normalized.organizationId) {
            "الفاتورة الأصلية تتبع منشأة أخرى"
        }
        require(original.lifecycleStatus == InvoiceLifecycleStatus.POSTED) {
            "يمكن إنشاء مرتجع لفاتورة مرحلة فقط"
        }
        require(original.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN) {
            "حقيقة عملة الفاتورة الأصلية غير موثوقة؛ راجع بيانات العملة قبل المرتجع"
        }
        require(original.transactionCurrencyCode.isNotBlank() && original.functionalCurrencyCode.isNotBlank()) {
            "الفاتورة الأصلية لا تحتوي رموز عملة تاريخية مكتملة"
        }
        require(original.transactionAmountMinor > 0L && original.functionalAmountAtRecognitionMinor > 0L) {
            "الفاتورة الأصلية لا تحتوي مبالغ عملة تاريخية مكتملة"
        }
        require(authorization.canCreate(original.category)) { "لا توجد صلاحية لإنشاء المرتجع" }

        val existing = returns.getByWriteId(normalized.organizationId, normalized.writeId)
        if (existing != null) {
            require(existing.originalInvoiceId == original.id) { "writeId مستخدم لمرتجع فاتورة أخرى" }
            return@runCatching existing.toResult(duplicate = true)
        }

        val originalLines = invoices.getInvoiceItems(original.id)
        val lineById = originalLines.associateBy { it.id }
        val requestedByLine = linkedMapOf<String, Int>()
        normalized.lines.forEach { line ->
            requestedByLine[line.originalInvoiceItemId] = Math.addExact(
                requestedByLine[line.originalInvoiceItemId] ?: 0,
                line.quantity,
            )
        }
        require(requestedByLine.isNotEmpty()) { "اختر بنداً واحداً على الأقل للمرتجع" }

        val prepared = requestedByLine.entries.map { (lineId, quantity) ->
            val line = requireNotNull(lineById[lineId]) { "بند المرتجع لا يتبع الفاتورة الأصلية" }
            require(quantity > 0) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
            if (original.category == InvoiceCategory.SALE && line.inventoryItemId.isNotBlank()) {
                require(line.costSnapshotStatus == "KNOWN") {
                    "تكلفة البيع التاريخية للبند ${line.itemName} غير موثوقة؛ لا يمكن عكس COGS بالتخمين"
                }
            }
            val alreadyReturned = returns.getReturnedQuantity(lineId)
            val remaining = Math.subtractExact(line.quantity, alreadyReturned)
            require(quantity <= remaining) { "كمية المرتجع تتجاوز الكمية القابلة للإرجاع للبند ${line.itemName}" }
            PreparedLine(line, quantity, alreadyReturned)
        }

        val returnId = stableId("return", normalized.organizationId, normalized.writeId)
        val documentType = when (original.category) {
            InvoiceCategory.SALE -> InvoiceReturnDocumentType.SALES_RETURN_CREDIT_NOTE
            InvoiceCategory.PURCHASE -> InvoiceReturnDocumentType.PURCHASE_RETURN_DEBIT_NOTE
        }
        val totalTransactionMinor = prepared.fold(0L) { acc, p ->
            Math.addExact(acc, Math.multiplyExact(p.unitTransactionMinor(original.category), p.quantity.toLong()))
        }
        require(totalTransactionMinor > 0L) { "قيمة المرتجع يجب أن تكون أكبر من صفر" }

        val invoiceTransactionMinor = original.transactionAmountMinor
        val invoiceFunctionalMinor = original.functionalAmountAtRecognitionMinor
        val totalFunctionalMinor = prorate(totalTransactionMinor, invoiceTransactionMinor, invoiceFunctionalMinor)
        require(totalFunctionalMinor > 0L) { "قيمة المرتجع الوظيفية غير صالحة" }

        val returnLines = buildReturnLines(
            returnId = returnId,
            category = original.category,
            prepared = prepared,
            invoiceTransactionMinor = invoiceTransactionMinor,
            invoiceFunctionalMinor = invoiceFunctionalMinor,
            expectedFunctionalTotal = totalFunctionalMinor,
        )
        val payments = effectivePayments(invoices.getPayments(original.id))
        val allocations = allocatePayments(returnId, payments, totalFunctionalMinor, normalized.occurredAt)
        if (normalized.settlementMode == InvoiceReturnSettlementMode.CASH_REFUND) {
            val allocated = allocations.fold(0L) { acc, row -> Math.addExact(acc, row.allocatedFunctionalAmountMinor) }
            require(allocated == totalFunctionalMinor) {
                "لا يمكن رد نقد أكبر من المبلغ المسدد فعلياً على الفاتورة"
            }
        }

        val actorId = session.user.id.trim()
        val actorName = session.user.name.trim()
        val recordedAt = System.currentTimeMillis()
        val aggregate = InvoiceReturnAggregate(
            document = InvoiceReturnRecord(
                id = returnId,
                organizationId = normalized.organizationId,
                originalInvoiceId = original.id,
                clientId = original.clientId,
                documentType = documentType,
                settlementMode = normalized.settlementMode,
                transactionCurrencyCode = original.transactionCurrencyCode,
                functionalCurrencyCode = original.functionalCurrencyCode,
                transactionAmountMinor = totalTransactionMinor,
                functionalAmountMinor = totalFunctionalMinor,
                reason = normalized.reason,
                occurredAt = normalized.occurredAt,
                recordedAt = recordedAt,
                createdBy = actorId,
                createdByName = actorName,
                writeId = normalized.writeId,
            ),
            lines = returnLines,
            paymentAllocations = allocations,
        )

        transaction.inTransaction {
            // Resolve same-write retries before quantity checks so a concurrent retry is idempotent, not a false over-return.
            val duplicateInside = returns.getByWriteId(normalized.organizationId, normalized.writeId)
            if (duplicateInside != null) {
                require(duplicateInside.originalInvoiceId == original.id) { "writeId مستخدم لمرتجع فاتورة أخرى" }
                return@inTransaction duplicateInside.toResult(duplicate = true)
            }
            // Recheck every quantity inside the owner transaction. The DB trigger is the final concurrent guard.
            prepared.forEach { p ->
                val nowReturned = returns.getReturnedQuantity(p.line.id)
                require(Math.addExact(nowReturned, p.quantity) <= p.line.quantity) {
                    "كمية المرتجع تغيرت بواسطة عملية متزامنة؛ أعد المحاولة"
                }
            }

            returns.insertAggregate(aggregate)
            writeInventoryEffects(
                aggregate = aggregate,
                original = original,
                originalLines = originalLines,
                prepared = prepared,
                actorId = actorId,
                actorName = actorName,
            )
            writePartySettlement(aggregate)
            outbox.append(aggregate)
            audit.logInsert(
                table = AuditTable.INVOICE,
                recordId = returnId,
                summary = when (documentType) {
                    InvoiceReturnDocumentType.SALES_RETURN_CREDIT_NOTE -> "مرتجع بيع / إشعار دائن"
                    InvoiceReturnDocumentType.PURCHASE_RETURN_DEBIT_NOTE -> "مرتجع شراء / إشعار مدين"
                },
                newValue = "originalInvoice=${original.id};transactionMinor=$totalTransactionMinor;functionalMinor=$totalFunctionalMinor;reason=${normalized.reason}",
                employeeId = actorId,
                employeeName = actorName,
                sourceType = "INVOICE_RETURN",
                sourceId = original.id,
                writeId = normalized.writeId,
            )
            aggregate.document.toResult(duplicate = false)
        }
    }

    private suspend fun writeInventoryEffects(
        aggregate: InvoiceReturnAggregate,
        original: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
        originalLines: List<InvoiceLine>,
        prepared: List<PreparedLine>,
        actorId: String,
        actorName: String,
    ) {
        val byLine = aggregate.lines.associateBy { it.originalInvoiceItemId }
        when (original.category) {
            InvoiceCategory.SALE -> prepared.forEach { p ->
                val line = byLine.getValue(p.line.id)
                if (line.inventoryItemId.isNotBlank()) {
                    stock.restoreSalesReturn(
                        itemId = line.inventoryItemId,
                        quantity = line.quantity,
                        returnId = aggregate.document.id,
                        returnLineId = line.id,
                        clientId = original.clientId,
                        historicalUnitCostMinor = line.unitCostAtSaleMinor,
                        occurredAt = aggregate.document.occurredAt,
                        writeId = aggregate.document.writeId,
                    )
                }
            }
            InvoiceCategory.PURCHASE -> {
                val returnLinesByInventory = aggregate.lines.filter { it.inventoryItemId.isNotBlank() }
                    .groupBy { it.inventoryItemId }
                val requestedByInventory = returnLinesByInventory
                    .mapValues { (_, rows) -> rows.fold(0) { acc, row -> Math.addExact(acc, row.quantity) } }
                val finalReturnLineByInventory = returnLinesByInventory.mapValues { (_, rows) -> rows.last().id }
                prepared.forEach { p ->
                    val line = byLine.getValue(p.line.id)
                    if (line.inventoryItemId.isBlank()) return@forEach
                    val originalQtyForItem = originalLines.filter { it.inventoryItemId == line.inventoryItemId }
                        .fold(0) { acc, row -> Math.addExact(acc, row.quantity) }
                    val returnedBefore = returns.getReturnedQuantityForInvoiceInventoryItem(
                        original.id,
                        line.inventoryItemId,
                    ) - requestedByInventory.getValue(line.inventoryItemId)
                    val returningNow = requestedByInventory.getValue(line.inventoryItemId)
                    val sourceStillValid = Math.addExact(returnedBefore.coerceAtLeast(0), returningNow) < originalQtyForItem
                    stock.deductPurchaseReturn(
                        itemId = line.inventoryItemId,
                        quantity = line.quantity,
                        returnId = aggregate.document.id,
                        returnLineId = line.id,
                        supplierId = original.clientId,
                        originalInvoiceId = original.id,
                        originalInvoiceItemId = line.originalInvoiceItemId,
                        internationalPurchase = original.purchaseScope == PurchaseScope.INTERNATIONAL,
                        originalUnitCostMinor = line.originalPurchaseUnitCostMinor,
                        sourceStillValidForItem = sourceStillValid || finalReturnLineByInventory.getValue(line.inventoryItemId) != line.id,
                        occurredAt = aggregate.document.occurredAt,
                        writeId = aggregate.document.writeId,
                        actorId = actorId,
                        actorName = actorName,
                    )
                }
            }
        }
    }

    private suspend fun writePartySettlement(aggregate: InvoiceReturnAggregate) {
        val d = aggregate.document
        // Existing client-credit convention: + party owes us, - we owe party.
        val noteSigned = when (d.documentType) {
            InvoiceReturnDocumentType.SALES_RETURN_CREDIT_NOTE -> -d.functionalAmountMinor
            InvoiceReturnDocumentType.PURCHASE_RETURN_DEBIT_NOTE -> d.functionalAmountMinor
        }
        credits.record(
            id = stableId("return-credit", d.organizationId, d.writeId),
            clientId = d.clientId,
            signedFunctionalAmountMinor = noteSigned,
            note = "INVOICE_RETURN_NOTE:${d.id}",
            sourceReference = d.id,
            occurredAt = d.occurredAt,
            actorId = d.createdBy,
            actorName = d.createdByName,
        )
        if (d.settlementMode == InvoiceReturnSettlementMode.CASH_REFUND) {
            val cashNote = "تسوية نقدية للمرتجع ${d.id}"
            when (d.documentType) {
                InvoiceReturnDocumentType.SALES_RETURN_CREDIT_NOTE -> cash.cashOut(
                    amountMinor = d.functionalAmountMinor,
                    returnId = d.id,
                    writeId = d.writeId,
                    note = cashNote,
                )
                InvoiceReturnDocumentType.PURCHASE_RETURN_DEBIT_NOTE -> cash.cashIn(
                    amountMinor = d.functionalAmountMinor,
                    returnId = d.id,
                    writeId = d.writeId,
                    note = cashNote,
                )
            }
            // Consumes the note balance; the document remains as the immutable reason for the cash movement.
            credits.record(
                id = stableId("return-cash-settlement", d.organizationId, d.writeId),
                clientId = d.clientId,
                signedFunctionalAmountMinor = Math.negateExact(noteSigned),
                note = "INVOICE_RETURN_CASH_SETTLEMENT:${d.id}",
                sourceReference = d.id,
                occurredAt = d.occurredAt,
                actorId = d.createdBy,
                actorName = d.createdByName,
            )
        }
    }

    private suspend fun effectivePayments(rows: List<InvoicePayment>): List<InvoicePayment> {
        val originals = rows.filter {
            it.reversedPaymentId == null && it.amountMinor > 0L && it.legacyCurrencyStatus == LegacyCurrencyStatus.KNOWN
        }
        val result = ArrayList<InvoicePayment>(originals.size)
        for (payment in originals.sortedWith(compareBy<InvoicePayment> { it.paidAt }.thenBy { it.id })) {
            if (invoices.getReversalForPayment(payment.id) == null) result += payment
        }
        return result
    }

    private suspend fun allocatePayments(
        returnId: String,
        payments: List<InvoicePayment>,
        targetFunctionalMinor: Long,
        occurredAt: Long,
    ): List<InvoiceReturnPaymentAllocationRecord> {
        var remaining = targetFunctionalMinor
        val rows = mutableListOf<InvoiceReturnPaymentAllocationRecord>()
        for (payment in payments) {
            if (remaining <= 0L) break
            val paidFunctional = when {
                payment.functionalCashAmountMinor > 0L -> payment.functionalCashAmountMinor
                payment.historicalFunctionalAmountMinor > 0L -> payment.historicalFunctionalAmountMinor
                else -> payment.amountMinor
            }
            val prior = returns.getAllocatedFunctionalForPayment(payment.id)
            val available = Math.subtractExact(paidFunctional, prior).coerceAtLeast(0L)
            if (available == 0L) continue
            val allocated = minOf(remaining, available)
            rows += InvoiceReturnPaymentAllocationRecord(
                id = stableId("return-allocation", returnId, payment.id),
                returnId = returnId,
                paymentId = payment.id,
                allocatedFunctionalAmountMinor = allocated,
                createdAt = occurredAt,
            )
            remaining = Math.subtractExact(remaining, allocated)
        }
        return rows
    }

    private fun buildReturnLines(
        returnId: String,
        category: InvoiceCategory,
        prepared: List<PreparedLine>,
        invoiceTransactionMinor: Long,
        invoiceFunctionalMinor: Long,
        expectedFunctionalTotal: Long,
    ): List<InvoiceReturnLineRecord> {
        var assignedFunctional = 0L
        return prepared.mapIndexed { index, p ->
            val unitTransaction = p.unitTransactionMinor(category)
            val transaction = Math.multiplyExact(unitTransaction, p.quantity.toLong())
            val functional = if (index == prepared.lastIndex) {
                Math.subtractExact(expectedFunctionalTotal, assignedFunctional)
            } else {
                prorate(transaction, invoiceTransactionMinor, invoiceFunctionalMinor).also {
                    assignedFunctional = Math.addExact(assignedFunctional, it)
                }
            }
            require(functional > 0L) { "قيمة بند المرتجع الوظيفية غير صالحة" }
            val unitFunctional = BigDecimal.valueOf(functional)
                .divide(BigDecimal.valueOf(p.quantity.toLong()), 0, RoundingMode.HALF_UP)
                .longValueExact()
            InvoiceReturnLineRecord(
                id = stableId("return-line", returnId, p.line.id),
                returnId = returnId,
                originalInvoiceItemId = p.line.id,
                inventoryItemId = p.line.inventoryItemId,
                itemNameSnapshot = p.line.itemName,
                quantity = p.quantity,
                unitTransactionAmountMinor = unitTransaction,
                transactionAmountMinor = transaction,
                unitFunctionalAmountMinor = unitFunctional,
                functionalAmountMinor = functional,
                unitCostAtSaleMinor = if (category == InvoiceCategory.SALE) p.line.unitCostAtSaleMinor else 0L,
                historicalCostAmountMinor = if (category == InvoiceCategory.SALE) {
                    Math.multiplyExact(p.line.unitCostAtSaleMinor, p.quantity.toLong())
                } else 0L,
                originalPurchaseUnitCostMinor = if (category == InvoiceCategory.PURCHASE) p.line.buyPriceMinor else 0L,
            )
        }
    }

    private fun prorate(part: Long, whole: Long, total: Long): Long {
        require(part >= 0L && whole > 0L && total >= 0L)
        return BigDecimal.valueOf(part)
            .multiply(BigDecimal.valueOf(total))
            .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun CreateInvoiceReturnCommand.normalized(): CreateInvoiceReturnCommand = copy(
        originalInvoiceId = originalInvoiceId.trim().also { require(it.isNotEmpty()) { "originalInvoiceId is required" } },
        organizationId = organizationId.trim().also { require(it.isNotEmpty()) { "organizationId is required" } },
        writeId = writeId.trim().also { require(it.isNotEmpty()) { "writeId is required" } },
        reason = reason.trim().also { require(it.isNotEmpty()) { "سبب المرتجع مطلوب" } },
        occurredAt = occurredAt.also { require(it > 0L) { "occurredAt is required" } },
        lines = lines.map { line ->
            line.copy(originalInvoiceItemId = line.originalInvoiceItemId.trim().also {
                require(it.isNotEmpty()) { "originalInvoiceItemId is required" }
            })
        },
    )

    private data class PreparedLine(
        val line: InvoiceLine,
        val quantity: Int,
        val alreadyReturned: Int,
    ) {
        fun unitTransactionMinor(category: InvoiceCategory): Long = when (category) {
            InvoiceCategory.SALE -> line.unitSellPriceMinor.takeIf { it > 0L } ?: line.sellPriceMinor
            InvoiceCategory.PURCHASE -> line.buyPriceMinor
        }.also { require(it > 0L) { "سعر البند الأصلي غير صالح للمرتجع" } }
    }

    private fun InvoiceReturnRecord.toResult(duplicate: Boolean) = InvoiceReturnResult(
        returnId = id,
        duplicate = duplicate,
        documentType = documentType,
        transactionAmountMinor = transactionAmountMinor,
        functionalAmountMinor = functionalAmountMinor,
    )

    private fun stableId(prefix: String, a: String, b: String): String =
        UUID.nameUUIDFromBytes("$prefix|$a|$b".toByteArray(StandardCharsets.UTF_8)).toString()
}
