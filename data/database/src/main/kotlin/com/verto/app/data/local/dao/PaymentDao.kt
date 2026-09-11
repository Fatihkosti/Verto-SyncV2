package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.InvoiceCategory
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.local.entity.PaymentAllocationEntity
import com.verto.app.data.local.entity.RealizedFxEventEntity
import kotlinx.coroutines.flow.Flow

data class PaymentSearchCard(
    @Embedded val payment: PaymentEntity,
    val partyName: String,
    val invoiceNumber: Int,
    val invoiceCategory: InvoiceCategory,
    val invoiceVoided: Boolean
)

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY paidAt DESC")
    fun getPaymentsForInvoice(invoiceId: String): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE invoiceId = :invoiceId AND legacy_currency_status != 'UNKNOWN'")
    fun getTotalPaidForInvoice(invoiceId: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE invoiceId = :invoiceId AND legacy_currency_status != 'UNKNOWN'")
    suspend fun getTotalPaidForInvoiceSync(invoiceId: String): Double

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM payments WHERE invoiceId = :invoiceId AND legacy_currency_status != 'UNKNOWN'")
    suspend fun getTotalPaidMinorForInvoiceSync(invoiceId: String): Long

    /** قراءة مجمعة تمنع N+1 عند فحص عدد كبير من الفواتير في Worker. */
    @Query("""
        SELECT invoiceId, COALESCE(SUM(amount), 0) AS totalPaid
        FROM payments
        WHERE invoiceId IN (:invoiceIds) AND legacy_currency_status != 'UNKNOWN'
        GROUP BY invoiceId
    """)
    suspend fun getTotalsForInvoicesSync(invoiceIds: List<String>): List<InvoicePaymentTotal>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE clientId = :clientId")
    fun getTotalPaidForClient(clientId: String): Flow<Double>

    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY paidAt DESC")
    fun getPaymentsForClient(clientId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE paidAt BETWEEN :from AND :to ORDER BY paidAt DESC")
    fun getPaymentsByDateRange(from: Long, to: Long): Flow<List<PaymentEntity>>

    @Query("""
        SELECT p.*,
            c.name AS partyName,
            i.invoiceNumber AS invoiceNumber,
            i.category AS invoiceCategory,
            i.voided AS invoiceVoided
        FROM payments p
        INNER JOIN clients c ON c.id = p.clientId
        INNER JOIN invoices i ON i.id = p.invoiceId
        WHERE i.organization_id = :organizationId
          AND ((:includeSales = 1 AND i.category = 'SALE')
               OR (:includePurchases = 1 AND i.category = 'PURCHASE'))
          AND (
              (length(:textQuery) >= 2
               AND c.nameSearch >= :textQuery
               AND c.nameSearch < (:textQuery || char(1114111)))
              OR (length(:identifierQuery) >= 2 AND (
                    CAST(ABS(p.amount) AS TEXT) LIKE :identifierQuery || '%'
                    OR strftime('%d%m%Y', p.paidAt / 1000, 'unixepoch', 'localtime')
                        LIKE :identifierQuery || '%'
                    OR strftime('%Y%m%d', p.paidAt / 1000, 'unixepoch', 'localtime')
                        LIKE :identifierQuery || '%'
              ))
          )
        ORDER BY
            CASE
                WHEN c.nameSearch = :textQuery
                  OR CAST(ABS(p.amount) AS TEXT) = :identifierQuery THEN 0
                ELSE 1
            END,
            p.paidAt DESC,
            p.id ASC
        LIMIT :limit
    """)
    suspend fun searchPaymentCards(
        organizationId: String,
        textQuery: String,
        identifierQuery: String,
        includeSales: Int,
        includePurchases: Int,
        limit: Int
    ): List<PaymentSearchCard>

    @Query(
        """
        SELECT p.*,
            c.name AS partyName,
            i.invoiceNumber AS invoiceNumber,
            i.category AS invoiceCategory,
            i.voided AS invoiceVoided
        FROM payments p
        INNER JOIN clients c ON c.id = p.clientId
        INNER JOIN invoices i ON i.id = p.invoiceId
        WHERE i.organization_id = :organizationId
          AND p.paidAt >= :sinceEpochMillis
        ORDER BY p.paidAt DESC, p.id ASC
        LIMIT :limit
        """
    )
    fun observeActivityPayments(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<PaymentSearchCard>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE paidAt BETWEEN :from AND :to")
    fun getTotalCollectedInRange(from: Long, to: Long): Flow<Double>
    // ── الدالة الناقصة الخاصة بـ ClientDashboardViewModel ──
    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE clientId = :clientId AND paidAt BETWEEN :from AND :to")
    fun getTotalCollectedForClientInRange(clientId: String, from: Long, to: Long): Flow<Double>

    // ── الدالة الناقصة الخاصة بـ HomeViewModel (تحصيل الديون الآجلة) ──
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM payments p
        INNER JOIN invoices i ON p.invoiceId = i.id
        WHERE i.status = 'CLOSED_CREDIT' AND p.paymentMethod = 'CASH' AND p.paidAt BETWEEN :from AND :to
    """)
    fun getTotalCreditCollectedInRange(from: Long, to: Long): Flow<Double>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentAllocation(allocation: PaymentAllocationEntity)

    @Query("SELECT * FROM payment_allocations WHERE invoice_id = :invoiceId ORDER BY created_at ASC")
    suspend fun getPaymentAllocationsForInvoiceSync(invoiceId: String): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations WHERE payment_id = :paymentId ORDER BY created_at ASC")
    suspend fun getPaymentAllocationsForPaymentSync(paymentId: String): List<PaymentAllocationEntity>

    @Query("SELECT * FROM payment_allocations ORDER BY created_at ASC, id ASC")
    fun getAllPaymentAllocations(): Flow<List<PaymentAllocationEntity>>

    @Query("SELECT * FROM payment_allocations ORDER BY created_at ASC, id ASC")
    suspend fun getAllPaymentAllocationsSync(): List<PaymentAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentAllocationsFromRemote(rows: List<PaymentAllocationEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRealizedFxEvent(event: RealizedFxEventEntity)

    @Query("SELECT * FROM realized_fx_events WHERE invoice_id = :invoiceId ORDER BY occurred_at ASC")
    suspend fun getRealizedFxEventsForInvoiceSync(invoiceId: String): List<RealizedFxEventEntity>

    @Query("SELECT * FROM realized_fx_events ORDER BY occurred_at ASC, id ASC")
    suspend fun getAllRealizedFxEventsSync(): List<RealizedFxEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRealizedFxEventsFromRemote(rows: List<RealizedFxEventEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    /** SYNC-011: upsert للـ pull — يُحدِّث المبلغ المعدَّل على جهاز آخر (REPLACE). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentFromRemote(payment: PaymentEntity)

    /** SYNC-012: المدفوعات المتسخة فقط (للرفع) + تصفير العلم بعد رفع ناجح. */
    @Query("SELECT * FROM payments WHERE isDirty = 1")
    suspend fun getDirtyPaymentsSync(): List<PaymentEntity>

    @Query("UPDATE payments SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markPaymentsClean(ids: List<String>)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("SELECT * FROM payments")
    suspend fun getAllPaymentsSync(): List<PaymentEntity>

    @Query("SELECT * FROM payments")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId")
    suspend fun getPaymentsForInvoiceSync(invoiceId: String): List<PaymentEntity>

    // ── Session 7: عكس الدفعة ──────────────────────────────────────────────
    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentByIdSync(id: String): PaymentEntity?

    /** الحركة العكسية لدفعة أصلية (إن وُجدت) — لمنع العكس مرتين. */
    @Query("SELECT * FROM payments WHERE reversedPaymentId = :originalId LIMIT 1")
    suspend fun getReversalForPaymentSync(originalId: String): PaymentEntity?

    // ✅ تمت إضافة الدالة الجديدة لمسح مدفوعات فاتورة محددة
    @Query("DELETE FROM payments WHERE invoiceId = :invoiceId")
    suspend fun deletePaymentsByInvoiceId(invoiceId: String)

    // ── للتقارير — مدفوعات الفواتير الآجلة فقط (بدل getPaymentsByDateRange(0, MAX)) ──
    @Query("""
        SELECT p.* FROM payments p
        INNER JOIN invoices i ON p.invoiceId = i.id
        WHERE i.status = 'CLOSED_CREDIT' AND i.isOwedToMe = 1
    """)
    fun getPaymentsForCreditInvoices(): Flow<List<PaymentEntity>>
}

data class InvoicePaymentTotal(
    val invoiceId: String,
    val totalPaid: Double
)
