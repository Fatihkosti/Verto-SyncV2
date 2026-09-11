package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.InvoiceEditorDraftEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftLineEntity
import com.verto.app.data.local.entity.InvoiceEditorDraftMaintenanceImageEntity

@Dao
abstract class InvoiceDraftDao {
    @Query("SELECT * FROM invoice_editor_drafts WHERE draft_key = :draftKey AND organization_id = :organizationId LIMIT 1")
    abstract suspend fun getDraft(draftKey: String, organizationId: String): InvoiceEditorDraftEntity?

    @Query("SELECT * FROM invoice_editor_draft_lines WHERE draft_key = :draftKey ORDER BY sort_order ASC")
    abstract suspend fun getLines(draftKey: String): List<InvoiceEditorDraftLineEntity>

    @Query("SELECT * FROM invoice_editor_draft_maintenance_images WHERE draft_key = :draftKey ORDER BY sort_order ASC")
    abstract suspend fun getImages(draftKey: String): List<InvoiceEditorDraftMaintenanceImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertHeader(row: InvoiceEditorDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertLines(rows: List<InvoiceEditorDraftLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertImages(rows: List<InvoiceEditorDraftMaintenanceImageEntity>)

    @Query("DELETE FROM invoice_editor_draft_lines WHERE draft_key = :draftKey")
    protected abstract suspend fun deleteLines(draftKey: String)

    @Query("DELETE FROM invoice_editor_draft_maintenance_images WHERE draft_key = :draftKey")
    protected abstract suspend fun deleteImages(draftKey: String)

    @Query("DELETE FROM invoice_editor_drafts WHERE draft_key = :draftKey AND organization_id = :organizationId")
    abstract suspend fun deleteDraft(draftKey: String, organizationId: String): Int

    @Transaction
    open suspend fun replaceDraft(
        header: InvoiceEditorDraftEntity,
        lines: List<InvoiceEditorDraftLineEntity>,
        images: List<InvoiceEditorDraftMaintenanceImageEntity>,
    ) {
        upsertHeader(header)
        deleteLines(header.draftKey)
        deleteImages(header.draftKey)
        if (lines.isNotEmpty()) upsertLines(lines)
        if (images.isNotEmpty()) upsertImages(images)
    }
}
