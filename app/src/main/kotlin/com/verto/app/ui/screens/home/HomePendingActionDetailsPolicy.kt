package com.verto.app.ui.screens.home

import com.verto.feature.dashboard.api.PendingAction

internal fun PendingAction.shouldOpenDetails(actionId: String? = null): Boolean {
    if (details == null) return false
    if (actionId == null) return true
    val action = actions.firstOrNull { it.id == actionId.trim() } ?: return false
    return action.destination == destination
}
