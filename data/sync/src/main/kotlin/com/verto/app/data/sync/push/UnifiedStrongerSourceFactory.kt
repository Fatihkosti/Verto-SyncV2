package com.verto.app.data.sync.push

import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.InventoryCostOutboxEntity
import com.verto.app.data.local.entity.InventoryCostRevisionEntity
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryStockOutboxEntity
import com.verto.app.data.local.entity.OptimalOutboxEntity
import com.verto.app.data.sync.StrongerMutationSource
import com.verto.app.data.sync.InventoryCostRevisionDtoV2
import com.verto.app.data.sync.InventoryMovementDtoV2
import com.verto.app.data.sync.SYNC_REPAIR_PAYLOAD_VERSION
import com.verto.app.data.sync.SyncContractV2Codec
import com.verto.app.data.sync.UnifiedStrongerBridgeRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Session 310 deterministic source adapters. They read stronger durable intents; they never enqueue
 * into sync_outbox. Scheduling/claim ownership intentionally remains with Session 311.
 */
object UnifiedStrongerSourceFactory {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }

    fun financial(row: FinancialOutboxEntity): StrongerMutationSource {
        val aggregate = if (row.operationType.startsWith("PAYMENT_")) "PAYMENT" else "INVOICE"
        val domainPayload = parseObject(row.payload)
        return StrongerMutationSource(
            organizationId = row.organizationId,
            aggregateType = aggregate,
            aggregateId = row.aggregateId,
            businessIdentity = "${row.operationType}:${row.writeId}",
            domainOperation = row.operationType,
            localSequence = row.sequence,
            aggregateSequence = row.sequence,
            payloadVersion = SYNC_REPAIR_PAYLOAD_VERSION,
            payload = buildJsonObject {
                put("financialSnapshot", domainPayload)
                put("event", buildJsonObject {
                    put("eventId", row.eventId)
                    put("writeId", row.writeId)
                    put("aggregateVersion", row.aggregateVersion)
                    put("aggregateSequence", row.sequence)
                    put("domainOperation", row.operationType)
                    put("domainPayloadVersion", row.payloadVersion)
                    put("schemaVersion", row.schemaVersion)
                    put("occurredAt", row.occurredAt)
                    put("recordedAt", row.recordedAt)
                })
            },
            createdAtEpochMillis = row.createdAt,
            baseVersion = row.aggregateVersion.toLong().coerceAtLeast(1L),
        )
    }

    fun inventoryMovement(
        outbox: InventoryStockOutboxEntity,
        movement: InventoryMovementEntity,
    ): StrongerMutationSource {
        require(outbox.movementId == movement.id) { "inventory stronger source identity mismatch" }
        val identity = movement.idempotencyKey?.takeIf { it.isNotBlank() }
            ?: outbox.idempotencyKey.takeIf { it.isNotBlank() }
            ?: error("inventory movement idempotency identity missing")
        val commandId = movement.commandId?.takeIf { it.isNotBlank() } ?: outbox.commandId.takeIf { it.isNotBlank() }
            ?: error("CONTRACT_FIELD_MISSING: commandId")
        val dto = InventoryMovementDtoV2(
            id = movement.id, itemId = movement.itemId, invoiceId = movement.invoiceId,
            clientId = movement.clientId, movementKind = requireNotNull(movement.movementKind) { "CONTRACT_FIELD_MISSING: movementKind" }.name,
            signedBaseQuantity = requireNotNull(movement.signedBaseQuantity) { "CONTRACT_FIELD_MISSING: signedBaseQuantity" },
            unitPriceMinor = movement.unitPriceMinor, note = movement.note, shipmentId = movement.shipmentId,
            sourceType = movement.sourceType, sourceId = movement.sourceId, sourceLineId = movement.sourceLineId,
            sourceVersion = movement.sourceVersion, writeId = movement.writeId,
            organizationId = requireNotNull(movement.organizationId) { "CONTRACT_FIELD_MISSING: organizationId" },
            commandId = commandId, idempotencyKey = identity, postingGroupId = movement.postingGroupId,
            reversesMovementId = movement.reversesMovementId,
            conversionFactorSnapshot = movement.conversionFactorSnapshot?.takeIf {reti -> reti.isNotBlank()}
                ?: error("CONTRACT_FIELD_MISSING: conversionFactorSnapshot"),
            occurredAt = requireNotNull(movement.occurredAt) { "CONTRACT_FIELD_MISSING: occurredAt" },
            recordedAt = movement.recordedAt, serverAcceptedAt = movement.serverAcceptedAt,
            serverSequence = movement.serverSequence, createdBy = movement.createdBy,
            deviceId = movement.deviceId?.takeIf { it.isNotBlank() } ?: error("CONTRACT_FIELD_MISSING: deviceId"),
            contractVersion = movement.contractVersion, createdAt = movement.createdAt,
        )
        SyncContractV2Codec.requireValid(dto)
        val command = SyncContractV2Codec.json.parseToJsonElement(SyncContractV2Codec.encode(dto)) as JsonObject
        return StrongerMutationSource(
            organizationId = outbox.organizationId,
            aggregateType = "INVENTORY_MOVEMENT",
            aggregateId = movement.id,
            businessIdentity = identity,
            domainOperation = if (movement.movementKind?.name == "REVERSAL") "REVERSAL" else "COMMAND",
            localSequence = outbox.createdAt.coerceAtLeast(1L),
            aggregateSequence = movement.serverSequence ?: outbox.createdAt.coerceAtLeast(1L),
            payloadVersion = SYNC_REPAIR_PAYLOAD_VERSION,
            payload = buildJsonObject { put("inventoryMovement", command) },
            createdAtEpochMillis = outbox.createdAt,
            transactionId = movement.postingGroupId,
        )
    }

    fun inventoryCost(
        outbox: InventoryCostOutboxEntity,
        revision: InventoryCostRevisionEntity,
    ): StrongerMutationSource {
        require(outbox.costRevisionId == revision.costRevisionId) { "inventory cost stronger source identity mismatch" }
        val dto = InventoryCostRevisionDtoV2(
            revision.costRevisionId, revision.organizationId, revision.itemId, revision.sourceType,
            revision.sourceId, revision.sourceLineId, revision.revisionKind.name,
            revision.directPurchaseCostMinor, revision.landedCostPerBaseUnitMinor,
            revision.approvedInventoryCostMinor, revision.currencyCode, revision.exchangeRateSnapshot,
            revision.allocationBasis, revision.allocationResidualMinor, revision.isProvisional,
            revision.reversesCostRevisionId, revision.commandId, revision.idempotencyKey,
            revision.costSequence, revision.approvedAt, revision.recordedAt, revision.createdBy,
            revision.deviceId, revision.contractVersion,
        )
        SyncContractV2Codec.requireValid(dto)
        val command = SyncContractV2Codec.json.parseToJsonElement(SyncContractV2Codec.encode(dto)) as JsonObject
        return StrongerMutationSource(
            organizationId = outbox.organizationId,
            aggregateType = "INVENTORY_COST_REVISION",
            aggregateId = revision.costRevisionId,
            businessIdentity = revision.idempotencyKey,
            domainOperation = if (revision.revisionKind.name == "REVERSAL") "REVERSAL" else "COMMAND",
            localSequence = outbox.createdAt.coerceAtLeast(1L),
            aggregateSequence = revision.costSequence ?: outbox.createdAt.coerceAtLeast(1L),
            payloadVersion = SYNC_REPAIR_PAYLOAD_VERSION,
            payload = buildJsonObject { put("inventoryCostRevision", command) },
            createdAtEpochMillis = outbox.createdAt,
        )
    }

    /** Explicit bridge for owner310 Optimal operations only; source lease stays in optimal_outbox. */
    fun optimal(row: OptimalOutboxEntity, owner310Aggregate: String): StrongerMutationSource {
        require(owner310Aggregate in setOf("OPTIMAL_VEHICLE", "OPTIMAL_MAINTENANCE", "OPTIMAL_FOLLOW_UP"))
        val expected = when (owner310Aggregate) {
            "OPTIMAL_VEHICLE" -> "VEHICLE"
            "OPTIMAL_MAINTENANCE" -> "MAINTENANCE"
            else -> "FOLLOW_UP"
        }
        val payloadObject = parseObject(row.payloadJson)
        return StrongerMutationSource(
            organizationId = row.organizationId,
            aggregateType = owner310Aggregate,
            aggregateId = row.aggregateId,
            businessIdentity = row.idempotencyKey,
            domainOperation = row.operation,
            localSequence = row.sequence,
            aggregateSequence = row.sequence,
            payloadVersion = 1,
            payload = buildJsonObject {
                put("optimalRequest", buildJsonObject {
                    put("aggregateType", expected)
                    put("operationType", row.operation)
                    put("payload", payloadObject)
                    put("idempotencyKey", row.idempotencyKey)
                    put("localVersion", row.sequence.coerceAtLeast(1L))
                })
            },
            createdAtEpochMillis = row.createdAt,
            baseVersion = row.remoteVersion,
        )
    }

    fun toMutation(source: StrongerMutationSource) = UnifiedStrongerBridgeRegistry.toUnifiedMutation(source)

    private fun parseObject(raw: String): JsonObject =
        json.parseToJsonElement(raw) as? JsonObject ?: error("stronger payload must be a JSON object")

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) {
        if (value == null) put(name, JsonNull) else put(name, JsonPrimitive(value))
    }
}
