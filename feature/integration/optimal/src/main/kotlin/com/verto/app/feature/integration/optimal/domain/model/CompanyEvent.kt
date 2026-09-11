package com.verto.app.feature.integration.optimal.domain.model

enum class CompanyEventType {
    INVOICE,
    PAYMENT,
    MESSAGE,
}

data class CompanyEvent(
    val organizationId: String,
    val clientId: String,
    val eventId: String,
    val type: CompanyEventType,
    val occurredAt: Long,
    val title: String,
    val description: String,
    val amount: Double? = null,
)

data class CompanyEventsSnapshot(
    val organizationId: String = "",
    val clientId: String = "",
    val events: List<CompanyEvent> = emptyList(),
    val canViewInvoices: Boolean = false,
    val canViewMessages: Boolean = false,
    val accessDenied: Boolean = false,
)
