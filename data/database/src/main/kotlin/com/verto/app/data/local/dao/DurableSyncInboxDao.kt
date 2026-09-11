package com.verto.app.data.local.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.SyncInboxApplyRequestEntity
import com.verto.app.data.local.entity.SyncInboxDependencyEntity
import com.verto.app.data.local.entity.SyncInboxEntity
import com.verto.app.data.local.entity.SyncInboxGroupEntity
import com.verto.app.data.local.entity.SyncInboxTouchedKeyEntity

/** B10 metadata primitives. Receive/apply callers own the outer Room transaction. */
interface DurableSyncInboxDao {
    @Query("""SELECT * FROM sync_inbox_group WHERE scope_id=:scopeId
        AND state IN ('RECEIVED','READY','WAITING_LOCAL','WAITING_DEPENDENCY')
        AND last_attempt_generation<:generation ORDER BY first_revision LIMIT :limit""")
    suspend fun inboxApplyCandidates(scopeId: String, generation: Long, limit: Int): List<SyncInboxGroupEntity>

    @Query("""UPDATE sync_inbox_group SET last_attempt_generation=:generation
        WHERE scope_id=:scopeId AND transaction_id=:transactionId AND state<>'APPLIED'""")
    suspend fun markInboxGroupAttempt(scopeId: String, transactionId: String, generation: Long): Int

    @Query("""UPDATE sync_inbox_apply_request SET next_wake_at=:wakeAt, updated_at=:now
        WHERE scope_id=:scopeId AND requested_generation>drained_generation""")
    suspend fun scheduleInboxContinuation(scopeId: String, wakeAt: Long, now: Long): Int

