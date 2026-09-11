package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.entity.InvoiceEntity
import com.verto.app.data.local.entity.InvoiceItemEntity
import com.verto.app.data.local.entity.InvoiceWriteGuardEntity
import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.FinancialInboxEntity
import com.verto.app.data.local.entity.withSearchKeys
import kotlinx.coroutines.flow.Flow


interface InvoiceFinancialEventDao {
    @Query("SELECT * FROM invoice_due_installments WHERE invoice_id = :invoiceId ORDER BY sequence ASC")
    abstract suspend fun getDueInstallments(invoiceId: String): List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity>

    @Query("SELECT * FROM invoice_due_installments WHERE invoice_id = :invoiceId ORDER BY sequence ASC")
    abstract fun observeDueInstallments(invoiceId: String): Flow<List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity>>

    @Query("SELECT * FROM invoice_due_installments ORDER BY invoice_id ASC, due_date ASC, sequence ASC")
    abstract fun observeAllDueInstallments(): Flow<List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDueInstallments(rows: List<com.verto.app.data.local.entity.InvoiceDueInstallmentEntity>)

    @Query("DELETE FROM invoice_due_installments WHERE invoice_id = :invoiceId")
    abstract suspend fun deleteDueInstallments(invoiceId: String)

    // ── Pagination ───────────────────────────────────────────────────

    @Query("""
        SELECT inv.*,
            COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoiceId = inv.id), 0) AS totalPaid
        FROM invoices inv
        LEFT JOIN clients c ON c.id = inv.clientId
        WHERE inv.category = :category
          AND inv.voided = 0
          AND (:purchaseScope IS NULL OR (inv.category = 'PURCHASE' AND inv.purchase_scope = :purchaseScope))
          AND (:tab = 0 OR (:tab = 1 AND inv.status = 'CLOSED_CASH') OR (:tab = 2 AND inv.status = 'CLOSED_CREDIT'))
          AND inv.createdAt BETWEEN :from AND :to
          AND (:search = '' OR c.name LIKE '%' || :search || '%' OR CAST(inv.invoiceNumber AS TEXT) LIKE '%' || :search || '%')
        ORDER BY
            CASE WHEN :sort = 'NEWEST'  THEN (0 - inv.createdAt)    END ASC,
            CASE WHEN :sort = 'HIGHEST' THEN (0 - inv.totalAmount)  END ASC,
            CASE WHEN :sort = 'OVERDUE' THEN
                CASE WHEN inv.dueDate > 0
                          AND inv.dueDate < (strftime('%s','now') * 1000)
                          AND inv.status = 'CLOSED_CREDIT'
                          AND inv.totalAmount > COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoiceId = inv.id), 0) + 0.01
                     THEN -(strftime('%s','now') * 1000 - inv.dueDate)
                     ELSE 0 END
            END ASC,
            inv.createdAt DESC
    """)
    abstract fun getInvoicesPagedWithPaid(
        category: String,
        tab: Int,
        from: Long,
        to: Long,
        search: String,
        sort: String,
        purchaseScope: String?
    ): PagingSource<Int, InvoiceWithPaid>

    // ── F249: financial Outbox/Inbox ───────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFinancialOutbox(event: FinancialOutboxEntity): Long

    @Query("""
        SELECT COALESCE(MAX(sequence), 0) + 1
        FROM (
            SELECT sequence FROM financial_outbox
            WHERE organization_id = :organizationId AND aggregate_id = :aggregateId
            UNION ALL
            SELECT sequence FROM financial_inbox
            WHERE organization_id = :organizationId AND aggregate_id = :aggregateId
        )
    """)
    abstract suspend fun nextFinancialOutboxSequence(organizationId: String, aggregateId: String): Long

    @Query("""
        SELECT * FROM financial_outbox
        WHERE organization_id = :organizationId
        ORDER BY created_at ASC, aggregate_id ASC, sequence ASC
    """)
    abstract fun observeFinancialOutbox(organizationId: String): Flow<List<FinancialOutboxEntity>>

    @Query("""
        SELECT * FROM invoice_write_guard
        WHERE organization_id = :organizationId
        ORDER BY createdAt ASC, id ASC
    """)
    abstract fun observeInvoiceWriteGuards(organizationId: String): Flow<List<InvoiceWriteGuardEntity>>

    @Query("""
        SELECT * FROM financial_outbox
        WHERE organization_id = :organizationId
          AND operation_type = :operationType
          AND write_id = :writeId
        LIMIT 1
    """)
    abstract suspend fun getFinancialOutboxByIdentity(
        organizationId: String,
        operationType: String,
        writeId: String,
    ): FinancialOutboxEntity?

