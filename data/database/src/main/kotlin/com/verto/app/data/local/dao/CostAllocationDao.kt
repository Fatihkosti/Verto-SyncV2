package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.CostAllocationEntity
import com.verto.app.data.local.entity.CostAllocationSource
import kotlinx.coroutines.flow.Flow

@Dao
interface CostAllocationDao {

    @Query("SELECT * FROM cost_allocations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CostAllocationEntity>>

    @Query("SELECT * FROM cost_allocations WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun getByItemId(itemId: String): Flow<List<CostAllocationEntity>>

    @Query("SELECT * FROM cost_allocations WHERE sourceId = :sourceId")
    fun getBySourceId(sourceId: String): Flow<List<CostAllocationEntity>>

    @Query("""
        SELECT itemId, SUM(perUnitCost) as totalPerUnit
        FROM cost_allocations
        WHERE itemId IN (:itemIds)
        GROUP BY itemId
    """)
    suspend fun getTotalAllocatedPerUnit(itemIds: List<String>): List<ItemAllocationSum>

    @Query("""
        SELECT SUM(allocatedAmount) FROM cost_allocations
        WHERE sourceType = :sourceType
          AND createdAt >= :from
          AND createdAt <= :to
    """)
    suspend fun getTotalAllocatedInRange(
        sourceType: CostAllocationSource,
        from: Long,
        to: Long
    ): Double?

    @Insert
    suspend fun insert(allocation: CostAllocationEntity)

    @Query("SELECT * FROM cost_allocations WHERE id = :id LIMIT 1")
    suspend fun getByIdSync(id: String): CostAllocationEntity?

    /** Session 310 immutable REMOTE_APPLY; divergent duplicate is checked by the caller. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFromRemote(allocation: CostAllocationEntity): Long

    @Insert
    suspend fun insertAll(allocations: List<CostAllocationEntity>)

    @Query("DELETE FROM cost_allocations WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    /** SYNC-014.b: مزامنة — قراءة الكل + upsert دفعي + حذف بمعرّفات. */
    @Query("SELECT * FROM cost_allocations")
    suspend fun getAllSync(): List<CostAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllFromRemote(items: List<CostAllocationEntity>)

    @Query("DELETE FROM cost_allocations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

data class ItemAllocationSum(
    val itemId: String,
    val totalPerUnit: Double
)
