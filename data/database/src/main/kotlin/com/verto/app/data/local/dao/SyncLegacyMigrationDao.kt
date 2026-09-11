package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.verto.app.data.local.entity.SyncLegacyMigrationEntryEntity
import com.verto.app.data.local.entity.SyncLegacyMigrationStateEntity

@Dao
interface SyncLegacyMigrationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: SyncLegacyMigrationEntryEntity): Long

    @Query(
        """
        SELECT * FROM sync_legacy_migration_entry
        WHERE organization_id = :organizationId AND source_kind = :sourceKind AND source_id = :sourceId
        LIMIT 1
        """
    )
    suspend fun getEntry(organizationId: String, sourceKind: String, sourceId: String): SyncLegacyMigrationEntryEntity?

    @Query(
        """
        UPDATE sync_legacy_migration_entry
        SET source_state = :sourceState,
            source_sequence = :sourceSequence,
            command_batch_id = :commandBatchId,
            command_order = :commandOrder,
            depends_on_source_id = :dependsOnSourceId,
            target_kind = :targetKind,
            target_mutation_id = :targetMutationId,
            disposition = :disposition,
            reason_code = :reasonCode,
            updated_at = :updatedAt
        WHERE organization_id = :organizationId AND source_kind = :sourceKind AND source_id = :sourceId
        """
    )
    suspend fun updateOutcome(
        organizationId: String,
        sourceKind: String,
        sourceId: String,
        sourceState: String,
        sourceSequence: Long?,
        commandBatchId: String?,
        commandOrder: Int?,
        dependsOnSourceId: String?,
        targetKind: String,
        targetMutationId: String?,
        disposition: String,
        reasonCode: String?,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT * FROM sync_legacy_migration_entry
        WHERE organization_id = :organizationId
        ORDER BY source_kind, source_sequence, source_id
        """
    )
    suspend fun listEntries(organizationId: String): List<SyncLegacyMigrationEntryEntity>

    @Query("SELECT COUNT(*) FROM sync_legacy_migration_entry WHERE organization_id = :organizationId")
    suspend fun countEntries(organizationId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM sync_legacy_migration_entry
        WHERE organization_id = :organizationId AND disposition = :disposition
        """
    )
    suspend fun countDisposition(organizationId: String, disposition: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: SyncLegacyMigrationStateEntity)

    @Query(
        """
        SELECT * FROM sync_legacy_migration_state
        WHERE organization_id = :organizationId AND sync_principal_id = :syncPrincipalId
        LIMIT 1
        """
    )
    suspend fun getState(organizationId: String, syncPrincipalId: String): SyncLegacyMigrationStateEntity?
}
