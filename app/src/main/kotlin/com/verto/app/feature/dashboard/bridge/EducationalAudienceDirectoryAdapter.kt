package com.verto.app.feature.dashboard.bridge

import com.verto.app.feature.organization.domain.repository.OrganizationTeamGateway
import com.verto.feature.dashboard.api.EducationalAudienceDirectory
import com.verto.feature.dashboard.api.EducationalAudienceOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EducationalAudienceDirectoryAdapter @Inject constructor(
    private val teamGateway: OrganizationTeamGateway,
) : EducationalAudienceDirectory {
    override suspend fun roles(): List<EducationalAudienceOption> = listOf(
        EducationalAudienceOption("admin", "المدير"),
        EducationalAudienceOption("accountant", "المحاسب"),
        EducationalAudienceOption("sales", "المبيعات"),
        EducationalAudienceOption("warehouse", "المخزن"),
    )

    override suspend fun users(): List<EducationalAudienceOption> = teamGateway.getEmployees()
        .getOrThrow()
        .filter { it.isActive && it.userId.isNotBlank() }
        .distinctBy { it.userId }
        .sortedBy { it.name }
        .map { employee ->
            EducationalAudienceOption(
                id = employee.userId,
                label = employee.name.ifBlank { employee.userId },
            )
        }
}
