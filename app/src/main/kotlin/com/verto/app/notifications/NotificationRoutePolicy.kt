package com.verto.app.notifications

/** Single allow-listed resolver used by both FCM taps and Notification Center rows. */
object NotificationRoutePolicy {
    private const val FALLBACK = "notifications_standalone"

    private val exactRoutes = setOf(
        "home",
        "inventory",
        "messages",
        FALLBACK,
        "commission_management",
        "users_dashboard",
        "management/benzine",
    )
    private val invoiceRoute = Regex("^invoice/[A-Za-z0-9_-]{1,128}\\?openCommission=(true|false)$")
    private val identifierRoute = Regex(
        "^(client_dashboard|logistics_v2_detail|user_dashboard_detail)/[A-Za-z0-9_-]{1,128}$"
    )

    fun validate(route: String?): String? {
        val raw = route?.trim()?.takeIf { it.length in 1..240 } ?: return null
        val normalized = if (raw.startsWith("client/")) "client_dashboard/${raw.removePrefix("client/")}" else raw
        return normalized.takeIf {
            it in exactRoutes || invoiceRoute.matches(it) || identifierRoute.matches(it)
        }
    }

    fun resolve(
        type: String,
        requestedRoute: String?,
        relatedEntityId: String? = null,
        relatedEntityType: String? = null,
        data: Map<String, String> = emptyMap(),
    ): String {
        validate(requestedRoute)?.let { return it }

        val normalizedType = type.trim().uppercase()
        val relatedType = relatedEntityType?.trim()?.lowercase().orEmpty()
        val invoiceId = data["invoice_id"]
            ?: relatedEntityId?.takeIf { relatedType.contains("invoice") }
        val shipmentId = data["shipment_id"]
            ?: relatedEntityId?.takeIf { relatedType.contains("shipment") }

        val derived = when (normalizedType) {
            "ADMIN_COMMISSION_NEEDED" -> invoiceId?.let { "invoice/$it?openCommission=true" }
            "CREDIT_SALE_INVOICE_CREATED",
            "PAYMENT_RECORDED",
            "PAYMENT_DUE_REMINDER",
            "PAYMENT_OVERDUE",
            "PURCHASE_INVOICE_CREATED" -> invoiceId?.let { "invoice/$it?openCommission=false" }
            "LOW_STOCK" -> "inventory"
            "GOODS_RECEIVED" -> shipmentId?.let { "logistics_v2_detail/$it" }
            "WITHDRAWAL_REQUESTED" -> "commission_management"
            "MARKETER_REGISTERED", "AUTODRIVE_JOIN_REQUEST" -> "management/benzine"
            "NEW_CHAT_MESSAGE", "CHAT" -> "messages"
            else -> null
        }
        return validate(derived) ?: FALLBACK
    }
}
