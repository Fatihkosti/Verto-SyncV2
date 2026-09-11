package com.verto.app.data.local.dao

import com.verto.app.core.audit.domain.AuditTable

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.entity.AuditLogEntity
import com.verto.app.data.local.entity.InvoiceCategory
import kotlinx.coroutines.flow.Flow

data class AuditActivityEntryRow(
    @Embedded val audit: AuditLogEntity,
    val invoiceCategory: InvoiceCategory,
)

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AuditLogEntity>>

    @Query(
        """
        SELECT a.*, i.category AS invoiceCategory
        FROM audit_log a
        INNER JOIN invoices i ON i.id = a.recordId
        WHERE i.organization_id = :organizationId
          AND a.createdAt >= :sinceEpochMillis
          AND a.auditTable = 'INVOICE'
          AND (a.sourceType = 'INVOICE_VOID' OR a.action = 'DELETE')
        ORDER BY a.createdAt DESC, a.id ASC
        LIMIT :limit
        """,
    )
    fun observeActivityEntries(
        organizationId: String,
        sinceEpochMillis: Long,
        limit: Int,
    ): Flow<List<AuditActivityEntryRow>>

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE auditTable = :table ORDER BY createdAt DESC")
    fun getByTable(table: AuditTable): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE recordId = :recordId ORDER BY createdAt DESC")
    fun getByRecord(recordId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE canUndo = 1 ORDER BY createdAt DESC")
    fun getUndoable(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): AuditLogEntity?

    // تعطيل التراجع للسجلات الأقدم من 24 ساعة — لا حذف
    @Query("UPDATE audit_log SET canUndo = 0 WHERE createdAt < :cutoff AND canUndo = 1")
    suspend fun expireOldEntries(cutoff: Long)

    // ✅ الإصلاح: بدلاً من حذف السجل، نغلق باب التراجع فقط
    // السجل يبقى في قاعدة البيانات للأغراض الجنائية والمحاسبية
    @Query("UPDATE audit_log SET canUndo = 0 WHERE id = :id")
    suspend fun markAsUndone(id: String)

    // ── Pagination ───────────────────────────────────────────────────
    @Query("""
        SELECT * FROM audit_log
        WHERE (:table = '' OR auditTable = :table)
          AND (:undoOnly = 0 OR canUndo = 1)
        ORDER BY createdAt DESC
    """)
    fun getFilteredPaged(table: String, undoOnly: Int): PagingSource<Int, AuditLogEntity>
}