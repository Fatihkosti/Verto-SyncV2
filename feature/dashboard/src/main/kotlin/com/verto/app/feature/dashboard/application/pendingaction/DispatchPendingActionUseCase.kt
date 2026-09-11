package com.verto.app.feature.dashboard.application.pendingaction

import com.verto.feature.dashboard.api.HomeDestination
import com.verto.feature.dashboard.api.HomeDestinationIds
import com.verto.feature.dashboard.api.HomePermissionContext
import com.verto.feature.dashboard.api.PendingAction
import javax.inject.Inject

/**
 * Re-authorizes every pending-action invocation against the latest visible snapshot.
 * UI visibility is never treated as authorization: stale, removed, malformed, or forbidden
 * invocations are rejected even when called directly.
 */
class DispatchPendingActionUseCase @Inject constructor() {

    fun openEvent(
        context: HomePermissionContext,
        visiblePendingActions: List<PendingAction>,
        eventKey: String,
    ): PendingActionDispatchResult {
        val event = authorizedEvent(context, visiblePendingActions, eventKey)
            ?: return PendingActionDispatchResult.Rejected(PendingActionRejectionReason.EVENT_NOT_AVAILABLE)
        return authorizeDestination(event, event.destination)
    }

    fun dispatchAction(
        context: HomePermissionContext,
        visiblePendingActions: List<PendingAction>,
        eventKey: String,
        actionId: String,
    ): PendingActionDispatchResult {
        val event = authorizedEvent(context, visiblePendingActions, eventKey)
            ?: return PendingActionDispatchResult.Rejected(PendingActionRejectionReason.EVENT_NOT_AVAILABLE)
        val action = event.actions.firstOrNull { it.id == actionId.trim() }
            ?: return PendingActionDispatchResult.Rejected(PendingActionRejectionReason.ACTION_NOT_AVAILABLE)
        if (!context.allows(action.requiredPermission)) {
            return PendingActionDispatchResult.Rejected(PendingActionRejectionReason.PERMISSION_DENIED)
        }
        return authorizeDestination(event, action.destination)
    }

    fun canManageEvent(
        context: HomePermissionContext,
        visiblePendingActions: List<PendingAction>,
        eventKey: String,
    ): Boolean = authorizedEvent(context, visiblePendingActions, eventKey) != null

    private fun authorizedEvent(
        context: HomePermissionContext,
        visiblePendingActions: List<PendingAction>,
        eventKey: String,
    ): PendingAction? {
        val normalizedKey = eventKey.trim()
        if (normalizedKey.isEmpty()) return null
        return visiblePendingActions.firstOrNull { it.eventKey == normalizedKey }
            ?.takeIf { it.isAllowedBy(context) }
    }

    private fun authorizeDestination(
        event: PendingAction,
        destination: HomeDestination,
    ): PendingActionDispatchResult {
        val valid = when (destination.id) {
            HomeDestinationIds.PARTY_CALL,
            HomeDestinationIds.PARTY_WHATSAPP -> destination.arguments[PHONE_ARGUMENT].isNullOrBlank().not()

            HomeDestinationIds.PENDING_ACTION_REMIND ->
                destination.arguments[EVENT_KEY_ARGUMENT] == event.eventKey

            else -> true
        }
        return if (valid) {
            PendingActionDispatchResult.Authorized(destination)
        } else {
            PendingActionDispatchResult.Rejected(PendingActionRejectionReason.INVALID_DESTINATION)
        }
    }

    companion object {
        const val PHONE_ARGUMENT = "phone"
        const val EVENT_KEY_ARGUMENT = "eventKey"
    }
}

sealed interface PendingActionDispatchResult {
    data class Authorized(val destination: HomeDestination) : PendingActionDispatchResult
    data class Rejected(val reason: PendingActionRejectionReason) : PendingActionDispatchResult
}

enum class PendingActionRejectionReason {
    EVENT_NOT_AVAILABLE,
    ACTION_NOT_AVAILABLE,
    PERMISSION_DENIED,
    INVALID_DESTINATION,
}
