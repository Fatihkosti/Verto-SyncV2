package com.verto.app.feature.party.data.sync

import com.verto.app.data.sync.SyncFailureMode
import com.verto.app.data.sync.SyncOperation
import com.verto.app.data.sync.SyncParticipant
import com.verto.app.data.sync.SyncRunContext
import com.verto.app.data.sync.SyncRuntime
import com.verto.app.data.sync.SyncOperationSlot
import com.verto.app.data.sync.pullClients
import com.verto.app.data.sync.pullNotes
import com.verto.app.data.sync.pullReminders
import com.verto.app.data.sync.pushClientDeletions
import com.verto.app.data.sync.pushClients
import com.verto.app.data.sync.pushNotes
import com.verto.app.data.sync.pushReminders
import com.verto.app.data.sync.rollout.SyncAggregateOwnership
import com.verto.app.data.sync.rollout.SyncRolloutPolicy

class ClientSyncParticipant(
    private val runtime: SyncRuntime
) : SyncParticipant {
    override val key: String = "clients"

    override fun operations(context: SyncRunContext): List<SyncOperation> {
        val ownership = SyncRolloutPolicy.ownership(
            snapshot = SyncRolloutPolicy.snapshot(context.organizationId),
            aggregateType = "PARTY_IDENTITY",
        ).ownership
        val partyV2OwnsTransport = ownership == SyncAggregateOwnership.V2_AUTHORITATIVE ||
            ownership == SyncAggregateOwnership.RETIRED_LEGACY

        return buildList {
            if (!partyV2OwnsTransport) {
                add(SyncOperation(SyncOperationSlot.PUSH_CLIENTS, "push العملاء", SyncFailureMode.ABORT, execute = {
                    runtime.pushClients(context.organizationId, context.userId)
                }))
            }
            add(SyncOperation(SyncOperationSlot.PUSH_NOTES, "push الملاحظات", SyncFailureMode.COLLECT, execute = {
                runtime.pushNotes(context.organizationId, context.userId)
            }))
            add(SyncOperation(SyncOperationSlot.PUSH_REMINDERS, "push التذكيرات", SyncFailureMode.COLLECT, execute = {
                runtime.pushReminders(context.organizationId)
            }))
            add(SyncOperation(SyncOperationSlot.DELETE_CLIENTS, "حذف العملاء", SyncFailureMode.COLLECT, execute = {
                runtime.pushClientDeletions(context.organizationId)
            }))
            if (!partyV2OwnsTransport) {
                add(SyncOperation(SyncOperationSlot.PULL_CLIENTS, "pull العملاء", SyncFailureMode.ABORT, execute = {
                    runtime.pullClients(context.organizationId, context.deletions.clientIds)
                }))
            }
            add(SyncOperation(SyncOperationSlot.PULL_NOTES, "pull الملاحظات", SyncFailureMode.COLLECT, execute = {
                runtime.pullNotes(context.organizationId)
            }))
            add(SyncOperation(SyncOperationSlot.PULL_REMINDERS, "pull التذكيرات", SyncFailureMode.COLLECT, execute = {
                runtime.pullReminders(context.organizationId)
            }))
        }
    }
}
