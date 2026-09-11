package com.verto.app.data.repository

import com.verto.app.data.local.dao.CostAllocationDao
import com.verto.app.data.local.entity.CostAllocationEntity
import com.verto.app.data.local.entity.CostAllocationSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CostAllocationRepository @Inject constructor(
    private val dao: CostAllocationDao
) {
    fun getAll() = dao.getAll()
    fun getByItemId(itemId: String) = dao.getByItemId(itemId)
    fun getBySourceId(sourceId: String) = dao.getBySourceId(sourceId)

    suspend fun getTotalAllocatedPerUnit(itemIds: List<String>): Map<String, Double> {
        if (itemIds.isEmpty()) return emptyMap()
        return dao.getTotalAllocatedPerUnit(itemIds)
            .associate { it.itemId to it.totalPerUnit }
    }

    suspend fun getTotalAllocatedInRange(source: CostAllocationSource, from: Long, to: Long): Double =
        dao.getTotalAllocatedInRange(source, from, to) ?: 0.0

    suspend fun saveAllocation(allocation: CostAllocationEntity) = dao.insert(allocation)
    suspend fun saveAllocations(allocations: List<CostAllocationEntity>) = dao.insertAll(allocations)
    suspend fun deleteBySourceId(sourceId: String) = dao.deleteBySourceId(sourceId)
}
