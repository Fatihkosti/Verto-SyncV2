package com.verto.app.data.local.dao

import androidx.room.Dao

/**
 * Stable Room entry point for unified synchronization persistence.
 *
 * The cohesive contracts preserve the original transaction identities while preventing a
 * single DAO from owning outbox, conflict, inbox, orchestration, and cursor responsibilities.
 */
@Dao
abstract class UnifiedSyncDao :
    UnifiedSyncOutboxDao,
    UnifiedSyncConflictDao,
    UnifiedSyncOrchestrationDao,
    UnifiedSyncInboxDao,
    DurableSyncInboxDao,
    UnifiedSyncCursorDao,
    SyncRepairV2Dao,
    SyncWriteBatchDispatchDao {
    companion object {
        const val MAX_MUTATION_PAYLOAD_BYTES = 524_288
        const val MAX_WORKER_OPERATIONS_PER_RUN = 500
    }
}
