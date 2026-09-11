package com.verto.app.feature.management.data

import com.verto.app.feature.management.domain.model.ManagementIntegration
import com.verto.app.feature.management.domain.model.ManagementIntegrationIcon
import com.verto.app.feature.management.domain.model.ManagementIntegrationId
import com.verto.app.feature.management.domain.repository.ManagementIntegrationContributor
import com.verto.app.feature.management.navigation.BenzineNavigation
import javax.inject.Inject

class BenzineManagementIntegrationContributor @Inject constructor() : ManagementIntegrationContributor {
    override fun integration(): ManagementIntegration = ManagementIntegration(
        id = ManagementIntegrationId(BenzineNavigation.INTEGRATION_ID),
        displayName = "بنزين",
        description = "تشغيل AutoDrive: الطلبات، المستخدمون، الصحة والأخطاء",
        route = BenzineNavigation.ROUTE,
        order = 100,
        icon = ManagementIntegrationIcon.BENZINE,
    )
}
