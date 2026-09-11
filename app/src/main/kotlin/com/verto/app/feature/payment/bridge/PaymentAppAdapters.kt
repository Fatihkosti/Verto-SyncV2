package com.verto.app.feature.payment.bridge

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.ClientCreditDao
import com.verto.app.data.local.entity.CashMovementType
import com.verto.app.data.local.entity.ClientCreditEntity
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.local.entity.PaymentAllocationEntity
import com.verto.app.data.local.entity.RealizedFxEventEntity
import com.verto.app.data.local.entity.PaymentMethod
import com.verto.app.data.remote.FinancialPostingRemote
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.data.remote.PostPaymentV2Request
import com.verto.app.data.remote.SupabaseFinancialPostingRemote
import com.verto.app.data.repository.InvoiceRepository
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.feature.payment.domain.model.AdvanceCreditRecord
import com.verto.app.feature.payment.domain.model.PaymentInvoice
import com.verto.app.feature.payment.domain.model.PaymentInvoiceCategory
import com.verto.app.feature.payment.domain.model.PaymentRecord
import com.verto.app.feature.payment.domain.model.PaymentAllocationRecord
import com.verto.app.feature.payment.domain.model.RealizedFxEventRecord
import com.verto.app.feature.payment.domain.port.AdvanceCreditPort
import com.verto.app.feature.payment.domain.port.PaymentAuthorizationPort
import com.verto.app.feature.payment.domain.port.PaymentCashPort
import com.verto.app.feature.payment.domain.port.PaymentRemotePort
import com.verto.app.feature.payment.domain.port.PaymentStorePort
import com.verto.app.utils.CashRegisterManager
import com.verto.app.utils.FeatureFlags
import com.verto.app.utils.SupabaseDateParser
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class RepositoryPaymentStoreAdapter @Inject constructor(
    private val repository: InvoiceRepository
) : PaymentStorePort {
    override suspend fun getInvoice(invoiceId: String): PaymentInvoice? =
        repository.getInvoiceByIdSync(invoiceId)?.let { invoice ->
            PaymentInvoice(
                id = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                clientId = invoice.clientId,
                category = when (invoice.category) {
                    InvoiceCategory.SALE -> PaymentInvoiceCategory.SALE
                    InvoiceCategory.PURCHASE -> PaymentInvoiceCategory.PURCHASE
                },
                totalAmount = invoice.totalAmount,
                totalAmountMinor = invoice.totalAmountMinor,
                transactionCurrencyCode = invoice.transactionCurrencyCode,
                functionalCurrencyCode = invoice.functionalCurrencyCode,
                invoiceExchangeRateSnapshot = invoice.invoiceExchangeRateSnapshot,
                functionalAmountAtRecognitionMinor = invoice.functionalAmountAtRecognitionMinor,
                legacyCurrencyStatus = invoice.legacyCurrencyStatus.name,
                voided = invoice.voided,
            )
        }

    override suspend fun getTotalPaid(invoiceId: String): Double =
        repository.getTotalPaidForInvoiceSync(invoiceId)

    override suspend fun getTotalPaidMinor(invoiceId: String): Long =
        repository.getTotalPaidMinorForInvoiceSync(invoiceId)

    override suspend fun insertPayment(payment: PaymentRecord): Boolean =
        repository.insertPaymentReturningRow(payment.toEntity()) != -1L

    override suspend fun insertAllocation(allocation: PaymentAllocationRecord) =
        repository.addPaymentAllocation(allocation.toEntity())

    override suspend fun insertRealizedFxEvent(event: RealizedFxEventRecord) =
        repository.addRealizedFxEvent(event.toEntity())

    override suspend fun getPayment(paymentId: String): PaymentRecord? =
        repository.getPaymentByIdSync(paymentId)?.toDomain()

    override suspend fun getReversalFor(paymentId: String): PaymentRecord? =
        repository.getReversalForPaymentSync(paymentId)?.toDomain()

    override suspend fun mirrorPayment(payment: PaymentRecord) {
        repository.mirrorPaymentFromServer(payment.toEntity())
    }
}

class CashRegisterPaymentCashAdapter @Inject constructor(
    private val manager: CashRegisterManager
) : PaymentCashPort {
    override suspend fun onPaymentReceived(amount: Double, paymentId: String) =
        manager.onPaymentReceived(amount, paymentId)

    override suspend fun onPaymentMade(amount: Double, paymentId: String) =
        manager.onPaymentMade(amount, paymentId)

    override suspend fun reversePaymentReceived(amount: Double, paymentId: String, note: String) =
        manager.reverseMovement(CashMovementType.PAYMENT_RECEIVED, amount, paymentId, note)

    override suspend fun reversePaymentMade(amount: Double, paymentId: String, note: String) =
        manager.reverseMovement(CashMovementType.PAYMENT_MADE, amount, paymentId, note)
}

class PermissionPaymentAuthorizationAdapter @Inject constructor(
    private val permissions: PermissionProvider
) : PaymentAuthorizationPort {
    override suspend fun canRecordClientPayment(): Boolean =
        permissions.canNow { it.clientsAddPayment }

    override suspend fun canRecordSupplierPayment(): Boolean =
        permissions.canNow { it.suppliersAddPayment }

    override suspend fun canOverrideUnreceivedPurchasePayment(): Boolean =
        permissions.canNow { it.suppliersAddPayment && it.purchasesEdit }

    override suspend fun canReversePayment(): Boolean =
        permissions.canNow { it.paymentsReverse }
}

class DefaultPaymentRemoteAdapter @Inject constructor() : PaymentRemotePort {
    private val remote: FinancialPostingRemote = SupabaseFinancialPostingRemote()

    override fun isEnabled(): Boolean = FeatureFlags.isFinancialMutationsEnabled

