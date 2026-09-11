package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE lifecycle_state = 'ACTIVE' ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE lifecycle_state = 'ACTIVE' AND date >= :from AND date < :to ORDER BY date DESC")
    fun getExpensesByDateRange(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT CAST(COALESCE(SUM(amount_minor), 0) AS REAL) / 100.0 FROM expenses WHERE lifecycle_state = 'ACTIVE' AND date >= :from AND date < :to")
    fun getTotalExpensesInRange(from: Long, to: Long): Flow<Double>
    @Query("SELECT category, CAST(COALESCE(SUM(amount_minor), 0) AS REAL) / 100.0 as total FROM expenses WHERE lifecycle_state = 'ACTIVE' AND date >= :from AND date < :to GROUP BY category ORDER BY total DESC")
    fun getExpensesByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)
    /** Session 310 REMOTE_APPLY: never creates dirty work; duplicate identity is reconciled by caller. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenseFromRemote(expense: ExpenseEntity): Long
    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseByIdSync(id: String): ExpenseEntity?

    /** SYNC-012: المصروفات المتسخة فقط (للرفع) + تصفير العلم بعد رفع ناجح. */
    @Query("SELECT * FROM expenses WHERE isDirty = 1")
    suspend fun getDirtyExpensesSync(): List<ExpenseEntity>

    @Query("UPDATE expenses SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun markExpensesClean(ids: List<String>)
    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM expenses WHERE lifecycle_state = 'ACTIVE' AND date >= :from AND date < :to")
    fun getTotalExpensesMinorInRange(from: Long, to: Long): Flow<Long>
    @Query("SELECT category, COALESCE(SUM(amount_minor), 0) AS totalMinor FROM expenses WHERE lifecycle_state = 'ACTIVE' AND date >= :from AND date < :to GROUP BY category ORDER BY totalMinor DESC")
    fun getExpensesByCategoryMinor(from: Long, to: Long): Flow<List<CategoryTotalMinor>>
}

data class CategoryTotal(val category: String, val total: Double)
data class CategoryTotalMinor(val category: String, val totalMinor: Long)
