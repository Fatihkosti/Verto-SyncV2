package com.verto.app.feature.payment.domain.port

import com.verto.app.feature.payment.domain.model.AdvanceCreditRecord
import com.verto.app.feature.payment.domain.model.PaymentInvoice
import com.verto.app.feature.payment.domain.model.PaymentRecord
import com.verto.app.feature.payment.domain.model.PaymentAllocationRecord
import com.verto.app.feature.payment.domain.model.RealizedFxEventRecord
import com.verto.app.feature.payment.domain.model.PersistPaymentIntegrationCommand

interface PaymentStorePort {
    suspend fun getInvoice(invoiceId: String): PaymentInvoice?
    suspend fun getTotalPaid(invoiceId: String): Double
    suspend fun getTotalPaidMinor(invoiceId: String): Long
    suspend fun insertPayment(payment: PaymentRecord): Boolean
    suspend fun insertAllocation(allocation: PaymentAllocationRecord)
    suspend fun insertRealizedFxEvent(event: RealizedFxEventRecord)
    suspend fun getPayment(paymentId: String): PaymentRecord?
    suspend fun getReversalFor(paymentId: String): PaymentRecord?
    suspend fun mirrorPayment(payment: PaymentRecord)
}

interface PaymentTransactionPort {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

interface PaymentCashPort {
    suspend fun onPaymentReceived(amount: Double, paymentId: String)
    suspend fun onPaymentMade(amount: Double, paymentId: String)
    suspend fun reversePaymentReceived(amount: Double, paymentId: String, note: String)
    suspend fun reversePaymentMade(amount: Double, paymentId: String, note: String)
}

interface PaymentAuthorizationPort {
    suspend fun canRecordClientPayment(): Boolean
    suspend fun canRecordSupplierPayment(): Boolean
    suspend fun canOverrideUnreceivedPurchasePayment(): Boolean = false
    suspend fun canReversePayment(): Boolean
}

interface PaymentRemotePort {
    fun isEnabled(): Boolean
    suspend fun postPayment(payment: PaymentRecord, cashAmount: Double): String
    suspend fun reversePayment(paymentId: String, requestId: String): String
}

interface AdvanceCreditPort {
    suspend fun insert(credit: AdvanceCreditRecord): Boolean
}

/** Integration-owned durable event writer; implementations must remain database-only. */
interface PaymentIntegrationOutboxPort {
    val integrationKey: String

    suspend fun appendPaymentEvent(
        command: PersistPaymentIntegrationCommand,
    ): Result<Unit>
}

/** Runs inside the existing payment Room transaction and propagates every failure. */
interface PaymentAtomicPersistenceCoordinator {
    suspend fun persist(
        command: PersistPaymentIntegrationCommand,
    ): Result<Unit>
}

object NoOpPaymentAtomicPersistenceCoordinator : PaymentAtomicPersistenceCoordinator {
    override suspend fun persist(command: PersistPaymentIntegrationCommand): Result<Unit> =
        Result.success(Unit)
}


/** F253 cross-feature guard. Implemented by app/database so payment stays independent of invoice internals. */
data class PurchasePaymentGuardRequest(
    val organizationId: String,
    val invoiceId: String,
    val paymentRequestId: String,
    val requestedAmountMinor: Long,
    val overrideReason: String?,
    val overrideAuthorized: Boolean,
    val approvedBy: String,
    val approvedByName: String,
    val createdAt: Long,
)

interface PurchaseReceiptPaymentGuardPort {
    suspend fun enforce(request: PurchasePaymentGuardRequest)
}

object NoOpPurchaseReceiptPaymentGuardPort : PurchaseReceiptPaymentGuardPort {
    override suspend fun enforce(request: PurchasePaymentGuardRequest) = Unit
}
