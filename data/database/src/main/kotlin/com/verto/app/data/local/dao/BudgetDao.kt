package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.BudgetEntity
import com.verto.app.data.local.entity.BudgetType
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY periodStart DESC")
    fun getAllActiveBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY periodStart DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("""
        SELECT * FROM budgets
        WHERE isActive = 1
          AND periodStart <= :now
          AND periodEnd >= :now
        ORDER BY budgetType ASC
    """)
    fun getCurrentBudgets(now: Long = System.currentTimeMillis()): Flow<List<BudgetEntity>>

    @Query("""
        SELECT * FROM budgets
        WHERE isActive = 1
          AND budgetType = :type
          AND periodStart <= :now
          AND periodEnd >= :now
        LIMIT 1
    """)
    suspend fun getActiveBudgetForType(type: BudgetType, now: Long = System.currentTimeMillis()): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    /** SYNC-014.b: قراءة كل الميزانيات للمزامنة. */
    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsSync(): List<BudgetEntity>
}
