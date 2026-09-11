package com.verto.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.verto.app.data.local.*
import com.verto.app.data.local.entity.*
import com.verto.app.money.Money
import kotlinx.coroutines.flow.Flow

interface InventoryMovementDao : InventoryCostDao {
@Transaction
suspend fun insertMovements(movements: List<InventoryMovementEntity>) {
    movements.forEach { insertMovement(it) }
}

@Query("SELECT * FROM inventory_movements WHERE itemId = :itemId ORDER BY createdAt DESC")
fun getMovementsForItem(itemId: String): Flow<List<InventoryMovementEntity>>

@Query("SELECT * FROM inventory_movements WHERE invoiceId = :invoiceId")
suspend fun getMovementsForInvoice(invoiceId: String): List<InventoryMovementEntity>

@Query("SELECT * FROM inventory_movements WHERE invoiceId = :invoiceId")
suspend fun getMovementsByInvoice(invoiceId: String): List<InventoryMovementEntity>

@Query("SELECT * FROM inventory_movements ORDER BY createdAt DESC")
fun getAllMovements(): Flow<List<InventoryMovementEntity>>

@Query(
    """
    SELECT movement.*, item.name AS itemName
    FROM inventory_movements movement
    INNER JOIN inventory_items item ON item.id = movement.itemId
    WHERE movement.organization_id = :organizationId
      AND COALESCE(movement.occurred_at, movement.createdAt) >= :sinceEpochMillis
    ORDER BY COALESCE(movement.occurred_at, movement.createdAt) DESC, movement.id ASC
    LIMIT :limit
    """
)
fun observeActivityMovements(organizationId: String, sinceEpochMillis: Long, limit: Int): Flow<List<InventoryActivityMovementRow>>

@Query("SELECT * FROM inventory_movements WHERE movementType = :type AND createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
fun getMovementsByTypeInRange(type: String, from: Long, to: Long): Flow<List<InventoryMovementEntity>>
}
