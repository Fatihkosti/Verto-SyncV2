package com.verto.app.feature.management.domain.model

enum class ManagementIntegrationBadgeTone {
    STANDARD,
    ERROR,
}

data class ManagementIntegrationBadge(
    val label: String,
    val count: Int,
    val tone: ManagementIntegrationBadgeTone = ManagementIntegrationBadgeTone.STANDARD,
) {
    init {
        require(label.isNotBlank()) { "Badge label cannot be blank" }
        require(count >= 0) { "Badge count cannot be negative" }
    }
}
