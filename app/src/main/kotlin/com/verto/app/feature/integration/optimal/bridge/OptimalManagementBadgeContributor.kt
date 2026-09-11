package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.application.ObserveOptimalBadgesUseCase
import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.feature.management.domain.model.ManagementIntegrationBadge
import com.verto.app.feature.management.domain.model.ManagementIntegrationBadgeTone
import com.verto.app.feature.management.domain.model.ManagementIntegrationId
import com.verto.app.feature.management.domain.repository.ManagementIntegrationBadgeContributor
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OptimalManagementBadgeContributor @Inject constructor(
    private val observeBadgesUseCase: ObserveOptimalBadgesUseCase,
) : ManagementIntegrationBadgeContributor {
    override val integrationId = ManagementIntegrationId(OptimalNavigation.INTEGRATION_ID)

    override fun observeBadges(): Flow<List<ManagementIntegrationBadge>> = observeBadgesUseCase().map { counts ->
        buildList {
            if (counts.unreadMessages > 0) {
                add(
                    ManagementIntegrationBadge(
                        label = "الرسائل",
                        count = counts.unreadMessages,
                    ),
                )
            }
            if (counts.syncIssues > 0) {
                add(
                    ManagementIntegrationBadge(
                        label = "الأخطاء",
                        count = counts.syncIssues,
                        tone = ManagementIntegrationBadgeTone.ERROR,
                    ),
                )
            }
        }
    }
}
