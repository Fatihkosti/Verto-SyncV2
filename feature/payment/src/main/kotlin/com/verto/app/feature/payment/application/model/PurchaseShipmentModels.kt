package com.verto.app.feature.payment.application.model

data class PurchaseShipmentOption(
    val id: String,
    val title: String,
    val statusLabel: String,
)

data class PurchaseShipmentCreateCommand(
    val shipmentNumber: String,
    val origin: String,
    val destination: String,
    val routeStops: List<String>,
    val createdAt: Long,
)
