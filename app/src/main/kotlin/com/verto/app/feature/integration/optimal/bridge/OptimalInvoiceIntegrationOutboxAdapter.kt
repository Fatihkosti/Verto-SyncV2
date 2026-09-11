package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.data.local.dao.OptimalOutboxDao
import com.verto.app.data.local.entity.OptimalOutboxEntity
import com.verto.app.data.local.entity.OptimalOutboxStatus
import com.verto.app.feature.invoice.domain.model.InvoiceIntegrationWriteKind
import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand
import com.verto.app.feature.invoice.domain.port.InvoiceIntegrationOutboxPort
import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendContractGate
import com.verto.app.feature.integration.optimal.domain.model.OptimalIdempotencyKeyFactory
import com.verto.app.feature.integration.optimal.domain.model.OptimalRemoteContract
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRepository
import com.verto.app.feature.payment.domain.model.PaymentIntegrationEventKind
import com.verto.app.feature.payment.domain.model.PaymentRecord
import com.verto.app.feature.payment.domain.model.PersistPaymentIntegrationCommand
import com.verto.app.feature.payment.domain.port.PaymentIntegrationOutboxPort
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Owns the tenant-scoped invoice aggregate Outbox. It records the complete local lifecycle while
 * leaving every unverified Optimal remote contract LOCAL_ONLY, as required by the v49 gate.
 */
