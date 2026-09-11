package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.LogisticsShipmentNumberSequenceEntity

@Dao
interface LogisticsShipmentNumberDao {
    @Query(
        "SELECT MAX(CAST(shipment_number AS INTEGER)) FROM logistics_shipments " +
            "WHERE organization_id = :organizationId " +
            "AND shipment_number != '' " +
            "AND shipment_number NOT GLOB '*[^0-9]*'",
    )
    suspend fun getPersistedMax(organizationId: String): Int?

    @Query("SELECT * FROM logistics_shipment_number_sequences WHERE organization_id = :organizationId LIMIT 1")
    suspend fun getSequence(organizationId: String): LogisticsShipmentNumberSequenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSequence(entity: LogisticsShipmentNumberSequenceEntity)

    /** Atomic organization-scoped display-number allocation; never uses an unprotected MAX()+1. */
    @Transaction
    suspend fun allocateNext(organizationId: String, remoteCandidate: Int? = null): Int? {
        require(organizationId.isNotBlank()) { "organizationId is required" }
        val floor = maxOf(getSequence(organizationId)?.lastNumber ?: 0, getPersistedMax(organizationId) ?: 0)
        val next = when {
            remoteCandidate != null && remoteCandidate > floor -> remoteCandidate
            floor < Int.MAX_VALUE -> floor + 1
            else -> return null
        }
        upsertSequence(LogisticsShipmentNumberSequenceEntity(organizationId, next))
        return next
    }
}
