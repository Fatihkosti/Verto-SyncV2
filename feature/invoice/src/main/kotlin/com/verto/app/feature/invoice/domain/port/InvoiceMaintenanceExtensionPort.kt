package com.verto.app.feature.invoice.domain.port

import com.verto.app.feature.invoice.domain.model.InvoiceVehicleSuggestionsQuery
import com.verto.app.feature.invoice.domain.model.SaveInvoiceMaintenanceCommand
import com.verto.app.feature.invoice.domain.model.VehicleSuggestion
import kotlinx.coroutines.flow.Flow

/** Optional extension contributed by integrations such as Optimal. */
interface InvoiceMaintenanceExtensionPort {
    val extensionKey: String

    fun observeVehicleSuggestions(
        query: InvoiceVehicleSuggestionsQuery,
    ): Flow<List<VehicleSuggestion>>

    suspend fun saveMaintenance(
        command: SaveInvoiceMaintenanceCommand,
    ): Result<Unit>
}

/** Invoice-facing coordinator contract. Its implementation belongs to the operations owner. */
interface InvoiceMaintenanceCoordinator {
    fun observeVehicleSuggestions(
        query: InvoiceVehicleSuggestionsQuery,
    ): Flow<List<VehicleSuggestion>>

    suspend fun saveOptionalMaintenance(
        command: SaveInvoiceMaintenanceCommand,
    ): Result<Unit>
}

/** Optional Outbox owner contributed by an integration without exposing its DAO. */
interface InvoiceIntegrationOutboxPort {
    val integrationKey: String

    suspend fun appendInvoiceEvent(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand,
    ): Result<Unit>

    suspend fun appendMaintenanceEvent(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand,
    ): Result<Unit>

    suspend fun appendVoidEvent(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand,
    ): Result<Unit>
}

/**
 * Called only from inside the invoice transaction. Implementations must not open a second
 * transaction or perform network work; any failure is propagated so Room rolls everything back.
 */
interface InvoiceAtomicPersistenceCoordinator {
    suspend fun persist(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand,
    ): Result<Unit>

    suspend fun persistVoid(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand,
    ): Result<Unit>
}

/** Default used only by direct unit construction; production Hilt supplies the operations owner. */
object NoOpInvoiceAtomicPersistenceCoordinator : InvoiceAtomicPersistenceCoordinator {
    override suspend fun persist(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceIntegrationCommand,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun persistVoid(
        command: com.verto.app.feature.invoice.domain.model.PersistInvoiceVoidIntegrationCommand,
    ): Result<Unit> = Result.success(Unit)
}
