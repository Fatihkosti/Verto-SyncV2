package com.verto.app.feature.integration.optimal.data

import com.verto.app.data.local.dao.OptimalVehicleDao
import com.verto.app.data.local.entity.OptimalVehicleEntity
import com.verto.app.feature.integration.optimal.domain.model.VehicleSuggestion
import com.verto.app.feature.integration.optimal.domain.repository.OptimalVehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomOptimalVehicleRepository @Inject constructor(
    private val dao: OptimalVehicleDao,
) : OptimalVehicleRepository {
    override fun observeSuggestions(
        organizationId: String,
        clientId: String,
        searchTerm: String,
        limit: Int,
    ): Flow<List<VehicleSuggestion>> = dao.observeSuggestions(
        organizationId = organizationId.trim(),
        clientId = clientId.trim(),
        searchTerm = searchTerm.trim(),
        limit = limit.coerceIn(1, MAX_SUGGESTIONS),
    ).map { entities -> entities.map(OptimalVehicleEntity::toDomain) }

    override suspend fun replaceCache(
        organizationId: String,
        clientId: String,
        vehicles: List<VehicleSuggestion>,
    ): Boolean {
        val normalizedOrganizationId = organizationId.trim()
        val normalizedClientId = clientId.trim()
        require(normalizedOrganizationId.isNotEmpty()) { "organizationId is required" }
        require(normalizedClientId.isNotEmpty()) { "clientId is required" }

        val normalized = vehicles.map { vehicle ->
            require(vehicle.organizationId.trim() == normalizedOrganizationId) {
                "vehicle organization does not match active tenant"
            }
            require(vehicle.clientId.trim() == normalizedClientId) {
                "vehicle client does not match requested company"
            }
            require(vehicle.remoteVehicleId.isNotBlank()) { "remoteVehicleId is required" }
            vehicle.toEntity(
                organizationId = normalizedOrganizationId,
                clientId = normalizedClientId,
            )
        }
        return dao.replaceForLinkedClient(
            organizationId = normalizedOrganizationId,
            clientId = normalizedClientId,
            vehicles = normalized,
        )
    }

    private companion object {
        const val MAX_SUGGESTIONS = 100
    }
}

private fun OptimalVehicleEntity.toDomain(): VehicleSuggestion = VehicleSuggestion(
    organizationId = organizationId,
    clientId = clientId,
    remoteVehicleId = remoteVehicleId,
    name = name,
    vehicleType = vehicleType,
    plateNumber = plateNumber,
    updatedAt = updatedAt,
)

private fun VehicleSuggestion.toEntity(
    organizationId: String,
    clientId: String,
): OptimalVehicleEntity = OptimalVehicleEntity(
    organizationId = organizationId,
    clientId = clientId,
    remoteVehicleId = remoteVehicleId.trim(),
    name = name.trim(),
    vehicleType = vehicleType.trim(),
    plateNumber = plateNumber.trim(),
    updatedAt = updatedAt,
)
