package com.verto.app.data.sync

import androidx.room.withTransaction
import android.database.sqlite.SQLiteException
import com.verto.app.data.local.entity.FinancialInboxEntity
import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.InvoiceDueInstallmentEntity
import com.verto.app.data.local.entity.InvoiceReturnDocumentEntity
import com.verto.app.data.local.entity.InvoiceReturnLineEntity
import com.verto.app.data.local.entity.InvoiceReturnPaymentAllocationEntity
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

private const val FINANCIAL_SCHEMA_VERSION = 1
private const val OUTBOX_BATCH_SIZE = 100
private const val INBOX_BATCH_SIZE = 250
private const val MAX_OUTBOX_DRAIN_BATCHES = 20

@Serializable
private data class FinancialEventApplyRequest(
    @SerialName("p_event_id") val eventId: String,
    @SerialName("p_write_id") val writeId: String,
    @SerialName("p_aggregate_id") val aggregateId: String,
    @SerialName("p_aggregate_version") val aggregateVersion: Int,
    @SerialName("p_aggregate_sequence") val aggregateSequence: Long,
    @SerialName("p_operation_type") val operationType: String,
    @SerialName("p_payload_version") val payloadVersion: Int,
    @SerialName("p_schema_version") val schemaVersion: Int,
    @SerialName("p_payload") val payload: String,
    @SerialName("p_occurred_at") val occurredAt: Long,
    @SerialName("p_recorded_at") val recordedAt: Long,
)

@Serializable
private data class FinancialEventApplyResult(
    @SerialName("event_id") val eventId: String = "",
    @SerialName("write_id") val writeId: String = "",
    @SerialName("aggregate_id") val aggregateId: String = "",
    @SerialName("server_revision") val serverRevision: Long = 0L,
    @SerialName("server_recorded_at") val serverRecordedAt: Long = 0L,
    val status: String = "",
    val replayed: Boolean = false,
    @SerialName("conflict_reason") val conflictReason: String = "",
)

@Serializable
private data class FinancialEventPullRequest(
    @SerialName("p_since_revision") val sinceRevision: Long,
    @SerialName("p_limit") val limit: Int,
)

@Serializable
private data class FinancialInboundEventDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("aggregate_id") val aggregateId: String,
    @SerialName("aggregate_version") val aggregateVersion: Int,
    @SerialName("aggregate_sequence") val aggregateSequence: Long,
    @SerialName("operation_type") val operationType: String,
    @SerialName("payload_version") val payloadVersion: Int,
    @SerialName("schema_version") val schemaVersion: Int,
    val payload: String,
    @SerialName("occurred_at") val occurredAt: Long,
    @SerialName("recorded_at") val recordedAt: Long,
    @SerialName("server_revision") val serverRevision: Long,
)

/**
 * Drains the financial Outbox before legacy row upserts. A CONFLICT is terminal for automatic
 * delivery of that aggregate: the event becomes REQUIRES_REVIEW and compatibility row pushes are
 * blocked until a human resolves it.
 */
suspend fun SyncRuntime.pushFinancialOutbox(orgId: String) {
    require(orgId.isNotBlank()) { "financial outbox requires organization" }

    repeat(MAX_OUTBOX_DRAIN_BATCHES) {
        val now = System.currentTimeMillis()
        val events = db.invoiceDao().getReadyFinancialOutbox(orgId, now, OUTBOX_BATCH_SIZE)
        if (events.isEmpty()) return

        for (event in events) {
            val result = try {
                supabase.postgrest.rpc(
                    "financial_sync_apply_event_v1",
                    event.toApplyRequest(),
                ).decodeList<FinancialEventApplyResult>().single()
            } catch (failure: Exception) {
                db.invoiceDao().retryFinancialOutbox(
                    eventId = event.eventId,
                    nextAttemptAt = now + retryDelayMillis(event.attemptCount + 1),
                    reason = failure.safeFinancialSyncReason(),
                )
                throw failure
            }

            require(result.eventId.isBlank() || result.eventId == event.eventId) {
                "financial sync response event mismatch"
            }
            require(result.aggregateId.isBlank() || result.aggregateId == event.aggregateId) {
                "financial sync response aggregate mismatch"
            }
            when (result.status.uppercase()) {
                "APPLIED", "REPLAYED" -> {
                    check(
                        db.invoiceDao().acknowledgeFinancialOutbox(
                            eventId = event.eventId,
                            serverRevision = result.serverRevision,
                            syncedAt = result.serverRecordedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        ) == 1
                    ) { "financial outbox acknowledgement lost" }
                }
                "CONFLICT", "REQUIRES_REVIEW" -> {
                    db.invoiceDao().markFinancialOutboxRequiresReview(
                        eventId = event.eventId,
                        reason = result.conflictReason.ifBlank { "server financial conflict" },
                        serverRevision = result.serverRevision.takeIf { it > 0L },
                    )
                }
                "WAITING_DEPENDENCY" -> {
                    db.invoiceDao().retryFinancialOutbox(
                        eventId = event.eventId,
                        nextAttemptAt = now + retryDelayMillis(event.attemptCount + 1),
                        reason = result.conflictReason.ifBlank { "remote dependency is not ready" },
                    )
                }
                else -> {
                    db.invoiceDao().retryFinancialOutbox(
                        eventId = event.eventId,
                        nextAttemptAt = now + retryDelayMillis(event.attemptCount + 1),
                        reason = "unsupported financial sync result: ${result.status.take(48)}",
                    )
                    error("unsupported financial sync result")
                }
            }
        }

        if (events.size < OUTBOX_BATCH_SIZE) return
    }
    error("financial outbox exceeded bounded drain limit")
}