class OptimalInvoiceIntegrationOutboxAdapter @Inject constructor(
    private val outboxDao: OptimalOutboxDao,
    private val maintenanceRepository: OptimalMaintenanceRepository,
    private val maintenancePayloadMapper: OptimalMaintenancePayloadMapper,
    private val contractGate: OptimalBackendContractGate,
) : InvoiceIntegrationOutboxPort, PaymentIntegrationOutboxPort {
    override val integrationKey: String = INTEGRATION_KEY

    override suspend fun appendInvoiceEvent(command: PersistInvoiceIntegrationCommand): Result<Unit> =
        runCatching {
            val operation = when (command.writeKind) {
                InvoiceIntegrationWriteKind.CREATED -> OPERATION_INVOICE_CREATED
                InvoiceIntegrationWriteKind.UPDATED -> OPERATION_INVOICE_UPDATED
            }
            outboxDao.appendOptimal(OptimalOutboxAppendRequest(
                organizationId = command.organizationId,
                invoiceId = command.invoiceId,
                localIdentity = "${command.invoiceId}|${command.writeId}|invoice",
                eventId = "${command.writeId}:invoice",
                operation = operation,
                status = lifecycleStatus(OptimalRemoteContract.UPSERT_INVOICE),
                occurredAt = command.occurredAt,
            )) { aggregateVersion ->
                Json.encodeToString(
                    InvoiceWriteOutboxPayload(
                        organizationId = command.organizationId,
                        invoiceId = command.invoiceId,
                        clientId = command.clientId,
                        writeKind = command.writeKind.name,
                        syncOwner = INVOICE_SYNC_OWNER,
                        aggregateVersion = aggregateVersion,
                        occurredAt = command.occurredAt,
                    ),
                )
            }
        }

    override suspend fun appendMaintenanceEvent(command: PersistInvoiceIntegrationCommand): Result<Unit> =
        runCatching {
            requireNotNull(command.maintenance?.takeIf { it.hasContent() }) {
                "maintenance Outbox event requires maintenance data"
            }
            val persisted = requireNotNull(
                maintenanceRepository.getByInvoice(command.organizationId, command.invoiceId),
            ) { "maintenance record must exist before its Outbox event" }
            val mapping = maintenancePayloadMapper.map(command, persisted)
            outboxDao.appendOptimal(OptimalOutboxAppendRequest(
                organizationId = command.organizationId,
                invoiceId = command.invoiceId,
                localIdentity = "${command.invoiceId}|${command.writeId}|maintenance",
                eventId = "${command.writeId}:maintenance",
                operation = OPERATION_MAINTENANCE_UPSERTED,
                status = when (mapping.disposition) {
                    MaintenancePayloadDisposition.REMOTE_READY -> OptimalOutboxStatus.PENDING
                    MaintenancePayloadDisposition.LOCAL_ONLY_MANUAL_VEHICLE,
                    MaintenancePayloadDisposition.LOCAL_ONLY_CONTRACT_UNAVAILABLE,
                    -> OptimalOutboxStatus.LOCAL_ONLY
                },
                occurredAt = command.occurredAt,
            )) { aggregateVersion ->
                maintenancePayload(
                    MaintenancePayloadRequest(command, persisted.recordId, persisted.images.size, mapping, aggregateVersion),
                )
            }
        }

    override suspend fun appendPaymentEvent(command: PersistPaymentIntegrationCommand): Result<Unit> =
        runCatching {
            when (command.eventKind) {
                PaymentIntegrationEventKind.RECORDED -> appendRecordedPayment(command.payment, command)
                PaymentIntegrationEventKind.REVERSED -> {
                    val original = requireNotNull(command.originalPayment) {
                        "reversal requires original payment"
                    }
                    // Legacy payments may predate v79. Materialize their recorded event first so a
                    // reversal can never be sequenced before the event it reverses.
                    appendRecordedPayment(original, command, occurredAt = original.paidAt)
                    appendReversedPayment(command.payment, original, command)
                }
            }
        }

    override suspend fun appendVoidEvent(command: PersistInvoiceVoidIntegrationCommand): Result<Unit> =
        runCatching {
            outboxDao.appendOptimal(OptimalOutboxAppendRequest(
                organizationId = command.organizationId,
                invoiceId = command.invoiceId,
                localIdentity = "${command.invoiceId}|void",
                eventId = "${command.invoiceId}:void",
                operation = OPERATION_INVOICE_VOIDED,
                status = lifecycleStatus(OptimalRemoteContract.VOID_INVOICE),
                occurredAt = command.occurredAt,
            )) { aggregateVersion ->
                Json.encodeToString(
                    InvoiceVoidOutboxPayload(
                        organizationId = command.organizationId,
                        invoiceId = command.invoiceId,
                        clientId = command.clientId,
                        tombstone = true,
                        aggregateVersion = aggregateVersion,
                        occurredAt = command.occurredAt,
                    ),
                )
            }
        }

    private suspend fun appendRecordedPayment(
        payment: PaymentRecord,
        command: PersistPaymentIntegrationCommand,
        occurredAt: Long = command.occurredAt,
    ) {
        outboxDao.appendOptimal(OptimalOutboxAppendRequest(
            organizationId = command.organizationId,
            invoiceId = command.invoiceId,
            localIdentity = "${payment.id}|payment-recorded",
            eventId = "${payment.id}:payment",
            operation = OPERATION_PAYMENT_RECORDED,
            status = lifecycleStatus(OptimalRemoteContract.UPSERT_PAYMENT),
            occurredAt = occurredAt,
        )) { aggregateVersion ->
            paymentPayload(PaymentPayloadRequest(
                command, payment, PaymentIntegrationEventKind.RECORDED, null, aggregateVersion, occurredAt,
            ))
        }
    }

    private suspend fun appendReversedPayment(
        reversal: PaymentRecord,
        original: PaymentRecord,
        command: PersistPaymentIntegrationCommand,
    ) {
        outboxDao.appendOptimal(OptimalOutboxAppendRequest(
            organizationId = command.organizationId,
            invoiceId = command.invoiceId,
            localIdentity = "${reversal.id}|${original.id}|payment-reversed",
            eventId = "${reversal.id}:payment-reversal",
            operation = OPERATION_PAYMENT_REVERSED,
            status = lifecycleStatus(OptimalRemoteContract.REVERSE_PAYMENT),
            occurredAt = command.occurredAt,
        )) { aggregateVersion ->
            paymentPayload(PaymentPayloadRequest(
                command, reversal, PaymentIntegrationEventKind.REVERSED, original.id, aggregateVersion, command.occurredAt,
            ))
        }
    }

    private fun lifecycleStatus(contract: OptimalRemoteContract): OptimalOutboxStatus =
        if (contractGate.allows(contract)) OptimalOutboxStatus.PENDING else OptimalOutboxStatus.LOCAL_ONLY

    private companion object {
        const val INTEGRATION_KEY = "optimal"
        const val AGGREGATE_INVOICE = "invoice"
        const val OPERATION_INVOICE_CREATED = "INVOICE_CREATED"
        const val OPERATION_INVOICE_UPDATED = "INVOICE_UPDATED"
        const val OPERATION_PAYMENT_RECORDED = "PAYMENT_RECORDED"
        const val OPERATION_PAYMENT_REVERSED = "PAYMENT_REVERSED"
        const val OPERATION_INVOICE_VOIDED = "INVOICE_VOIDED"
        const val OPERATION_MAINTENANCE_UPSERTED = "MAINTENANCE_UPSERTED"
        const val INVOICE_SYNC_OWNER = "VERTO_INVOICE_SYNC"
        const val PAYMENT_SYNC_OWNER = "VERTO_PAYMENT_SYNC"
        const val PAYLOAD_VERSION = 1
    }
}


private data class OptimalOutboxAppendRequest(
    val organizationId: String,
    val invoiceId: String,
    val localIdentity: String,
    val eventId: String,
    val operation: String,
    val status: OptimalOutboxStatus,
    val occurredAt: Long
)

