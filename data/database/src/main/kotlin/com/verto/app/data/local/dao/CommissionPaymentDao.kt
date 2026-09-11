package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.CommissionPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommissionPaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: CommissionPaymentEntity)

    @Query("SELECT * FROM commission_payments ORDER BY paidAt DESC")
    fun getAll(): Flow<List<CommissionPaymentEntity>>

    @Query("SELECT * FROM commission_payments ORDER BY paidAt DESC")
    suspend fun getAllSync(): List<CommissionPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFromRemote(payment: CommissionPaymentEntity)

    @Query("DELETE FROM commission_payments WHERE id = :id")
    suspend fun deleteById(id: String)
}
