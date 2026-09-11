package com.verto.feature.dashboard.api

import kotlinx.coroutines.flow.Flow

enum class TeamObservationStatus {
    NEW,
    REVIEWED,
    CLOSED,
}

enum class TeamObservationCategory {
    IDEA,
    MARKET_INFO,
    COMPLAINT,
}

data class TeamObservation(
    val id: String,
    val text: String,
    val category: TeamObservationCategory = TeamObservationCategory.IDEA,
    val authorUserId: String,
    val authorName: String,
    val status: TeamObservationStatus,
    val isImportant: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/** Raw team observations: no automatic classification or text analysis. */
interface TeamObservationRepository {
    fun observeCurrentOrganization(): Flow<List<TeamObservation>>

    suspend fun submit(text: String, category: TeamObservationCategory = TeamObservationCategory.IDEA): TeamObservation

    suspend fun refreshFromRemote()

    suspend fun setStatus(observationId: String, status: TeamObservationStatus)

    suspend fun setImportant(observationId: String, important: Boolean)
}
