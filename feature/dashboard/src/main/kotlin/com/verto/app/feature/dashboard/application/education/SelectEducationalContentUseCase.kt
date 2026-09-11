package com.verto.app.feature.dashboard.application.education

import com.verto.app.core.session.domain.SessionReader
import com.verto.feature.dashboard.api.EducationalAudienceContext
import com.verto.feature.dashboard.api.EducationalContent
import com.verto.feature.dashboard.api.EducationalContentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Selects one stable card per local day from topics visible to the current tenant, role, and user. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SelectEducationalContentUseCase @Inject constructor(
    private val repository: EducationalContentRepository,
    private val sessionReader: SessionReader,
) {
    operator fun invoke(
        rotationDay: Long,
        restoredContentId: String? = null,
    ): Flow<EducationalContent?> = combine(
        sessionReader.organizationId,
        sessionReader.userId,
        sessionReader.role,
    ) { organizationId, userId, role ->
        Triple(organizationId.trim(), userId.trim(), role.trim().lowercase())
    }
        .distinctUntilChanged()
        .flatMapLatest { (organizationId, userId, role) ->
            if (organizationId.isBlank() || userId.isBlank()) {
                flowOf(null)
            } else {
                repository.observeVisible(
                    EducationalAudienceContext(
                        organizationId = organizationId,
                        userId = userId,
                        role = role,
                    ),
                ).map { available ->
                    select(
                        available = available,
                        rotationDay = rotationDay,
                        restoredContentId = restoredContentId,
                    )
                }
            }
        }
        .distinctUntilChanged()

    internal fun select(
        available: List<EducationalContent>,
        rotationDay: Long,
        restoredContentId: String?,
    ): EducationalContent? {
        val visible = available
            .asSequence()
            .filter(EducationalContent::isActive)
            .distinctBy(EducationalContent::id)
            .sortedBy(EducationalContent::id)
            .toList()
        if (visible.isEmpty()) return null

        restoredContentId
            ?.takeIf(String::isNotBlank)
            ?.let { restoredId -> visible.firstOrNull { it.id == restoredId } }
            ?.let { return it }

        val index = Math.floorMod(rotationDay, visible.size.toLong()).toInt()
        return visible[index]
    }
}
