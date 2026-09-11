package com.verto.app.feature.payment.application

import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import com.verto.app.core.error.AppFailure
import com.verto.app.core.error.ErrorClassifier
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.payment.domain.model.PaymentInvoiceCategory
import com.verto.app.feature.payment.domain.model.PaymentMoney
import com.verto.app.feature.payment.domain.model.PaymentOperationResult
import com.verto.app.feature.payment.domain.model.PaymentRecord
import com.verto.app.feature.payment.domain.model.PaymentIntegrationEventKind
import com.verto.app.feature.payment.domain.model.PersistPaymentIntegrationCommand
import com.verto.app.feature.payment.domain.model.ReversePaymentCommand
import com.verto.app.feature.payment.domain.port.PaymentAuthorizationPort
import com.verto.app.feature.payment.domain.port.PaymentAtomicPersistenceCoordinator
import com.verto.app.feature.payment.domain.port.NoOpPaymentAtomicPersistenceCoordinator
import com.verto.app.feature.payment.domain.port.PaymentCashPort
import com.verto.app.feature.payment.domain.port.PaymentRemotePort
import com.verto.app.feature.payment.domain.port.PaymentStorePort
import com.verto.app.feature.payment.domain.port.PaymentTransactionPort
import com.verto.app.money.Money
import javax.inject.Inject

class ReversePaymentCoordinator @Inject constructor(
    private val transaction: PaymentTransactionPort,
    private val store: PaymentStorePort,
    private val cash: PaymentCashPort,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader,
    private val authorization: PaymentAuthorizationPort,
    private val remote: PaymentRemotePort,
    private val atomicPersistenceCoordinator: PaymentAtomicPersistenceCoordinator =
        NoOpPaymentAtomicPersistenceCoordinator,
) {
    private class ReverseRejected(message: String) : Exception(message)

    suspend fun reverse(command: ReversePaymentCommand): PaymentOperationResult {
        if (!authorization.canReversePayment()) {
            runCatching {
                auditLogger.logPermissionDenied(
                    "payments_reverse",
                    "paymentId=${command.paymentId}",
                    sessionReader
                )
            }
            return PaymentOperationResult.Error(
                failure = AppFailure.PermissionDenied(target = "payment"),
            )
        }

        val original = store.getPayment(command.paymentId)
            ?: return PaymentOperationResult.Error(
                failure = AppFailure.NotFound(target = "payment"),
            )
        if (original.reversedPaymentId != null || original.amount <= 0.0) {
            return PaymentOperationResult.Error(
                failure = AppFailure.BusinessRule(code = "PAYMENT_REVERSAL_INVALID", target = "payment"),
            )
        }
        if (store.getReversalFor(command.paymentId) != null) {
            return PaymentOperationResult.Error(
                failure = AppFailure.BusinessRule(code = "PAYMENT_ALREADY_REVERSED", target = "payment"),
            )
        }
        val invoice = store.getInvoice(original.invoiceId)
            ?: return PaymentOperationResult.Error(
                failure = AppFailure.NotFound(target = "invoice"),
            )
        if (invoice.voided) {
            return PaymentOperationResult.Error(
                failure = AppFailure.BusinessRule(code = PaymentFailureCodes.INVOICE_VOIDED, target = "invoice"),
            )
        }

        val session = sessionReader.snapshot()
        val currentUser = session.user
        val organizationId = session.organization.id.trim()
        val originalFunctionalCash = if (
            original.functionalCashAmountMinor > 0L && invoice.functionalCurrencyCode.isNotBlank()
        ) Money.ofMinor(original.functionalCashAmountMinor, invoice.functionalCurrencyCode).toLegacyDouble()
        else original.amount
        val reversal = PaymentRecord(
            id = command.requestId,
            invoiceId = original.invoiceId,
            clientId = original.clientId,
            amount = -original.amount,
            amountMinor = -original.amountMinor,
            paymentCurrencyCode = original.paymentCurrencyCode,
            supplierAmountMinor = -original.supplierAmountMinor,
            paymentExchangeRate = original.paymentExchangeRate,
            paymentExchangeRateTimestamp = original.paymentExchangeRateTimestamp,
            paymentExchangeRateSource = original.paymentExchangeRateSource,
            functionalCashAmountMinor = -original.functionalCashAmountMinor,
            historicalFunctionalAmountMinor = -original.historicalFunctionalAmountMinor,
            realizedFxDifferenceMinor = -original.realizedFxDifferenceMinor,
            legacyCurrencyStatus = original.legacyCurrencyStatus,
            paymentMethod = original.paymentMethod,
            note = "عكس دفعة (${PaymentMoney.format(original.amount)})",
            employeeId = currentUser.id,
            employeeName = currentUser.name,
            reversedPaymentId = original.id
        )

        val note = "عكس دفعة — فاتورة #${invoice.invoiceNumber}"
        val outcome = runCatching {
            transaction.inTransaction {
                if (!store.insertPayment(reversal)) {
                    throw ReverseRejected("reverse payment insert rejected")
                }
                when (invoice.category) {
                    PaymentInvoiceCategory.SALE ->
                        cash.reversePaymentReceived(originalFunctionalCash, original.id, note)
                    PaymentInvoiceCategory.PURCHASE ->
                        cash.reversePaymentMade(originalFunctionalCash, original.id, note)
                }
                persistIntegration(organizationId, reversal, original)
            }
        }
        outcome.exceptionOrNull()?.let { error ->
            return PaymentOperationResult.Error(
                failure = ErrorClassifier.classify(error),
                cause = error,
            )
        }

        runCatching {
            auditLogger.logInsert(
                table = AuditTable.PAYMENT,
                recordId = reversal.id,
                summary = "عكس دفعة ${PaymentMoney.format(original.amount)} — فاتورة #${invoice.invoiceNumber}",
                newValue = reversal.toAuditJson()
            )
        }
        return PaymentOperationResult.Success(reversal.id)
    }

    private suspend fun persistIntegration(
        organizationId: String,
        reversal: PaymentRecord,
        original: PaymentRecord,
    ) {
        if (organizationId.isBlank()) return
        atomicPersistenceCoordinator.persist(
            PersistPaymentIntegrationCommand(
                organizationId = organizationId,
                invoiceId = reversal.invoiceId,
                clientId = reversal.clientId,
                payment = reversal,
                eventKind = PaymentIntegrationEventKind.REVERSED,
                originalPayment = original,
                occurredAt = reversal.paidAt,
            ),
        ).getOrThrow()
    }
}
