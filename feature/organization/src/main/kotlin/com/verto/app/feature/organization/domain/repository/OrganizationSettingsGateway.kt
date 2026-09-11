package com.verto.app.feature.organization.domain.repository

import com.verto.app.feature.organization.domain.model.OrganizationSettings
import kotlinx.coroutines.flow.Flow

/** عقد إعدادات المؤسسة للواجهة دون كشف التخزين المحلي أو عميل الشبكة. */
interface OrganizationSettingsGateway {
    val settings: Flow<OrganizationSettings>

    suspend fun save(settings: OrganizationSettings)
    suspend fun refresh()
}
