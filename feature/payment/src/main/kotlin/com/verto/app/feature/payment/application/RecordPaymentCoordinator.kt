package com.verto.app.feature.payment.application

import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.BusinessRuleFailureException
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.payment.domain.model.PaymentAllocationRecord
import com.verto.app.feature.payment.domain.model.PaymentIntegrationEventKind
import com.verto.app.feature.payment.domain.model.PaymentInvoiceCategory
import com.verto.app.feature.payment.domain.model.PaymentOperationResult
import com.verto.app.feature.payment.domain.model.PaymentRecord
import com.verto.app.feature.payment.domain.model.PersistPaymentIntegrationCommand
import com.verto.app.feature.payment.domain.model.RealizedFxEventRecord
import com.verto.app.feature.payment.domain.model.RecordPaymentCommand
import com.verto.app.feature.payment.domain.port.NoOpPaymentAtomicPersistenceCoordinator
import com.verto.app.feature.payment.domain.port.NoOpPurchaseReceiptPaymentGuardPort
import com.verto.app.feature.payment.domain.port.PaymentAtomicPersistenceCoordinator
import com.verto.app.feature.payment.domain.port.PaymentAuthorizationPort
import com.verto.app.feature.payment.domain.port.PaymentCashPort
import com.verto.app.feature.payment.domain.port.PaymentRemotePort
import com.verto.app.feature.payment.domain.port.PaymentStorePort
import com.verto.app.feature.payment.domain.port.PaymentTransactionPort
import com.verto.app.feature.payment.domain.port.PurchasePaymentGuardRequest
import com.verto.app.feature.payment.domain.port.PurchaseReceiptPaymentGuardPort
import javax.inject.Inject

