package com.verto.app.feature.management.domain.repository

import com.verto.app.feature.management.domain.model.ManagementIntegration

fun interface ManagementIntegrationContributor {
    fun integration(): ManagementIntegration
}

fun interface ManagementIntegrationCatalog {
    fun integrations(): List<ManagementIntegration>
}
