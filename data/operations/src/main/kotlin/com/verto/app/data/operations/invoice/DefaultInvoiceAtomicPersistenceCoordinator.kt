package com.verto.app.data.operations.invoice

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand
import com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand
import com.verto.app.feature.invoice.domain.model.SaveInvoiceMaintenanceCommand
import com.verto.app.feature.invoice.domain.port.InvoiceAtomicPersistenceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceIntegrationOutboxPort
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceExtensionPort
import com.verto.app.data.operations.transaction.FinancialOutboxWriter
import javax.inject.Inject

/**
 * Coordinates integration-owned database writes while the invoice owner already holds the single
 * Room transaction. It performs no remote IO; the mandatory F249 writer persists only through Room.
 */
class DefaultInvoiceAtomicPersistenceCoordinator @Inject constructor(
    private val sessionReader: SessionReader,
    private val financialOutboxWriter: FinancialOutboxWriter,
    private val maintenanceExtensions: Set<@JvmSuppressWildcards InvoiceMaintenanceExtensionPort>,
    private val outboxPorts: Set<@JvmSuppressWildcards InvoiceIntegrationOutboxPort>,
) : InvoiceAtomicPersistenceCoordinator {

    override suspend fun persist(
        command: PersistInvoiceIntegrationCommand,
    ): Result<Unit> = runCatching {
        val normalized = command.normalized()
        val activeOrganizationId = sessionReader.snapshot().organization.id.trim()
        if (activeOrganizationId.isEmpty() || activeOrganizationId != normalized.organizationId) {
            throw SecurityException("invoice write organization does not match the active session")
        }

        val maintenance = normalized.maintenance?.takeIf { it.hasContent() }
        if (maintenance != null) {
            require(normalized.isSale) { "maintenance can only be attached to a sales invoice" }
            require(normalized.companyClient) { "maintenance requires a COMPANY client" }
        }

        val maintenanceExtension = activeMaintenanceExtensionOrNull()
        val outbox = activeOutboxOrNull()
        if (maintenanceExtension != null && outbox == null) {
            error("invoice maintenance integration requires an Outbox owner")
        }
        if (outbox != null && maintenanceExtension != null &&
            outbox.integrationKey != maintenanceExtension.extensionKey
        ) {
            error("invoice maintenance and Outbox owners do not match")
        }

        // Optional integration event remains separate from the financial synchronization contract.
        outbox?.appendInvoiceEvent(normalized)?.getOrThrow()

        if (maintenance != null && maintenanceExtension != null) {
            maintenanceExtension.saveMaintenance(
                SaveInvoiceMaintenanceCommand(
                    organizationId = normalized.organizationId,
                    invoiceId = normalized.invoiceId,
                    clientId = normalized.clientId,
                    maintenance = maintenance,
                ),
            ).getOrThrow()
            outbox?.appendMaintenanceEvent(normalized.copy(maintenance = maintenance))?.getOrThrow()
        }

        // Seal the full financial snapshot only after every transaction-owned dependent write.
        financialOutboxWriter.appendInvoice(normalized)
    }


    override suspend fun persistVoid(
        command: PersistInvoiceVoidIntegrationCommand,
    ): Result<Unit> = runCatching {
        val normalized = command.normalized()
        val activeOrganizationId = sessionReader.snapshot().organization.id.trim()
        if (activeOrganizationId.isEmpty() || activeOrganizationId != normalized.organizationId) {
            throw SecurityException("invoice void organization does not match the active session")
        }
        activeOutboxOrNull()?.appendVoidEvent(normalized)?.getOrThrow()
        financialOutboxWriter.appendVoid(normalized)
    }

    private fun activeMaintenanceExtensionOrNull(): InvoiceMaintenanceExtensionPort? {
        require(maintenanceExtensions.size <= 1) {
            "multiple invoice maintenance extensions are active: " +
                maintenanceExtensions.map { it.extensionKey }.sorted().joinToString()
        }
        return maintenanceExtensions.singleOrNull()
    }

    private fun activeOutboxOrNull(): InvoiceIntegrationOutboxPort? {
        require(outboxPorts.size <= 1) {
            "multiple invoice integration Outbox owners are active: " +
                outboxPorts.map { it.integrationKey }.sorted().joinToString()
        }
        return outboxPorts.singleOrNull()
    }

    private fun PersistInvoiceVoidIntegrationCommand.normalized(): PersistInvoiceVoidIntegrationCommand {
        val organization = organizationId.trim()
        val invoice = invoiceId.trim()
        val client = clientId.trim()
        val write = writeId.trim()
        require(organization.isNotEmpty()) { "organizationId is required" }
        require(invoice.isNotEmpty()) { "invoiceId is required" }
        require(client.isNotEmpty()) { "clientId is required" }
        require(write.isNotEmpty()) { "writeId is required" }
        require(occurredAt > 0L) { "occurredAt is required" }
        return copy(
            organizationId = organization,
            invoiceId = invoice,
            clientId = client,
            writeId = write,
        )
    }

    private fun PersistInvoiceIntegrationCommand.normalized(): PersistInvoiceIntegrationCommand {
        val organization = organizationId.trim()
        val invoice = invoiceId.trim()
        val client = clientId.trim()
        val write = writeId.trim()
        require(organization.isNotEmpty()) { "organizationId is required" }
        require(invoice.isNotEmpty()) { "invoiceId is required" }
        require(client.isNotEmpty()) { "clientId is required" }
        require(write.isNotEmpty()) { "writeId is required" }
        require(occurredAt > 0L) { "occurredAt is required" }
        return copy(
            organizationId = organization,
            invoiceId = invoice,
            clientId = client,
            writeId = write,
        )
    }
}
