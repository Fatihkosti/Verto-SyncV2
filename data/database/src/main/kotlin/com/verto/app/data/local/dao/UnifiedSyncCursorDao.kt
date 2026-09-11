package com.verto.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.SyncConflictEntity
import com.verto.app.data.local.entity.SyncCursorEntity
import com.verto.app.data.local.entity.SyncInboxEntity
import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.local.entity.SyncSequenceStateEntity
import java.security.MessageDigest

/**
 * Session 306 persistence primitives only. No network/worker/runtime wiring belongs here.
 *
 * Canonical future producer boundary (Session 307):
 * AppDatabase.withTransaction { domain write; enqueueMutation(...) }.
 *
 * Canonical future pull boundary (Session 308):
 * AppDatabase.withTransaction { insertInboxChecked(...); domain apply; advanceCursorOrThrow(...) }.
 * Nested Room transactions join the caller transaction, so sequence/outbox and inbox/cursor remain
 * representable inside one database commit without DataStore or network authority.
 */

interface UnifiedSyncCursorDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCursorRaw(entity: SyncCursorEntity): Long

    suspend fun insertInitialCursor(entity: SyncCursorEntity) {
        validateCursor(entity)
        check(insertCursorRaw(entity) != -1L) { "cursor insert failed" }
    }

    @Query("SELECT * FROM sync_cursor WHERE scope_id = :scopeId LIMIT 1")
    abstract suspend fun getCursor(scopeId: String): SyncCursorEntity?

    /** Finds the previous scope-bound cursor without treating organization identity as sufficient. */
    @Query(
        """
        SELECT * FROM sync_cursor
        WHERE organization_id = :organizationId
          AND sync_principal_id = :syncPrincipalId
          AND contract_family = :contractFamily
          AND contract_version = :contractVersion
          AND state = 'ACTIVE'
        ORDER BY updated_at DESC
        LIMIT 1
        """
    )
    abstract suspend fun getActiveCursorForPrincipal(
        organizationId: String,
        syncPrincipalId: String,
        contractFamily: String,
        contractVersion: Int,
    ): SyncCursorEntity?

    @Query(
        """
        UPDATE sync_cursor
        SET state = 'INVALIDATED', updated_at = :updatedAt
        WHERE scope_id = :scopeId
          AND organization_id = :organizationId
          AND sync_principal_id = :syncPrincipalId
          AND contract_family = :contractFamily
          AND contract_version = :contractVersion
          AND scope_definition_version = :scopeDefinitionVersion
        """
    )
    abstract suspend fun invalidateCursor(
        scopeId: String,
        organizationId: String,
        syncPrincipalId: String,
        contractFamily: String,
        contractVersion: Int,
        scopeDefinitionVersion: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE sync_cursor
        SET state = 'BOOTSTRAP_REQUIRED', updated_at = :updatedAt
        WHERE scope_id = :scopeId
          AND organization_id = :organizationId
          AND sync_principal_id = :syncPrincipalId
          AND contract_family = :contractFamily
          AND contract_version = :contractVersion
          AND scope_definition_version = :scopeDefinitionVersion
          AND state <> 'INVALIDATED'
        """
    )
    abstract suspend fun markBootstrapRequired(
        scopeId: String,
        organizationId: String,
        syncPrincipalId: String,
        contractFamily: String,
        contractVersion: Int,
        scopeDefinitionVersion: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE sync_cursor
        SET cursor_token = :nextCursor,
            received_cursor_token = :nextCursor,
            received_high_watermark = COALESCE(:pageHighWatermark, received_high_watermark),
            applied_checkpoint = COALESCE(:lastAppliedChangeRevision, applied_checkpoint),
            last_applied_change_revision = COALESCE(:lastAppliedChangeRevision, last_applied_change_revision),
            page_high_watermark = COALESCE(:pageHighWatermark, page_high_watermark),
            min_available_revision = :minAvailableRevision,
            updated_at = :updatedAt
        WHERE scope_id = :scopeId
          AND organization_id = :organizationId
          AND sync_principal_id = :syncPrincipalId
          AND contract_family = :contractFamily
          AND contract_version = :contractVersion
          AND scope_definition_version = :scopeDefinitionVersion
          AND cursor_token = :expectedCursor
          AND (:lastAppliedChangeRevision IS NULL OR applied_checkpoint IS NULL
               OR :lastAppliedChangeRevision >= applied_checkpoint)
          AND state = 'ACTIVE'
        """
    )
    abstract suspend fun compareAndSetAdvanceRaw(
        scopeId: String,
        organizationId: String,
        syncPrincipalId: String,
        contractFamily: String,
        contractVersion: Int,
        scopeDefinitionVersion: Int,
        expectedCursor: String,
        nextCursor: String,
        lastAppliedChangeRevision: Long?,
        pageHighWatermark: Long?,
        minAvailableRevision: Long?,
        updatedAt: Long,
    ): Int

    /** Must be called inside the same AppDatabase.withTransaction as inbox/domain apply in Session 308. */
    suspend fun advanceCursorOrThrow(
        scopeId: String,
        organizationId: String,
        syncPrincipalId: String,
        contractFamily: String,
        contractVersion: Int,
        scopeDefinitionVersion: Int,
        expectedCursor: String,
        nextCursor: String,
        lastAppliedChangeRevision: Long?,
        pageHighWatermark: Long?,
        minAvailableRevision: Long?,
        updatedAt: Long,
    ) {
        require(expectedCursor.isNotBlank() && nextCursor.isNotBlank()) { "opaque cursor token must be nonblank" }
        check(
            compareAndSetAdvanceRaw(
                scopeId,
                organizationId,
                syncPrincipalId,
                contractFamily,
                contractVersion,
                scopeDefinitionVersion,
                expectedCursor,
                nextCursor,
                lastAppliedChangeRevision,
                pageHighWatermark,
                minAvailableRevision,
                updatedAt,
            ) == 1
        ) { "FAIL_STALE_CURSOR_WRITE" }
    }

}
