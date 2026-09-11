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


interface InvoiceSyncDao {
    /**
     * F248 optimistic transition POSTED -> VOID. Version mismatch means a concurrent writer won.
     * Legacy `voided` is updated in the same statement until F249 transport migration is complete.
     */
    @Query("""
        UPDATE invoices
        SET lifecycle_status = 'VOID',
            lifecycle_version = lifecycle_version + 1,
            voided_at = :voidedAt,
            void_reason = :reason,
            void_write_id = :writeId,
            voided = 1,
            isDirty = 1
        WHERE id = :id
          AND lifecycle_status = 'POSTED'
          AND lifecycle_version = :expectedVersion
    """)
    abstract suspend fun markInvoiceVoidedOptimistic(
        id: String,
        expectedVersion: Int,
        voidedAt: Long,
        reason: String,
        writeId: String,
    ): Int

    /** Safe descriptive edit allow-list for posted invoices. */
    @Query("""
        UPDATE invoices
        SET notes = :notes,
            dueDate = :dueDate,
            lifecycle_version = lifecycle_version + 1,
            isDirty = 1
        WHERE id = :id
          AND lifecycle_status = 'POSTED'
          AND lifecycle_version = :expectedVersion
    """)
    abstract suspend fun updatePostedDescriptionOptimistic(
        id: String,
        notes: String,
        dueDate: Long,
        expectedVersion: Int,
    ): Int

    /** F248 compatibility pull until F249 carries lifecycle fields explicitly. */
    @Query("""
        UPDATE invoices
        SET voided = :voided,
            lifecycle_status = CASE WHEN :voided = 1 THEN 'VOID' ELSE lifecycle_status END
        WHERE id = :id
    """)
    abstract suspend fun updateInvoiceVoided(id: String, voided: Boolean)

    // SYNC-012: تعديلات محلية ⇒ تُعلّم الصف متسخاً
    @Query("UPDATE invoices SET status = :status, isDirty = 1 WHERE id = :id AND lifecycle_status = 'DRAFT'")
    abstract suspend fun updateStatus(id: String, status: String)

    @Query("""
        UPDATE invoices SET commission = :commission,
            commission_minor = CAST(ROUND(:commission * 100.0) AS INTEGER),
            commission_beneficiary_client_id = :beneficiaryClientId,
            commission_source = :commissionSource,
            lifecycle_version = lifecycle_version + 1, isDirty = 1
        WHERE id = :id AND lifecycle_status IN ('DRAFT','POSTED') AND lifecycle_version = :expectedVersion
    """)
    abstract suspend fun updateCommission(
        id: String, commission: Double, beneficiaryClientId: String?, commissionSource: String, expectedVersion: Int
    ): Int

    /** SYNC-012: الفواتير المتسخة فقط (للرفع) + تصفير العلم بعد رفع ناجح. */
    @Query("SELECT * FROM invoices WHERE isDirty = 1")
    abstract suspend fun getDirtyInvoicesSync(): List<InvoiceEntity>

    @Query("UPDATE invoices SET isDirty = 0 WHERE id IN (:ids)")
    abstract suspend fun markInvoicesClean(ids: List<String>)

}
