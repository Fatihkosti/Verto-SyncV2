package com.verto.app.feature.integration.optimal.domain.port

import com.verto.app.feature.integration.optimal.domain.model.OptimalRemoteContract

/** Immutable request reconstructed from the same durable Outbox row on every retry. */
data class OptimalRemoteDispatchRequest(
    val organizationId: String,
    val eventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val operation: String,
    val payloadJson: String,
    val payloadVersion: Int,
    val idempotencyKey: String,
    val aggregateVersion: Long,
    val expectedRemoteId: String?,
    val expectedRemoteVersion: Long?,
)

sealed interface OptimalRemoteDispatchResult {
    data class Applied(
        val remoteId: String? = null,
        val remoteVersion: Long? = null,
    ) : OptimalRemoteDispatchResult

    /** Returned when the server already committed the same idempotency key before a lost response. */
    data class AlreadyApplied(
        val remoteId: String? = null,
        val remoteVersion: Long? = null,
    ) : OptimalRemoteDispatchResult

    data class RetryableFailure(val reason: String) : OptimalRemoteDispatchResult

    data class Conflict(
        val reason: String,
        val remoteId: String? = null,
        val actualRemoteVersion: Long? = null,
    ) : OptimalRemoteDispatchResult

    data class Rejected(val reason: String) : OptimalRemoteDispatchResult
}

/**
 * A concrete remote adapter must prove tenant isolation and server-side idempotency before it can
 * be selected. No implementation is provided while the v49 backend matrix remains blocked.
 */
interface OptimalRemoteOutboxDispatcher {
    val contract: OptimalRemoteContract
    val supportedOperations: Set<String>
    val supportsTenantScope: Boolean
    val supportsServerIdempotency: Boolean
    val supportsOptimisticVersion: Boolean

    suspend fun dispatch(request: OptimalRemoteDispatchRequest): OptimalRemoteDispatchResult
}
