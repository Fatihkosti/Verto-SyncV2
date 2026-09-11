package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.ClientCreditEntity
import kotlinx.coroutines.flow.Flow

/**
 * Session 4: سجل الرصيد المقدَّم (الفائض من السداد الجماعي).
 * صافي رصيد الطرف = SUM(amount). موجب = لصالحنا، سالب = علينا.
 */
@Dao
interface ClientCreditDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(credit: ClientCreditEntity): Long

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM client_credits WHERE clientId = :clientId")
    fun getNetCreditMinorForClient(clientId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM client_credits WHERE clientId = :clientId")
    suspend fun getNetCreditMinorForClientSync(clientId: String): Long

    @Query("SELECT * FROM client_credits WHERE clientId = :clientId ORDER BY createdAt DESC")
    fun getCreditsForClient(clientId: String): Flow<List<ClientCreditEntity>>

    // ── SYNC (Session 9) ──
    @Query("SELECT * FROM client_credits WHERE isDirty = 1")
    suspend fun getDirtyCreditsSync(): List<ClientCreditEntity>

    @Query("UPDATE client_credits SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markCreditsClean(ids: List<String>)

    @Query("SELECT * FROM client_credits WHERE id = :id LIMIT 1")
    suspend fun getCreditByIdSync(id: String): ClientCreditEntity?

    @Query("SELECT * FROM client_credits")
    suspend fun getAllCreditsSync(): List<ClientCreditEntity>

    @Query("SELECT * FROM client_credits ORDER BY createdAt ASC, id ASC")
    fun observeAllCredits(): Flow<List<ClientCreditEntity>>

    /** Pull: مسحوب من السيرفر = نظيف (REPLACE للصفوف من جهاز آخر). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditFromRemote(credit: ClientCreditEntity)
}
