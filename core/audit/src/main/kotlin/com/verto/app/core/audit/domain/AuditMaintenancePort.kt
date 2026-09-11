package com.verto.app.core.audit.domain

/** عمليات صيانة سجل التدقيق التي لا تنتمي إلى عقد الكتابة. */
interface AuditMaintenancePort {
    suspend fun expireOldEntries()
}
