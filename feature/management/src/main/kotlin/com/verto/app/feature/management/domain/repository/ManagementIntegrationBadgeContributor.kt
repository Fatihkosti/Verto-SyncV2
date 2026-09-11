package com.verto.app.feature.management.domain.repository

import com.verto.app.feature.management.domain.model.ManagementIntegrationBadge
import com.verto.app.feature.management.domain.model.ManagementIntegrationId
import kotlinx.coroutines.flow.Flow

/** Optional local-only badge stream contributed by an integration module. */
interface ManagementIntegrationBadgeContributor {
    val integrationId: ManagementIntegrationId

    fun observeBadges(): Flow<List<ManagementIntegrationBadge>>
}
