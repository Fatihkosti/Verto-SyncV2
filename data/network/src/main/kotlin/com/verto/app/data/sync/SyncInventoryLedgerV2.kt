package com.verto.app.data.sync

import com.verto.app.data.local.entity.InventoryCostRevisionEntity
import com.verto.app.data.local.entity.InventoryCostRevisionKind
import com.verto.app.data.local.entity.InventoryMovementEntity
import com.verto.app.data.local.entity.InventoryMovementKind
import com.verto.app.data.local.entity.InventorySyncConflictEntity
import com.verto.app.data.local.entity.MovementType
import com.verto.app.data.remote.dto.InventoryCommandAckDto
import com.verto.app.data.remote.dto.InventoryCommandBatchRequest
import com.verto.app.data.remote.dto.InventoryCommandDto
import com.verto.app.data.remote.dto.InventoryCostAckDto
import com.verto.app.data.remote.dto.InventoryCostBatchRequest
import com.verto.app.data.remote.dto.InventoryCostCommandDto
import com.verto.app.data.remote.dto.InventoryCostRevisionDto
import com.verto.app.data.remote.dto.InventoryMovementDto
import com.verto.app.data.remote.dto.InventoryMovementPullRequest
import com.verto.app.data.remote.dto.toRemoteDouble
import com.verto.app.utils.SupabaseDateParser
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

@Suppress("UNUSED_PARAMETER")
suspend fun SyncRuntime.pushInventoryMovements(orgId: String, userId: String) {
    reconcileLegacyInventoryIfRequired(orgId)
    val dao = db.inventoryDao()
    val now = System.currentTimeMillis()
    val pending = dao.getPendingInventoryStockOutbox(orgId, now, 100)
    if (pending.isEmpty()) return
    val commands = pending.map { outbox ->
        val mv = requireNotNull(dao.getMovementById(outbox.movementId)) { "inventory outbox movement missing" }
        InventoryCommandDto(
            clientOutboxId = outbox.id, movementId = mv.id, itemId = mv.itemId, invoiceId = mv.invoiceId,
            clientId = mv.clientId.takeIf(String::isNotBlank)?.let { localClientIdToUuid(it, orgId) },
            movementKind = requireNotNull(mv.movementKind).name,
            signedBaseQuantity = requireNotNull(mv.signedBaseQuantity), unitPriceMinor = mv.unitPriceMinor,
            note = mv.note, sourceType = mv.sourceType, sourceId = mv.sourceId,
            sourceLineId = mv.sourceLineId, commandId = requireNotNull(mv.commandId),
            idempotencyKey = requireNotNull(mv.idempotencyKey), postingGroupId = mv.postingGroupId,
            reversesMovementId = mv.reversesMovementId,
            conversionFactorSnapshot = mv.conversionFactorSnapshot ?: "1",
            occurredAt = mv.occurredAt ?: mv.createdAt, deviceId = mv.deviceId ?: "android",
            contractVersion = mv.contractVersion,
        )
    }
    val acknowledgements = try {
        supabase.postgrest.rpc("inventory_apply_commands_v2", InventoryCommandBatchRequest(commands))
            .decodeList<InventoryCommandAckDto>()
    } catch (failure: Exception) {
        pending.forEach { row ->
            dao.retryInventoryStockOutbox(row.id, now + inventoryRetryDelayMillis(row.attemptCount + 1), failure.message.orEmpty().take(240))
        }
        throw failure
    }
    val byId = acknowledgements.associateBy { it.clientOutboxId }
    pending.forEach { row ->
        val ack = requireNotNull(byId[row.id]) { "inventory RPC omitted outbox ${row.id}" }
        require(ack.movementId == row.movementId) { "inventory acknowledgement movement mismatch" }
        when (ack.status.uppercase()) {
            "APPLIED", "DUPLICATE" -> dao.acknowledgeInventoryStockOutbox(row.id, ack.serverSequence)
            "QUARANTINED" -> dao.reviewInventoryStockOutbox(row.id, "server quarantine")
            else -> dao.retryInventoryStockOutbox(row.id, now + inventoryRetryDelayMillis(row.attemptCount + 1), ack.status)
        }
        if (ack.conflictType == "OVERSOLD_CONFLICT") {
            dao.insertInventorySyncConflict(
                InventorySyncConflictEntity(
                    id = "oversold:${row.itemId}:${ack.serverSequence}", organizationId = orgId,
                    conflictKey = "oversold:${row.itemId}:${ack.serverSequence}", itemId = row.itemId,
                    conflictType = ack.conflictType, serverSequence = ack.serverSequence,
                    projectedQuantity = ack.projectedQuantity, details = "movement=${row.movementId}", detectedAt = now,
                )
            )
        }
    }
}

