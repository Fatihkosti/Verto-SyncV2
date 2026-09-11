package com.verto.app.data.remote

import com.verto.app.core.security.TenantIsolationPolicy
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.remote.dto.OrgSettingsDto
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrganizationSettingsRemote @Inject constructor(
    private val sessionReader: SessionReader
) {
    suspend fun fetch(organizationId: String): OrgSettingsDto? {
        val orgId = currentTenant(organizationId)
        return VertoSupabase.client.postgrest["organization_settings"]
            .select { filter { eq("organization_id", orgId) } }
            .decodeSingleOrNull<OrgSettingsDto>()
            ?.also { TenantIsolationPolicy.requireSameTenant(orgId, it.organizationId) }
    }

    suspend fun upsert(settings: OrgSettingsDto) {
        val orgId = currentTenant(settings.organizationId)
        VertoSupabase.client.postgrest["organization_settings"]
            .upsert(settings.copy(organizationId = orgId)) { onConflict = "organization_id" }
    }

    private suspend fun currentTenant(requestedOrganizationId: String): String {
        val sessionOrgId = sessionReader.organizationId.first()
        return TenantIsolationPolicy.requireSameTenant(sessionOrgId, requestedOrganizationId)
    }
}