private data class PaymentPayloadRequest(
    val command: PersistPaymentIntegrationCommand,
    val payment: PaymentRecord,
    val eventKind: PaymentIntegrationEventKind,
    val originalPaymentId: String?,
    val aggregateVersion: Long,
    val occurredAt: Long,
)

private fun paymentPayload(request: PaymentPayloadRequest): String = buildJsonObject {
    put("organizationId", JsonPrimitive(request.command.organizationId))
    put("invoiceId", JsonPrimitive(request.command.invoiceId))
    put("clientId", JsonPrimitive(request.command.clientId))
    put("paymentId", JsonPrimitive(request.payment.id))
    put("eventKind", JsonPrimitive(request.eventKind.name))
    put("originalPaymentId", request.originalPaymentId?.let(::JsonPrimitive) ?: JsonNull)
    put("amount", JsonPrimitive(request.payment.amount))
    put("paymentMethod", JsonPrimitive(request.payment.paymentMethod.name))
    put("paidAt", JsonPrimitive(request.payment.paidAt))
    put("syncOwner", JsonPrimitive("VERTO_PAYMENT_SYNC"))
    put("aggregateVersion", JsonPrimitive(request.aggregateVersion))
    put("occurredAt", JsonPrimitive(request.occurredAt))
}.toString()

private suspend fun OptimalOutboxDao.appendOptimal(
    request: OptimalOutboxAppendRequest,
    payload: (Long) -> String,
) {
    val idempotencyKey = OptimalIdempotencyKeyFactory.create(
        organizationId = request.organizationId,
        operation = request.operation,
        localIdentity = request.localIdentity,
        operationVersion = 1,
    )
    if (getByIdempotencyKey(request.organizationId, idempotencyKey) != null) return
    val latest = getLatestForAggregate(request.organizationId, "invoice", request.invoiceId)
    if (latest?.operation == "INVOICE_VOIDED" && request.operation != "INVOICE_VOIDED") {
        error("invoice aggregate is terminal after INVOICE_VOIDED")
    }
    val sequence = nextSequence(request.organizationId, "invoice", request.invoiceId)
    insert(
        OptimalOutboxEntity(
            organizationId = request.organizationId,
            eventId = request.eventId,
            aggregateType = "invoice",
            aggregateId = request.invoiceId,
            operation = request.operation,
            payloadJson = payload(sequence),
            payloadVersion = 1,
            idempotencyKey = idempotencyKey,
            sequence = sequence,
            status = request.status,
            createdAt = request.occurredAt,
        ),
    )
}


private data class MaintenancePayloadRequest(
    val command: PersistInvoiceIntegrationCommand,
    val recordId: String,
    val imageCount: Int,
    val mapping: MaintenanceOutboxMapping,
    val aggregateVersion: Long,
)

private fun maintenancePayload(request: MaintenancePayloadRequest): String = buildJsonObject {
    put("organizationId", JsonPrimitive(request.command.organizationId))
    put("invoiceId", JsonPrimitive(request.command.invoiceId))
    put("clientId", JsonPrimitive(request.command.clientId))
    put("recordId", JsonPrimitive(request.recordId))
    put("disposition", JsonPrimitive(request.mapping.disposition.name))
    put("remoteMaintenance", request.mapping.remotePayload?.toJson() ?: JsonNull)
    put("imageCount", JsonPrimitive(request.imageCount))
    put("aggregateVersion", JsonPrimitive(request.aggregateVersion))
    put("occurredAt", JsonPrimitive(request.command.occurredAt))
}.toString()

private fun OptimalMaintenanceRemotePayload.toJson(): JsonObject = buildJsonObject {
    put("organizationId", JsonPrimitive(organizationId))
    put("invoiceId", JsonPrimitive(invoiceId))
    put("clientId", JsonPrimitive(clientId))
    put("recordId", JsonPrimitive(recordId))
    put("vehicleId", JsonPrimitive(vehicleId))
    put("vehicleNameSnapshot", JsonPrimitive(vehicleNameSnapshot))
    put("vehicleTypeSnapshot", JsonPrimitive(vehicleTypeSnapshot))
    put("plateNumberSnapshot", JsonPrimitive(plateNumberSnapshot))
    put("driverOrDelegate", JsonPrimitive(driverOrDelegate))
    put("notes", JsonPrimitive(notes))
}

@Serializable
private data class InvoiceWriteOutboxPayload(
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val writeKind: String,
    val syncOwner: String,
    val aggregateVersion: Long,
    val occurredAt: Long
)

@Serializable
private data class InvoiceVoidOutboxPayload(
    val organizationId: String,
    val invoiceId: String,
    val clientId: String,
    val tombstone: Boolean,
    val aggregateVersion: Long,
    val occurredAt: Long,
)
