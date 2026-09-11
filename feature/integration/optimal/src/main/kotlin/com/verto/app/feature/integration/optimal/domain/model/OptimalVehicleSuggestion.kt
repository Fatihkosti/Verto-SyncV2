package com.verto.app.feature.integration.optimal.domain.model

/** Optimal-owned vehicle suggestion used by the integration boundary. */
data class OptimalVehicleSuggestion(
    val organizationId: String,
    val clientId: String,
    val remoteVehicleId: String,
    val name: String,
    val vehicleType: String,
    val plateNumber: String,
    val updatedAt: Long
)
