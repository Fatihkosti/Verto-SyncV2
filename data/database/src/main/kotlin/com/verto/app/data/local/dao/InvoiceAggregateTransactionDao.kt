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


interface InvoiceAggregateTransactionDao: InvoiceReadDao, InvoiceWriteDao, InvoiceItemDao {
    // ─────────────────────────────────────────────────────────────────────────
    // ✅ الإصلاح الجوهري — الثغرة #7
    //
    // insertInvoiceWithItemsAtomic / updateInvoiceWithItemsAtomic:
    // تضمن أن الفاتورة وبنودها تُحفظ أو تُحدَّث معاً في transaction واحدة.
    //
    // قبل الإصلاح: insertInvoice() ثم insertInvoiceItems() في خطوتين منفصلتين
    //               → انقطاع الكهرباء بينهما = فاتورة بدون بنود في DB
    // بعد الإصلاح: @Transaction يضمن all-or-nothing — إما تنجح معاً أو تُلغى معاً
    // ─────────────────────────────────────────────────────────────────────────

    @Transaction
    open suspend fun insertInvoiceWithItemsAtomic(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>
    ): Int {
        // إن كان رقم الفاتورة 0 (sentinel)، يُحسب داخل الـ transaction لتجنب race condition
        val finalInvoice = if (invoice.invoiceNumber == 0) {
            invoice.copy(invoiceNumber = (getMaxInvoiceNumber() ?: 0) + 1)
        } else invoice
        insertInvoice(finalInvoice)
        deleteInvoiceItemsByInvoiceId(finalInvoice.id)
        if (items.isNotEmpty()) {
            insertInvoiceItems(items.map { it.copy(invoiceId = finalInvoice.id) })
        }
        return finalInvoice.invoiceNumber
    }

    @Transaction
    open suspend fun updateInvoiceWithItemsAtomic(
        invoice: InvoiceEntity,
        items: List<InvoiceItemEntity>
    ) {
        updateInvoice(invoice)
        deleteInvoiceItemsByInvoiceId(invoice.id)
        if (items.isNotEmpty()) {
            insertInvoiceItems(items.map { it.copy(invoiceId = invoice.id) })
        }
    }
}
