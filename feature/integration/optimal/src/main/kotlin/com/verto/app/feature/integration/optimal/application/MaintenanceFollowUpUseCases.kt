package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUp
import com.verto.app.feature.integration.optimal.domain.model.MaintenanceFollowUpStatus
import com.verto.app.feature.integration.optimal.domain.repository.MaintenanceFollowUpRepository
import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import javax.inject.Inject

sealed interface MaintenanceFollowUpCommandResult {
    data class Saved(val followUp: MaintenanceFollowUp) : MaintenanceFollowUpCommandResult
    data object SessionUnavailable : MaintenanceFollowUpCommandResult
    data object InvalidInput : MaintenanceFollowUpCommandResult
    data object NotFound : MaintenanceFollowUpCommandResult
    data object InvalidTransition : MaintenanceFollowUpCommandResult
}

class StartMaintenanceFollowUpUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: MaintenanceFollowUpRepository,
    private val clock: OptimalClock,
) {
    suspend operator fun invoke(
        recordId: String,
        expectedAt: Long? = null,
    ): MaintenanceFollowUpCommandResult {
        val organizationId = sessionReader.snapshot().organization.id.trim()
        val normalizedRecordId = recordId.trim()
        if (organizationId.isEmpty()) return MaintenanceFollowUpCommandResult.SessionUnavailable
        if (normalizedRecordId.isEmpty()) return MaintenanceFollowUpCommandResult.InvalidInput
        val now = clock.nowMillis()
        if (expectedAt != null && expectedAt < now) {
            return MaintenanceFollowUpCommandResult.InvalidInput
        }
        return runCatching {
            repository.start(organizationId, normalizedRecordId, now, expectedAt)
        }.fold(
            onSuccess = { MaintenanceFollowUpCommandResult.Saved(it) },
            onFailure = { MaintenanceFollowUpCommandResult.NotFound },
        )
    }
}

class UpdateMaintenanceFollowUpStatusUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: MaintenanceFollowUpRepository,
    private val clock: OptimalClock,
) {
    suspend operator fun invoke(
        recordId: String,
        status: MaintenanceFollowUpStatus,
    ): MaintenanceFollowUpCommandResult {
        val organizationId = sessionReader.snapshot().organization.id.trim()
        val normalizedRecordId = recordId.trim()
        if (organizationId.isEmpty()) return MaintenanceFollowUpCommandResult.SessionUnavailable
        if (normalizedRecordId.isEmpty() || status == MaintenanceFollowUpStatus.IN_PROGRESS) {
            return MaintenanceFollowUpCommandResult.InvalidInput
        }
        return runCatching {
            repository.updateStatus(organizationId, normalizedRecordId, status, clock.nowMillis())
        }.fold(
            onSuccess = { updated ->
                updated?.let(MaintenanceFollowUpCommandResult::Saved)
                    ?: MaintenanceFollowUpCommandResult.NotFound
            },
            onFailure = { MaintenanceFollowUpCommandResult.InvalidTransition },
        )
    }
}

class UpdateMaintenanceExpectedAtUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: MaintenanceFollowUpRepository,
    private val clock: OptimalClock,
) {
    suspend operator fun invoke(
        recordId: String,
        expectedAt: Long?,
    ): MaintenanceFollowUpCommandResult {
        val organizationId = sessionReader.snapshot().organization.id.trim()
        val normalizedRecordId = recordId.trim()
        if (organizationId.isEmpty()) return MaintenanceFollowUpCommandResult.SessionUnavailable
        if (normalizedRecordId.isEmpty()) return MaintenanceFollowUpCommandResult.InvalidInput
        val now = clock.nowMillis()
        if (expectedAt != null && expectedAt < now) {
            return MaintenanceFollowUpCommandResult.InvalidInput
        }
        return runCatching {
            repository.updateExpectedAt(organizationId, normalizedRecordId, expectedAt, now)
        }.fold(
            onSuccess = { updated ->
                updated?.let(MaintenanceFollowUpCommandResult::Saved)
                    ?: MaintenanceFollowUpCommandResult.NotFound
            },
            onFailure = { MaintenanceFollowUpCommandResult.InvalidTransition },
        )
    }
}
