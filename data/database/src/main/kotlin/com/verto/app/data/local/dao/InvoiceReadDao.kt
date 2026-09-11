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


interface InvoiceReadDao {
    @Query("SELECT * FROM invoices WHERE clientId = :clientId AND voided = 0 ORDER BY createdAt DESC")
    abstract fun getInvoicesForClient(clientId: String): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE clientId = :clientId AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    abstract fun getInvoicesForClientInRange(clientId: String, from: Long, to: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    abstract fun getInvoiceById(id: String): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    abstract suspend fun getInvoiceByIdSync(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    abstract fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT inv.*, COALESCE(c.name, '') AS partyName
        FROM invoices inv
        LEFT JOIN clients c ON c.id = inv.clientId
        WHERE inv.organization_id = :organizationId
          AND inv.createdAt >= :sinceEpochMillis
        ORDER BY inv.createdAt DESC, inv.id ASC
        LIMIT :limit
        """
    )
    abstract fun observeActivityInvoices(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<InvoiceActivityRow>>

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    abstract suspend fun getAllInvoicesSync(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE createdAt BETWEEN :from AND :to")
    abstract fun getInvoicesByDateRange(from: Long, to: Long): Flow<List<InvoiceEntity>>

    @Query("SELECT MAX(invoiceNumber) FROM invoices")
    abstract suspend fun getMaxInvoiceNumber(): Int?

    @Query("""
        SELECT * FROM invoices
        WHERE length(:numberQuery) >= 2
          AND invoiceNumberSearch >= :numberQuery AND invoiceNumberSearch < (:numberQuery || char(1114111))
        ORDER BY
            CASE WHEN invoiceNumberSearch = :numberQuery THEN 0 ELSE 1 END,
            createdAt DESC
        LIMIT :limit
    """)
    abstract suspend fun searchInvoicesByNumberPrefix(
        numberQuery: String,
        limit: Int
    ): List<InvoiceEntity>

    @Query("""
        SELECT inv.*,
            (SELECT ii.itemName
             FROM invoice_items ii
             WHERE ii.invoiceId = inv.id
             ORDER BY ii.totalPrice DESC, ii.id ASC
             LIMIT 1) AS highlightedItem
        FROM invoices inv
        WHERE inv.organization_id = :organizationId
          AND ((:includeSales = 1 AND inv.category = 'SALE')
               OR (:includePurchases = 1 AND inv.category = 'PURCHASE'))
          AND length(:numberQuery) >= 2
          AND inv.invoiceNumberSearch >= :numberQuery
          AND inv.invoiceNumberSearch < (:numberQuery || char(1114111))
        ORDER BY
            CASE WHEN inv.invoiceNumberSearch = :numberQuery THEN 0 ELSE 1 END,
            inv.createdAt DESC,
            inv.id ASC
        LIMIT :limit
    """)
    abstract suspend fun searchInvoiceCardsByNumberPrefix(
        organizationId: String,
        numberQuery: String,
        includeSales: Int,
        includePurchases: Int,
        limit: Int
    ): List<InvoiceSearchCard>

    @Query("""
        SELECT inv.id AS invoiceId,
               inv.invoiceNumber AS invoiceNumber,
               inv.clientId AS partyId,
               c.name AS partyName,
               c.phone AS partyPhone,
               inv.totalAmount AS totalAmount,
               COALESCE(SUM(p.amount), 0) AS totalPaid,
               inv.dueDate AS dueDate,
               inv.createdAt AS createdAt,
               inv.category AS category
        FROM invoices inv
        INNER JOIN clients c ON c.id = inv.clientId
        LEFT JOIN payments p ON p.invoiceId = inv.id
        WHERE inv.organization_id = :organizationId
          AND inv.status = 'CLOSED_CREDIT'
          AND inv.voided = 0
          AND inv.dueDate > 0
          AND inv.dueDate <= :nowEpochMillis
          AND (
              (:includeSales = 1 AND inv.category = 'SALE')
              OR (:includePurchases = 1 AND inv.category = 'PURCHASE')
          )
        GROUP BY inv.id
        HAVING inv.totalAmount > COALESCE(SUM(p.amount), 0)
        ORDER BY
            CASE WHEN inv.dueDate <= (:nowEpochMillis - 604800000) THEN 0 ELSE 1 END,
            inv.dueDate ASC,
            inv.id ASC
    """)
    abstract fun observeFinancialPendingInvoices(
        organizationId: String,
        nowEpochMillis: Long,
        includeSales: Int,
        includePurchases: Int,
    ): Flow<List<InvoiceFinancialPendingRow>>

    /** v370: broad outstanding-credit stream; schedule-aware due selection happens in feature layer. */
    @Query("""
        SELECT inv.id AS invoiceId,
               inv.invoiceNumber AS invoiceNumber,
               inv.clientId AS partyId,
               c.name AS partyName,
               c.phone AS partyPhone,
               inv.totalAmount AS totalAmount,
               COALESCE(SUM(p.amount), 0) AS totalPaid,
               inv.dueDate AS dueDate,
               inv.createdAt AS createdAt,
               inv.category AS category
        FROM invoices inv
        INNER JOIN clients c ON c.id = inv.clientId
        LEFT JOIN payments p ON p.invoiceId = inv.id
        WHERE inv.organization_id = :organizationId
          AND inv.status = 'CLOSED_CREDIT'
          AND inv.voided = 0
          AND (
              (:includeSales = 1 AND inv.category = 'SALE')
              OR (:includePurchases = 1 AND inv.category = 'PURCHASE')
          )
        GROUP BY inv.id
        HAVING inv.totalAmount > COALESCE(SUM(p.amount), 0)
        ORDER BY inv.dueDate ASC, inv.id ASC
    """)
    abstract fun observeOutstandingCreditInvoicesForSchedule(
        organizationId: String,
        includeSales: Int,
        includePurchases: Int,
    ): Flow<List<InvoiceFinancialPendingRow>>

    @Query("SELECT * FROM invoices WHERE notificationsEnabled = 1 AND voided = 0")
    abstract suspend fun getInvoicesWithNotifications(): List<InvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertInvoiceWriteGuard(guard: InvoiceWriteGuardEntity): Long

    @Query(
        """
        SELECT * FROM invoice_write_guard
        WHERE organization_id = :organizationId
          AND operation_type = :operationType
          AND write_id = :writeId
        LIMIT 1
        """
    )
    abstract suspend fun getInvoiceWriteGuard(
        organizationId: String,
        operationType: String,
        writeId: String,
    ): InvoiceWriteGuardEntity?

}
