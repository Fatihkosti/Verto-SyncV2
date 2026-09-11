package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * استعلامات أداء الموظف (Employee Performance Binding).
 *
 * النِسبة:
 *  • الفواتير/المبيعات/الأرباح/الديون → فواتير البيع التي أنشأها الموظف (invoices.createdBy).
 *  • التحصيل → الدفعات المسجَّلة على فواتير البيع التي أنشأها الموظف (قرار المالك #3).
 *  • العملاء المضافون → العملاء الذين أنشأهم الموظف (clients.createdBy).
 *
 * كل الاستعلامات مقيّدة بـ employeeId + نطاق زمني [from, to].
 * الأرقام خام (Double/Int) — التنسيق في طبقة الـ UI فقط.
 */
@Dao
interface EmployeePerformanceDao {

    @Query("""
        SELECT COUNT(*) FROM invoices
        WHERE category = 'SALE' AND createdBy = :employeeId
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun invoiceCount(employeeId: String, from: Long, to: Long): Int

    @Query("""
        SELECT COUNT(*) FROM invoices
        WHERE category = 'SALE' AND status = 'CLOSED_CASH' AND createdBy = :employeeId
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun cashInvoiceCount(employeeId: String, from: Long, to: Long): Int

    @Query("""
        SELECT COUNT(*) FROM invoices
        WHERE category = 'SALE' AND status = 'CLOSED_CREDIT' AND createdBy = :employeeId
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun creditInvoiceCount(employeeId: String, from: Long, to: Long): Int

    @Query("""
        SELECT COALESCE(SUM(totalAmount), 0) FROM invoices
        WHERE category = 'SALE' AND createdBy = :employeeId
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun totalSales(employeeId: String, from: Long, to: Long): Double

    /** الربح الإجمالي: (sellPrice - buyPrice) * quantity على بنود فواتير البيع للموظف. */
    @Query("""
        SELECT COALESCE(SUM((ii.sellPrice - ii.buyPrice) * ii.quantity), 0)
        FROM invoice_items ii
        INNER JOIN invoices i ON ii.invoiceId = i.id
        WHERE i.category = 'SALE' AND i.createdBy = :employeeId
        AND i.createdAt BETWEEN :from AND :to
    """)
    suspend fun totalProfit(employeeId: String, from: Long, to: Long): Double

    /** إجمالي قيمة فواتير الآجل التي أنشأها الموظف (لحساب الديون). */
    @Query("""
        SELECT COALESCE(SUM(totalAmount), 0) FROM invoices
        WHERE category = 'SALE' AND status = 'CLOSED_CREDIT' AND createdBy = :employeeId
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun creditSalesTotal(employeeId: String, from: Long, to: Long): Double

    /** إجمالي المدفوع على فواتير الآجل التي أنشأها الموظف (لحساب الديون). */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM payments p
        INNER JOIN invoices i ON p.invoiceId = i.id
        WHERE i.category = 'SALE' AND i.status = 'CLOSED_CREDIT' AND i.createdBy = :employeeId
        AND i.createdAt BETWEEN :from AND :to
    """)
    suspend fun creditSalesPaid(employeeId: String, from: Long, to: Long): Double

    /**
     * التحصيل (قرار #3): مجموع الدفعات على فواتير البيع التي أنشأها الموظف،
     * ضمن نطاق تاريخ الدفع.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM payments p
        INNER JOIN invoices i ON p.invoiceId = i.id
        WHERE i.category = 'SALE' AND i.createdBy = :employeeId
        AND p.paidAt BETWEEN :from AND :to
    """)
    suspend fun collections(employeeId: String, from: Long, to: Long): Double

    @Query("""
        SELECT COUNT(*) FROM clients
        WHERE createdBy = :employeeId
        AND createdAt BETWEEN :from AND :to
    """)
    suspend fun addedClients(employeeId: String, from: Long, to: Long): Int
}
