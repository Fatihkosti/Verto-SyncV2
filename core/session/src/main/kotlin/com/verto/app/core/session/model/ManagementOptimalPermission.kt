package com.verto.app.core.session.model

import kotlinx.serialization.Serializable

/** أسماء ثابتة ضمن عقد تخزين صلاحيات الإدارة وOptimal. */
@Serializable
enum class ManagementOptimalPermission {
    VIEW_MANAGEMENT,
    VIEW_OPTIMAL,
    VIEW_OPTIMAL_COMPANIES,
    ISSUE_OPTIMAL_CODE,
    VIEW_OPTIMAL_MESSAGES,
    SEND_OPTIMAL_MESSAGES,
    VIEW_OPTIMAL_INVOICES,
    VIEW_OPTIMAL_MAINTENANCE,
    VIEW_OPTIMAL_SYNC_ISSUES,
    RETRY_OPTIMAL_SYNC,
}
