package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.CashDenominationEntity
import com.verto.app.data.local.entity.CashReconciliationEntity
import com.verto.app.data.local.entity.ReconciliationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CashReconciliationDao {

    @Query("SELECT * FROM cash_reconciliation_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<CashReconciliationEntity>>

    @Query("SELECT * FROM cash_reconciliation_sessions WHERE status = :status ORDER BY startedAt DESC")
    fun getSessionsByStatus(status: ReconciliationStatus): Flow<List<CashReconciliationEntity>>

    @Query("SELECT * FROM cash_reconciliation_sessions WHERE status = 'OPEN' ORDER BY startedAt DESC LIMIT 1")
    fun getCurrentOpenSession(): Flow<CashReconciliationEntity?>

    @Query("SELECT * FROM cash_reconciliation_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): CashReconciliationEntity?

    @Query("""
        SELECT * FROM cash_reconciliation_sessions
        WHERE startedAt >= :from AND startedAt <= :to
        ORDER BY startedAt DESC
    """)
    fun getSessionsInRange(from: Long, to: Long): Flow<List<CashReconciliationEntity>>

    @Insert
    suspend fun insertSession(session: CashReconciliationEntity)

    @Update
    suspend fun updateSession(session: CashReconciliationEntity)

    @Delete
    suspend fun deleteSession(session: CashReconciliationEntity)

    @Query("SELECT * FROM cash_denominations WHERE reconciliationId = :sessionId ORDER BY denominationValue DESC")
    fun getDenominationsForSession(sessionId: String): Flow<List<CashDenominationEntity>>

    @Query("SELECT * FROM cash_denominations WHERE reconciliationId = :sessionId ORDER BY id ASC")
    suspend fun getDenominationsForSessionSync(sessionId: String): List<CashDenominationEntity>

    @Insert
    suspend fun insertDenominations(denominations: List<CashDenominationEntity>)

    @Query("DELETE FROM cash_denominations WHERE reconciliationId = :sessionId")
    suspend fun deleteDenominationsForSession(sessionId: String)

    @Transaction
    suspend fun replaceDenominations(sessionId: String, denominations: List<CashDenominationEntity>) {
        deleteDenominationsForSession(sessionId)
        insertDenominations(denominations)
    }

    // ── SYNC-014.c: مزامنة ─────────────────────────────────────────────────────
    @Query("SELECT * FROM cash_reconciliation_sessions")
    suspend fun getAllSessionsSync(): List<CashReconciliationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessionFromRemote(session: CashReconciliationEntity)

    @Query("DELETE FROM cash_reconciliation_sessions WHERE id NOT IN (:ids)")
    suspend fun deleteSessionsNotIn(ids: List<String>)

    @Query("SELECT * FROM cash_denominations")
    suspend fun getAllDenominationsSync(): List<CashDenominationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDenominationsFromRemote(denominations: List<CashDenominationEntity>)

    @Query("DELETE FROM cash_denominations WHERE id IN (:ids)")
    suspend fun deleteDenominationsByIds(ids: List<String>)
}