    override suspend fun postPayment(payment: PaymentRecord, cashAmount: Double): String {
        val amount = BigDecimal(payment.amount.toString())
        val cash = BigDecimal(cashAmount.toString())
        return remote.postPayment(
            PostPaymentV2Request(
                invoiceId = payment.invoiceId,
                amount = amount,
                cashAmount = cash,
                paymentMethod = payment.paymentMethod.name,
                note = payment.note,
                paidAt = SupabaseDateParser.format(payment.paidAt),
                requestId = payment.id,
                originalFxRate = cash.divide(amount, 8, RoundingMode.HALF_UP)
            )
        ).paymentId
    }

    override suspend fun reversePayment(paymentId: String, requestId: String): String =
        remote.reversePayment(paymentId, requestId).paymentId
}

class RoomAdvanceCreditAdapter @Inject constructor(
    private val database: AppDatabase,
    private val dao: ClientCreditDao,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) : AdvanceCreditPort {
    override suspend fun insert(credit: AdvanceCreditRecord): Boolean {
        val organizationId = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
        }
        return database.withTransaction {
            val inserted = dao.insert(
                ClientCreditEntity(
                    id = credit.id,
                    clientId = credit.clientId,
                    amount = credit.amount,
                    amountMinor = credit.amountMinor,
                    note = credit.note,
                    createdAt = credit.createdAt,
                    employeeId = credit.employeeId,
                    employeeName = credit.employeeName,
                    sourcePaymentId = credit.sourcePaymentId,
                    isDirty = true,
                )
            ) != -1L
            if (inserted) {
                outbox.enqueue(
                    organizationId = organizationId,
                    aggregateType = "CLIENT_CREDIT",
                    aggregateId = credit.id,
                    operationType = "COMMAND",
                    payload = mapOf("materialization" to mapOf(
                        "id" to credit.id,
                        "clientId" to credit.clientId,
                        "amountMinor" to credit.amountMinor,
                        "note" to credit.note,
                        "sourcePaymentId" to credit.sourcePaymentId,
                        "createdAt" to credit.createdAt,
                        "employeeId" to credit.employeeId,
                        "employeeName" to credit.employeeName,
                    )),
                    mutationId = credit.id,
                    createdAt = credit.createdAt,
                )
            }
            inserted
        }
    }
}

private fun PaymentRecord.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amount = amount,
    amountMinor = amountMinor,
    paymentCurrencyCode = paymentCurrencyCode,
    supplierAmountMinor = supplierAmountMinor,
    paymentExchangeRate = paymentExchangeRate,
    paymentExchangeRateTimestamp = paymentExchangeRateTimestamp,
    paymentExchangeRateSource = paymentExchangeRateSource,
    functionalCashAmountMinor = functionalCashAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    legacyCurrencyStatus = runCatching { com.verto.app.data.local.entity.LegacyCurrencyStatus.valueOf(legacyCurrencyStatus) }
        .getOrDefault(com.verto.app.data.local.entity.LegacyCurrencyStatus.REVIEW_REQUIRED),
    paymentMethod = when (paymentMethod) {
        com.verto.app.feature.payment.domain.model.PaymentMethod.CASH -> PaymentMethod.CASH
        com.verto.app.feature.payment.domain.model.PaymentMethod.TRANSFER -> PaymentMethod.TRANSFER
        com.verto.app.feature.payment.domain.model.PaymentMethod.CHECK -> PaymentMethod.CHECK
    },
    note = note,
    paidAt = paidAt,
    employeeId = employeeId,
    employeeName = employeeName,
    reversedPaymentId = reversedPaymentId,
    isDirty = isDirty
)

private fun PaymentEntity.toDomain(): PaymentRecord = PaymentRecord(
    id = id,
    invoiceId = invoiceId,
    clientId = clientId,
    amount = amount,
    amountMinor = amountMinor,
    paymentCurrencyCode = paymentCurrencyCode,
    supplierAmountMinor = supplierAmountMinor,
    paymentExchangeRate = paymentExchangeRate,
    paymentExchangeRateTimestamp = paymentExchangeRateTimestamp,
    paymentExchangeRateSource = paymentExchangeRateSource,
    functionalCashAmountMinor = functionalCashAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    legacyCurrencyStatus = legacyCurrencyStatus.name,
    paymentMethod = when (paymentMethod) {
        PaymentMethod.CASH -> com.verto.app.feature.payment.domain.model.PaymentMethod.CASH
        PaymentMethod.TRANSFER -> com.verto.app.feature.payment.domain.model.PaymentMethod.TRANSFER
        PaymentMethod.CHECK -> com.verto.app.feature.payment.domain.model.PaymentMethod.CHECK
    },
    note = note,
    paidAt = paidAt,
    employeeId = employeeId,
    employeeName = employeeName,
    reversedPaymentId = reversedPaymentId,
    isDirty = isDirty
)


private fun PaymentAllocationRecord.toEntity() = PaymentAllocationEntity(
    id = id, paymentId = paymentId, invoiceId = invoiceId,
    allocatedTransactionAmountMinor = allocatedTransactionAmountMinor,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    realizedFxDifferenceMinor = realizedFxDifferenceMinor,
    createdAt = createdAt, sourceId = invoiceId, writeId = writeId,
)

private fun RealizedFxEventRecord.toEntity() = RealizedFxEventEntity(
    id = id, paymentId = paymentId, invoiceId = invoiceId,
    functionalCurrencyCode = functionalCurrencyCode,
    historicalFunctionalAmountMinor = historicalFunctionalAmountMinor,
    functionalCashAmountMinor = functionalCashAmountMinor,
    differenceMinor = differenceMinor, result = result, occurredAt = occurredAt,
    sourceId = paymentId, writeId = writeId,
)
