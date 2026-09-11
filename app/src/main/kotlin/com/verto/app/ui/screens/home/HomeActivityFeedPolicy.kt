package com.verto.app.ui.screens.home

import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext

/** Resolves only events that are still visible at click time. */
internal fun resolveVisibleActivityEventDestination(
    visibleEvents: List<ActivityEvent>,
    eventKey: String,
    context: HomePermissionContext,
): HomeDestination? {
    val event = visibleEvents.firstOrNull { candidate -> candidate.eventKey == eventKey.trim() } ?: return null
    if (event.organizationId != context.organizationId || !event.isAllowedBy(context)) return null
    return event.destination.takeIf(::isValidActivityDestination)
}

private fun isValidActivityDestination(destination: HomeDestination): Boolean = when (destination.id) {
    HomeDestinationIds.INVOICE_DETAILS -> destination.arguments["invoiceId"].isNullOrBlank().not()
    HomeDestinationIds.INVENTORY_ITEM_DETAILS -> destination.arguments["itemId"].isNullOrBlank().not()
    HomeDestinationIds.PARTY_DETAILS ->
        destination.arguments["partyId"].isNullOrBlank().not() &&
            destination.arguments["isSupplier"] in setOf("true", "false")
    HomeDestinationIds.SHIPMENT_DETAILS -> destination.arguments["shipmentId"].isNullOrBlank().not()
    HomeDestinationIds.OPTIMAL_MAINTENANCE_DETAILS -> destination.arguments["recordId"].isNullOrBlank().not()
    else -> false
}

internal fun activityEventAccessibilityLabel(
    event: ActivityEvent,
    formattedTime: String,
): String = buildList {
    add("فتح الحدث")
    add(event.title)
    add(event.description)
    event.subject?.label?.let(::add)
    event.value?.let { value ->
        add(listOfNotNull(value.label, value.text).joinToString(": "))
    }
    event.actor?.displayName?.let { actor -> add("بواسطة $actor") }
    formattedTime.takeIf(String::isNotBlank)?.let(::add)
}.joinToString("، ")
