package com.verto.app.data.operations.transaction

import com.verto.app.data.local.AppDatabase
import com.verto.app.data.local.entity.FinancialOutboxEntity
import com.verto.app.data.local.entity.PaymentEntity
import com.verto.app.data.sync.FinancialAggregateSnapshotV2
import com.verto.app.data.sync.FrozenMutationStore
import com.verto.app.data.sync.FrozenOwnerIntent
import com.verto.app.data.sync.SYNC_REPAIR_PAYLOAD_VERSION
import com.verto.app.data.sync.SyncBatchCoordinatorV2
import com.verto.app.data.sync.SyncContractV2Codec
import com.verto.app.data.sync.ownership.ProtectedSyncKey
import com.verto.app.data.sync.ownership.SyncSourceOwner
import com.verto.app.feature.invoice.domain.model.InvoiceIntegrationWriteKind
import com.verto.app.feature.invoice.domain.model.InvoiceReturnAggregate
import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand
import com.verto.app.feature.payment.domain.model.PaymentIntegrationEventKind
import com.verto.app.feature.payment.domain.model.PersistPaymentIntegrationCommand
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Captures a complete immutable v2 aggregate at the end of the caller's Room transaction. */
@Singleton
class FinancialOutboxWriter @Inject constructor(
    private val database: AppDatabase,
    private val snapshotFactory: FinancialSnapshotFactoryV2,
    private val frozenMutationStore: FrozenMutationStore,
    private val batchCoordinator: SyncBatchCoordinatorV2,
) {
    suspend fun appendInvoice(command: PersistInvoiceIntegrationCommand) = appendInvoiceAggregate(
        command.organizationId, command.invoiceId, command.writeId,
        if (command.writeKind == InvoiceIntegrationWriteKind.CREATED) OP_INVOICE_CREATED else OP_INVOICE_UPDATED,
        command.occurredAt,
    )

    suspend fun appendVoid(command: PersistInvoiceVoidIntegrationCommand) {
        appendInvoiceAggregate(command.organizationId, command.invoiceId, command.writeId, OP_INVOICE_VOIDED, command.occurredAt)
        database.paymentDao().getPaymentsForInvoiceSync(command.invoiceId)
            .filter { it.reversedPaymentId != null }.sortedBy { it.id }.forEach { reversal ->
                appendPaymentEntity(command.organizationId, command.invoiceId, reversal, OP_PAYMENT_REVERSED,
                    reversal.paidAt.takeIf { it > 0 } ?: command.occurredAt)
            }
    }

    suspend fun appendReturn(aggregate: InvoiceReturnAggregate) {
        val document = aggregate.document
        appendInvoiceAggregate(
            document.organizationId, document.originalInvoiceId, document.writeId,
            OP_INVOICE_RETURN_POSTED, document.occurredAt,
        )
    }

    suspend fun appendPayment(command: PersistPaymentIntegrationCommand) {
        val payment = requireNotNull(database.paymentDao().getPaymentByIdSync(command.payment.id)) {
            "CONTRACT_FIELD_MISSING: payment"
        }
        appendPaymentEntity(
            command.organizationId, command.invoiceId, payment,
            if (command.eventKind == PaymentIntegrationEventKind.RECORDED) OP_PAYMENT_RECORDED else OP_PAYMENT_REVERSED,
            command.occurredAt,
        )
    }

    private suspend fun appendPaymentEntity(
        organizationId: String,
        invoiceId: String,
        payment: PaymentEntity,
        operationType: String,
        occurredAt: Long,
    ) {
        require(payment.invoiceId == invoiceId) { "CONTRACT_REFERENCE_INVALID: payment.invoiceId" }
        appendInvoiceAggregate(organizationId, invoiceId, payment.id, operationType, occurredAt)
    }

    private suspend fun appendInvoiceAggregate(
        organizationId: String,
        invoiceId: String,
        writeId: String,
        operationType: String,
        occurredAt: Long,
    ) {
        check(database.inTransaction()) { "FINANCIAL_OUTBOX_TRANSACTION_REQUIRED" }
        val invoice = requireNotNull(database.invoiceDao().getInvoiceByIdSync(invoiceId)) {
            "CONTRACT_FIELD_MISSING: invoice"
        }
        require(invoice.organizationId == organizationId) { "SCOPE_MISMATCH: invoice.organizationId" }
        database.invoiceDao().getFinancialOutboxByIdentity(organizationId, operationType, writeId)?.let { existing ->
            require(existing.aggregateId == invoiceId && existing.payloadVersion == PAYLOAD_VERSION) {
                "FAIL_IDEMPOTENCY_CONFLICT"
            }
            val frozenSnapshot = SyncContractV2Codec.decodeFinancial(existing.payload)
            SyncContractV2Codec.requireValid(frozenSnapshot)
            captureFrozenIntent(existing, frozenSnapshot)
            sealSingleMemberBatch(existing)
            return
        }
        val sequence = database.invoiceDao().nextFinancialOutboxSequence(organizationId, invoiceId)
        val expectedVersion = database.unifiedSyncDao().readUnambiguousAppliedVersion(
            organizationId = organizationId,
            versionFamily = VERSION_FAMILY,
            aggregateId = invoiceId,
        )
        val snapshot = snapshotFactory.capture(organizationId, invoiceId, sequence, expectedVersion)
        val payload = SyncContractV2Codec.encode(snapshot)
        val eventId = stableEventId(organizationId, operationType, writeId)
        val event = FinancialOutboxEntity(
            eventId = eventId, organizationId = organizationId, writeId = writeId, aggregateId = invoiceId,
            aggregateVersion = invoice.lifecycleVersion.coerceAtLeast(1), sequence = sequence,
            operationType = operationType, payloadVersion = PAYLOAD_VERSION, schemaVersion = SCHEMA_VERSION,
            payload = payload, occurredAt = occurredAt, recordedAt = occurredAt, createdAt = occurredAt,
        ).also { candidate ->
            val inserted = database.invoiceDao().insertFinancialOutbox(candidate)
            if (inserted == -1L) {
                val duplicate = requireNotNull(database.invoiceDao().getFinancialOutboxByIdentity(organizationId, operationType, writeId))
                require(duplicate == candidate) { "FAIL_IDEMPOTENCY_CONFLICT" }
            }
        }
        require(event.eventId == eventId && event.aggregateId == invoiceId && event.payload == payload) {
            "FAIL_IDEMPOTENCY_CONFLICT"
        }
        captureFrozenIntent(event, snapshot)
        sealSingleMemberBatch(event)
    }

    private suspend fun sealSingleMemberBatch(event: FinancialOutboxEntity) {
        batchCoordinator.seal(
            organizationId = event.organizationId,
            batchId = event.writeId,
            orderedMutationIds = listOf(event.eventId),
            sealedAt = event.createdAt,
        )
    }

    private suspend fun captureFrozenIntent(event: FinancialOutboxEntity, snapshot: FinancialAggregateSnapshotV2) {
        val payload = SyncContractV2Codec.json.parseToJsonElement(event.payload)
        val intentJson = buildJsonObject {
            put("contractFamily", "verto-unified-sync"); put("contractVersion", 2)
            put("mutationId", event.eventId); put("organizationId", event.organizationId)
            put("aggregateType", if (event.operationType.startsWith("PAYMENT_")) "PAYMENT" else "INVOICE")
            put("aggregateId", event.aggregateId); put("operationType", event.operationType)
            put("localSequence", event.sequence); put("aggregateSequence", event.sequence)
            put("payloadVersion", SYNC_REPAIR_PAYLOAD_VERSION); put("payload", payload)
            put("createdAtEpochMillis", event.createdAt)
        }.toString()
        val protected = linkedMapOf(ProtectedSyncKey("INVOICE", snapshot.invoiceId) to snapshot.businessContentHash)
        snapshot.items.forEach { protected[ProtectedSyncKey("INVOICE_ITEM", it.id)] = hash(SyncContractV2Codec.encode(it)) }
        snapshot.payments.forEach { protected[ProtectedSyncKey("PAYMENT", it.id)] = hash(SyncContractV2Codec.encode(it)) }
        snapshot.returnDocuments.forEach { protected[ProtectedSyncKey("INVOICE_RETURN", it.id)] = hash(SyncContractV2Codec.encode(it)) }
        frozenMutationStore.captureOwner(FrozenOwnerIntent(
            organizationId = event.organizationId, mutationId = event.eventId,
            sourceOwner = SyncSourceOwner.FINANCIAL, sourceId = event.eventId,
            businessIdentity = "${event.operationType}:${event.writeId}", intentJson = intentJson,
            versionFamily = VERSION_FAMILY, initialBaseVersion = snapshot.expectedFinancialStreamVersion,
            batchId = event.writeId, protectedContent = protected, createdAt = event.createdAt,
        ))
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun stableEventId(organizationId: String, operationType: String, writeId: String): String =
        UUID.nameUUIDFromBytes("$organizationId|$operationType|$writeId".toByteArray(StandardCharsets.UTF_8)).toString()

    companion object {
        const val PAYLOAD_VERSION: Int = SYNC_REPAIR_PAYLOAD_VERSION
        const val SCHEMA_VERSION: Int = SYNC_REPAIR_PAYLOAD_VERSION
        const val OP_INVOICE_CREATED = "INVOICE_CREATED"
        const val OP_INVOICE_UPDATED = "INVOICE_UPDATED"
        const val OP_INVOICE_VOIDED = "INVOICE_VOIDED"
        const val OP_PAYMENT_RECORDED = "PAYMENT_RECORDED"
        const val OP_PAYMENT_REVERSED = "PAYMENT_REVERSED"
        const val OP_INVOICE_RETURN_POSTED = "INVOICE_RETURN_POSTED"
        private const val VERSION_FAMILY = "FINANCIAL_INVOICE"
    }
}
