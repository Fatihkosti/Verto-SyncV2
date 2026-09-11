package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.VehicleSuggestion
import com.verto.app.feature.integration.optimal.domain.repository.OptimalVehicleRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveVehicleSuggestionsUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalVehicleRepository,
) {
    operator fun invoke(
        clientId: String,
        searchTerm: String = "",
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<VehicleSuggestion>> {
        val normalizedClientId = clientId.trim()
        if (normalizedClientId.isEmpty()) return flowOf(emptyList())
        val normalizedSearch = searchTerm.trim()
        val normalizedLimit = limit.coerceIn(1, MAX_LIMIT)

        return sessionReader.organizationId
            .map(String::trim)
            .distinctUntilChanged()
            .flatMapLatest { organizationId ->
                if (organizationId.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    repository.observeSuggestions(
                        organizationId = organizationId,
                        clientId = normalizedClientId,
                        searchTerm = normalizedSearch,
                        limit = normalizedLimit,
                    ).map { suggestions ->
                        suggestions.filter {
                            it.organizationId == organizationId &&
                                it.clientId == normalizedClientId
                        }
                    }
                }
            }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}
