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


interface InvoiceItemDao {
    // ── بنود الفاتورة ─────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertInvoiceItems(items: List<InvoiceItemEntity>)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    abstract fun getInvoiceItemsFlow(invoiceId: String): Flow<List<InvoiceItemEntity>>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    abstract suspend fun getInvoiceItemsSync(invoiceId: String): List<InvoiceItemEntity>

    @Query("UPDATE invoice_items SET inventoryItemId = :inventoryItemId, isDirty = 1 WHERE id = :invoiceItemId")
    abstract suspend fun updateInvoiceItemInventoryLink(invoiceItemId: String, inventoryItemId: String)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    abstract suspend fun deleteInvoiceItemsByInvoiceId(invoiceId: String)

    /** SYNC-010: حذف بنود بمعرّفات محددة — يُستخدَم لإزالة البنود اليتيمة محلياً عند المزامنة. */
    @Query("DELETE FROM invoice_items WHERE id IN (:ids)")
    abstract suspend fun deleteInvoiceItemsByIds(ids: List<String>)

    /** SYNC-012: بنود الفاتورة المتسخة فقط (للرفع) + تصفير العلم بعد رفع ناجح. */
    @Query("SELECT * FROM invoice_items WHERE isDirty = 1")
    abstract suspend fun getDirtyInvoiceItemsSync(): List<InvoiceItemEntity>

    @Query("UPDATE invoice_items SET isDirty = 0 WHERE id IN (:ids)")
    abstract suspend fun markInvoiceItemsClean(ids: List<String>)

    @Query("SELECT * FROM invoice_items WHERE id IN (SELECT MAX(id) FROM invoice_items GROUP BY itemName)")
    abstract fun getUniqueItemsWithLatestPrices(): Flow<List<InvoiceItemEntity>>

    @Query("SELECT * FROM invoice_items")
    abstract fun getAllInvoiceItems(): Flow<List<InvoiceItemEntity>>

    @Query("SELECT * FROM invoice_items")
    abstract suspend fun getAllInvoiceItemsSync(): List<InvoiceItemEntity>

    @Query("""
        SELECT ii.* FROM invoice_items ii
        INNER JOIN invoices i ON ii.invoiceId = i.id
        WHERE i.clientId = :clientId
        ORDER BY i.createdAt DESC
    """)
    abstract fun getItemsForClient(clientId: String): Flow<List<InvoiceItemEntity>>
    /** مخصص للـ PULL فقط — لا يمحو البنود المحلية الحديثة */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertInvoiceItemsFromRemote(items: List<InvoiceItemEntity>)
}
