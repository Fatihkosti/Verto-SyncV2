package com.verto.app.data.sync.push

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.sync.SyncReceiptStatus
import com.verto.app.data.sync.UnifiedStrongerBridgeRegistry
import com.verto.app.data.sync.UnifiedSyncPushRemote
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** M05 explicit V2 consumers for stronger financial and inventory authorities. */
@Singleton
class UnifiedStrongerOutboxPushEngine @Inject constructor(
    private val database: AppDatabase,
    private val remote: UnifiedSyncPushRemote,
) {
    suspend fun pushAvailable(organizationId: String, limitPerSource: Int = 50): StrongerPushRunResult {
        require(organizationId.isNotBlank()) { "SCOPE_MISMATCH: organizationId required" }
        require(limitPerSource in 1..500)
        val now = System.currentTimeMillis()
        var sent = 0
        var ack = 0
        var retry = 0
        var review = 0
        var rejected = 0

        val financialRows = database.invoiceDao().getReadyFinancialOutbox(organizationId, now, limitPerSource)
        for (row in financialRows) {
            val source = try {
                UnifiedStrongerSourceFactory.financial(row)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                database.invoiceDao().markFinancialOutboxRequiresReview(row.eventId, "M05_SOURCE_INVALID:${failure.message}", null)
                review += 1
                continue
            }
            sent += 1
            val response = try {
                remote.apply(UnifiedStrongerSourceFactory.toMutation(source))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val code = classify(t)
                if (code == "AUTHENTICATION") {
                    database.invoiceDao().retryFinancialOutbox(row.eventId, now + AUTH_RECHECK_MILLIS, code)
                    throw UnifiedSyncPushFailure(code, "financial push authentication blocked; intent preserved", t)
                }
                if (code in RETRYABLE_CODES) {
                    database.invoiceDao().retryFinancialOutbox(row.eventId, now + retryDelay(row.attemptCount + 1), code)
                    retry += 1
                } else {
                    database.invoiceDao().markFinancialOutboxRequiresReview(row.eventId, code, null)
                    review += 1
                }
                continue
            }
            val receipt = try {
                UnifiedStrongerBridgeRegistry.reconcileReceipt(source, response.receipt)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                database.invoiceDao().markFinancialOutboxRequiresReview(row.eventId, "SERVER_PROTOCOL_INCONSISTENCY", response.receipt.serverRevision)
                review += 1
                continue
            }
            when (receipt.status) {
                SyncReceiptStatus.APPLIED, SyncReceiptStatus.REPLAYED, SyncReceiptStatus.NO_OP -> {
                    val domainRevision = response.receipt.serverRevision ?: response.receipt.authoritativePayload
                        ?.get("financialEvent")?.jsonObject?.get("domainServerRevision")?.jsonPrimitive?.longOrNull
                    if (domainRevision == null || domainRevision <= 0L) {
                        database.invoiceDao().markFinancialOutboxRequiresReview(row.eventId, "SERVER_PROTOCOL_INCONSISTENCY:MISSING_FINANCIAL_REVISION", null)
                        review += 1
                    } else if (database.invoiceDao().acknowledgeFinancialOutbox(row.eventId, domainRevision, System.currentTimeMillis()) == 1) {
                        ack += 1
                    }
                }
                SyncReceiptStatus.RETRYABLE -> {
                    database.invoiceDao().retryFinancialOutbox(
                        row.eventId,
                        response.receipt.retryAfterEpochMillis ?: (System.currentTimeMillis() + retryDelay(row.attemptCount + 1)),
                        response.receipt.validationCode ?: "SERVER_RETRYABLE",
                    )
                    retry += 1
                }
                SyncReceiptStatus.CONFLICT -> {
                    database.invoiceDao().markFinancialOutboxRequiresReview(row.eventId, response.receipt.conflictCode ?: "CONFLICT", response.receipt.serverRevision)
                    review += 1
                }
                SyncReceiptStatus.REJECTED -> {
                    database.invoiceDao().markFinancialOutboxRequiresReview(row.eventId, response.receipt.validationCode ?: "REJECTED", response.receipt.serverRevision)
                    rejected += 1
                }
            }
        }

        val stockRows = database.inventoryDao().getPendingInventoryStockOutbox(organizationId, now, limitPerSource)
        for (outbox in stockRows) {
            val movement = database.inventoryDao().getMovementById(outbox.movementId)
            if (movement == null || movement.organizationId != organizationId) {
                database.inventoryDao().reviewInventoryStockOutbox(outbox.id, "M05_MOVEMENT_MISSING_OR_SCOPE")
                review += 1; continue
            }
            val source = try {
                UnifiedStrongerSourceFactory.inventoryMovement(outbox, movement)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                database.inventoryDao().reviewInventoryStockOutbox(outbox.id, "M05_SOURCE_INVALID:${failure.message}")
                review += 1
                continue
            }
            sent += 1
            val response = try {
                remote.apply(UnifiedStrongerSourceFactory.toMutation(source))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val code = classify(t)
                if (code == "AUTHENTICATION") {
                    database.inventoryDao().retryInventoryStockOutbox(outbox.id, now + AUTH_RECHECK_MILLIS, code)
                    throw UnifiedSyncPushFailure(code, "inventory push authentication blocked; intent preserved", t)
                }
                if (code in RETRYABLE_CODES) {
                    database.inventoryDao().retryInventoryStockOutbox(outbox.id, now + retryDelay(outbox.attemptCount + 1), code); retry += 1
                } else {
                    database.inventoryDao().reviewInventoryStockOutbox(outbox.id, code); review += 1
                }
                continue
            }
            val receipt = try {
                UnifiedStrongerBridgeRegistry.reconcileReceipt(source, response.receipt)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                database.inventoryDao().reviewInventoryStockOutbox(outbox.id, "SERVER_PROTOCOL_INCONSISTENCY")
                review += 1
                continue
            }
            when (receipt.status) {
                SyncReceiptStatus.APPLIED, SyncReceiptStatus.REPLAYED, SyncReceiptStatus.NO_OP -> {
                    val sequence = response.receipt.serverVersion
                    if (sequence == null || sequence <= 0L) {
                        database.inventoryDao().reviewInventoryStockOutbox(outbox.id, "SERVER_PROTOCOL_INCONSISTENCY:MISSING_SERVER_SEQUENCE"); review += 1
                    } else if (database.inventoryDao().acknowledgeInventoryStockOutbox(outbox.id, sequence) == 1) ack += 1
                }
                SyncReceiptStatus.RETRYABLE -> {
                    database.inventoryDao().retryInventoryStockOutbox(outbox.id, response.receipt.retryAfterEpochMillis ?: (System.currentTimeMillis() + retryDelay(outbox.attemptCount + 1)), response.receipt.validationCode ?: "SERVER_RETRYABLE"); retry += 1
                }
                SyncReceiptStatus.CONFLICT -> { database.inventoryDao().reviewInventoryStockOutbox(outbox.id, response.receipt.conflictCode ?: "CONFLICT"); review += 1 }
                SyncReceiptStatus.REJECTED -> { database.inventoryDao().reviewInventoryStockOutbox(outbox.id, response.receipt.validationCode ?: "REJECTED"); rejected += 1 }
            }
        }

        val costRows = database.inventoryDao().getPendingInventoryCostOutbox(organizationId, now, limitPerSource)
        for (outbox in costRows) {
            val revision = database.inventoryDao().getCostRevisionById(outbox.costRevisionId)
            if (revision == null || revision.organizationId != organizationId) {
                database.inventoryDao().reviewInventoryCostOutbox(outbox.id, "M05_COST_REVISION_MISSING_OR_SCOPE")
                review += 1; continue
            }
            val source = try {
                UnifiedStrongerSourceFactory.inventoryCost(outbox, revision)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                database.inventoryDao().reviewInventoryCostOutbox(outbox.id, "M05_SOURCE_INVALID:${failure.message}")
                review += 1
                continue
            }
            sent += 1
            val response = try {
                remote.apply(UnifiedStrongerSourceFactory.toMutation(source))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val code = classify(t)
                if (code == "AUTHENTICATION") {
                    database.inventoryDao().retryInventoryCostOutbox(outbox.id, now + AUTH_RECHECK_MILLIS, code)
                    throw UnifiedSyncPushFailure(code, "inventory cost push authentication blocked; intent preserved", t)
                }
                if (code in RETRYABLE_CODES) {
                    database.inventoryDao().retryInventoryCostOutbox(outbox.id, now + retryDelay(outbox.attemptCount + 1), code); retry += 1
                } else {
                    database.inventoryDao().reviewInventoryCostOutbox(outbox.id, code); review += 1
                }
                continue
            }
            val receipt = try {
                UnifiedStrongerBridgeRegistry.reconcileReceipt(source, response.receipt)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                database.inventoryDao().reviewInventoryCostOutbox(outbox.id, "SERVER_PROTOCOL_INCONSISTENCY")
                review += 1
                continue
            }
            when (receipt.status) {
                SyncReceiptStatus.APPLIED, SyncReceiptStatus.REPLAYED, SyncReceiptStatus.NO_OP -> {
                    val sequence = response.receipt.serverVersion
                    if (sequence == null || sequence <= 0L) {
                        database.inventoryDao().reviewInventoryCostOutbox(outbox.id, "SERVER_PROTOCOL_INCONSISTENCY:MISSING_COST_SEQUENCE"); review += 1
                    } else if (database.inventoryDao().acknowledgeInventoryCostOutbox(outbox.id, sequence) == 1) ack += 1
                }
                SyncReceiptStatus.RETRYABLE -> {
                    database.inventoryDao().retryInventoryCostOutbox(outbox.id, response.receipt.retryAfterEpochMillis ?: (System.currentTimeMillis() + retryDelay(outbox.attemptCount + 1)), response.receipt.validationCode ?: "SERVER_RETRYABLE"); retry += 1
                }
                SyncReceiptStatus.CONFLICT -> { database.inventoryDao().reviewInventoryCostOutbox(outbox.id, response.receipt.conflictCode ?: "CONFLICT"); review += 1 }
                SyncReceiptStatus.REJECTED -> { database.inventoryDao().reviewInventoryCostOutbox(outbox.id, response.receipt.validationCode ?: "REJECTED"); rejected += 1 }
            }
        }

        val financialBacklog = database.invoiceDao().countFinancialOutboxBacklog(organizationId)
        val stockBacklog = database.inventoryDao().countInventoryStockBacklog(organizationId)
        val costBacklog = database.inventoryDao().countInventoryCostBacklog(organizationId)
        val next = listOfNotNull(
            database.invoiceDao().nextFinancialRetryAt(organizationId, System.currentTimeMillis()),
            database.inventoryDao().nextInventoryStockRetryAt(organizationId, System.currentTimeMillis()),
            database.inventoryDao().nextInventoryCostRetryAt(organizationId, System.currentTimeMillis()),
        ).minOrNull()
        val saturated = financialRows.size >= limitPerSource || stockRows.size >= limitPerSource || costRows.size >= limitPerSource
        return StrongerPushRunResult(
            sent = sent, acknowledged = ack, retried = retry, requiresReview = review, rejected = rejected,
            backlog = financialBacklog + stockBacklog + costBacklog,
            immediateMore = saturated,
            nextEligibleAt = next,
        )
    }

    private fun classify(t: Throwable): String {
        val text = generateSequence(t) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
        return when {
            text.contains("401") || text.contains("403") || text.contains("AUTH", true) -> "AUTHENTICATION"
            text.contains("429") || text.contains("too many requests", true) -> "RATE_LIMITED"
            text.contains("SCOPE_MISMATCH", true) -> "SCOPE_MISMATCH"
            text.contains("VALIDATION", true) || text.contains("CONTRACT_UNSUPPORTED", true) -> "VALIDATION"
            else -> "TRANSIENT_NETWORK"
        }
    }

    private fun retryDelay(attempt: Int): Long = (1_000L * (1L shl attempt.coerceIn(0, 8))).coerceAtMost(300_000L)

    private companion object {
        val RETRYABLE_CODES = setOf("RATE_LIMITED", "TRANSIENT_NETWORK")
        const val AUTH_RECHECK_MILLIS = 60_000L
    }
}

data class StrongerPushRunResult(
    val sent: Int,
    val acknowledged: Int,
    val retried: Int,
    val requiresReview: Int,
    val rejected: Int,
    val backlog: Long,
    val immediateMore: Boolean,
    val nextEligibleAt: Long?,
)
