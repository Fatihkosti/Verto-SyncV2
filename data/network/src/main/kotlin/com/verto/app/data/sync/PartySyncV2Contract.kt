package com.verto.app.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PARTY_SYNC_CONTRACT_VERSION = 2

@Serializable
enum class PartyAggregateType { IDENTITY, ROLE, CUSTOMER_PROFILE, SUPPLIER_PROFILE, TOMBSTONE }

@Serializable
data class PartyMutationV2(
    @SerialName("operation_id") val operationId: String,
    @SerialName("aggregate_type") val aggregateType: PartyAggregateType,
    @SerialName("aggregate_id") val aggregateId: String,
    @SerialName("base_revision") val baseRevision: Long,
    @SerialName("contract_version") val contractVersion: Int = PARTY_SYNC_CONTRACT_VERSION,
    val payload: Map<String, String>,
)

@Serializable
data class PartyRemoteVersionV2(
    @SerialName("aggregate_id") val aggregateId: String,
    @SerialName("server_revision") val serverRevision: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String,
    @SerialName("last_operation_id") val lastOperationId: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val payload: Map<String, String>,
)

enum class PartyConflictDecision { APPLY_REMOTE, ACK_REPLAY, RECORD_STALE_CONFLICT }

object PartySyncV2Policy {
    val pushOrder = listOf(
        PartyAggregateType.IDENTITY, PartyAggregateType.ROLE, PartyAggregateType.CUSTOMER_PROFILE,
        PartyAggregateType.SUPPLIER_PROFILE, PartyAggregateType.TOMBSTONE,
    )

    fun decide(local: PartyMutationV2, remote: PartyRemoteVersionV2): PartyConflictDecision = when {
        remote.lastOperationId == local.operationId -> PartyConflictDecision.ACK_REPLAY
        remote.serverRevision != local.baseRevision -> PartyConflictDecision.RECORD_STALE_CONFLICT
        else -> PartyConflictDecision.APPLY_REMOTE
    }

    fun retryable(httpStatus: Int): Boolean = httpStatus == 408 || httpStatus == 429 || httpStatus in 500..599
}