/**
 * Pulls only the durable event feed. Legacy row pulls still hydrate the compatibility tables during
 * the transition window. The Inbox owns deduplication and dependency state, never row absence.
 */
suspend fun SyncRuntime.pullFinancialInbox(orgId: String) {
    require(orgId.isNotBlank()) { "financial inbox requires organization" }
    var cursor = db.invoiceDao().getFinancialInboxRevision(orgId)

    repeat(20) {
        val remote = supabase.postgrest.rpc(
            "financial_sync_pull_events_v1",
            FinancialEventPullRequest(cursor, INBOX_BATCH_SIZE),
        ).decodeList<FinancialInboundEventDto>()
        if (remote.isEmpty()) return

        require(remote.all { it.organizationId == orgId }) {
            "financial inbox returned foreign organization data"
        }
        val ordered = remote.sortedBy { it.serverRevision }
        require(ordered.all { it.serverRevision > cursor }) {
            "financial inbox page did not advance"
        }
        require(ordered.all { it.schemaVersion in 1..FINANCIAL_SCHEMA_VERSION }) {
            "unsupported financial event schema"
        }

        ordered.forEach { dto ->
            val invoiceExists = db.invoiceDao().getInvoiceByIdSync(dto.aggregateId) != null
            val initialState = if (invoiceExists || dto.operationType == "INVOICE_CREATED") {
                "RECEIVED"
            } else {
                "WAITING_DEPENDENCY"
            }
            db.invoiceDao().insertFinancialInbox(
                FinancialInboxEntity(
                    eventId = dto.eventId,
                    organizationId = orgId,
                    aggregateId = dto.aggregateId,
                    aggregateVersion = dto.aggregateVersion.coerceAtLeast(1),
                    sequence = dto.aggregateSequence,
                    operationType = dto.operationType,
                    payloadVersion = dto.payloadVersion,
                    schemaVersion = dto.schemaVersion,
                    payload = dto.payload,
                    occurredAt = dto.occurredAt,
                    recordedAt = dto.recordedAt,
                    serverRevision = dto.serverRevision,
                    applyState = initialState,
                    applyReason = if (initialState == "WAITING_DEPENDENCY") "parent invoice not available" else "",
                )
            )
            cursor = maxOf(cursor, dto.serverRevision)
        }

        if (remote.size < INBOX_BATCH_SIZE) return
    }
    error("financial inbox exceeded bounded pull limit")
}

