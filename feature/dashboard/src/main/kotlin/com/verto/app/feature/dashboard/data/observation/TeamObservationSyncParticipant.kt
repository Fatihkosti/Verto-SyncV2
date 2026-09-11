package com.verto.app.feature.dashboard.data.observation

import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.rollout.SyncAggregateOwnership
import com.verto.app.data.sync.rollout.SyncRolloutPolicy
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class TeamObservationSyncParticipant @Inject constructor(
    private val repository: RoomTeamObservationRepository,
) : SyncParticipant {
    override val key: String = "team_observations"

    override fun operations(context: SyncRunContext): List<SyncOperation> {
        val ownership = SyncRolloutPolicy.ownership(
            snapshot = SyncRolloutPolicy.snapshot(context.organizationId),
            aggregateType = "TEAM_OBSERVATION",
        ).ownership
        val v2OwnsTransport = ownership in setOf(
            SyncAggregateOwnership.V2_AUTHORITATIVE,
            SyncAggregateOwnership.V2_PAUSED_SAFE,
            SyncAggregateOwnership.RETIRED_LEGACY,
        )
        if (v2OwnsTransport) return emptyList()

        return listOf(
            SyncOperation(
                slot = SyncOperationSlot.PUSH_TEAM_OBSERVATIONS,
                label = "رفع ملاحظات الفريق",
                failureMode = SyncFailureMode.COLLECT,
                execute = { skipWhenTableUnavailable { repository.pushDirty(context.organizationId) } },
            ),
            SyncOperation(
                slot = SyncOperationSlot.PULL_TEAM_OBSERVATIONS,
                label = "تنزيل ملاحظات الفريق",
                failureMode = SyncFailureMode.COLLECT,
                execute = { skipWhenTableUnavailable { repository.pullRemote(context.organizationId) } },
            ),
        )
    }

    private suspend fun skipWhenTableUnavailable(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (!failure.isMissingTeamObservationTable()) throw failure
            android.util.Log.w("TeamObservationSync", "Skipping team observation sync: remote table unavailable")
        }
    }
}

private fun Throwable.isMissingTeamObservationTable(): Boolean =
    generateSequence(this) { it.cause }.any { "public.team_observations" in it.message.orEmpty() }
