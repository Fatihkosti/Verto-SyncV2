package com.verto.app.notifications

import java.util.concurrent.atomic.AtomicReference

/** One-shot in-process handoff from the non-exported notification router. */
object InternalNavigationMailbox {
    private val pendingRoute = AtomicReference<String?>(null)

    fun offer(route: String) {
        NotificationRoutePolicy.validate(route)?.let(pendingRoute::set)
    }

    fun consume(): String? = pendingRoute.getAndSet(null)
}
