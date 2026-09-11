package com.verto.app.feature.invoice.application

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.invoice.domain.model.InvoiceAuthorizationException
import com.verto.app.feature.invoice.domain.model.InvoiceCashMovementType
import com.verto.app.feature.invoice.domain.model.InvoiceCategory
import com.verto.app.feature.invoice.domain.model.InvoiceLifecycleStatus
import com.verto.app.feature.invoice.domain.model.InvoicePayment
import com.verto.app.feature.invoice.domain.model.InvoicePaymentAllocation
import com.verto.app.feature.invoice.domain.model.InvoiceStatus
import com.verto.app.feature.invoice.domain.model.InvoiceVoidPaymentDisposition
import com.verto.app.feature.invoice.domain.model.InvoiceVoidRequest
import com.verto.app.feature.invoice.domain.model.InvoiceWriteIdentity
import com.verto.app.feature.invoice.domain.model.InvoiceWriteOperation
import com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand
import com.verto.app.feature.invoice.domain.model.RealizedFxEvent
import com.verto.app.feature.invoice.domain.port.InvoiceAuthorizationPort
import com.verto.app.feature.invoice.domain.port.InvoiceAtomicPersistenceCoordinator
import com.verto.app.feature.invoice.domain.port.NoOpInvoiceAtomicPersistenceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceCashPort
import com.verto.app.feature.invoice.domain.port.InvoiceStockPort
import com.verto.app.feature.invoice.domain.port.InvoiceStorePort
import com.verto.app.feature.invoice.domain.port.InvoiceTransactionPort
import com.verto.app.money.Money
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

