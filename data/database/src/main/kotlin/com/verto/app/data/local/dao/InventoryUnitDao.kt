package com.verto.app.data.local.dao

import androidx.room.*
import com.verto.app.data.local.entity.InventoryUnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryUnitDao {

    @Query("SELECT * FROM inventory_units ORDER BY name ASC")
    fun getAllUnits(): Flow<List<InventoryUnitEntity>>

    @Query("SELECT * FROM inventory_units ORDER BY name ASC")
    suspend fun getAllUnitsSync(): List<InventoryUnitEntity>

    @Query("SELECT * FROM inventory_units WHERE id = :id")
    suspend fun getUnitById(id: String): InventoryUnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: InventoryUnitEntity)

    @Update
    suspend fun updateUnit(unit: InventoryUnitEntity)

    @Query("DELETE FROM inventory_units WHERE id = :id")
    suspend fun deleteUnit(id: String)

    /** SYNC-014.a: حذف الوحدات المحلية التي لم تَعُد على السيرفر (مرآة المزامنة). */
    @Query("DELETE FROM inventory_units WHERE id NOT IN (:ids)")
    suspend fun deleteUnitsNotIn(ids: List<String>)
}
