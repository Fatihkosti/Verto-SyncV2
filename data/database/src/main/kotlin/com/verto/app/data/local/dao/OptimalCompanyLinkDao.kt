package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.verto.app.data.local.entity.OptimalCompanyLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimalCompanyLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(link: OptimalCompanyLinkEntity)

    @Update
    suspend fun update(link: OptimalCompanyLinkEntity): Int

    @Query(
        """
        SELECT * FROM optimal_company_links
        WHERE organization_id = :organizationId AND client_id = :clientId
        LIMIT 1
        """,
    )
    suspend fun get(organizationId: String, clientId: String): OptimalCompanyLinkEntity?

    @Query(
        """
        SELECT * FROM optimal_company_links
        WHERE organization_id = :organizationId
        ORDER BY linked_at DESC
        """,
    )
    fun observeForOrganization(organizationId: String): Flow<List<OptimalCompanyLinkEntity>>

    /** Scoped cleanup replaces an unsafe foreign-key cascade through the legacy clients table. */
    @Query(
        """
        DELETE FROM optimal_company_links
        WHERE organization_id = :organizationId AND client_id = :clientId
        """,
    )
    suspend fun deleteForClient(organizationId: String, clientId: String): Int
}