class InvoiceVoidCoordinator @Inject constructor(
    private val transactionPort: InvoiceTransactionPort,
    private val store: InvoiceStorePort,
    private val stock: InvoiceStockPort,
    private val cash: InvoiceCashPort,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val authorization: InvoiceAuthorizationPort,
    private val atomicPersistenceCoordinator: InvoiceAtomicPersistenceCoordinator =
        NoOpInvoiceAtomicPersistenceCoordinator,
) {
    suspend fun void(request: InvoiceVoidRequest) {
        val invoiceId = request.invoiceId.trim()
        require(invoiceId.isNotEmpty()) { "معرف الفاتورة مطلوب" }
        val initial = store.getInvoiceById(invoiceId) ?: return
        if (initial.lifecycleStatus == InvoiceLifecycleStatus.VOID || initial.voided) return
        require(initial.lifecycleStatus == InvoiceLifecycleStatus.POSTED) {
            "يمكن إلغاء الفاتورة المرحلة فقط"
        }
        require(initial.shipmentId.isNullOrBlank()) {
            "الفاتورة مرتبطة بشحنة/استلام؛ اعكس السلسلة التابعة أولاً قبل الإلغاء"
        }

        val reason = request.reason.trim()
        require(reason.length >= 3) { "سبب الإلغاء إلزامي" }
        if (!authorization.canVoid(initial.category)) {
            val action = if (initial.category == InvoiceCategory.SALE) "sales_void" else "purchases_void"
            runCatching { auditLogger.logPermissionDenied(action, "invoiceId=$invoiceId", sessionReader) }
            throw InvoiceAuthorizationException("لا تملك صلاحية إلغاء الفواتير")
        }

        val session = sessionReader.snapshot()
        val organizationId = initial.organizationId.trim().ifBlank { session.organization.id.trim() }
        require(organizationId.isNotBlank()) { "تعذر تحديد الشركة لإلغاء الفاتورة" }
        val requestedAt = request.requestedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val requestId = request.requestId.trim().ifBlank {
            "void:$invoiceId:v${initial.lifecycleVersion}"
        }
        val expectedVersion = initial.lifecycleVersion

        transactionPort.inTransaction {
            val current = store.getInvoiceById(invoiceId) ?: error("اختفت الفاتورة أثناء الإلغاء")
            if (current.lifecycleStatus == InvoiceLifecycleStatus.VOID || current.voided) return@inTransaction
            check(current.lifecycleStatus == InvoiceLifecycleStatus.POSTED) { "حالة الفاتورة تغيرت؛ أعد تحميلها" }
            check(current.lifecycleVersion == expectedVersion) {
                "CONFLICT: تم تعديل الفاتورة من عملية أخرى؛ أعد تحميلها ثم حاول مجددًا"
            }
            check(current.shipmentId.isNullOrBlank()) {
                "الفاتورة أصبحت مرتبطة بشحنة/استلام؛ لا يمكن إلغاؤها مباشرة"
            }

            val claim = store.claimWrite(
                InvoiceWriteIdentity(
                    organizationId = organizationId,
                    operation = InvoiceWriteOperation.VOID,
                    writeId = requestId,
                    invoiceId = invoiceId,
                    sourceVersion = current.lifecycleVersion,
                )
            )
            if (!claim.claimed) {
                val latest = store.getInvoiceById(invoiceId)
                check(latest?.lifecycleStatus == InvoiceLifecycleStatus.VOID || latest?.voided == true) {
                    "writeId الإلغاء مستخدم دون اكتمال حالة VOID"
                }
                return@inTransaction
            }

            val historicalPayments = store.getPayments(invoiceId)
            val originals = historicalPayments
                .filter { it.amountMinor > 0L && it.reversedPaymentId == null }
                .filter { store.getReversalForPayment(it.id) == null }
            if (current.status == InvoiceStatus.CLOSED_CASH && historicalPayments.isEmpty()) {
                error("تعذر إلغاء فاتورة كاش بلا سجل دفعة تاريخي موثوق")
            }
            if (originals.isNotEmpty()) {
                check(request.paymentDisposition == InvoiceVoidPaymentDisposition.REFUND_TO_CASH) {
                    "للفاتورة دفعات؛ اختر صراحةً رد/عكس الدفعات قبل الإلغاء"
                }
            }

            val allocations = store.getPaymentAllocations(invoiceId).groupBy { it.paymentId }
            val fxEvents = store.getRealizedFxEvents(invoiceId).groupBy { it.paymentId }

            stock.reverseMovements(invoiceId, requestId)
            originals.forEach { original ->
                val reversal = createPaymentReversal(current, original, requestId, requestedAt)
                store.addPayment(reversal)
                allocations[original.id].orEmpty().forEach { allocation ->
                    store.addPaymentAllocation(
                        InvoicePaymentAllocation(
                            id = stableId("$requestId:allocation:${allocation.id}"),
                            paymentId = reversal.id,
                            invoiceId = invoiceId,
                            allocatedTransactionAmountMinor = Math.negateExact(allocation.allocatedTransactionAmountMinor),
                            historicalFunctionalAmountMinor = Math.negateExact(allocation.historicalFunctionalAmountMinor),
                            realizedFxDifferenceMinor = Math.negateExact(allocation.realizedFxDifferenceMinor),
                            createdAt = requestedAt,
                            sourceType = "INVOICE_VOID",
                            sourceId = invoiceId,
                            sourceVersion = current.lifecycleVersion + 1,
                            writeId = requestId,
                        )
                    )
                }
                fxEvents[original.id].orEmpty().forEach { event ->
                    store.addRealizedFxEvent(
                        RealizedFxEvent(
                            id = stableId("$requestId:fx:${event.id}"),
                            paymentId = reversal.id,
                            invoiceId = invoiceId,
                            functionalCurrencyCode = event.functionalCurrencyCode,
                            historicalFunctionalAmountMinor = Math.negateExact(event.historicalFunctionalAmountMinor),
                            functionalCashAmountMinor = Math.negateExact(event.functionalCashAmountMinor),
                            differenceMinor = Math.negateExact(event.differenceMinor),
                            result = invertFxResult(event.result),
                            occurredAt = requestedAt,
                            writeId = requestId,
                        )
                    )
                }
                reverseHistoricalCash(current, original, requestId)
                auditLogger.log(
                    action = AuditAction.INSERT,
                    table = AuditTable.PAYMENT,
                    recordId = reversal.id,
                    summary = "عكس دفعة ${original.id} بسبب إلغاء فاتورة #${current.invoiceNumber}",
                    oldValue = original.id,
                    newValue = reversal.id,
                    employeeId = session.user.id,
                    employeeName = session.user.name,
                    canUndo = false,
                    sourceType = "INVOICE_VOID",
                    sourceId = invoiceId,
                    sourceVersion = current.lifecycleVersion + 1,
                    writeId = requestId,
                )
            }

            check(
                store.markVoided(
                    invoiceId = invoiceId,
                    expectedVersion = current.lifecycleVersion,
                    voidedAt = requestedAt,
                    reason = reason,
                    writeId = requestId,
                )
            ) { "CONFLICT: تغير إصدار الفاتورة أثناء الإلغاء" }

            val voided = current.copy(
                lifecycleStatus = InvoiceLifecycleStatus.VOID,
                lifecycleVersion = current.lifecycleVersion + 1,
                voidedAt = requestedAt,
                voidReason = reason,
                voidWriteId = requestId,
                voided = true,
            )
            atomicPersistenceCoordinator.persistVoid(
                PersistInvoiceVoidIntegrationCommand(
                    writeId = requestId,
                    organizationId = organizationId,
                    invoiceId = invoiceId,
                    clientId = current.clientId,
                    occurredAt = requestedAt,
                )
            ).getOrThrow()
            auditLogger.logUpdate(
                table = AuditTable.INVOICE,
                recordId = invoiceId,
                summary = "إلغاء موثق لفاتورة #${current.invoiceNumber} — السبب: $reason",
                oldValue = current.toAuditJson(),
                newValue = voided.toAuditJson(),
                employeeId = session.user.id,
                employeeName = session.user.name,
                sourceType = "INVOICE_VOID",
                sourceId = invoiceId,
                sourceVersion = voided.lifecycleVersion,
                writeId = requestId,
            )
        }
    }

    private fun createPaymentReversal(
        invoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
        original: InvoicePayment,
        requestId: String,
        requestedAt: Long,
    ): InvoicePayment = original.copy(
        id = stableId("$requestId:payment:${original.id}"),
        amount = Money.ofMinor(Math.negateExact(original.amountMinor)).toLegacyDouble(),
        amountMinor = Math.negateExact(original.amountMinor),
        supplierAmountMinor = Math.negateExact(original.supplierAmountMinor),
        functionalCashAmountMinor = Math.negateExact(original.functionalCashAmountMinor),
        historicalFunctionalAmountMinor = Math.negateExact(original.historicalFunctionalAmountMinor),
        realizedFxDifferenceMinor = Math.negateExact(original.realizedFxDifferenceMinor),
        note = "عكس بسبب إلغاء فاتورة #${invoice.invoiceNumber} — ${original.note}".trimEnd(),
        paidAt = requestedAt,
        sourceType = "INVOICE_VOID",
        sourceId = invoice.id,
        sourceVersion = invoice.lifecycleVersion + 1,
        writeId = requestId,
        reversedPaymentId = original.id,
    )

    private suspend fun reverseHistoricalCash(
        invoice: com.verto.app.feature.invoice.domain.model.InvoiceRecord,
        original: InvoicePayment,
        requestId: String,
    ) {
        val actualFunctionalMinor = when {
            original.functionalCashAmountMinor > 0L -> original.functionalCashAmountMinor
            invoice.transactionCurrencyCode.isBlank() ||
                invoice.functionalCurrencyCode.isBlank() ||
                invoice.transactionCurrencyCode == invoice.functionalCurrencyCode -> original.amountMinor
            else -> error("لا يمكن إلغاء دفعة دولية قديمة دون قيمة الصندوق الوظيفية التاريخية")
        }
        require(actualFunctionalMinor > 0L) { "قيمة الصندوق التاريخية للدفعة غير صالحة" }
        val movementType = when {
            invoice.status == InvoiceStatus.CLOSED_CASH && invoice.category == InvoiceCategory.SALE ->
                InvoiceCashMovementType.SALE_CASH
            invoice.status == InvoiceStatus.CLOSED_CASH -> InvoiceCashMovementType.PURCHASE_CASH
            invoice.category == InvoiceCategory.SALE -> InvoiceCashMovementType.PAYMENT_RECEIVED
            else -> InvoiceCashMovementType.PAYMENT_MADE
        }
        cash.reverseMovement(
            type = movementType,
            amount = Money.ofMinor(actualFunctionalMinor).toLegacyDouble(),
            referenceId = invoice.id,
            note = "عكس دفعة ${original.id} عند إلغاء فاتورة #${invoice.invoiceNumber}",
            sourceWriteId = requestId,
        )
    }

    private fun stableId(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun invertFxResult(result: String): String = when (result.uppercase()) {
        "GAIN" -> "LOSS"
        "LOSS" -> "GAIN"
        else -> result
    }
}
