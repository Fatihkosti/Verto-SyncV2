package com.verto.app.data.repository

import com.verto.app.data.local.dao.RfmCacheDao
import com.verto.app.data.local.entity.RfmCacheEntity
import com.verto.app.data.local.entity.RfmSegment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RfmRepository @Inject constructor(
    private val dao: RfmCacheDao
) {
    fun getAll() = dao.getAll()
    fun getBySegment(segment: RfmSegment) = dao.getBySegment(segment)
    fun getSegmentDistribution() = dao.getSegmentDistribution()

    suspend fun getByClientId(clientId: String) = dao.getByClientId(clientId)
    suspend fun getLastCalculatedAt() = dao.getLastCalculatedAt()

    suspend fun upsert(rfm: RfmCacheEntity) = dao.upsert(rfm)
    suspend fun upsertAll(rfms: List<RfmCacheEntity>) = dao.upsertAll(rfms)
    suspend fun clearAll() = dao.clearAll()
    suspend fun deleteByClientId(clientId: String) = dao.deleteByClientId(clientId)
}
