package com.verto.app.feature.management.data

import com.verto.app.feature.management.domain.model.ManagementIntegration
import com.verto.app.feature.management.domain.repository.ManagementIntegrationCatalog
import com.verto.app.feature.management.domain.repository.ManagementIntegrationContributor
import javax.inject.Inject

class DefaultManagementIntegrationCatalog @Inject constructor(
    private val contributors: Set<@JvmSuppressWildcards ManagementIntegrationContributor>,
) : ManagementIntegrationCatalog {
    override fun integrations(): List<ManagementIntegration> = contributors
        .map(ManagementIntegrationContributor::integration)
        .sortedWith(compareBy<ManagementIntegration>({ it.order }, { it.id.value }))
}
