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


interface InvoiceReportingDao {
    // ── المبيعات ──────────────────────────────────────
    @Query("""
        SELECT * FROM invoices
        WHERE category = 'SALE' AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt ASC
    """)
    abstract fun getAllSalesInvoicesByDate(from: Long, to: Long): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE category = 'SALE' AND status = 'CLOSED_CASH' AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    abstract fun getCashSalesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE category = 'SALE' AND status = 'CLOSED_CREDIT' AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    abstract fun getCreditSalesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>>

    // ── المشتريات ─────────────────────────────────────
    @Query("""
        SELECT * FROM invoices
        WHERE category = 'PURCHASE' AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    abstract fun getPurchaseInvoicesByDate(from: Long, to: Long): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE category = 'PURCHASE' AND status = 'CLOSED_CASH' AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    abstract fun getCashPurchasesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>>

    @Query("""
        SELECT * FROM invoices
        WHERE category = 'PURCHASE' AND status = 'CLOSED_CREDIT' AND voided = 0
        AND createdAt BETWEEN :from AND :to
        ORDER BY createdAt DESC
    """)
    abstract fun getCreditPurchasesInRange(from: Long, to: Long): Flow<List<InvoiceEntity>>

    // ── بنود المبيعات ─────────────────────────────────
    @Query("""
        SELECT ii.* FROM invoice_items ii
        INNER JOIN invoices i ON ii.invoiceId = i.id
        WHERE i.category = 'SALE' AND i.voided = 0
        AND i.createdAt BETWEEN :from AND :to
    """)
    abstract fun getSalesItemsInRange(from: Long, to: Long): Flow<List<InvoiceItemEntity>>


    // ── للتقارير — فواتير آجلة مستحقة فقط (بدل getAllInvoices لتحليل العملاء) ──
    @Query("""
        SELECT * FROM invoices
        WHERE status = 'CLOSED_CREDIT' AND isOwedToMe = 1 AND voided = 0
        ORDER BY dueDate ASC
    """)
    abstract fun getCreditOwedInvoices(): Flow<List<InvoiceEntity>>

}
