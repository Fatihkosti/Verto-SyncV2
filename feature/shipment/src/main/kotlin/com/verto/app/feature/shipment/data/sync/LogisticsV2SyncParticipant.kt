package com.verto.app.feature.shipment.data.sync

import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncOperationSlot

interface LogisticsV2SyncRuntime {
    fun isRemoteEnabled(organizationId: String): Boolean
    suspend fun push(organizationId: String)
    suspend fun pull(organizationId: String)
}

class LogisticsV2SyncParticipant(
    private val runtime: LogisticsV2SyncRuntime,
) : SyncParticipant {
    override val key: String = "logistics-v2"

    override fun operations(context: SyncRunContext): List<SyncOperation> {
        // v211 remains inert because the runtime/bridge hard gates are closed.
        if (!runtime.isRemoteEnabled(context.organizationId)) return emptyList()

        return listOf(
            SyncOperation(
                slot = SyncOperationSlot.PUSH_LOGISTICS_V2,
                label = "push logistics v2",
                failureMode = SyncFailureMode.COLLECT,
                participantKey = key,
                idempotencyKey = "$key:${context.organizationId}:push",
                execute = { runtime.push(context.organizationId) },
            ),
            SyncOperation(
                slot = SyncOperationSlot.PULL_LOGISTICS_V2,
                label = "pull logistics v2",
                failureMode = SyncFailureMode.COLLECT,
                participantKey = key,
                idempotencyKey = "$key:${context.organizationId}:pull",
                execute = { runtime.pull(context.organizationId) },
            ),
        )
    }
}
