package com.verto.app.feature.integration.optimal.data

import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendContractGate
import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxEvent
import com.verto.app.feature.integration.optimal.domain.model.OptimalOutboxExecutionResult
import com.verto.app.feature.integration.optimal.domain.model.OptimalRemoteContract
import com.verto.app.feature.integration.optimal.domain.port.OptimalOutboxEventExecutor
import com.verto.app.feature.integration.optimal.domain.port.OptimalRemoteDispatchRequest
import com.verto.app.feature.integration.optimal.domain.port.OptimalRemoteDispatchResult
import com.verto.app.feature.integration.optimal.domain.port.OptimalRemoteOutboxDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects only a verified, tenant-scoped, server-idempotent remote adapter. Missing contracts,
 * ambiguous adapters, and unresolved version conflicts become BLOCKED instead of infinite retries.
 */
@Singleton
class ContractGuardedOptimalOutboxEventExecutor @Inject constructor(
    private val contractGate: OptimalBackendContractGate,
    private val dispatchers: Set<@JvmSuppressWildcards OptimalRemoteOutboxDispatcher>,
) : OptimalOutboxEventExecutor {
    override suspend fun execute(event: OptimalOutboxEvent): OptimalOutboxExecutionResult {
        val contract = event.operation.remoteContract()
            ?: return blocked("UNSUPPORTED_OUTBOX_OPERATION:${event.operation}")
        if (!contractGate.allows(contract)) {
            return blocked("REMOTE_CONTRACT_BLOCKED:${contract.name}")
        }

        val matching = dispatchers.filter { dispatcher ->
            dispatcher.contract == contract && event.operation in dispatcher.supportedOperations
        }
        if (matching.isEmpty()) {
            return blocked("REMOTE_DISPATCH_ADAPTER_UNAVAILABLE:${event.operation}")
        }
        if (matching.size != 1) {
            return blocked("REMOTE_DISPATCH_ADAPTER_AMBIGUOUS:${event.operation}")
        }

        val dispatcher = matching.single()
        if (!dispatcher.supportsTenantScope) {
            return blocked("REMOTE_TENANT_SCOPE_UNVERIFIED:${event.operation}")
        }
        if (!dispatcher.supportsServerIdempotency) {
            return blocked("REMOTE_IDEMPOTENCY_UNVERIFIED:${event.operation}")
        }
        if (event.remoteVersion != null && !dispatcher.supportsOptimisticVersion) {
            return blocked(
                reason = "REMOTE_VERSION_CONTRACT_UNAVAILABLE:${event.operation}",
                remoteId = event.remoteId,
                remoteVersion = event.remoteVersion,
            )
        }

        val request = OptimalRemoteDispatchRequest(
            organizationId = event.organizationId,
            eventId = event.eventId,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            operation = event.operation,
            payloadJson = event.payloadJson,
            payloadVersion = event.payloadVersion,
            idempotencyKey = event.idempotencyKey,
            aggregateVersion = event.aggregateVersion,
            expectedRemoteId = event.remoteId,
            expectedRemoteVersion = event.remoteVersion,
        )
        return when (val result = dispatcher.dispatch(request)) {
            is OptimalRemoteDispatchResult.Applied -> OptimalOutboxExecutionResult.Synced(
                remoteId = normalizedRemoteId(result.remoteId),
                remoteVersion = result.remoteVersion,
            )

            is OptimalRemoteDispatchResult.AlreadyApplied -> OptimalOutboxExecutionResult.Synced(
                remoteId = normalizedRemoteId(result.remoteId),
                remoteVersion = result.remoteVersion,
            )

            is OptimalRemoteDispatchResult.RetryableFailure -> OptimalOutboxExecutionResult.Failed(
                "REMOTE_RETRYABLE_FAILURE",
            )

            is OptimalRemoteDispatchResult.Conflict -> blocked(
                reason = "REMOTE_VERSION_CONFLICT",
                remoteId = normalizedRemoteId(result.remoteId),
                remoteVersion = result.actualRemoteVersion,
            )

            is OptimalRemoteDispatchResult.Rejected -> blocked(
                "REMOTE_OPERATION_REJECTED",
            )
        }
    }

    private fun blocked(
        reason: String,
        remoteId: String? = null,
        remoteVersion: Long? = null,
    ): OptimalOutboxExecutionResult.Blocked = OptimalOutboxExecutionResult.Blocked(
        reason = reason.ifBlank { "REMOTE_OPERATION_BLOCKED" },
        remoteId = normalizedRemoteId(remoteId),
        remoteVersion = remoteVersion,
    )

    private fun normalizedRemoteId(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.remoteContract(): OptimalRemoteContract? = when (this) {
        "INVOICE_CREATED", "INVOICE_UPDATED" -> OptimalRemoteContract.UPSERT_INVOICE
        "PAYMENT_RECORDED" -> OptimalRemoteContract.UPSERT_PAYMENT
        "PAYMENT_REVERSED" -> OptimalRemoteContract.REVERSE_PAYMENT
        "INVOICE_VOIDED" -> OptimalRemoteContract.VOID_INVOICE
        "SEND_MESSAGE" -> OptimalRemoteContract.SEND_MESSAGE
        "ARCHIVE_CONVERSATION", "UNARCHIVE_CONVERSATION" -> OptimalRemoteContract.ARCHIVE_CONVERSATION
        "MAINTENANCE_UPSERTED" -> OptimalRemoteContract.UPSERT_MAINTENANCE
        else -> null
    }

}
