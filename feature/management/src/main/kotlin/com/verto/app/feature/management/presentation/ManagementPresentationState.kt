package com.verto.app.feature.management.presentation

import com.verto.app.feature.management.domain.model.ManagementIntegration
import com.verto.app.feature.management.domain.model.ManagementIntegrationBadge
import com.verto.app.feature.management.domain.model.ManagementIntegrationId

data class ManagementPresentationState(
    val integrations: List<ManagementIntegration> = emptyList(),
    val badges: Map<ManagementIntegrationId, List<ManagementIntegrationBadge>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
