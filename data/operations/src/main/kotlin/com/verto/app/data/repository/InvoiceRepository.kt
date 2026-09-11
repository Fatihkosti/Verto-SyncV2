package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.InvoiceDao
import com.verto.app.data.local.dao.PaymentDao
import com.verto.app.data.local.entity.*
import com.verto.app.data.sync.UnifiedOutboxWriter
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class InvoiceRepository(
    private val invoiceDao: InvoiceDao,
    private val paymentDao: PaymentDao,
    private val userPrefs: PreferencesManager,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    /** v327 stable repository access for cross-feature read adapters; DAO stays infrastructure-internal. */
    fun observeAllInvoices(): Flow<List<InvoiceEntity>> = invoiceDao.getAllInvoices()

    fun getInvoicesForClient(clientId: String): Flow<List<InvoiceEntity>> = invoiceDao.getInvoicesForClient(clientId)

    fun getInvoicesForClientInRange(clientId: String, from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getInvoicesForClientInRange(clientId, from, to)

    fun getPaymentsForClient(clientId: String): Flow<List<PaymentEntity>> = paymentDao.getPaymentsForClient(clientId)

    fun observeAllInvoiceItems(): Flow<List<InvoiceItemEntity>> = invoiceDao.getAllInvoiceItems()

    fun observeAllPayments(): Flow<List<PaymentEntity>> = paymentDao.getAllPayments()

    fun observeAllPaymentAllocations(): Flow<List<PaymentAllocationEntity>> = paymentDao.getAllPaymentAllocations()

    fun observeFinancialOutbox(organizationId: String): Flow<List<FinancialOutboxEntity>> =
        invoiceDao.observeFinancialOutbox(organizationId)

    fun observeInvoiceWriteGuards(organizationId: String): Flow<List<InvoiceWriteGuardEntity>> =
        invoiceDao.observeInvoiceWriteGuards(organizationId)

    suspend fun mirrorInvoicesFromServer(invoices: List<InvoiceEntity>) {
        if (invoices.isNotEmpty()) invoiceDao.insertInvoicesFromRemote(invoices)
    }
    fun getInvoiceSummariesForClient(clientId: String): Flow<List<InvoiceSummary>> =
        combine(
            invoiceDao.getInvoicesForClient(clientId),
            paymentDao.getPaymentsForClient(clientId)
        ) { invoices, payments ->
            invoices.filter { it.legacyCurrencyStatus != LegacyCurrencyStatus.UNKNOWN }.map { invoice ->
                val invoicePayments = payments.filter {
                    it.invoiceId == invoice.id && it.legacyCurrencyStatus != LegacyCurrencyStatus.UNKNOWN
                }
                InvoiceSummary(invoice, invoicePayments.sumOf { it.amount }, invoicePayments)
            }
        }
    fun getInvoiceSummariesForClientInRange(clientId: String, from: Long, to: Long): Flow<List<InvoiceSummary>> =
        combine(
            invoiceDao.getInvoicesForClientInRange(clientId, from, to),
            paymentDao.getPaymentsForClient(clientId)
        ) { invoices, payments ->
            invoices.filter { it.legacyCurrencyStatus != LegacyCurrencyStatus.UNKNOWN }.map { invoice ->
                val invoicePayments = payments.filter {
                    it.invoiceId == invoice.id && it.legacyCurrencyStatus != LegacyCurrencyStatus.UNKNOWN
                }
                InvoiceSummary(invoice, invoicePayments.sumOf { it.amount }, invoicePayments)
            }
        }
    fun getInvoiceSummary(invoiceId: String): Flow<InvoiceSummary?> =
        combine(
            invoiceDao.getInvoiceById(invoiceId),
            paymentDao.getPaymentsForInvoice(invoiceId),
            paymentDao.getTotalPaidForInvoice(invoiceId)
        ) { invoice, payments, totalPaid ->
            invoice?.let { InvoiceSummary(it, totalPaid, payments) }
        }
    fun getPaymentsForInvoice(invoiceId: String): Flow<List<PaymentEntity>> =
        paymentDao.getPaymentsForInvoice(invoiceId)
    suspend fun getNextInvoiceNumber(): Int =
        (invoiceDao.getMaxInvoiceNumber() ?: 0) + 1
    suspend fun getInvoiceByIdSync(invoiceId: String): InvoiceEntity? =
        invoiceDao.getInvoiceByIdSync(invoiceId)
    suspend fun getAllInvoicesSync(): List<InvoiceEntity> =
        invoiceDao.getAllInvoicesSync()
    suspend fun getAllInvoiceItemsSync(): List<InvoiceItemEntity> =
        invoiceDao.getAllInvoiceItemsSync()
    /**
     * Claims tenant+operation+writeId inside the caller's Room transaction.
     * A duplicate returns the already-bound invoice id without repeating any side effect.
     */
    suspend fun claimInvoiceWrite(
        organizationId: String,
        operationType: String,
        writeId: String,
        targetInvoiceId: String,
        sourceVersion: Int,
    ): Pair<String, Boolean> {
        val organization = organizationId.trim()
        val operation = operationType.trim()
        val write = writeId.trim()
        val target = targetInvoiceId.trim()
        require(organization.isNotEmpty()) { "organizationId is required for invoice write" }
        require(operation.isNotEmpty()) { "operationType is required for invoice write" }
        require(write.isNotEmpty()) { "writeId is required for invoice write" }
        require(target.isNotEmpty()) { "targetInvoiceId is required for invoice write" }
        require(sourceVersion > 0) { "sourceVersion must be positive" }
        val inserted = invoiceDao.insertInvoiceWriteGuard(
            InvoiceWriteGuardEntity(
                id = UUID.randomUUID().toString(),
                organizationId = organization,
                operationType = operation,
                writeId = write,
                targetInvoiceId = target,
                sourceId = target,
                sourceVersion = sourceVersion,
            )
        )
        if (inserted != -1L) return target to true
        val existing = invoiceDao.getInvoiceWriteGuard(organization, operation, write)
            ?: error("invoice write guard conflict without persisted owner")
        return existing.targetInvoiceId to false
    }
    suspend fun insertInvoice(invoice: InvoiceEntity): String {
        val synced = invoice.copy(
            category = if (invoice.isOwedToMe) InvoiceCategory.SALE else InvoiceCategory.PURCHASE,
            isDirty = true   // SYNC-012: كتابة محلية = متسخ
        )
        invoiceDao.insertInvoice(synced)
        return synced.id
    }
    suspend fun updateInvoice(invoice: InvoiceEntity) {
        val synced = invoice.copy(
            category = if (invoice.isOwedToMe) InvoiceCategory.SALE else InvoiceCategory.PURCHASE,
            isDirty = true   // SYNC-012: كتابة محلية = متسخ
        )
        invoiceDao.updateInvoice(synced)
    }
    suspend fun deleteInvoiceById(id: String) {
        val organizationId = sessionReader.snapshot().organization.id.trim().also {
            require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
        }
        val mutationId = UUID.randomUUID().toString()
        database.withTransaction {
            val deleted = invoiceDao.deleteDraftInvoiceById(id)
            check(deleted == 1) { "لا يمكن حذف فاتورة مرحلة؛ استخدم الإلغاء الموثق" }
            outbox.enqueue(
                organizationId = organizationId,
                aggregateType = "INVOICE",
                aggregateId = id,
                operationType = "COMMAND",
                payload = mapOf("command" to "DISCARD_DRAFT"),
                mutationId = mutationId,
            )
        }
        runCatching { userPrefs.addPendingInvoiceDeletion(id) } // compatibility-only after durable commit
    }
    suspend fun updatePostedDescriptionOptimistic(
        id: String,
        notes: String,
        dueDate: Long,
        expectedVersion: Int,
    ): Boolean = invoiceDao.updatePostedDescriptionOptimistic(id, notes, dueDate, expectedVersion) == 1
    suspend fun markInvoiceVoidedOptimistic(
        id: String,
        expectedVersion: Int,
        voidedAt: Long,
        reason: String,
        writeId: String,
    ): Boolean = invoiceDao.markInvoiceVoidedOptimistic(
        id = id,
        expectedVersion = expectedVersion,
        voidedAt = voidedAt,
        reason = reason,
        writeId = writeId,
    ) == 1
    /** نسخة متزامنة لقراءة مدفوعات الفاتورة (لعكس النقد عند الإلغاء). */
    suspend fun getPaymentsForInvoiceSync(invoiceId: String): List<PaymentEntity> =
        paymentDao.getPaymentsForInvoiceSync(invoiceId)
    suspend fun getReversalForPaymentSync(originalPaymentId: String): PaymentEntity? =
        paymentDao.getReversalForPaymentSync(originalPaymentId)
    suspend fun addPayment(payment: PaymentEntity): String {
        val rowId = paymentDao.insertPayment(payment)
        check(rowId != -1L) { "invoice payment insert was ignored unexpectedly" }
        return payment.id
    }
    suspend fun addPaymentAllocation(allocation: PaymentAllocationEntity) =
        paymentDao.insertPaymentAllocation(allocation)
    suspend fun addRealizedFxEvent(event: RealizedFxEventEntity) =
        paymentDao.insertRealizedFxEvent(event)
    suspend fun getPaymentAllocationsForInvoiceSync(invoiceId: String): List<PaymentAllocationEntity> =
        paymentDao.getPaymentAllocationsForInvoiceSync(invoiceId)
    suspend fun getRealizedFxEventsForInvoiceSync(invoiceId: String): List<RealizedFxEventEntity> =
        paymentDao.getRealizedFxEventsForInvoiceSync(invoiceId)
    suspend fun getDueInstallmentsSync(invoiceId: String): List<InvoiceDueInstallmentEntity> =
        invoiceDao.getDueInstallments(invoiceId)

    suspend fun replaceDueInstallments(invoiceId: String, rows: List<InvoiceDueInstallmentEntity>) {
        invoiceDao.deleteDueInstallments(invoiceId)
        if (rows.isNotEmpty()) invoiceDao.insertDueInstallments(rows)
    }
    /**
     * يُدرج الدفعة ويُرجع rowId الخام من Room.
     * عند تعارض المفتاح (OnConflictStrategy.IGNORE) يُرجع -1L — يستخدمه
     * [com.verto.app.feature.payment.application.AddPaymentUseCase] لاكتشاف التكرار
     * وإفشال العملية قبل تحريك الصندوق.
     */
    suspend fun insertPaymentReturningRow(payment: PaymentEntity): Long =
        paymentDao.insertPayment(payment)

    suspend fun mirrorPaymentFromServer(payment: PaymentEntity) =
        paymentDao.upsertPaymentFromRemote(payment.copy(isDirty = false))

    /** المتبقي الحقيقي يُشتق من مجموع المدفوعات في قاعدة البيانات — مصدر السداد الموثوق. */
    suspend fun getTotalPaidForInvoiceSync(invoiceId: String): Double =
        paymentDao.getTotalPaidForInvoiceSync(invoiceId)

    /** Fixed-point source used by financial write guards; never authorize from REAL sums. */
    suspend fun getTotalPaidMinorForInvoiceSync(invoiceId: String): Long =
        paymentDao.getTotalPaidMinorForInvoiceSync(invoiceId)

    suspend fun deletePayment(payment: PaymentEntity) = paymentDao.deletePayment(payment)

    // ── Session 7: عكس الدفعة بدل الحذف ──────────────────────────────────
    suspend fun getPaymentByIdSync(id: String): PaymentEntity? =
        paymentDao.getPaymentByIdSync(id)

    // ✅ تمرير الدالة الجديدة
    suspend fun deletePaymentsByInvoiceId(invoiceId: String) =
        paymentDao.deletePaymentsByInvoiceId(invoiceId)

    suspend fun getInvoicesWithNotifications(): List<InvoiceEntity> =
        invoiceDao.getInvoicesWithNotifications()

    fun getAllInvoicesByRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getInvoicesByDateRange(from, to)

    fun getPaymentsByRange(from: Long, to: Long): Flow<List<PaymentEntity>> =
        paymentDao.getPaymentsByDateRange(from, to)

    fun getTotalCollectedInRange(from: Long, to: Long): Flow<Double> =
        paymentDao.getTotalCollectedInRange(from, to)

    fun getTotalCollectedForClientInRange(clientId: String, from: Long, to: Long): Flow<Double> =
        paymentDao.getTotalCollectedForClientInRange(clientId, from, to)

    fun getSalesInvoicesByRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getAllSalesInvoicesByDate(from, to)

    fun getSalesItemsInRange(from: Long, to: Long): Flow<List<InvoiceItemEntity>> =
        invoiceDao.getSalesItemsInRange(from, to)

    fun getPurchaseInvoicesByRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getPurchaseInvoicesByDate(from, to)

    fun getCashSalesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getCashSalesInRange(from, to)

    fun getCreditSalesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getCreditSalesInRange(from, to)

    fun getCashPurchasesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getCashPurchasesInRange(from, to)

    fun getCreditPurchasesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>> =
        invoiceDao.getCreditPurchasesInRange(from, to)

    /** يُدرج الفاتورة وبنودها ذرياً ويُرجع (invoiceId, invoiceNumber المُعيَّن فعلياً) */
    suspend fun insertInvoiceWithItems(invoice: InvoiceEntity, items: List<InvoiceItemEntity>): Pair<String, Int> {
        val synced = invoice.copy(
            category = if (invoice.isOwedToMe) InvoiceCategory.SALE else InvoiceCategory.PURCHASE,
            isDirty = true   // SYNC-012: كتابة محلية = متسخ
        )
        val assignedNumber = invoiceDao.insertInvoiceWithItemsAtomic(synced, items)
        return Pair(synced.id, assignedNumber)
    }

    suspend fun updateInvoiceWithItems(invoice: InvoiceEntity, items: List<InvoiceItemEntity>) {
        val synced = invoice.copy(
            category = if (invoice.isOwedToMe) InvoiceCategory.SALE else InvoiceCategory.PURCHASE,
            isDirty = true   // SYNC-012: كتابة محلية = متسخ
        )
        invoiceDao.updateInvoiceWithItemsAtomic(synced, items)
    }

    fun getInvoiceItemsFlow(invoiceId: String): Flow<List<InvoiceItemEntity>> =
        invoiceDao.getInvoiceItemsFlow(invoiceId)

    suspend fun getInvoiceItemsSync(invoiceId: String): List<InvoiceItemEntity> =
        invoiceDao.getInvoiceItemsSync(invoiceId)

    fun getItemsForClient(clientId: String): Flow<List<InvoiceItemEntity>> =
        invoiceDao.getItemsForClient(clientId)

    fun getUniqueItemsWithLatestPrices(): Flow<List<InvoiceItemEntity>> =
        invoiceDao.getUniqueItemsWithLatestPrices()

    fun getCreditOwedInvoices(): Flow<List<InvoiceEntity>> =
        invoiceDao.getCreditOwedInvoices()

    fun getPaymentsForCreditInvoices(): Flow<List<PaymentEntity>> =
        paymentDao.getPaymentsForCreditInvoices()
}
