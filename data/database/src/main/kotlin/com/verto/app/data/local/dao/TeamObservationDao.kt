package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.TeamObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamObservationDao {
    @Query(
        """
        SELECT * FROM team_observations
        WHERE organization_id = :organizationId
        ORDER BY
            CASE status WHEN 'NEW' THEN 0 WHEN 'REVIEWED' THEN 1 ELSE 2 END ASC,
            is_important DESC,
            created_at DESC
        """,
    )
    fun observeForOrganization(organizationId: String): Flow<List<TeamObservationEntity>>

    @Query(
        """
        SELECT * FROM team_observations
        WHERE organization_id = :organizationId
          AND observation_id = :observationId
        LIMIT 1
        """,
    )
    suspend fun getById(organizationId: String, observationId: String): TeamObservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TeamObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemote(entities: List<TeamObservationEntity>)

    @Query(
        """
        SELECT * FROM team_observations
        WHERE organization_id = :organizationId AND is_dirty = 1
        ORDER BY updated_at ASC
        """,
    )
    suspend fun getDirty(organizationId: String): List<TeamObservationEntity>

    @Query(
        """
        UPDATE team_observations
        SET is_dirty = 0
        WHERE organization_id = :organizationId
          AND observation_id IN (:observationIds)
        """,
    )
    suspend fun markClean(organizationId: String, observationIds: List<String>): Int
}