suspend fun SyncRuntime.pushInventoryCostRevisions(orgId: String) {
    val dao = db.inventoryDao()
    val now = System.currentTimeMillis()
    val pending = dao.getPendingInventoryCostOutbox(orgId, now, 100)
    if (pending.isEmpty()) return
    val revisions = pending.map { outbox ->
        val row = requireNotNull(dao.getCostRevisionById(outbox.costRevisionId)) { "inventory cost outbox revision missing" }
        InventoryCostCommandDto(
            clientOutboxId = outbox.id, costRevisionId = row.costRevisionId, itemId = row.itemId,
            sourceType = row.sourceType, sourceId = row.sourceId, sourceLineId = row.sourceLineId,
            revisionKind = row.revisionKind.name, directPurchaseCostMinor = row.directPurchaseCostMinor,
            landedCostPerBaseUnitMinor = row.landedCostPerBaseUnitMinor,
            approvedInventoryCostMinor = row.approvedInventoryCostMinor, currencyCode = row.currencyCode,
            exchangeRateSnapshot = row.exchangeRateSnapshot, allocationBasis = row.allocationBasis,
            allocationResidualMinor = row.allocationResidualMinor, isProvisional = row.isProvisional,
            reversesCostRevisionId = row.reversesCostRevisionId, commandId = row.commandId,
            idempotencyKey = row.idempotencyKey, approvedAt = row.approvedAt, deviceId = row.deviceId,
            contractVersion = row.contractVersion,
        )
    }
    val acknowledgements = try {
        supabase.postgrest.rpc("inventory_apply_cost_revisions_v2", InventoryCostBatchRequest(revisions))
            .decodeList<InventoryCostAckDto>()
    } catch (failure: Exception) {
        pending.forEach { row ->
            dao.retryInventoryCostOutbox(row.id, now + inventoryRetryDelayMillis(row.attemptCount + 1), failure.message.orEmpty().take(240))
        }
        throw failure
    }
    val byId = acknowledgements.associateBy { it.clientOutboxId }
    pending.forEach { row ->
        val ack = requireNotNull(byId[row.id]) { "inventory cost RPC omitted outbox ${row.id}" }
        require(ack.costRevisionId == row.costRevisionId) { "inventory cost acknowledgement mismatch" }
        when (ack.status.uppercase()) {
            "APPLIED", "DUPLICATE" -> dao.acknowledgeInventoryCostOutbox(row.id, ack.costSequence)
            "QUARANTINED" -> dao.reviewInventoryCostOutbox(row.id, "server quarantine")
            else -> dao.retryInventoryCostOutbox(row.id, now + inventoryRetryDelayMillis(row.attemptCount + 1), ack.status)
        }
    }
}

