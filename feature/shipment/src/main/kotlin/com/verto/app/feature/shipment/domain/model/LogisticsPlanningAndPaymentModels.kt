package com.verto.app.feature.shipment.domain.model

import java.math.BigDecimal

enum class LogisticsPaymentState { PENDING, POSTING, PAID, ADJUSTING, REVERSED }

data class LogisticsPayment(
    val id: String,
    val organizationId: String,
    val shipmentId: String,
    val costId: String,
    val state: LogisticsPaymentState,
    val amount: BigDecimal,
    val currency: String = "SDG",
    val accountId: String? = null,
    val paidAt: Long? = null,
    val reference: String? = null,
    val proofDocumentId: String? = null,
    val cashReference: String? = null,
    val requestId: String,
    val createdAt: Long,
)

enum class LogisticsRouteTransportPlanKind { UNIFIED, MIXED }

data class LogisticsRouteTemplate(
    val id: String,
    val organizationId: String,
    val name: String,
    val originCountryCode: String,
    val originCity: String,
    val destinationCountryCode: String,
    val destinationCity: String,
    val transportPlanKind: LogisticsRouteTransportPlanKind,
    val unifiedTransportMode: LogisticsLegTransportMode? = null,
    val customsStopOrder: Int? = null,
    val expectedCustomsMinutes: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val stops: List<LogisticsRouteTemplateStop> = emptyList(),
)

data class LogisticsRouteTemplateStop(
    val id: String,
    val templateId: String,
    val order: Int,
    val countryCode: String,
    val city: String,
    val placeName: String = "",
    val expectedTransitMinutesToNext: Int? = null,
)

