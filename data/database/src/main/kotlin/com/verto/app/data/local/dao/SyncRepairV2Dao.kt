package com.verto.app.data.local.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.verto.app.data.local.entity.ExpenseRevisionHistoryEntity
import com.verto.app.data.local.entity.SyncEntityVersionEntity
import com.verto.app.data.local.entity.SyncLocalGenerationEntity
import com.verto.app.data.local.entity.SyncMutationPacketEntity
import com.verto.app.data.local.entity.SyncPendingReferenceEntity
import com.verto.app.data.local.entity.SyncSnapshotBlobEntity
import com.verto.app.data.local.entity.SyncWriteBatchEntity
import com.verto.app.data.local.entity.SyncWriteBatchMemberEntity

interface SyncRepairV2Dao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMutationPacketIfAbsent(packet: SyncMutationPacketEntity): Long

    @Query(
        """SELECT * FROM sync_mutation_packet
           WHERE organization_id=:organizationId AND mutation_id=:mutationId LIMIT 1"""
    )
    abstract suspend fun readMutationPacket(
        organizationId: String,
        mutationId: String,
    ): SyncMutationPacketEntity?

    @Query(
        """SELECT p.* FROM sync_mutation_packet p
           JOIN sync_outbox o ON p.source_owner='sync_outbox' AND o.mutation_id=p.source_id
           WHERE p.organization_id=:organizationId AND p.version_family=:versionFamily
             AND p.business_identity=:businessIdentity AND p.mutation_id<>:mutationId
             AND o.state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW','REJECTED')
           ORDER BY p.captured_generation DESC, p.created_at DESC, p.mutation_id DESC LIMIT 1"""
    )
    abstract suspend fun readLatestUnresolvedUnifiedPredecessor(
        organizationId: String,
        versionFamily: String,
        businessIdentity: String,
        mutationId: String,
    ): SyncMutationPacketEntity?

    @Query(
        """SELECT CASE WHEN COUNT(DISTINCT applied_server_version)=1
                       THEN MAX(applied_server_version) ELSE NULL END
           FROM sync_entity_version
           WHERE organization_id=:organizationId AND version_family=:versionFamily
             AND aggregate_id=:aggregateId AND applied_server_version IS NOT NULL"""
    )
    abstract suspend fun readUnambiguousAppliedVersion(
        organizationId: String,
        versionFamily: String,
        aggregateId: String,
    ): Long?

    @Query(
        """UPDATE sync_mutation_packet
           SET wire_json=:wireJson, wire_sha256=:wireSha256, prepared_at=:preparedAt
           WHERE organization_id=:organizationId AND mutation_id=:mutationId
             AND wire_json IS NULL AND wire_sha256 IS NULL AND prepared_at IS NULL"""
    )
    abstract suspend fun freezeMutationWireRaw(
        organizationId: String,
        mutationId: String,
        wireJson: String,
        wireSha256: String,
        preparedAt: Long,
    ): Int

    @Query(
        """UPDATE sync_mutation_packet SET first_dispatch_at=:dispatchedAt
           WHERE organization_id=:organizationId AND mutation_id=:mutationId
             AND first_dispatch_at IS NULL"""
    )
    abstract suspend fun recordFirstDispatch(
        organizationId: String,
        mutationId: String,
        dispatchedAt: Long,
    ): Int

    @Query(
        """SELECT * FROM sync_write_batch
           WHERE organization_id=:organizationId AND batch_id=:batchId LIMIT 1"""
    )
    abstract suspend fun readWriteBatch(organizationId: String, batchId: String): SyncWriteBatchEntity?

    @Query(
        """SELECT * FROM sync_write_batch_member
           WHERE organization_id=:organizationId AND batch_id=:batchId ORDER BY member_order"""
    )
    abstract suspend fun listWriteBatchMembers(
        organizationId: String,
        batchId: String,
    ): List<SyncWriteBatchMemberEntity>

    @Query(
        """UPDATE sync_write_batch
           SET wire_json=:wireJson, wire_sha256=:wireSha256, prepared_at=:preparedAt
           WHERE organization_id=:organizationId AND batch_id=:batchId
             AND wire_json IS NULL AND wire_sha256 IS NULL AND prepared_at IS NULL"""
    )
    abstract suspend fun freezeBatchWireRaw(
        organizationId: String,
        batchId: String,
        wireJson: String,
        wireSha256: String,
        preparedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPendingReferences(references: List<SyncPendingReferenceEntity>): List<Long>

    @Query(
        """SELECT * FROM sync_pending_reference
           WHERE organization_id=:organizationId AND protected_type=:protectedType
             AND protected_id=:protectedId
           ORDER BY source_owner, source_id"""
    )
    abstract suspend fun listPendingReferences(
        organizationId: String,
        protectedType: String,
        protectedId: String,
    ): List<SyncPendingReferenceEntity>

    @Query(
        """SELECT * FROM sync_pending_reference
           WHERE organization_id=:organizationId AND source_owner=:sourceOwner
             AND source_id=:sourceId
           ORDER BY protected_type, protected_id"""
    )
    abstract suspend fun listSourcePendingReferences(
        organizationId: String,
        sourceOwner: String,
        sourceId: String,
    ): List<SyncPendingReferenceEntity>

    @Query(
        """DELETE FROM sync_pending_reference
           WHERE organization_id=:organizationId AND source_owner=:sourceOwner
             AND source_id=:sourceId"""
    )
    abstract suspend fun deleteSourcePendingReferences(
        organizationId: String,
        sourceOwner: String,
        sourceId: String,
    ): Int

    @Query(
        """SELECT COUNT(*) FROM sync_pending_reference r
           LEFT JOIN sync_local_generation g
             ON g.organization_id=r.organization_id
            AND g.aggregate_type=r.protected_type AND g.aggregate_id=r.protected_id
           WHERE r.organization_id=:organizationId AND r.source_owner=:sourceOwner
             AND r.source_id=:sourceId
             AND (g.generation IS NULL OR g.generation<>r.captured_generation
                  OR g.content_hash<>r.captured_content_hash)"""
    )
    abstract suspend fun countChangedProtectedKeys(
        organizationId: String,
        sourceOwner: String,
        sourceId: String,
    ): Int

    @Query(
        """SELECT COUNT(*) FROM sync_pending_reference current_ref
           JOIN sync_pending_reference later_ref
             ON later_ref.organization_id=current_ref.organization_id
            AND later_ref.protected_type=current_ref.protected_type
            AND later_ref.protected_id=current_ref.protected_id
            AND later_ref.source_id<>current_ref.source_id
           JOIN sync_mutation_packet current_packet
             ON current_packet.organization_id=current_ref.organization_id
            AND current_packet.source_owner=current_ref.source_owner
            AND current_packet.source_id=current_ref.source_id
           JOIN sync_mutation_packet later_packet
             ON later_packet.organization_id=later_ref.organization_id
            AND later_packet.source_owner=later_ref.source_owner
            AND later_packet.source_id=later_ref.source_id
           JOIN sync_outbox later_owner
             ON later_ref.source_owner='sync_outbox' AND later_owner.mutation_id=later_ref.source_id
           WHERE current_ref.organization_id=:organizationId
             AND current_ref.source_owner=:sourceOwner AND current_ref.source_id=:sourceId
             AND later_owner.state IN ('PENDING','LEASED','RETRY','REQUIRES_REVIEW','REJECTED')
             AND (later_packet.captured_generation>current_packet.captured_generation
                  OR (later_packet.captured_generation=current_packet.captured_generation
                      AND later_packet.created_at>current_packet.created_at))"""
    )
    abstract suspend fun countLaterUnresolvedProtectedMutations(
        organizationId: String,
        sourceOwner: String,
        sourceId: String,
    ): Int

    @Query("SELECT state FROM sync_outbox WHERE organization_id=:organizationId AND mutation_id=:sourceId LIMIT 1")
    abstract suspend fun readUnifiedSourceState(organizationId: String, sourceId: String): String?

    @Query("SELECT sync_state FROM financial_outbox WHERE organization_id=:organizationId AND event_id=:sourceId LIMIT 1")
    abstract suspend fun readFinancialSourceState(organizationId: String, sourceId: String): String?

    /** The legacy Party queue has no org column; a business row must prove the tenant. */
    @Query(
        """SELECT o.state FROM party_sync_outbox o
           WHERE o.id=:sourceId AND EXISTS (
             SELECT 1 FROM party_roles r
             WHERE r.organization_id=:organizationId AND r.party_id=o.aggregate_id
           ) LIMIT 1"""
    )
    abstract suspend fun readPartyRoleSourceState(organizationId: String, sourceId: String): String?

    @Query("SELECT sync_state FROM inventory_stock_outbox WHERE organization_id=:organizationId AND id=:sourceId LIMIT 1")
    abstract suspend fun readInventoryStockSourceState(organizationId: String, sourceId: String): String?

    @Query("SELECT sync_state FROM inventory_cost_outbox WHERE organization_id=:organizationId AND id=:sourceId LIMIT 1")
    abstract suspend fun readInventoryCostSourceState(organizationId: String, sourceId: String): String?

    @Query("SELECT status FROM optimal_outbox WHERE organization_id=:organizationId AND event_id=:sourceId LIMIT 1")
    abstract suspend fun readOptimalSourceState(organizationId: String, sourceId: String): String?

    @Query("SELECT state FROM sync_attachment_transfer WHERE organization_id=:organizationId AND transfer_id=:sourceId LIMIT 1")
    abstract suspend fun readAttachmentSourceState(organizationId: String, sourceId: String): String?

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE organization_id=:organizationId AND state NOT IN ('ACKNOWLEDGED','SUPERSEDED_WITH_PROOF')")
    abstract suspend fun countUnconfirmedUnified(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM financial_outbox WHERE organization_id=:organizationId AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED')")
    abstract suspend fun countUnconfirmedFinancial(organizationId: String): Long

    @Query(
        """SELECT COUNT(*) FROM party_sync_outbox o
           WHERE o.state NOT IN ('ACKNOWLEDGED','SYNCED') AND EXISTS (
             SELECT 1 FROM party_roles r
             WHERE r.organization_id=:organizationId AND r.party_id=o.aggregate_id
           )"""
    )
    abstract suspend fun countUnconfirmedParty(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_stock_outbox WHERE organization_id=:organizationId AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED')")
    abstract suspend fun countUnconfirmedInventoryStock(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM inventory_cost_outbox WHERE organization_id=:organizationId AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED')")
    abstract suspend fun countUnconfirmedInventoryCost(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM optimal_outbox WHERE organization_id=:organizationId AND status NOT IN ('ACKNOWLEDGED','SYNCED')")
    abstract suspend fun countUnconfirmedOptimal(organizationId: String): Long

    @Query("SELECT COUNT(*) FROM sync_attachment_transfer WHERE organization_id=:organizationId AND state NOT IN ('COMPLETED','CANCELLED')")
    abstract suspend fun countUnconfirmedAttachment(organizationId: String): Long

    @Query(
        """SELECT EXISTS(SELECT 1 FROM sync_outbox
           WHERE organization_id=:organizationId AND aggregate_type=:aggregateType
             AND aggregate_id=:aggregateId AND state NOT IN ('ACKNOWLEDGED','SUPERSEDED_WITH_PROOF'))"""
    )
    abstract suspend fun hasUnconfirmedUnifiedAggregate(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
    ): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM financial_outbox
           WHERE organization_id=:organizationId AND aggregate_id=:aggregateId
             AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED'))"""
    )
    abstract suspend fun hasUnconfirmedFinancialAggregate(organizationId: String, aggregateId: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM inventory_stock_outbox
           WHERE organization_id=:organizationId AND movement_id=:aggregateId
             AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED'))"""
    )
    abstract suspend fun hasUnconfirmedInventoryStockAggregate(organizationId: String, aggregateId: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM inventory_cost_outbox
           WHERE organization_id=:organizationId AND cost_revision_id=:aggregateId
             AND sync_state NOT IN ('ACKNOWLEDGED','SYNCED'))"""
    )
    abstract suspend fun hasUnconfirmedInventoryCostAggregate(organizationId: String, aggregateId: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM party_sync_outbox o
           WHERE o.aggregate_type='ROLE' AND o.aggregate_id=:partyId
             AND o.state NOT IN ('ACKNOWLEDGED','SYNCED') AND EXISTS (
               SELECT 1 FROM party_roles r
               WHERE r.organization_id=:organizationId AND r.party_id=o.aggregate_id
             ))"""
    )
    abstract suspend fun hasUnconfirmedPartyRoleAggregate(organizationId: String, partyId: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM optimal_outbox
           WHERE organization_id=:organizationId AND aggregate_id=:aggregateId
             AND status NOT IN ('ACKNOWLEDGED','SYNCED'))"""
    )
    abstract suspend fun hasUnconfirmedOptimalAggregate(organizationId: String, aggregateId: String): Boolean

    @Query(
        """SELECT EXISTS(SELECT 1 FROM sync_attachment_transfer
           WHERE organization_id=:organizationId AND aggregate_type=:aggregateType
             AND aggregate_id=:aggregateId AND state NOT IN ('COMPLETED','CANCELLED'))"""
    )
    abstract suspend fun hasUnconfirmedAttachmentAggregate(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
    ): Boolean

    @Query(
        """SELECT * FROM sync_entity_version
           WHERE organization_id=:organizationId AND scope_id=:scopeId
             AND version_family=:versionFamily AND aggregate_id=:aggregateId
           LIMIT 1"""
    )
    abstract suspend fun readEntityVersion(
        organizationId: String,
        scopeId: String,
        versionFamily: String,
        aggregateId: String,
    ): SyncEntityVersionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertEntityVersionIfAbsent(entity: SyncEntityVersionEntity): Long

    @Query(
        """UPDATE sync_entity_version
           SET observed_server_version =
                 CASE WHEN :observedVersion IS NULL THEN observed_server_version
                      WHEN observed_server_version IS NULL OR observed_server_version < :observedVersion
                      THEN :observedVersion ELSE observed_server_version END,
               updated_at=:updatedAt
           WHERE organization_id=:organizationId AND scope_id=:scopeId
             AND version_family=:versionFamily AND aggregate_id=:aggregateId"""
    )
    abstract suspend fun updateObservedVersion(
        organizationId: String,
        scopeId: String,
        versionFamily: String,
        aggregateId: String,
        observedVersion: Long?,
        updatedAt: Long,
    ): Int

    /** Inbox receipt records observation only; it never advances applied authority. */
    @Transaction
    open suspend fun recordObservedVersion(
        organizationId: String,
        scopeId: String,
        versionFamily: String,
        aggregateId: String,
        observedVersion: Long?,
        updatedAt: Long,
    ): SyncEntityVersionEntity {
        require(observedVersion == null || observedVersion >= 0L)
        insertEntityVersionIfAbsent(
            SyncEntityVersionEntity(
                organizationId = organizationId,
                scopeId = scopeId,
                versionFamily = versionFamily,
                aggregateId = aggregateId,
                appliedServerVersion = null,
                observedServerVersion = observedVersion,
                lastAppliedRevision = null,
                appliedContentHash = null,
                updatedAt = updatedAt,
            )
        )
        check(updateObservedVersion(
            organizationId, scopeId, versionFamily, aggregateId, observedVersion, updatedAt
        ) == 1)
        return checkNotNull(readEntityVersion(organizationId, scopeId, versionFamily, aggregateId))
    }

    @Query(
        """UPDATE sync_entity_version
           SET applied_server_version=:appliedVersion,
               observed_server_version =
                 CASE WHEN observed_server_version IS NULL OR observed_server_version < :appliedVersion
                      THEN :appliedVersion ELSE observed_server_version END,
               last_applied_revision=:appliedRevision,
               applied_content_hash=:contentHash,
               tombstone=:tombstone,
               updated_at=:updatedAt
           WHERE organization_id=:organizationId AND scope_id=:scopeId
             AND version_family=:versionFamily AND aggregate_id=:aggregateId
             AND (applied_server_version IS NULL OR applied_server_version <= :appliedVersion)"""
    )
    abstract suspend fun updateAppliedVersion(
        organizationId: String,
        scopeId: String,
        versionFamily: String,
        aggregateId: String,
        appliedVersion: Long,
        appliedRevision: Long,
        contentHash: String,
        tombstone: Boolean,
        updatedAt: Long,
    ): Int

    /**
     * Must be called in the same outer Room transaction as the domain materialization.
     * A stale applied version is rejected and can never be hidden by a newer observation.
     */
    @Transaction
    open suspend fun recordAppliedVersion(
        organizationId: String,
        scopeId: String,
        versionFamily: String,
        aggregateId: String,
        appliedVersion: Long,
        appliedRevision: Long,
        contentHash: String,
        tombstone: Boolean,
        updatedAt: Long,
    ): SyncEntityVersionEntity {
        require(appliedVersion >= 0L)
        require(appliedRevision > 0L)
        requireSha256(contentHash)
        insertEntityVersionIfAbsent(
            SyncEntityVersionEntity(
                organizationId = organizationId,
                scopeId = scopeId,
                versionFamily = versionFamily,
                aggregateId = aggregateId,
                appliedServerVersion = null,
                observedServerVersion = null,
                lastAppliedRevision = null,
                appliedContentHash = null,
                updatedAt = updatedAt,
            )
        )
        check(updateAppliedVersion(
            organizationId, scopeId, versionFamily, aggregateId, appliedVersion,
            appliedRevision, contentHash, tombstone, updatedAt
        ) == 1) { "STALE_APPLIED_SERVER_VERSION" }
        return checkNotNull(readEntityVersion(organizationId, scopeId, versionFamily, aggregateId))
    }

    @Query(
        """SELECT * FROM sync_local_generation
           WHERE organization_id=:organizationId AND aggregate_type=:aggregateType
             AND aggregate_id=:aggregateId LIMIT 1"""
    )
    abstract suspend fun readLocalGeneration(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
    ): SyncLocalGenerationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertLocalGenerationIfAbsent(entity: SyncLocalGenerationEntity): Long

    @Query(
        """UPDATE sync_local_generation
           SET generation=generation+1, content_hash=:contentHash, updated_at=:updatedAt
           WHERE organization_id=:organizationId AND aggregate_type=:aggregateType
             AND aggregate_id=:aggregateId AND generation=:expectedGeneration"""
    )
    abstract suspend fun incrementLocalGeneration(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        expectedGeneration: Long,
        contentHash: String,
        updatedAt: Long,
    ): Int

    /** Only a semantic local write calls this method; remote observation never does. */
    @Transaction
    open suspend fun recordLocalGeneration(
        organizationId: String,
        aggregateType: String,
        aggregateId: String,
        contentHash: String,
        updatedAt: Long,
    ): SyncLocalGenerationEntity {
        requireSha256(contentHash)
        val initial = SyncLocalGenerationEntity(
            organizationId, aggregateType, aggregateId, 1L, contentHash, updatedAt
        )
        if (insertLocalGenerationIfAbsent(initial) != -1L) return initial
        val current = checkNotNull(readLocalGeneration(organizationId, aggregateType, aggregateId))
        check(incrementLocalGeneration(
            organizationId, aggregateType, aggregateId, current.generation, contentHash, updatedAt
        ) == 1) { "LOCAL_GENERATION_CAS_FAILED" }
        return checkNotNull(readLocalGeneration(organizationId, aggregateType, aggregateId))
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWriteBatch(batch: SyncWriteBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertWriteBatchMembers(members: List<SyncWriteBatchMemberEntity>)

    @Transaction
    open suspend fun insertSealedWriteBatch(
        batch: SyncWriteBatchEntity,
        members: List<SyncWriteBatchMemberEntity>,
    ) {
        validateWriteBatchManifest(batch, members)
        insertWriteBatch(batch)
        insertWriteBatchMembers(members)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertExpenseRevisionHistoryIfAbsent(row: ExpenseRevisionHistoryEntity): Long

    @Query("""SELECT * FROM expense_revision_history
        WHERE organization_id=:organizationId AND expense_id=:expenseId AND server_version=:serverVersion LIMIT 1""")
    abstract suspend fun readExpenseRevisionHistory(
        organizationId: String, expenseId: String, serverVersion: Long,
    ): ExpenseRevisionHistoryEntity?

    @Query("""SELECT * FROM expense_revision_history
        WHERE organization_id=:organizationId AND write_id=:writeId LIMIT 1""")
    abstract suspend fun readExpenseRevisionHistoryByWriteId(
        organizationId: String, writeId: String,
    ): ExpenseRevisionHistoryEntity?

    @Transaction
    open suspend fun putExpenseRevisionHistory(row: ExpenseRevisionHistoryEntity): ExpenseRevisionHistoryEntity {
        require(row.organizationId.isNotBlank() && row.expenseId.isNotBlank() && row.writeId.isNotBlank())
        require(row.serverVersion > 0L)
        require(row.previousVersion == null || row.previousVersion >= 0L)
        row.beforeContentHash?.let(::requireSha256)
        requireSha256(row.afterContentHash)
        insertExpenseRevisionHistoryIfAbsent(row)
        val byVersion = checkNotNull(readExpenseRevisionHistory(row.organizationId, row.expenseId, row.serverVersion))
        check(byVersion == row) { "EXPENSE_REVISION_VERSION_CONFLICT" }
        val byWrite = checkNotNull(readExpenseRevisionHistoryByWriteId(row.organizationId, row.writeId))
        check(byWrite == row) { "EXPENSE_REVISION_WRITE_CONFLICT" }
        return row
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSnapshotBlobIfAbsent(blob: SyncSnapshotBlobEntity): Long

    @Query("SELECT * FROM sync_snapshot_blob WHERE content_hash=:contentHash LIMIT 1")
    abstract suspend fun readSnapshotBlob(contentHash: String): SyncSnapshotBlobEntity?

    @Transaction
    open suspend fun putImmutableSnapshotBlob(
        contentHash: String,
        snapshotJson: String,
        createdAt: Long,
    ): SyncSnapshotBlobEntity {
        requireSha256(contentHash)
        require(snapshotJson.isNotEmpty())
        val candidate = SyncSnapshotBlobEntity(contentHash, snapshotJson, createdAt)
        insertSnapshotBlobIfAbsent(candidate)
        val stored = checkNotNull(readSnapshotBlob(contentHash))
        require(stored.snapshotJson == snapshotJson) { "SNAPSHOT_HASH_CONTENT_MISMATCH" }
        return stored
    }

}

internal fun validateWriteBatchManifest(
    batch: SyncWriteBatchEntity,
    members: List<SyncWriteBatchMemberEntity>,
) {
    require(batch.memberCount == members.size && members.isNotEmpty())
    requireSha256(batch.manifestSha256)
    require(members.all { it.organizationId == batch.organizationId && it.batchId == batch.batchId })
    require(members.map { it.memberOrder }.sorted() == members.indices.toList()) {
        "BATCH_MEMBER_ORDER_MUST_BE_CONTIGUOUS_ZERO_BASED"
    }
    require(members.map { it.mutationId }.toSet().size == members.size)
    require(members.map { it.sourceOwner to it.sourceId }.toSet().size == members.size)
}

internal data class PendingSourceIdentity(val sourceOwner: String, val sourceId: String)

/** B03 detection primitive; B04 supplies the authoritative owner registry and active source IDs. */
internal fun findOrphanPendingSources(
    references: Collection<PendingSourceIdentity>,
    sourceIdsByOwner: Map<String, Set<String>>,
): Set<PendingSourceIdentity> = references.filterTo(linkedSetOf()) { reference ->
    reference.sourceId !in sourceIdsByOwner[reference.sourceOwner].orEmpty()
}

private fun requireSha256(value: String) {
    require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
        "lowercase SHA-256 required"
    }
}