/** Called after invoice/payment compatibility pulls to release out-of-order child events safely. */
suspend fun SyncRuntime.reconcileFinancialInbox(orgId: String) {
    val pending = db.invoiceDao().getPendingFinancialInbox(orgId)
    val now = System.currentTimeMillis()
    pending.forEach { event ->
        val invoice = db.invoiceDao().getInvoiceByIdSync(event.aggregateId)
        if (invoice == null) {
            db.invoiceDao().updateFinancialInboxState(
                event.eventId,
                "WAITING_DEPENDENCY",
                "parent invoice not available",
                null,
            )
            return@forEach
        }

        if (event.operationType == "INVOICE_RETURN_POSTED") {
            val result = materializeInvoiceReturnEvent(event)
            db.invoiceDao().updateFinancialInboxState(
                event.eventId,
                result.state,
                result.reason,
                now.takeIf { result.state == "APPLIED" },
            )
            return@forEach
        }

        val dependencyReady = when (event.operationType) {
            "PAYMENT_RECORDED", "PAYMENT_REVERSED" -> {
                val paymentId = runCatching { JSONObject(event.payload).optString("paymentId") }.getOrDefault("")
                paymentId.isNotBlank() && db.paymentDao().getPaymentByIdSync(paymentId) != null
            }
            else -> invoice.lifecycleVersion >= event.aggregateVersion
        }
        if (dependencyReady && event.operationType in INVOICE_AGGREGATE_OPERATIONS_370) {
            val scheduleResult = materializeInvoiceDueInstallments370(event, invoice.transactionCurrencyCode)
            db.invoiceDao().updateFinancialInboxState(
                event.eventId,
                scheduleResult.state,
                scheduleResult.reason,
                now.takeIf { scheduleResult.state == "APPLIED" },
            )
            return@forEach
        }
        db.invoiceDao().updateFinancialInboxState(
            event.eventId,
            if (dependencyReady) "APPLIED" else "WAITING_DEPENDENCY",
            if (dependencyReady) "" else "compatibility row has not arrived yet",
            now.takeIf { dependencyReady },
        )
    }
}


private data class ReturnMaterializationResult(val state: String, val reason: String = "")

private val INVOICE_AGGREGATE_OPERATIONS_370 = setOf("INVOICE_CREATED", "INVOICE_UPDATED", "INVOICE_VOIDED")

private suspend fun SyncRuntime.materializeInvoiceDueInstallments370(
    event: FinancialInboxEntity,
    transactionCurrencyCode: String,
): ReturnMaterializationResult {
    return try {
        val payload = JSONObject(event.payload)
        require(payload.optString("invoiceId") == event.aggregateId) { "invoice schedule aggregate mismatch" }
        if (!payload.has("dueInstallments")) return ReturnMaterializationResult("APPLIED")
        val rowsJson = payload.optJSONArray("dueInstallments") ?: JSONArray()
        val rows = buildList {
            for (index in 0 until rowsJson.length()) {
                val row = rowsJson.getJSONObject(index)
                val id = row.getString("id").trim()
                val sequence = row.getInt("sequence")
                val amountMinor = row.getLong("amountMinor")
                val currencyCode = row.getString("currencyCode").trim().uppercase()
                val dueDate = row.getLong("dueDate")
                val writeId = row.optString("writeId", event.eventId).trim().ifBlank { event.eventId }
                require(id.isNotEmpty()) { "installment id is required" }
                require(sequence >= 0) { "installment sequence is invalid" }
                require(amountMinor > 0L) { "installment amount must be positive" }
                require(currencyCode == transactionCurrencyCode.trim().uppercase()) {
                    "installment currency differs from invoice transaction currency"
                }
                require(dueDate > 0L) { "installment due date is invalid" }
                add(
                    InvoiceDueInstallmentEntity(
                        id = id,
                        invoiceId = event.aggregateId,
                        sequence = sequence,
                        amountMinor = amountMinor,
                        currencyCode = currencyCode,
                        dueDate = dueDate,
                        createdAt = event.occurredAt.takeIf { it > 0L } ?: event.recordedAt,
                        writeId = writeId,
                    )
                )
            }
        }
        require(rows.map { it.sequence }.distinct().size == rows.size) { "duplicate installment sequence" }
        require(rows.map { it.id }.distinct().size == rows.size) { "duplicate installment id" }
        db.withTransaction {
            db.invoiceDao().deleteDueInstallments(event.aggregateId)
            if (rows.isNotEmpty()) db.invoiceDao().insertDueInstallments(rows.sortedBy { it.sequence })
        }
        ReturnMaterializationResult("APPLIED")
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: org.json.JSONException) {
        failure.toScheduleReview370()
    } catch (failure: IllegalArgumentException) {
        failure.toScheduleReview370()
    } catch (failure: IllegalStateException) {
        failure.toScheduleReview370()
    } catch (failure: SQLiteException) {
        failure.toScheduleReview370()
    }
}

private fun Throwable.toScheduleReview370() = ReturnMaterializationResult(
    "REQUIRES_REVIEW",
    "invoice installment schedule rejected locally: ${message.orEmpty().replace(Regex("\\s+"), " ").take(160)}",
)

