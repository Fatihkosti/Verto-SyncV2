package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE clientId = :clientId ORDER BY createdAt DESC")
    fun getNotesForClient(clientId: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<NoteEntity>

    /** SYNC-013: إدراج من السيرفر دون استبدال الموجود محلياً. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteFromRemote(note: NoteEntity)

    /** Session 308 remote explicit delete; absence from a page is never deletion. */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String): Int
}
