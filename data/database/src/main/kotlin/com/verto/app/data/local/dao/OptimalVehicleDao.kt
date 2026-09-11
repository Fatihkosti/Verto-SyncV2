package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.OptimalVehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimalVehicleDao {
    @Query(
        """
        SELECT *
        FROM optimal_vehicles
        WHERE organization_id = :organizationId
          AND client_id = :clientId
          AND (
              :searchTerm = ''
              OR instr(lower(name), lower(:searchTerm)) > 0
              OR instr(lower(vehicle_type), lower(:searchTerm)) > 0
              OR instr(lower(plate_number), lower(:searchTerm)) > 0
          )
        ORDER BY
          CASE WHEN lower(plate_number) = lower(:searchTerm) AND :searchTerm != '' THEN 0 ELSE 1 END,
          name COLLATE NOCASE ASC,
          plate_number COLLATE NOCASE ASC,
          remote_vehicle_id ASC
        LIMIT :limit
        """,
    )
    fun observeSuggestions(
        organizationId: String,
        clientId: String,
        searchTerm: String,
        limit: Int,
    ): Flow<List<OptimalVehicleEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM optimal_company_links
            WHERE organization_id = :organizationId
              AND client_id = :clientId
        )
        """,
    )
    suspend fun isLinkedCompany(organizationId: String, clientId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(vehicles: List<OptimalVehicleEntity>)

    /** Session 310 REMOTE_APPLY for one server-authoritative vehicle snapshot; no outbox side effect. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(vehicle: OptimalVehicleEntity)

    @Query(
        """
        DELETE FROM optimal_vehicles
        WHERE organization_id = :organizationId
          AND client_id = :clientId
        """,
    )
    suspend fun deleteForClient(organizationId: String, clientId: String): Int

    @Query(
        """
        DELETE FROM optimal_vehicles
        WHERE organization_id = :organizationId
        """,
    )
    suspend fun deleteForOrganization(organizationId: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM optimal_vehicles
        WHERE organization_id = :organizationId
          AND client_id = :clientId
        """,
    )
    suspend fun countForClient(organizationId: String, clientId: String): Int

    /**
     * Atomically replaces one linked company's cache. An empty source snapshot removes stale
     * vehicles. A malformed or unlinked snapshot fails before deleting the last good cache.
     */
    @Transaction
    suspend fun replaceForLinkedClient(
        organizationId: String,
        clientId: String,
        vehicles: List<OptimalVehicleEntity>,
    ): Boolean {
        val normalizedOrganizationId = organizationId.trim()
        val normalizedClientId = clientId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        require(normalizedClientId.isNotEmpty()) { "clientId is required" }
        require(
            vehicles.all {
                it.organizationId == normalizedOrganizationId &&
                    it.clientId == normalizedClientId &&
                    it.remoteVehicleId.isNotBlank()
            },
        ) { "vehicle snapshot crosses tenant or client boundary" }

        if (!isLinkedCompany(normalizedOrganizationId, normalizedClientId)) return false
        deleteForClient(normalizedOrganizationId, normalizedClientId)
        if (vehicles.isNotEmpty()) insertAll(vehicles)
        return true
    }
}
