package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.verto.app.data.local.entity.OrganizationSettingsLocalEntity
import com.verto.app.data.local.entity.SyncAttachmentTransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnifiedSyncProducerV307Dao {
    @Query("SELECT * FROM organization_settings_local WHERE organization_id = :organizationId LIMIT 1")
    suspend fun getOrganizationSettings(organizationId: String): OrganizationSettingsLocalEntity?

    @Query("SELECT * FROM organization_settings_local WHERE organization_id = :organizationId LIMIT 1")
    fun observeOrganizationSettings(organizationId: String): Flow<OrganizationSettingsLocalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrganizationSettings(entity: OrganizationSettingsLocalEntity)

    @Query("SELECT * FROM organization_settings_local WHERE organization_id = :organizationId AND is_dirty = 1 LIMIT 1")
    suspend fun getDirtyOrganizationSettings(organizationId: String): OrganizationSettingsLocalEntity?

    @Query(
        """
        UPDATE organization_settings_local
        SET is_dirty = 0
        WHERE organization_id = :organizationId
          AND updated_at = :expectedUpdatedAt
          AND is_dirty = 1
        """
    )
    suspend fun markOrganizationSettingsClean(organizationId: String, expectedUpdatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachmentTransfer(entity: SyncAttachmentTransferEntity)

    @Query("SELECT * FROM sync_attachment_transfer WHERE transfer_id = :transferId LIMIT 1")
    suspend fun getAttachmentTransfer(transferId: String): SyncAttachmentTransferEntity?

    @Query("SELECT COUNT(*) FROM sync_attachment_transfer WHERE organization_id = :organizationId AND state IN ('PENDING','LEASED','RETRY')")
    suspend fun countPendingAttachmentTransfers(organizationId: String): Long
}