class RecordPaymentCoordinator @Inject constructor(
    private val transaction: PaymentTransactionPort,
    private val store: PaymentStorePort,
    private val cash: PaymentCashPort,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val authorization: PaymentAuthorizationPort,
    private val remote: PaymentRemotePort,
    private val atomicPersistenceCoordinator: PaymentAtomicPersistenceCoordinator =
        NoOpPaymentAtomicPersistenceCoordinator,
    private val purchaseReceiptGuard: PurchaseReceiptPaymentGuardPort = NoOpPurchaseReceiptPaymentGuardPort,
) {
    suspend fun record(command: RecordPaymentCommand): PaymentOperationResult {
        val permitted = if (command.requireSupplierPermission) {
            authorization.canRecordSupplierPayment()
        } else {
            authorization.canRecordClientPayment()
        }
        if (!permitted) {
            val action = if (command.requireSupplierPermission) "suppliers_add_payment" else "clients_add_payment"
            runCatching {
                auditLogger.logPermissionDenied(
                    action,
                    "invoiceId=${command.invoiceId} clientId=${command.clientId}",
                    sessionReader
                )
            }
            return PaymentOperationResult.Error(
                failure = AppFailure.PermissionDenied(target = "payment"),
            )
        }

        if (!command.amount.isFinite() || command.amount <= 0.0) {
            return businessError(PaymentFailureCodes.AMOUNT_INVALID, "amount")
        }
        if (command.remainingAmount != null) {
            val requestedMinor = com.verto.app.money.Money.fromLegacyDouble(command.amount).amountMinor
            val presentedRemainingMinor = com.verto.app.money.Money.fromLegacyDouble(command.remainingAmount).amountMinor
            if (requestedMinor > presentedRemainingMinor) {
                return businessError(PaymentFailureCodes.EXCEEDS_REMAINING, "amount")
            }
        }

        val invoice = store.getInvoice(command.invoiceId)
            ?: return PaymentOperationResult.Error(
                failure = AppFailure.NotFound(target = "invoice"),
            )
        if (invoice.voided) {
            return businessError(PaymentFailureCodes.INVOICE_VOIDED, "invoice")
        }
        val currencyFacts = runCatching { PaymentCurrencyCalculator.calculate(invoice, command) }
            .getOrElse { return errorResult(it) }
        val movedCash = currencyFacts.functionalCashAsDouble()
        if (!movedCash.isFinite() || movedCash <= 0.0) {
            return PaymentOperationResult.Error(
                failure = AppFailure.Validation(target = "amount"),
            )
        }

        val session = sessionReader.snapshot()
        val currentUser = session.user
        val organizationId = session.organization.id.trim()
        val payment = PaymentRecord(
            id = command.requestId,
            invoiceId = command.invoiceId,
            clientId = command.clientId,
            amount = command.amount,
            amountMinor = currencyFacts.supplierAmountMinor,
            paymentCurrencyCode = currencyFacts.paymentCurrencyCode,
            supplierAmountMinor = currencyFacts.supplierAmountMinor,
            paymentExchangeRate = currencyFacts.paymentExchangeRate,
            paymentExchangeRateTimestamp = currencyFacts.paymentExchangeRateTimestamp,
            paymentExchangeRateSource = currencyFacts.paymentExchangeRateSource,
            functionalCashAmountMinor = currencyFacts.functionalCashAmountMinor,
            historicalFunctionalAmountMinor = currencyFacts.historicalFunctionalAmountMinor,
            realizedFxDifferenceMinor = currencyFacts.realizedFxDifferenceMinor,
            legacyCurrencyStatus = currencyFacts.legacyCurrencyStatus,
            paymentMethod = command.paymentMethod,
            note = command.note.trim(),
            employeeId = currentUser.id,
            employeeName = currentUser.name,
            paidAt = command.paidAt
        )

        // Financial writes are local-first. The transaction either commits the payment and its
        // durable outbox atomically, or rolls back. A successful result is therefore explicitly
        // SAVED_LOCALLY_PENDING_SYNC rather than pretending the server is already updated.
        val outcome = runCatching {
            transaction.inTransaction {
                val paidMinor = store.getTotalPaidMinor(command.invoiceId)
                val remainingMinor = Math.subtractExact(invoice.totalAmountMinor, paidMinor)
                if (currencyFacts.supplierAmountMinor > remainingMinor) {
                    throw BusinessRuleFailureException(
                        code = PaymentFailureCodes.EXCEEDS_REMAINING,
                        target = "amount",
                    )
                }
                if (invoice.category == PaymentInvoiceCategory.PURCHASE) {
                    purchaseReceiptGuard.enforce(
                        PurchasePaymentGuardRequest(
                            organizationId = organizationId,
                            invoiceId = invoice.id,
                            paymentRequestId = payment.id,
                            requestedAmountMinor = currencyFacts.supplierAmountMinor,
                            overrideReason = command.unreceivedOverrideReason?.trim()?.takeIf { it.isNotEmpty() },
                            overrideAuthorized = authorization.canOverrideUnreceivedPurchasePayment(),
                            approvedBy = currentUser.id,
                            approvedByName = currentUser.name,
                            createdAt = command.paidAt,
                        )
                    )
                }
                if (!store.insertPayment(payment)) {
                    throw BusinessRuleFailureException(
                        code = PaymentFailureCodes.DUPLICATE,
                        target = "requestId",
                    )
                }
                persistCurrencyEffects(invoice, payment, currencyFacts)
                when (invoice.category) {
                    PaymentInvoiceCategory.PURCHASE -> cash.onPaymentMade(movedCash, payment.id)
                    PaymentInvoiceCategory.SALE -> cash.onPaymentReceived(movedCash, payment.id)
                }
                persistIntegration(organizationId, payment, command.paidAt)
            }
        }
        outcome.exceptionOrNull()?.let { return errorResult(it) }

        runCatching {
            auditLogger.logInsert(
                table = AuditTable.PAYMENT,
                recordId = payment.id,
                summary = "دفعة ${command.amount} — فاتورة #${command.invoiceNumber} — ${command.clientName}",
                newValue = payment.toAuditJson()
            )
        }
        return PaymentOperationResult.Success(payment.id)
    }

    private fun businessError(code: String, target: String? = null): PaymentOperationResult.Error =
        PaymentOperationResult.Error(
            failure = AppFailure.BusinessRule(code = code, target = target),
        )

    private fun errorResult(error: Throwable): PaymentOperationResult.Error =
        PaymentOperationResult.Error(
            failure = ErrorClassifier.classify(error),
            cause = error,
        )

    private suspend fun persistCurrencyEffects(
        invoice: com.verto.app.feature.payment.domain.model.PaymentInvoice,
        payment: PaymentRecord,
        facts: PaymentCurrencyFacts,
    ) {
        store.insertAllocation(
            PaymentAllocationRecord(
                id = "${payment.id}:${invoice.id}",
                paymentId = payment.id,
                invoiceId = invoice.id,
                allocatedTransactionAmountMinor = facts.supplierAmountMinor,
                historicalFunctionalAmountMinor = facts.historicalFunctionalAmountMinor,
                realizedFxDifferenceMinor = facts.realizedFxDifferenceMinor,
                createdAt = payment.paidAt,
                writeId = payment.id,
            )
        )
        if (facts.realizedFxDifferenceMinor > 0L) {
            store.insertRealizedFxEvent(
                RealizedFxEventRecord(
                    id = "${payment.id}:REALIZED_FX",
                    paymentId = payment.id,
                    invoiceId = invoice.id,
                    functionalCurrencyCode = facts.functionalCurrencyCode,
                    historicalFunctionalAmountMinor = facts.historicalFunctionalAmountMinor,
                    functionalCashAmountMinor = facts.functionalCashAmountMinor,
                    differenceMinor = facts.realizedFxDifferenceMinor,
                    result = facts.realizedFxResult,
                    occurredAt = payment.paidAt,
                    writeId = payment.id,
                )
            )
        }
    }

    private suspend fun persistIntegration(
        organizationId: String,
        payment: PaymentRecord,
        occurredAt: Long,
    ) {
        if (organizationId.isBlank()) return
        atomicPersistenceCoordinator.persist(
            PersistPaymentIntegrationCommand(
                organizationId = organizationId,
                invoiceId = payment.invoiceId,
                clientId = payment.clientId,
                payment = payment,
                eventKind = PaymentIntegrationEventKind.RECORDED,
                occurredAt = occurredAt,
            ),
        ).getOrThrow()
    }
}

internal fun PaymentRecord.toAuditJson(): String = buildString {
    append('{')
    append("\"id\":\"").append(id.jsonEscape()).append("\",")
    append("\"invoiceId\":\"").append(invoiceId.jsonEscape()).append("\",")
    append("\"clientId\":\"").append(clientId.jsonEscape()).append("\",")
    append("\"amount\":").append(amount).append(',')
    append("\"paymentMethod\":\"").append(paymentMethod.name).append("\",")
    append("\"note\":\"").append(note.jsonEscape()).append("\",")
    append("\"paidAt\":").append(paidAt)
    append('}')
}

private fun String.jsonEscape(): String = buildString(length) {
    this@jsonEscape.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
}
