package com.verto.app.data.audit

import com.verto.app.core.audit.domain.AuditAction
import com.verto.app.core.audit.domain.AuditTable
import com.verto.app.core.audit.domain.WriteAuditPort
import com.verto.app.utils.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** جسر مؤقت لمستهلكي Data الذين لم ينتقلوا بعد إلى SessionReader. */
suspend fun WriteAuditPort.logPermissionDeniedFromPreferences(
    action: String,
    details: String = "",
    preferences: PreferencesManager
) {
    log(
        action = AuditAction.UPDATE,
        table = AuditTable.CLIENT,
        recordId = "permission_denied:$action",
        summary = listOf("رفض صلاحية: $action", details)
            .filter { it.isNotBlank() }
            .joinToString(" — "),
        employeeId = preferences.userId.firstOrEmpty(),
        employeeName = preferences.userName.firstOrEmpty(),
        canUndo = false
    )
}

private suspend fun Flow<String>.firstOrEmpty(): String =
    runCatching { first() }.getOrDefault("")
