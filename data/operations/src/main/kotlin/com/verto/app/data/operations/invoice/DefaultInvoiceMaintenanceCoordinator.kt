package com.verto.app.data.operations.invoice

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.core.transaction.DatabaseTransactionRunner
import com.verto.app.feature.invoice.domain.model.InvoiceVehicleSuggestionsQuery
import com.verto.app.feature.invoice.domain.model.SaveInvoiceMaintenanceCommand
import com.verto.app.feature.invoice.domain.model.VehicleSuggestion
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceCoordinator
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceExtensionPort
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultInvoiceMaintenanceCoordinator @Inject constructor(
    private val sessionReader: SessionReader,
    private val transactionRunner: DatabaseTransactionRunner,
    private val extensions: Set<@JvmSuppressWildcards InvoiceMaintenanceExtensionPort>,
) : InvoiceMaintenanceCoordinator {

    override fun observeVehicleSuggestions(
        query: InvoiceVehicleSuggestionsQuery,
    ): Flow<List<VehicleSuggestion>> {
        val normalized = query.normalizedOrNull() ?: return flowOf(emptyList())
        val extension = activeExtensionOrNull() ?: return flowOf(emptyList())

        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { activeOrganizationId ->
                if (activeOrganizationId != normalized.organizationId) {
                    flowOf(emptyList())
                } else {
                    extension.observeVehicleSuggestions(normalized).map { suggestions ->
                        suggestions.filter { suggestion ->
                            suggestion.organizationId == activeOrganizationId &&
                                suggestion.clientId == normalized.clientId
                        }
                    }
                }
            }
    }

    override suspend fun saveOptionalMaintenance(
        command: SaveInvoiceMaintenanceCommand,
    ): Result<Unit> = runCatching {
        val normalized = command.normalized()
        val maintenance = normalized.maintenance
        if (maintenance == null || !maintenance.hasContent()) return@runCatching Unit

        val activeOrganizationId = sessionReader.snapshot().organization.id.trim()
        if (activeOrganizationId.isEmpty() || activeOrganizationId != normalized.organizationId) {
            throw SecurityException("invoice maintenance organization does not match the active session")
        }

        val extension = activeExtensionOrNull() ?: return@runCatching Unit
        transactionRunner.inTransaction {
            extension.saveMaintenance(normalized).getOrThrow()
        }
    }

    private fun activeExtensionOrNull(): InvoiceMaintenanceExtensionPort? {
        if (extensions.size > 1) {
            val keys = extensions.map { it.extensionKey }.sorted().joinToString()
            throw IllegalStateException("multiple invoice maintenance extensions are active: $keys")
        }
        return extensions.singleOrNull()
    }

    private fun InvoiceVehicleSuggestionsQuery.normalizedOrNull(): InvoiceVehicleSuggestionsQuery? {
        val organization = organizationId.trim()
        val client = clientId.trim()
        if (organization.isEmpty() || client.isEmpty()) return null
        return copy(
            organizationId = organization,
            clientId = client,
            searchTerm = searchTerm.trim(),
            limit = limit.coerceIn(1, 100),
        )
    }

    private fun SaveInvoiceMaintenanceCommand.normalized(): SaveInvoiceMaintenanceCommand {
        val organization = organizationId.trim()
        val invoice = invoiceId.trim()
        val client = clientId.trim()
        require(organization.isNotEmpty()) { "organizationId is required" }
        require(invoice.isNotEmpty()) { "invoiceId is required" }
        require(client.isNotEmpty()) { "clientId is required" }
        return copy(organizationId = organization, invoiceId = invoice, clientId = client)
    }
}