    /**
     * Only the first not-yet-acknowledged event of an aggregate can be delivered. This keeps
     * payment/void children behind their invoice predecessor even across process death/retry.
     */
    @Query("""
        SELECT candidate.*
        FROM financial_outbox AS candidate
        WHERE candidate.organization_id = :organizationId
          AND candidate.sync_state IN ('PENDING','RETRY')
          AND candidate.next_attempt_at <= :now
          AND NOT EXISTS (
              SELECT 1 FROM financial_outbox AS predecessor
              WHERE predecessor.organization_id = candidate.organization_id
                AND predecessor.aggregate_id = candidate.aggregate_id
                AND predecessor.sequence < candidate.sequence
                AND predecessor.sync_state <> 'ACKNOWLEDGED'
          )
        ORDER BY candidate.created_at ASC, candidate.aggregate_id ASC, candidate.sequence ASC
        LIMIT :limit
    """)
    abstract suspend fun getReadyFinancialOutbox(
        organizationId: String,
        now: Long,
        limit: Int,
    ): List<FinancialOutboxEntity>

    @Query("""
        UPDATE financial_outbox
        SET sync_state = 'ACKNOWLEDGED',
            server_revision = :serverRevision,
            synced_at = :syncedAt,
            last_error = ''
        WHERE event_id = :eventId
          AND sync_state IN ('PENDING','RETRY')
    """)
    abstract suspend fun acknowledgeFinancialOutbox(
        eventId: String,
        serverRevision: Long,
        syncedAt: Long,
    ): Int

    @Query("""
        UPDATE financial_outbox
        SET sync_state = 'REQUIRES_REVIEW',
            attempt_count = attempt_count + 1,
            last_error = :reason,
            server_revision = :serverRevision
        WHERE event_id = :eventId
          AND sync_state <> 'ACKNOWLEDGED'
    """)
    abstract suspend fun markFinancialOutboxRequiresReview(
        eventId: String,
        reason: String,
        serverRevision: Long?,
    ): Int

    @Query("""
        UPDATE financial_outbox
        SET sync_state = 'RETRY',
            attempt_count = attempt_count + 1,
            next_attempt_at = :nextAttemptAt,
            last_error = :reason
        WHERE event_id = :eventId
          AND sync_state IN ('PENDING','RETRY')
    """)
    abstract suspend fun retryFinancialOutbox(
        eventId: String,
        nextAttemptAt: Long,
        reason: String,
    ): Int

    @Query("SELECT COUNT(*) FROM financial_outbox WHERE organization_id=:organizationId AND sync_state IN ('PENDING','RETRY')")
    abstract suspend fun countFinancialOutboxBacklog(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM financial_outbox WHERE organization_id=:organizationId AND sync_state='REQUIRES_REVIEW'")
    abstract suspend fun countFinancialOutboxReview(organizationId: String): Long

    @Query("SELECT MIN(next_attempt_at) FROM financial_outbox WHERE organization_id=:organizationId AND sync_state='RETRY' AND next_attempt_at>:now")
    abstract suspend fun nextFinancialRetryAt(organizationId: String, now: Long): Long?

    @Query("""
        SELECT aggregate_id FROM financial_outbox
        WHERE organization_id = :organizationId
          AND sync_state <> 'ACKNOWLEDGED'
        UNION
        SELECT aggregate_id FROM financial_inbox
        WHERE organization_id = :organizationId
          AND apply_state = 'REQUIRES_REVIEW'
    """)
    abstract suspend fun getFinanciallyBlockedAggregateIds(organizationId: String): List<String>

    @Query("""
        SELECT COUNT(*) FROM financial_outbox
        WHERE organization_id = :organizationId
          AND sync_state = 'REQUIRES_REVIEW'
    """)
    abstract suspend fun countFinancialOutboxConflicts(organizationId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFinancialInbox(event: FinancialInboxEntity): Long

    @Query("""
        SELECT COALESCE(MAX(server_revision), 0)
        FROM financial_inbox
        WHERE organization_id = :organizationId
    """)
    abstract suspend fun getFinancialInboxRevision(organizationId: String): Long

    @Query("""
        SELECT * FROM financial_inbox
        WHERE organization_id = :organizationId
          AND apply_state IN ('RECEIVED','WAITING_DEPENDENCY')
        ORDER BY server_revision ASC
    """)
    abstract suspend fun getPendingFinancialInbox(organizationId: String): List<FinancialInboxEntity>

    @Query("""
        SELECT * FROM financial_inbox
        WHERE organization_id = :organizationId
          AND aggregate_id = :aggregateId
        ORDER BY server_revision DESC
        LIMIT 1
    """)
    abstract suspend fun getLatestFinancialInboxForAggregate(
        organizationId: String,
        aggregateId: String,
    ): FinancialInboxEntity?

    @Query("""
        UPDATE financial_inbox
        SET apply_state = :state,
            apply_reason = :reason,
            applied_at = :appliedAt
        WHERE event_id = :eventId
    """)
    abstract suspend fun updateFinancialInboxState(
        eventId: String,
        state: String,
        reason: String,
        appliedAt: Long?,
    ): Int

    // ── إعادة التعيين ─────────────────────────────────
    @Query("DELETE FROM invoices WHERE category = 'SALE'")
    abstract suspend fun deleteAllSalesInvoices()

    @Query("DELETE FROM invoices WHERE category = 'PURCHASE'")
    abstract suspend fun deleteAllPurchaseInvoices()

    @Query("DELETE FROM invoices")
    abstract suspend fun deleteAllInvoices()
}
