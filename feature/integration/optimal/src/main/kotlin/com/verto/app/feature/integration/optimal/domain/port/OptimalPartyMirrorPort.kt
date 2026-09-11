package com.verto.app.feature.integration.optimal.domain.port

data class OptimalPartyMirrorRecord(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val generalNote: String,
    val segment: String,
    val ageYears: Int? = null,
    val purchaseContactName: String = "",
    val businessActivity: String = "",
    val workplaceName: String = "",
    val shopName: String = "",
    val workshopName: String = "",
    val vehicleModels: String = "",
    val workshopWorkerCount: Int? = null,
    val createdAt: Long,
    val createdBy: String,
)

interface OptimalPartyMirrorPort {
    suspend fun mergeRemoteCompany(record: OptimalPartyMirrorRecord)
}
