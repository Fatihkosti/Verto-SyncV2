package com.verto.app.data.repository

import androidx.room.withTransaction
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.dao.NoteDao
import com.verto.app.data.local.entity.NoteEntity
import com.verto.app.data.sync.UnifiedOutboxWriter
import kotlinx.coroutines.flow.Flow

/** Session 307: NOTE local mutations and unified outbox intent commit atomically. */
class NoteRepository(
    private val noteDao: NoteDao,
    private val database: AppDatabase,
    private val sessionReader: SessionReader,
    private val outbox: UnifiedOutboxWriter,
) {
    fun getNotesForClient(clientId: String): Flow<List<NoteEntity>> =
        noteDao.getNotesForClient(clientId)

    suspend fun insertNote(note: NoteEntity): Long {
        val orgId = trustedOrganizationId()
        return database.withTransaction {
            val rowId = noteDao.insertNote(note)
            outbox.enqueue(
                organizationId = orgId,
                aggregateType = "NOTE",
                aggregateId = note.id,
                operationType = "UPSERT",
                payload = mapOf("clientId" to note.clientId, "createdAt" to note.createdAt, "text" to note.text),
            )
            rowId
        }
    }

    suspend fun deleteNote(note: NoteEntity) {
        val orgId = trustedOrganizationId()
        database.withTransaction {
            noteDao.deleteNote(note)
            outbox.enqueue(
                organizationId = orgId,
                aggregateType = "NOTE",
                aggregateId = note.id,
                operationType = "DELETE",
                payload = mapOf("clientId" to note.clientId, "deleted" to true),
            )
        }
    }

    suspend fun getAllSync(): List<NoteEntity> = noteDao.getAllNotesSync()

    private suspend fun trustedOrganizationId(): String = sessionReader.snapshot().organization.id.trim().also {
        require(it.isNotBlank()) { "FAIL_ORG_SCOPE" }
    }
}
