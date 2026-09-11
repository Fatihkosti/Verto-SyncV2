package com.verto.app.feature.integration.optimal.data

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.core.session.domain.SessionReader
import com.verto.app.data.remote.PermissionProvider
import com.verto.app.feature.integration.optimal.domain.model.OptimalAccessDecision
import com.verto.app.feature.integration.optimal.domain.model.OptimalBackendContractGate
import com.verto.app.feature.integration.optimal.domain.model.OptimalGuardLayer
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperation
import com.verto.app.feature.integration.optimal.domain.model.OptimalOperationGuard
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DefaultOptimalOperationGuard @Inject constructor(
    private val permissionProvider: PermissionProvider,
    private val backendContractGate: OptimalBackendContractGate,
    private val audit: WriteAuditPort,
    private val sessionReader: SessionReader,
) : OptimalOperationGuard {
    override suspend fun check(
        operation: OptimalOperation,
        layer: OptimalGuardLayer,
        details: String,
    ): OptimalAccessDecision {
        if (!permissionProvider.canPermissionNow(operation.permission)) {
            logDenied("permission_denied", operation, layer, details)
            return OptimalAccessDecision.PermissionDenied
        }

        val contract = operation.remoteContract
        if (contract != null && !backendContractGate.allows(contract)) {
            logDenied("backend_contract_blocked", operation, layer, details)
            return OptimalAccessDecision.BackendContractBlocked
        }
        return OptimalAccessDecision.Granted
    }

    private suspend fun logDenied(
        reason: String,
        operation: OptimalOperation,
        layer: OptimalGuardLayer,
        details: String,
    ) {
        val user = runCatching { sessionReader.currentUser.first() }.getOrNull()
        audit.log(
            action = AuditAction.UPDATE,
            table = AuditTable.OPTIMAL,
            recordId = "$reason:${operation.permission.name}:${layer.name}",
            summary = listOf(reason, operation.permission.name, layer.name, details)
                .filter(String::isNotBlank)
                .joinToString(" — "),
            employeeId = user?.id.orEmpty(),
            employeeName = user?.name.orEmpty(),
            canUndo = false,
        )
    }
}
