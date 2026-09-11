package com.verto.app.ui.screens.home

import com.verto.feature.dashboard.api.ActivityEvent
import com.verto.feature.dashboard.api.ActivityEventKind
import com.verto.feature.dashboard.api.ActivityEventStatus
import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeActivityFeedPolicy339Test {
    private val allowed = HomePermissionContext("org-a", "user-a", setOf("view"))

    @Test fun `visible allowed event dispatches canonical destination`() {
        val event = invoiceEvent()
        assertEquals(
            event.destination,
            resolveVisibleActivityEventDestination(listOf(event), event.eventKey, allowed),
        )
    }

    @Test fun `permission removal and organization change reject at click time`() {
        val event = invoiceEvent()
        assertNull(resolveVisibleActivityEventDestination(
            listOf(event), event.eventKey, allowed.copy(grantedPermissions = emptySet()),
        ))
        assertNull(resolveVisibleActivityEventDestination(
            listOf(event), event.eventKey, HomePermissionContext("org-b", "user-a", setOf("view")),
        ))
    }

    @Test fun `missing event and malformed known destination fail closed`() {
        val event = invoiceEvent()
        assertNull(resolveVisibleActivityEventDestination(listOf(event), "missing", allowed))
        val malformed = event.copy(destination = HomeDestination(HomeDestinationIds.INVOICE_DETAILS))
        assertNull(resolveVisibleActivityEventDestination(listOf(malformed), malformed.eventKey, allowed))
    }

    @Test fun `unknown destination fails closed`() {
        val event = invoiceEvent().copy(destination = HomeDestination("unknown_activity_destination"))
        assertNull(resolveVisibleActivityEventDestination(listOf(event), event.eventKey, allowed))
    }

    private fun invoiceEvent() = ActivityEvent(
        eventKey = "invoice-event",
        organizationId = "org-a",
        title = "invoice",
        description = "created",
        occurredAtEpochMillis = 1L,
        kind = ActivityEventKind.INVOICE,
        status = ActivityEventStatus.CREATED,
        destination = HomeDestination(
            HomeDestinationIds.INVOICE_DETAILS,
            mapOf("invoiceId" to "invoice-a"),
        ),
        requiredPermission = "view",
    )
}
