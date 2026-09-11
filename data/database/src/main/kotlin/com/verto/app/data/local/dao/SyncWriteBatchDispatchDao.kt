package com.verto.app.data.local.dao

import androidx.room.Query
import com.verto.app.data.local.entity.SyncMutationPacketEntity
import com.verto.app.data.local.entity.SyncWriteBatchEntity

/** Read-only scheduling surface for atomic V2 write batches. Source rows keep their original ownership. */
interface SyncWriteBatchDispatchDao {
    @Query(
        """SELECT * FROM sync_mutation_packet
           WHERE organization_id=:organizationId AND source_owner=:sourceOwner AND source_id=:sourceId
           ORDER BY created_at DESC LIMIT 1"""
    )
    suspend fun readMutationPacketBySource(
        organizationId: String,
        sourceOwner: String,
        sourceId: String,
    ): SyncMutationPacketEntity?

    @Query(
        """SELECT DISTINCT b.*
           FROM sync_write_batch b
           JOIN sync_write_batch_member m
             ON m.organization_id=b.organization_id AND m.batch_id=b.batch_id
           WHERE b.organization_id=:organizationId
             AND (
               (m.source_owner='sync_outbox' AND EXISTS(
                 SELECT 1 FROM sync_outbox o
                  WHERE o.organization_id=b.organization_id AND o.mutation_id=m.source_id
                    AND o.state NOT IN ('ACKNOWLEDGED','SUPERSEDED_WITH_PROOF')
               )) OR
               (m.source_owner='financial_outbox' AND EXISTS(
                 SELECT 1 FROM financial_outbox o
                  WHERE o.organization_id=b.organization_id AND o.event_id=m.source_id
                    AND o.sync_state NOT IN ('ACKNOWLEDGED','SYNCED')
               )) OR
               (m.source_owner='inventory_stock_outbox' AND EXISTS(
                 SELECT 1 FROM inventory_stock_outbox o
                  WHERE o.organization_id=b.organization_id AND o.id=m.source_id
                    AND o.sync_state NOT IN ('ACKNOWLEDGED','SYNCED')
               )) OR
               (m.source_owner='inventory_cost_outbox' AND EXISTS(
                 SELECT 1 FROM inventory_cost_outbox o
                  WHERE o.organization_id=b.organization_id AND o.id=m.source_id
                    AND o.sync_state NOT IN ('ACKNOWLEDGED','SYNCED')
               )) OR
               (m.source_owner='party_sync_outbox' AND EXISTS(
                 SELECT 1 FROM party_sync_outbox o
                  WHERE o.id=m.source_id AND o.state NOT IN ('ACKNOWLEDGED','SYNCED')
               )) OR
               (m.source_owner='optimal_outbox' AND EXISTS(
                 SELECT 1 FROM optimal_outbox o
                  WHERE o.organization_id=b.organization_id AND o.event_id=m.source_id
                    AND o.status NOT IN ('ACKNOWLEDGED','SYNCED')
               ))
             )
           ORDER BY b.sealed_at, b.batch_id
           LIMIT :limit"""
    )
    suspend fun listDispatchableWriteBatches(
        organizationId: String,
        limit: Int,
    ): List<SyncWriteBatchEntity>
}
