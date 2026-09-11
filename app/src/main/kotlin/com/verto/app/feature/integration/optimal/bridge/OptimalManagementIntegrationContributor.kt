package com.verto.app.feature.integration.optimal.bridge

import com.verto.app.feature.integration.optimal.navigation.OptimalNavigation
import com.verto.app.feature.management.domain.model.ManagementIntegration
import com.verto.app.feature.management.domain.model.ManagementIntegrationIcon
import com.verto.app.feature.management.domain.model.ManagementIntegrationId
import com.verto.app.feature.management.domain.repository.ManagementIntegrationContributor
import javax.inject.Inject

class OptimalManagementIntegrationContributor @Inject constructor() : ManagementIntegrationContributor {
    override fun integration(): ManagementIntegration = ManagementIntegration(
        id = ManagementIntegrationId(OptimalNavigation.INTEGRATION_ID),
        displayName = "Optimal",
        description = "إدارة شركات الصيانة والرسائل والفواتير المرتبطة",
        route = OptimalNavigation.ROUTE,
        order = 200,
        icon = ManagementIntegrationIcon.OPTIMAL,
    )
}
