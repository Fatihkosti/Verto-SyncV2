package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.domain.model.MaintenanceImageDraft
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceRecordDraft
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleReference
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceVehicleSnapshot
import com.verto.app.feature.integration.optimal.domain.repository.OptimalMaintenanceRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalVehicleRepository
import com.verto.app.feature.integration.optimal.domain.model.OptimalVehicleSuggestion
import com.verto.app.feature.invoice.domain.model.InvoiceVehicleSuggestionsQuery
import com.verto.app.feature.invoice.domain.model.SaveInvoiceMaintenanceCommand
import com.verto.app.feature.invoice.domain.model.VehicleSuggestion
import com.verto.app.feature.invoice.domain.port.InvoiceMaintenanceExtensionPort
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OptimalInvoiceMaintenanceExtensionAdapter @Inject constructor(
    private val vehicles: OptimalVehicleRepository,
    private val maintenance: OptimalMaintenanceRepository,
) : InvoiceMaintenanceExtensionPort {
    override val extensionKey: String = EXTENSION_KEY

    override fun observeVehicleSuggestions(
        query: InvoiceVehicleSuggestionsQuery,
    ): Flow<List<VehicleSuggestion>> = vehicles.observeSuggestions(
        organizationId = query.organizationId,
        clientId = query.clientId,
        searchTerm = query.searchTerm,
        limit = query.limit,
    ).map { rows -> rows.map(OptimalVehicleSuggestion::toInvoiceSuggestion) }

    override suspend fun saveMaintenance(
        command: SaveInvoiceMaintenanceCommand,
    ): Result<Unit> = runCatching {
        val normalizedOrganizationId = command.organizationId.trim()
        val normalizedClientId = command.clientId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        require(normalizedClientId.isNotEmpty()) { "clientId is required" }
        val data = requireNotNull(command.maintenance) { "maintenance data is required" }
        require(data.hasContent()) { "maintenance data is empty" }

        val officialVehicleId = data.officialVehicleId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val officialVehicleOrganizationId = data.officialVehicleOrganizationId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        require((officialVehicleId == null) == (officialVehicleOrganizationId == null)) {
            "official vehicle id and organization must both be present or both be absent"
        }
        if (officialVehicleOrganizationId != null) {
            require(officialVehicleOrganizationId == normalizedOrganizationId) {
                "official vehicle does not belong to the invoice organization"
            }
        }

        maintenance.create(
            organizationId = normalizedOrganizationId,
            draft = MaintenanceRecordDraft(
                recordId = data.recordId,
                invoiceId = command.invoiceId,
                vehicleReference = officialVehicleId?.let { vehicleId ->
                    MaintenanceVehicleReference(
                        clientId = normalizedClientId,
                        vehicleId = vehicleId,
                    )
                },
                vehicleSnapshot = MaintenanceVehicleSnapshot(
                    name = data.vehicleName,
                    vehicleType = data.vehicleType,
                    plateNumber = data.plateNumber,
                ),
                driverOrDelegate = data.driverOrDelegate,
                notes = data.notes,
                images = data.images.map { image ->
                    MaintenanceImageDraft(
                        imageId = image.imageId,
                        localUri = image.localUri,
                        mimeType = image.mimeType,
                        byteSize = image.byteSize,
                        sortOrder = image.sortOrder,
                    )
                },
                createdAt = data.createdAt,
            ),
        )
        Unit
    }

    private companion object {
        const val EXTENSION_KEY = "optimal"
    }
}

private fun OptimalVehicleSuggestion.toInvoiceSuggestion(): VehicleSuggestion = VehicleSuggestion(
    organizationId = organizationId,
    clientId = clientId,
    remoteVehicleId = remoteVehicleId,
    name = name,
    vehicleType = vehicleType,
    plateNumber = plateNumber,
    updatedAt = updatedAt,
)
