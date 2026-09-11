package com.verto.app.feature.notifications.domain.model

enum class AppNotificationAudience {
    DIRECT_EMPLOYEE,
    ALL_EMPLOYEES,
    MANAGER_ONLY
}

data class AppNotification(
    val id: String,
    val audience: AppNotificationAudience,
    val title: String,
    val body: String,
    val navigationRoute: String?,
    val isRead: Boolean,
    val createdAt: Long,
    val type: String = "GENERIC_NOTIFICATION",
    val relatedEntityId: String? = null,
    val relatedEntityType: String? = null,
)