private suspend fun SyncRuntime.materializeInvoiceReturnEvent(event: FinancialInboxEntity): ReturnMaterializationResult {
    return try {
        val payload = JSONObject(event.payload)
        val returnId = payload.requireText("returnId")
        val originalInvoiceId = payload.requireText("originalInvoiceId")
        if (originalInvoiceId != event.aggregateId) {
            return ReturnMaterializationResult("REQUIRES_REVIEW", "return parent invoice identity mismatch")
        }
        val invoice = db.invoiceDao().getInvoiceByIdSync(originalInvoiceId)
            ?: return ReturnMaterializationResult("WAITING_DEPENDENCY", "parent invoice not available")
        val originalItems = db.invoiceDao().getInvoiceItemsSync(originalInvoiceId).associateBy { it.id }
        val lineArray = payload.optJSONArray("lines") ?: JSONArray()
        if (lineArray.length() == 0) {
            return ReturnMaterializationResult("REQUIRES_REVIEW", "return event has no lines")
        }
        val lines = ArrayList<InvoiceReturnLineEntity>(lineArray.length())
        for (index in 0 until lineArray.length()) {
            val row = lineArray.getJSONObject(index)
            val originalItemId = row.requireText("originalInvoiceItemId")
            val originalItem = originalItems[originalItemId]
                ?: return ReturnMaterializationResult("WAITING_DEPENDENCY", "original invoice item not available")
            val quantity = row.getInt("quantity")
            if (quantity <= 0) return ReturnMaterializationResult("REQUIRES_REVIEW", "return quantity must be positive")
            lines += InvoiceReturnLineEntity(
                id = row.requireText("id"),
                returnId = returnId,
                originalInvoiceItemId = originalItem.id,
                inventoryItemId = row.optString("inventoryItemId", ""),
                itemNameSnapshot = row.optString("itemNameSnapshot", originalItem.itemName),
                quantity = quantity,
                unitTransactionAmountMinor = row.getLong("unitTransactionAmountMinor"),
                transactionAmountMinor = row.getLong("transactionAmountMinor"),
                unitFunctionalAmountMinor = row.getLong("unitFunctionalAmountMinor"),
                functionalAmountMinor = row.getLong("functionalAmountMinor"),
                unitCostAtSaleMinor = row.getLong("unitCostAtSaleMinor"),
                historicalCostAmountMinor = row.getLong("historicalCostAmountMinor"),
                originalPurchaseUnitCostMinor = row.getLong("originalPurchaseUnitCostMinor"),
            )
        }

        val allocationArray = payload.optJSONArray("paymentAllocations") ?: JSONArray()
        val allocations = ArrayList<InvoiceReturnPaymentAllocationEntity>(allocationArray.length())
        for (index in 0 until allocationArray.length()) {
            val row = allocationArray.getJSONObject(index)
            val paymentId = row.requireText("paymentId")
            val payment = db.paymentDao().getPaymentByIdSync(paymentId)
                ?: return ReturnMaterializationResult("WAITING_DEPENDENCY", "return payment allocation dependency not available")
            if (payment.invoiceId != originalInvoiceId) {
                return ReturnMaterializationResult("REQUIRES_REVIEW", "return allocation belongs to another invoice")
            }
            allocations += InvoiceReturnPaymentAllocationEntity(
                id = row.requireText("id"),
                returnId = returnId,
                paymentId = paymentId,
                allocatedFunctionalAmountMinor = row.getLong("allocatedFunctionalAmountMinor"),
                createdAt = row.getLong("createdAt"),
            )
        }

        val document = InvoiceReturnDocumentEntity(
            id = returnId,
            organizationId = event.organizationId,
            originalInvoiceId = originalInvoiceId,
            clientId = payload.requireText("clientId"),
            documentType = payload.requireText("documentType"),
            settlementMode = payload.requireText("settlementMode"),
            transactionCurrencyCode = payload.optString("transactionCurrencyCode", ""),
            functionalCurrencyCode = payload.optString("functionalCurrencyCode", ""),
            transactionAmountMinor = payload.getLong("transactionAmountMinor"),
            functionalAmountMinor = payload.getLong("functionalAmountMinor"),
            reason = payload.requireText("reason"),
            occurredAt = payload.getLong("occurredAt"),
            recordedAt = payload.getLong("recordedAt"),
            createdBy = payload.optString("createdBy", ""),
            createdByName = payload.optString("createdByName", ""),
            writeId = payload.requireText("writeId"),
            sourceVersion = payload.optInt("sourceVersion", 1).coerceAtLeast(1),
        )
        if (invoice.clientId != document.clientId) {
            return ReturnMaterializationResult("REQUIRES_REVIEW", "return party does not match original invoice")
        }
        db.invoiceReturnDao().materializeRemote(
            document = document,
            lines = lines.sortedBy { it.id },
            allocations = allocations.sortedWith(compareBy<InvoiceReturnPaymentAllocationEntity> { it.createdAt }.thenBy { it.id }),
        )
        ReturnMaterializationResult("APPLIED")
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: org.json.JSONException) {
        failure.toReturnReview()
    } catch (failure: IllegalArgumentException) {
        failure.toReturnReview()
    } catch (failure: IllegalStateException) {
        failure.toReturnReview()
    } catch (failure: SQLiteException) {
        failure.toReturnReview()
    }
}

