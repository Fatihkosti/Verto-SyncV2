package com.verto.app.feature.integration.optimal.domain.repository

import com.verto.app.feature.integration.optimal.domain.model.VehicleSuggestion
import kotlinx.coroutines.flow.Flow

interface OptimalVehicleRepository {
    fun observeSuggestions(
        organizationId: String,
        clientId: String,
        searchTerm: String,
        limit: Int,
    ): Flow<List<VehicleSuggestion>>

    /** Replaces a complete trusted source snapshot; Remote fetching is intentionally separate. */
    suspend fun replaceCache(
        organizationId: String,
        clientId: String,
        vehicles: List<VehicleSuggestion>,
    ): Boolean
}
