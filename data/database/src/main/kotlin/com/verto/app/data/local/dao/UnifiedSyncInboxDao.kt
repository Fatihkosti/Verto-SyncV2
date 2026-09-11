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

interface UnifiedSyncInboxDao {
    // ── Inbox scoped dedupe + immutable content ───────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertInboxRaw(entity: SyncInboxEntity): Long

    @Query(
        """
        SELECT * FROM sync_inbox
        WHERE scope_id = :scopeId AND server_revision = :serverRevision
        LIMIT 1
        """
    )
    abstract suspend fun getInbox(scopeId: String, serverRevision: Long): SyncInboxEntity?

    /** Same scope+revision+fingerprint is a no-op; divergent content fails closed. */
    @Transaction
    open suspend fun insertInboxChecked(entity: SyncInboxEntity): UnifiedInboxInsertResult {
        validateInbox(entity)
        val inserted = insertInboxRaw(entity)
        if (inserted != -1L) return UnifiedInboxInsertResult.INSERTED
        val existing = checkNotNull(getInbox(entity.scopeId, entity.serverRevision))
        require(existing.contentFingerprint == entity.contentFingerprint && existing.sameImmutableContent(entity)) {
            "FAIL_DUPLICATE_INBOX_CONTENT"
        }
        return UnifiedInboxInsertResult.DUPLICATE
    }

    @Query(
        """
        UPDATE sync_inbox
        SET apply_state = 'APPLIED', apply_error_code = NULL, applied_at = :appliedAt
        WHERE scope_id = :scopeId
          AND server_revision = :serverRevision
          AND apply_state IN ('RECEIVED','READY')
        """
    )
    abstract suspend fun markInboxApplied(scopeId: String, serverRevision: Long, appliedAt: Long): Int

    @Query(
        """
        UPDATE sync_inbox
        SET apply_state = 'REQUIRES_REVIEW', apply_error_code = :errorCode, applied_at = NULL
        WHERE scope_id = :scopeId
          AND server_revision = :serverRevision
          AND apply_state = 'RECEIVED'
        """
    )
    abstract suspend fun markInboxRequiresReview(scopeId: String, serverRevision: Long, errorCode: String): Int

    @Query(
        """
        SELECT * FROM sync_inbox
        WHERE scope_id = :scopeId
        ORDER BY server_revision DESC
        LIMIT :limit
        """
    )
    abstract suspend fun listInboxDiagnosticsRaw(scopeId: String, limit: Int): List<SyncInboxEntity>

    suspend fun listInboxDiagnostics(scopeId: String, limit: Int): List<SyncInboxEntity> {
        require(limit in 1..MAX_WORKER_OPERATIONS_PER_RUN) { "inbox diagnostic limit must be 1..500" }
        return listInboxDiagnosticsRaw(scopeId, limit)
    }

    // ── Opaque scoped cursor CAS ───────────────────────────────────────────────────────────────
}