    @Query("""SELECT EXISTS(SELECT 1 FROM sync_inbox i JOIN sync_inbox_group g
        ON g.scope_id=i.scope_id AND g.transaction_id=i.transaction_id
        WHERE i.scope_id=:scopeId AND g.first_revision<:firstRevision AND g.state<>'APPLIED'
        AND i.aggregate_type IN ('INVOICE','PAYMENT','EXPENSE','CASH_MOVEMENT','CASH_RECONCILIATION'))""")
    suspend fun hasUnappliedBalancePredecessor(scopeId: String, firstRevision: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInboxGroupRaw(group: SyncInboxGroupEntity): Long

    @Query("SELECT * FROM sync_inbox_group WHERE scope_id=:scopeId AND transaction_id=:transactionId LIMIT 1")
    suspend fun getInboxGroup(scopeId: String, transactionId: String): SyncInboxGroupEntity?

    @Query("""SELECT * FROM sync_inbox_group WHERE scope_id=:scopeId AND first_revision>:afterRevision
        ORDER BY first_revision LIMIT :limit""")
    suspend fun listInboxGroupsAfter(scopeId: String, afterRevision: Long, limit: Int): List<SyncInboxGroupEntity>

    @Query("""SELECT * FROM sync_inbox_group WHERE scope_id=:scopeId AND state<>'APPLIED'
        AND first_revision>:afterRevision ORDER BY first_revision LIMIT :limit""")
    suspend fun listUnappliedInboxGroupsAfter(scopeId: String, afterRevision: Long, limit: Int): List<SyncInboxGroupEntity>

    @Query("""SELECT * FROM sync_inbox WHERE scope_id=:scopeId AND transaction_id=:transactionId
        ORDER BY transaction_order""")
    suspend fun listInboxGroupMembers(scopeId: String, transactionId: String): List<SyncInboxEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInboxDependencies(rows: List<SyncInboxDependencyEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInboxTouchedKeys(rows: List<SyncInboxTouchedKeyEntity>)

    @Query("""SELECT d.depends_on_transaction_id FROM sync_inbox_dependency d
        LEFT JOIN sync_inbox_group g ON g.scope_id=d.scope_id AND g.transaction_id=d.depends_on_transaction_id
        WHERE d.scope_id=:scopeId AND d.transaction_id=:transactionId AND (g.state IS NULL OR g.state<>'APPLIED')
        ORDER BY d.depends_on_transaction_id""")
    suspend fun missingInboxDependencies(scopeId: String, transactionId: String): List<String>

    @Query("""WITH RECURSIVE prerequisites(transaction_id, depends_on_transaction_id) AS (
        SELECT transaction_id,depends_on_transaction_id FROM sync_inbox_dependency WHERE scope_id=:scopeId
        UNION
        SELECT p.transaction_id,d.depends_on_transaction_id FROM prerequisites p JOIN sync_inbox_dependency d
            ON d.scope_id=:scopeId AND d.transaction_id=p.depends_on_transaction_id
        ) SELECT EXISTS(SELECT 1 FROM sync_inbox_touched_key own_key
        JOIN sync_inbox_touched_key earlier_key ON earlier_key.scope_id=own_key.scope_id
            AND earlier_key.key_type=own_key.key_type AND earlier_key.key_id=own_key.key_id
        JOIN sync_inbox_group earlier_group ON earlier_group.scope_id=earlier_key.scope_id
            AND earlier_group.transaction_id=earlier_key.transaction_id
        WHERE own_key.scope_id=:scopeId AND own_key.transaction_id=:transactionId
            AND earlier_group.first_revision<:firstRevision AND earlier_group.state<>'APPLIED'
            AND NOT (earlier_group.state='WAITING_DEPENDENCY' AND EXISTS(SELECT 1 FROM prerequisites p
                WHERE p.transaction_id=earlier_group.transaction_id AND p.depends_on_transaction_id=:transactionId)))""")
    suspend fun hasEarlierUnappliedTouch(scopeId: String, transactionId: String, firstRevision: Long): Boolean

    @Query("""UPDATE sync_inbox_group SET state=:state, wait_reason=:reason, updated_at=:now,
        applied_at=CASE WHEN :state='APPLIED' THEN :now ELSE NULL END
        WHERE scope_id=:scopeId AND transaction_id=:transactionId AND state<>'APPLIED'""")
    suspend fun setInboxGroupStateRaw(scopeId: String, transactionId: String, state: String, reason: String?, now: Long): Int

    @Query("""UPDATE sync_inbox SET apply_state=:state, apply_error_code=:reason,
        applied_at=CASE WHEN :state='APPLIED' THEN :now ELSE NULL END
        WHERE scope_id=:scopeId AND transaction_id=:transactionId AND apply_state<>'APPLIED'""")
    suspend fun setInboxMembersStateRaw(scopeId: String, transactionId: String, state: String, reason: String?, now: Long): Int

    @Query("SELECT COALESCE(SUM(serialized_bytes),0) FROM sync_inbox_group WHERE scope_id=:scopeId AND state<>'APPLIED'")
    suspend fun unappliedInboxBytes(scopeId: String): Long

    @Query("SELECT COUNT(*) FROM sync_inbox_group WHERE scope_id=:scopeId AND state<>'APPLIED'")
    suspend fun unappliedInboxGroupCount(scopeId: String): Long

    @Query("SELECT COUNT(*) FROM sync_inbox_group WHERE scope_id=:scopeId AND state='REQUIRES_REVIEW'")
    suspend fun reviewInboxGroupCount(scopeId: String): Long

    @Query("""SELECT COUNT(*) FROM sync_inbox i LEFT JOIN sync_inbox_group g
        ON g.scope_id=i.scope_id AND g.transaction_id=i.transaction_id
        WHERE i.scope_id=:scopeId AND i.apply_state<>'APPLIED' AND (g.transaction_id IS NULL OR g.serialized_bytes<=0)""")
    suspend fun unprovenInboxRowCount(scopeId: String): Long

    @Query("""SELECT COUNT(*) FROM sync_inbox WHERE organization_id=:organizationId AND apply_state<>'APPLIED'""")
    suspend fun unconfirmedInboxRowCount(organizationId: String): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInboxApplyRequestRaw(row: SyncInboxApplyRequestEntity): Long

    @Query("SELECT * FROM sync_inbox_apply_request WHERE scope_id=:scopeId LIMIT 1")
    suspend fun getInboxApplyRequest(scopeId: String): SyncInboxApplyRequestEntity?

    @Query("SELECT * FROM sync_inbox_apply_request WHERE organization_id=:organizationId AND next_wake_at IS NOT NULL")
    suspend fun pendingInboxWakes(organizationId: String): List<SyncInboxApplyRequestEntity>

    @Query("""UPDATE sync_inbox_apply_request SET requested_generation=requested_generation+1,
        next_wake_at=CASE WHEN next_wake_at IS NULL OR next_wake_at>:wakeAt THEN :wakeAt ELSE next_wake_at END,
        updated_at=:now WHERE scope_id=:scopeId AND organization_id=:organizationId""")
    suspend fun incrementInboxRequestRaw(scopeId: String, organizationId: String, wakeAt: Long, now: Long): Int

    @Transaction
    suspend fun requestInboxApply(scopeId: String, organizationId: String, wakeAt: Long, now: Long): Long {
        require(scopeId.isNotBlank() && organizationId.isNotBlank() && wakeAt >= 0L && now >= 0L)
        insertInboxApplyRequestRaw(SyncInboxApplyRequestEntity(scopeId, organizationId, 0, 0, null, null, now))
        check(incrementInboxRequestRaw(scopeId, organizationId, wakeAt, now) == 1) { "SCOPE_MISMATCH" }
        return checkNotNull(getInboxApplyRequest(scopeId)).requestedGeneration
    }

    /** Called inside the same transaction which proves a real owner's terminal outcome. */
    @Query("""UPDATE sync_inbox_apply_request SET requested_generation=requested_generation+1,
        next_wake_at=CASE WHEN next_wake_at IS NULL OR next_wake_at>:now THEN :now ELSE next_wake_at END,
        updated_at=:now WHERE organization_id=:organizationId""")
    suspend fun requestInboxApplyForOrganization(organizationId: String, now: Long): Int

    /** Do not consume a request that arrived while this bounded pass was running. */
    @Query("""UPDATE sync_inbox_apply_request SET drained_generation=:observedGeneration, next_wake_at=NULL,
        updated_at=:now WHERE scope_id=:scopeId AND requested_generation=:observedGeneration
        AND drained_generation<=:observedGeneration""")
    suspend fun drainInboxRequestIfUnchanged(scopeId: String, observedGeneration: Long, now: Long): Int

    @Query("""UPDATE sync_inbox_apply_request SET storage_wait_reason=:reason, updated_at=:now
        WHERE scope_id=:scopeId AND organization_id=:organizationId""")
    suspend fun setInboxStorageWait(scopeId: String, organizationId: String, reason: String?, now: Long): Int

    /** B10 receive CAS: never writes applied_checkpoint or last_applied_change_revision. */
    @Query("""UPDATE sync_cursor SET cursor_token=:nextCursor, received_cursor_token=:nextCursor,
        received_high_watermark=:receivedThrough, page_high_watermark=:serverHigh,
        min_available_revision=:minimumRevision, updated_at=:now
        WHERE scope_id=:scopeId AND organization_id=:organizationId AND sync_principal_id=:principalId
        AND contract_family=:family AND contract_version=:version AND scope_definition_version=:definition
        AND state='ACTIVE' AND received_cursor_token=:expectedCursor AND cursor_token=:expectedCursor
        AND (received_high_watermark IS NULL OR received_high_watermark<=:receivedThrough)""")
    suspend fun advanceReceivedCursor(
        scopeId: String, organizationId: String, principalId: String, family: String,
        version: Int, definition: Int, expectedCursor: String, nextCursor: String,
        receivedThrough: Long, serverHigh: Long, minimumRevision: Long?, now: Long,
    ): Int

    /** Only the covered APPLIED prefix computed under the domain transaction may advance this. */
    @Query("""UPDATE sync_cursor SET applied_checkpoint=:checkpoint, last_applied_change_revision=:checkpoint, updated_at=:now
        WHERE scope_id=:scopeId AND organization_id=:organizationId AND state='ACTIVE'
        AND (applied_checkpoint IS NULL OR applied_checkpoint<=:checkpoint)
        AND received_high_watermark>=:checkpoint""")
    suspend fun advanceAppliedCheckpoint(scopeId: String, organizationId: String, checkpoint: Long, now: Long): Int
}
