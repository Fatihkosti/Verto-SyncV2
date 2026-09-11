package com.verto.app.feature.management.domain.model

@JvmInline
value class ManagementIntegrationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Integration id cannot be blank" }
    }
}

enum class ManagementIntegrationIcon {
    BENZINE,
    OPTIMAL,
    GENERIC,
}

data class ManagementIntegration(
    val id: ManagementIntegrationId,
    val displayName: String,
    val description: String,
    val route: String,
    val order: Int,
    val icon: ManagementIntegrationIcon = ManagementIntegrationIcon.GENERIC,
) {
    init {
        require(displayName.isNotBlank()) { "Integration display name cannot be blank" }
        require(description.isNotBlank()) { "Integration description cannot be blank" }
        require(route.isNotBlank()) { "Integration route cannot be blank" }
    }
}
