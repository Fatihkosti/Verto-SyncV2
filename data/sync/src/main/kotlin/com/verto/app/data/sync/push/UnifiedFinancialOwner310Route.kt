package com.verto.app.data.sync.push

import com.verto.app.data.local.entity.SyncOutboxEntity
import com.verto.app.data.sync.SyncMutation
import com.verto.app.data.sync.SyncMutationOperation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Owner-310 transport adapter. Production preparation only reads the frozen outbox payload. */
object UnifiedFinancialOwner310Route {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }
    private val materializedOwners = setOf(
        "CLIENT_CREDIT", "COST_ALLOCATION", "EXPENSE", "CASH_MOVEMENT", "CASH_RECONCILIATION",
    )
    private val purchaseOwners = setOf("GOODS_RECEIPT", "PURCHASE_MATCH", "PURCHASE_PAYMENT_OVERRIDE")
    private val owner310 = materializedOwners + purchaseOwners

    suspend fun prepare(
        row: SyncOutboxEntity,
        registry: UnifiedSyncPushRegistry,
    ): SyncMutation {
        val operation = normalizedOperation(row)
        val frozenPayload = parsePayload(row.payloadJson)
        when (row.aggregateType) {
            in materializedOwners -> {
                registry.validateStrongerMutation(row.aggregateType, row.payloadVersion, operation)
                requireObject(frozenPayload, "materialization")
            }
            in purchaseOwners -> {
                registry.validateStrongerMutation(row.aggregateType, row.payloadVersion, operation)
                requireObject(frozenPayload, "purchaseRequest")
            }
            else -> registry.validateGenericMutation(row.aggregateType, row.payloadVersion, operation)
        }
        return row.toMutation(operation, frozenPayload)
    }

    private fun requireObject(payload: JsonObject, field: String): JsonObject =
        payload[field] as? JsonObject
            ?: throw UnifiedSyncPushFailure("CONTRACT_FIELD_MISSING", "$field must be a frozen object")

    private fun normalizedOperation(row: SyncOutboxEntity): SyncMutationOperation {
        val parsed = runCatching { SyncMutationOperation.valueOf(row.operationType) }
            .getOrElse { throw UnifiedSyncPushFailure("CONTRACT_UNSUPPORTED", "unknown operation=${row.operationType}") }
        return if (row.aggregateType in owner310 && parsed == SyncMutationOperation.UPSERT) {
            SyncMutationOperation.COMMAND
        } else parsed
    }

    private fun parsePayload(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as JsonObject }
            .getOrElse { throw UnifiedSyncPushFailure("CONTRACT_FIELD_INVALID", "payloadJson must be an object", it) }

    private fun SyncOutboxEntity.toMutation(operation: SyncMutationOperation, payload: JsonObject) = SyncMutation(
        mutationId = mutationId, organizationId = organizationId, aggregateType = aggregateType,
        aggregateId = aggregateId, operationType = operation, baseVersion = baseVersion,
        localSequence = localSequence, aggregateSequence = aggregateSequence,
        payloadVersion = payloadVersion, payload = payload, createdAtEpochMillis = createdAt,
        commandBatchId = commandBatchId, commandOrder = commandOrder,
        dependsOnMutationId = dependsOnMutationId,
    )
}
