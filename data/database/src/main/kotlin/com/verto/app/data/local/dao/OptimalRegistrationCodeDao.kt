package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.verto.app.data.local.entity.OptimalRegistrationCodeEntity

@Dao
interface OptimalRegistrationCodeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(code: OptimalRegistrationCodeEntity)

    @Update
    suspend fun update(code: OptimalRegistrationCodeEntity): Int

    @androidx.room.Transaction
    suspend fun saveForClient(code: OptimalRegistrationCodeEntity) {
        if (update(code) == 0) insert(code)
    }

    @Query(
        """
        SELECT * FROM optimal_registration_codes
        WHERE organization_id = :organizationId AND client_id = :clientId
        LIMIT 1
        """,
    )
    suspend fun getCached(
        organizationId: String,
        clientId: String,
    ): OptimalRegistrationCodeEntity?

    @Query(
        """
        SELECT * FROM optimal_registration_codes
        WHERE organization_id = :organizationId
          AND client_id = :clientId
          AND used_at IS NULL
          AND expires_at > :now
        LIMIT 1
        """,
    )
    suspend fun getActive(
        organizationId: String,
        clientId: String,
        now: Long,
    ): OptimalRegistrationCodeEntity?

    @Query(
        """
        UPDATE optimal_registration_codes
        SET used_at = :usedAt
        WHERE organization_id = :organizationId
          AND client_id = :clientId
          AND used_at IS NULL
        """,
    )
    suspend fun markUsed(
        organizationId: String,
        clientId: String,
        usedAt: Long,
    ): Int

    @Query(
        """
        DELETE FROM optimal_registration_codes
        WHERE organization_id = :organizationId AND client_id = :clientId
        """,
    )
    suspend fun deleteForClient(organizationId: String, clientId: String): Int
}