private fun Throwable.toReturnReview() = ReturnMaterializationResult(
    "REQUIRES_REVIEW",
    "return event rejected locally: ${message.orEmpty().replace(Regex("\\s+"), " ").take(160)}",
)

private fun JSONObject.requireText(name: String): String =
    optString(name, "").trim().also { require(it.isNotEmpty()) { "$name is required" } }

/** Aggregate ids with any unacknowledged event cannot use the legacy write path. */
suspend fun SyncRuntime.financiallyBlockedAggregateIds(orgId: String): Set<String> =
    db.invoiceDao().getFinanciallyBlockedAggregateIds(orgId).toSet()

private fun FinancialOutboxEntity.toApplyRequest() = FinancialEventApplyRequest(
    eventId = eventId,
    writeId = writeId,
    aggregateId = aggregateId,
    aggregateVersion = aggregateVersion,
    aggregateSequence = sequence,
    operationType = operationType,
    payloadVersion = payloadVersion,
    schemaVersion = schemaVersion,
    payload = payload,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
)

internal object FinancialAggregateConflictPolicy {
    enum class Decision { APPLY_REMOTE, KEEP_LOCAL, REQUIRES_REVIEW }

    fun resolve(
        localLifecycle: String,
        localVersion: Int,
        localDirty: Boolean,
        remoteLifecycle: String,
        remoteVersion: Int,
    ): Decision {
        if (remoteVersion < localVersion) return Decision.KEEP_LOCAL
        if (localLifecycle == "VOID") {
            return if (remoteLifecycle == "VOID" && remoteVersion == localVersion) {
                Decision.KEEP_LOCAL
            } else {
                Decision.REQUIRES_REVIEW
            }
        }
        if (remoteLifecycle == "DRAFT" && localLifecycle != "DRAFT") {
            return Decision.REQUIRES_REVIEW
        }
        if (localDirty && remoteVersion != localVersion) {
            return Decision.REQUIRES_REVIEW
        }
        if (remoteVersion == localVersion) {
            if (remoteLifecycle != localLifecycle) return Decision.REQUIRES_REVIEW
            // Equal-version POSTED/VOID rows are compatibility echoes, never a field-merge signal.
            // Only DRAFT may accept same-version remote fields when the local draft is clean.
            return if (localLifecycle == "DRAFT" && !localDirty) {
                Decision.APPLY_REMOTE
            } else {
                Decision.KEEP_LOCAL
            }
        }
        if (remoteVersion != localVersion + 1) return Decision.REQUIRES_REVIEW

        return when (localLifecycle) {
            "DRAFT" -> if (localDirty) Decision.REQUIRES_REVIEW else Decision.APPLY_REMOTE
            "POSTED" -> when (remoteLifecycle) {
                "POSTED", "VOID" -> if (localDirty) Decision.REQUIRES_REVIEW else Decision.APPLY_REMOTE
                else -> Decision.REQUIRES_REVIEW
            }
            else -> Decision.REQUIRES_REVIEW
        }
    }
}

internal fun retryDelayMillis(attempt: Int): Long {
    val exponent = (attempt - 1).coerceIn(0, 10)
    return (30_000L * (1L shl exponent)).coerceAtMost(6L * 60L * 60L * 1000L)
}

private fun Throwable.safeFinancialSyncReason(): String =
    "${this::class.simpleName.orEmpty()}: ${message.orEmpty()}"
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(240)
