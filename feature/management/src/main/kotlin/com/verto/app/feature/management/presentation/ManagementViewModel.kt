package com.verto.app.feature.management.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.app.feature.management.domain.model.ManagementIntegrationBadge
import com.verto.app.feature.management.domain.model.ManagementIntegrationId
import com.verto.app.feature.management.domain.repository.ManagementIntegrationBadgeContributor
import com.verto.app.feature.management.domain.repository.ManagementIntegrationCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@HiltViewModel
class ManagementViewModel @Inject constructor(
    private val catalog: ManagementIntegrationCatalog,
    private val badgeContributors: Set<@JvmSuppressWildcards ManagementIntegrationBadgeContributor> = emptySet(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManagementPresentationState(isLoading = true))
    val uiState: StateFlow<ManagementPresentationState> = _uiState.asStateFlow()

    private val badgeObservationJobs = mutableListOf<Job>()

    init {
        loadIntegrations()
        observeBadges()
    }

    fun loadIntegrations() {
        runCatching { catalog.integrations() }
            .onSuccess { integrations ->
                mutateState { current ->
                    current.copy(
                        integrations = integrations,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }
            .onFailure {
                mutateState { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = "تعذّر تحميل خدمات الإدارة",
                    )
                }
            }
    }

    private inline fun mutateState(
        transform: (ManagementPresentationState) -> ManagementPresentationState,
    ) {
        synchronized(_uiState) {
            _uiState.value = transform(_uiState.value)
        }
    }

    private fun observeBadges() {
        badgeObservationJobs.forEach(Job::cancel)
        badgeObservationJobs.clear()

        badgeContributors
            .sortedBy { it.integrationId.value }
            .forEach { contributor ->
                badgeObservationJobs += viewModelScope.launch {
                    contributor.safeUpdates().collect { update ->
                        mutateState { current ->
                            current.copy(
                                badges = current.badges + (update.integrationId to update.badges),
                            )
                        }
                    }
                }
            }
    }
}

private data class ManagementBadgeUpdate(
    val integrationId: ManagementIntegrationId,
    val badges: List<ManagementIntegrationBadge>,
)

private fun ManagementIntegrationBadgeContributor.safeUpdates(): Flow<ManagementBadgeUpdate> =
    observeBadges()
        .map { badges ->
            ManagementBadgeUpdate(
                integrationId = integrationId,
                badges = badges.filter { it.count > 0 },
            )
        }
        .onStart { emit(ManagementBadgeUpdate(integrationId, emptyList())) }
        .catch { emit(ManagementBadgeUpdate(integrationId, emptyList())) }
