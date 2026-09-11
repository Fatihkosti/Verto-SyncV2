package com.verto.app.feature.integration.optimal.application

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import com.verto.app.feature.integration.optimal.domain.model.RetryOptimalSyncResult
import com.verto.app.feature.integration.optimal.domain.port.OptimalSyncCoordinator
import com.verto.app.feature.integration.optimal.domain.repository.OptimalClock
import com.verto.app.feature.integration.optimal.domain.repository.OptimalOutboxRepository
import javax.inject.Inject

class RetryOptimalSyncUseCase @Inject constructor(
    private val sessionReader: SessionReader,
    private val repository: OptimalOutboxRepository,
    private val operationGuard: OptimalOperationGuard,
    private val syncCoordinator: OptimalSyncCoordinator,
    private val clock: OptimalClock,
    private val audit: WriteAuditPort,
) {
    suspend operator fun invoke(eventId: String): RetryOptimalSyncResult {
        val normalizedEventId = eventId.trim()
        if (normalizedEventId.isBlank()) return RetryOptimalSyncResult.NotFoundOrNotRetryable

        val session = sessionReader.snapshot()
        val organizationId = session.organization.id.trim()
        val userId = session.user.id.trim()
        if (organizationId.isBlank() || userId.isBlank()) {
            return RetryOptimalSyncResult.SessionUnavailable
        }

        val access = operationGuard.check(
            operation = OptimalOperation.RETRY_SYNC,
            layer = OptimalGuardLayer.USE_CASE,
            details = "organizationId=$organizationId,eventId=$normalizedEventId",
        )
        if (access != OptimalAccessDecision.Granted) {
            return RetryOptimalSyncResult.PermissionDenied
        }

        val retried = repository.retryIssue(
            organizationId = organizationId,
            eventId = normalizedEventId,
            retriedAt = clock.nowMillis(),
        )
        if (!retried) return RetryOptimalSyncResult.NotFoundOrNotRetryable

        audit.log(
            action = AuditAction.UPDATE,
            table = AuditTable.OPTIMAL,
            recordId = normalizedEventId,
            summary = "إعادة محاولة مزامنة Optimal للمؤسسة $organizationId",
            employeeId = session.user.id,
            employeeName = session.user.name,
            canUndo = false,
        )
        return RetryOptimalSyncResult.Retried(
            syncScheduled = syncCoordinator.immediateForCurrentSession(),
        )
    }
}
