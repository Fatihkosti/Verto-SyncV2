package com.verto.app.feature.organization.bridge
import com.verto.app.data.model.PermissionDeniedException
import com.verto.app.data.remote.AuthRepository
import com.verto.app.data.repository.OrgSettingsRepository
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.organization.domain.model.OrganizationSettings
import com.verto.app.feature.organization.domain.repository.OrganizationSettingsGateway
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.audit.domain.logPermissionDenied
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** يحافظ على cache-first وSupabase الحاليين ويضيف حد الصلاحية في مكان واحد. */
@Singleton
class OrganizationSettingsGatewayAdapter @Inject constructor(
    private val repository: OrgSettingsRepository,
    private val authRepository: AuthRepository,
    private val auditLogger: WriteAuditPort,
    private val sessionReader: SessionReader
) : OrganizationSettingsGateway {

    override val settings: Flow<OrganizationSettings> = repository.orgSettings

    override suspend fun save(settings: OrganizationSettings) {
        requireAdmin()
        repository.saveOrgSettings(settings)
    }

    override suspend fun refresh() {
        repository.syncFromSupabase()
    }

    private suspend fun requireAdmin() {
        val isAdmin = runCatching { authRepository.getMyProfile()?.role == "admin" }
            .getOrDefault(false)
        if (isAdmin) return

        runCatching {
            auditLogger.logPermissionDenied(
                action = "settings_org_data",
                details = "بيانات المؤسسة متاحة للمدير فقط",
                sessionReader = sessionReader
            )
        }
        throw PermissionDeniedException("بيانات المؤسسة متاحة للمدير فقط")
    }
}
