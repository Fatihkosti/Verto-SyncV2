package com.verto.app.data.sync.pull

import com.verto.app.data.sync.SyncChange
import com.verto.app.data.sync.SyncMutationOperation
import kotlinx.serialization.json.JsonObject

/**
 * Shared remote materialization shape. A bootstrap snapshot implements this directly; it is not a
 * synthetic SyncChange and never enters sync_inbox or the revision-feed cursor path.
 */
interface UnifiedRemoteMaterialization {
    val scopeId: String
    val isBootstrap: Boolean
    val organizationId: String
    val aggregateType: String
    val aggregateId: String
    val operationType: SyncMutationOperation
    val entityVersion: Long?
    val payloadVersion: Int
    val payload: JsonObject
    val revision: Long
    val changedAtEpochMillis: Long
    val deletedAtEpochMillis: Long?
}

internal data class ChangeMaterialization(private val source: SyncChange) : UnifiedRemoteMaterialization {
    override val scopeId = source.syncScopeId
    override val isBootstrap = false
    override val organizationId = source.organizationId
    override val aggregateType = source.aggregateType
    override val aggregateId = source.aggregateId
    override val operationType = source.operationType
    override val entityVersion = source.entityVersion
    override val payloadVersion = source.payloadVersion
    override val payload = source.payload
    override val revision = source.revision
    override val changedAtEpochMillis = source.changedAtEpochMillis
    override val deletedAtEpochMillis = source.deletedAtEpochMillis
}

data class SyncSnapshotMaterialization(
    override val scopeId: String,
    override val organizationId: String,
    override val aggregateType: String,
    override val aggregateId: String,
    override val entityVersion: Long?,
    override val payloadVersion: Int,
    override val payload: JsonObject,
    /** Server-provided bootstrap baseline; diagnostic/materialization anchor only, never a cursor. */
    override val revision: Long,
    override val operationType: SyncMutationOperation = SyncMutationOperation.UPSERT,
    override val deletedAtEpochMillis: Long? = null,
) : UnifiedRemoteMaterialization {
    override val isBootstrap = true
    override val changedAtEpochMillis: Long = 0L
}
