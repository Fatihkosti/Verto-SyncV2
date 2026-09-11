package com.verto.app.data.operations.payment

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.payment.domain.model.PaymentIntegrationEventKind
import com.verto.app.feature.payment.domain.model.PersistPaymentIntegrationCommand
import com.verto.app.feature.payment.domain.port.PaymentAtomicPersistenceCoordinator
import com.verto.app.feature.payment.domain.port.PaymentIntegrationOutboxPort
import com.verto.app.data.operations.transaction.FinancialOutboxWriter
import javax.inject.Inject

/** Coordinates integration-owned payment events inside the payment owner's Room transaction. */
class DefaultPaymentAtomicPersistenceCoordinator @Inject constructor(
    private val sessionReader: SessionReader,
    private val financialOutboxWriter: FinancialOutboxWriter,
    private val outboxPorts: Set<@JvmSuppressWildcards PaymentIntegrationOutboxPort>,
) : PaymentAtomicPersistenceCoordinator {
    override suspend fun persist(command: PersistPaymentIntegrationCommand): Result<Unit> = runCatching {
        val normalized = command.normalized()
        val activeOrganizationId = sessionReader.snapshot().organization.id.trim()
        if (activeOrganizationId.isEmpty() || activeOrganizationId != normalized.organizationId) {
            throw SecurityException("payment write organization does not match the active session")
        }
        require(outboxPorts.size <= 1) {
            "multiple payment integration Outbox owners are active: " +
                outboxPorts.map { it.integrationKey }.sorted().joinToString()
        }
        outboxPorts.singleOrNull()?.appendPaymentEvent(normalized)?.getOrThrow()
        financialOutboxWriter.appendPayment(normalized)
    }

    private fun PersistPaymentIntegrationCommand.normalized(): PersistPaymentIntegrationCommand {
        val organization = organizationId.trim()
        val invoice = invoiceId.trim()
        val client = clientId.trim()
        val paymentId = payment.id.trim()
        require(organization.isNotEmpty()) { "organizationId is required" }
        require(invoice.isNotEmpty()) { "invoiceId is required" }
        require(client.isNotEmpty()) { "clientId is required" }
        require(paymentId.isNotEmpty()) { "paymentId is required" }
        require(payment.invoiceId.trim() == invoice) { "payment invoice does not match aggregate" }
        require(payment.clientId.trim() == client) { "payment client does not match aggregate" }
        require(occurredAt > 0L) { "occurredAt is required" }
        when (eventKind) {
            PaymentIntegrationEventKind.RECORDED -> require(originalPayment == null) {
                "recorded payment cannot carry originalPayment"
            }
            PaymentIntegrationEventKind.REVERSED -> {
                val original = requireNotNull(originalPayment) { "reversal requires originalPayment" }
                require(payment.reversedPaymentId == original.id) { "reversal target does not match original" }
                require(original.invoiceId == invoice) { "original payment invoice does not match aggregate" }
            }
        }
        return copy(
            organizationId = organization,
            invoiceId = invoice,
            clientId = client,
            payment = payment.copy(id = paymentId, invoiceId = invoice, clientId = client),
        )
    }
}