suspend fun SyncRuntime.pullInventoryMovements(orgId: String, forceFull: Boolean = false) {
    val blockedInvoices = financiallyBlockedAggregateIds(orgId)
    val dao = db.inventoryDao()
    val cursor = dao.getInventoryServerCursor(orgId)
    val remote = supabase.postgrest.rpc("inventory_pull_movements_v2", InventoryMovementPullRequest(cursor, 500))
        .decodeList<InventoryMovementDto>()
    if (remote.isEmpty()) return
    require(remote.zipWithNext().all { (a, b) -> (a.serverSequence ?: 0L) < (b.serverSequence ?: 0L) }) {
        "inventory server cursor page is not strictly ordered"
    }
    require(remote.none { it.invoiceId in blockedInvoices }) { "inventory cursor page contains a financially quarantined aggregate" }
    val existingItemIds = dao.getAllItemsIncludingArchivedSync().map { it.id }.toSet()
    val entities = remote.map { dto ->
        require(dto.itemId in existingItemIds) { "inventory pull references a missing item ${dto.itemId}" }
        InventoryMovementEntity(
            id = dto.id, itemId = dto.itemId, invoiceId = dto.invoiceId,
            clientId = resolveClientId(dto.clientId, orgId),
            movementType = runCatching { MovementType.valueOf(dto.movementType) }.getOrDefault(MovementType.ADJUST),
            quantity = dto.quantity, quantityBefore = dto.quantityBefore, quantityAfter = dto.quantityAfter,
            unitPrice = dto.unitPrice.toRemoteDouble(), note = dto.note, sourceType = dto.sourceType.orEmpty(),
            sourceId = dto.sourceId.orEmpty(), organizationId = dto.organizationId.ifBlank { orgId },
            movementKind = dto.movementKind?.let { runCatching { InventoryMovementKind.valueOf(it) }.getOrNull() },
            signedBaseQuantity = dto.signedBaseQuantity, sourceLineId = dto.sourceLineId,
            commandId = dto.commandId, idempotencyKey = dto.idempotencyKey, postingGroupId = dto.postingGroupId,
            reversesMovementId = dto.reversesMovementId, conversionFactorSnapshot = dto.conversionFactorSnapshot,
            occurredAt = dto.occurredAt, recordedAt = dto.recordedAt,
            serverAcceptedAt = dto.serverAcceptedAt?.let { SupabaseDateParser.parse(it) },
            serverSequence = dto.serverSequence, createdBy = dto.createdBy, deviceId = dto.deviceId,
            contractVersion = dto.contractVersion, createdAt = SupabaseDateParser.parse(dto.createdAt),
        )
    }
    dao.applyPulledInventoryMovements(orgId, entities, requireNotNull(remote.last().serverSequence))
}

suspend fun SyncRuntime.pullInventoryCostRevisions(orgId: String) {
    val dao = db.inventoryDao()
    val cursor = dao.getInventoryCostServerCursor(orgId)
    val remote = supabase.postgrest.rpc("inventory_pull_cost_revisions_v2", InventoryMovementPullRequest(cursor, 500))
        .decodeList<InventoryCostRevisionDto>()
    if (remote.isEmpty()) return
    require(remote.zipWithNext().all { (a, b) -> a.costSequence < b.costSequence }) {
        "inventory cost cursor page is not strictly ordered"
    }
    val entities = remote.map { dto ->
        InventoryCostRevisionEntity(
            costRevisionId = dto.costRevisionId, organizationId = dto.organizationId, itemId = dto.itemId,
            sourceType = dto.sourceType, sourceId = dto.sourceId, sourceLineId = dto.sourceLineId,
            revisionKind = InventoryCostRevisionKind.valueOf(dto.revisionKind),
            directPurchaseCostMinor = dto.directPurchaseCostMinor,
            landedCostPerBaseUnitMinor = dto.landedCostPerBaseUnitMinor,
            approvedInventoryCostMinor = dto.approvedInventoryCostMinor, currencyCode = dto.currencyCode,
            exchangeRateSnapshot = dto.exchangeRateSnapshot, allocationBasis = dto.allocationBasis,
            allocationResidualMinor = dto.allocationResidualMinor, isProvisional = dto.isProvisional,
            reversesCostRevisionId = dto.reversesCostRevisionId, commandId = dto.commandId,
            idempotencyKey = dto.idempotencyKey, costSequence = dto.costSequence, approvedAt = dto.approvedAt,
            recordedAt = dto.recordedAt, createdBy = dto.createdBy, deviceId = dto.deviceId,
            contractVersion = dto.contractVersion,
        )
    }
    dao.applyPulledInventoryCostRevisions(orgId, entities, remote.last().costSequence)
}

private fun inventoryRetryDelayMillis(attempt: Int): Long =
    (1_000L shl attempt.coerceIn(0, 10)).coerceAtMost(15L * 60L * 1_000L)
