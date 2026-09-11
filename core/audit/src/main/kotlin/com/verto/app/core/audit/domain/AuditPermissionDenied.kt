package com.verto.app.core.audit.domain

import com.verto.app.core.session.domain.SessionReader
import kotlinx.coroutines.flow.first

suspend fun WriteAuditPort.logPermissionDenied(
    action: String,
    details: String = "",
    sessionReader: SessionReader
) {
    val currentUser = runCatching { sessionReader.currentUser.first() }.getOrNull()
    log(
        action = AuditAction.UPDATE,
        table = AuditTable.CLIENT,
        recordId = "permission_denied:$action",
        summary = listOf("رفض صلاحية: $action", details)
            .filter { it.isNotBlank() }
            .joinToString(" — "),
        employeeId = currentUser?.id.orEmpty(),
        employeeName = currentUser?.name.orEmpty(),
        canUndo = false
    )
}
