package com.verto.app.ui.screens.home

import com.verto.feature.dashboard.api.HomeDestination

sealed interface HomePendingActionEffect {
    data class Navigate(val destination: HomeDestination) : HomePendingActionEffect
    data class Dial(val phone: String) : HomePendingActionEffect
    data class WhatsApp(val phone: String) : HomePendingActionEffect
    data class RequestSnooze(val eventKey: String) : HomePendingActionEffect
    data class ShowDetails(val eventKey: String) : HomePendingActionEffect
}
